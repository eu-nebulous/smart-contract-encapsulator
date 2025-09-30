package eu.nebulouscloud.sce.application;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.ScopeType;
import static picocli.CommandLine.Option;

/**
 * The main class of the Smart Contract Encapsulator application.
 */
@Slf4j
@Command(name = "nebulous-smart-contract-encapsulator-application", version = "0.1",

        mixinStandardHelpOptions = true, sortOptions = false, separator = " ", showAtFileInUsageHelp = true, description = "Receive sla creation messages or events from the UI and start up the SCE infrastructure."

)
public class Main implements Callable<Integer> {

    @Option(names = {
            "--activemq-host" }, description = "The hostname of the ActiveMQ server.  Can also be set via the @|bold ACTIVEMQ_HOST|@ environment variable.", paramLabel = "ACTIVEMQ_HOST", defaultValue = "${ACTIVEMQ_HOST:-localhost}")
    private String activemq_host;

    @Option(names = {
            "--activemq-port" }, description = "The port of the ActiveMQ server.  Can also be set via the @|bold ACTIVEMQ_PORT|@ environment variable.", paramLabel = "ACTIVEMQ_PORT", defaultValue = "${ACTIVEMQ_PORT:-5672}")
    private int activemq_port;

    @Option(names = {
            "--activemq-user" }, description = "The user name for the ActiveMQ server. Can also be set via the @|bold ACTIVEMQ_USER|@ environment variable.", paramLabel = "ACTIVEMQ_USER", defaultValue = "${ACTIVEMQ_USER}")
    private String activemq_user;

    @Option(names = {
            "--activemq-password" }, description = "The password for the ActiveMQ server. Can also be set via the @|bold ACTIVEMQ_PASSWORD|@ environment variable.", paramLabel = "ACTIVEMQ_PASSWORD", defaultValue = "${ACTIVEMQ_PASSWORD}")
    private String activemq_password;

    @Option(names = {
            "--log-dir" }, description = "Directory where to log incoming and outgoing messages as files. Can also be set via the @|bold LOGDIR|@ variable.", paramLabel = "LOGDIR", defaultValue = "${LOGDIR}")
    @Getter
    private static Path logDirectory;

    @Option(names = {
            "--app-activemq-host" }, description = "The hostname of the ActiveMQ server in a deployed app.  Can also be set via the @|bold APP_ACTIVEMQ_HOST|@ environment variable.", paramLabel = "APP_ACTIVEMQ_HOST", defaultValue = "${APP_ACTIVEMQ_HOST}")
    @Getter
    private static String appBrokerAddress;

    @Option(names = {
            "--app-nebulous-scripts-branch" }, description = "The name of the branch of the scripts to use for app deployment.  Can also be set via the @|bold APP_NEBULOUS_SCRIPTS_BRANCH|@ environment variable.", paramLabel = "APP_NEBULOUS_SCRIPTS_BRANCH", defaultValue = "${APP_NEBULOUS_SCRIPTS_BRANCH:-main}")
    @Getter
    private static String appNebulousScriptsBranch;

    @Option(names = {
            "--app-activemq-port" }, description = "The port of the ActiveMQ server in a deployed app.  Can also be set via the @|bold APP_ACTIVEMQ_PORT|@ environment variable.", paramLabel = "APP_ACTIVEMQ_PORT", defaultValue = "${APP_ACTIVEMQ_PORT:-5672}")
    @Getter
    private static int appBrokerPort;

    @Option(names = {
            "--onm-ip" }, description = "The IP address of the ONM server in a deployed app.  Can also be set via the @|bold ONM_IP|@ environment variable.  NOTE: will be deprecated soon.", paramLabel = "ONM_IP", defaultValue = "${ONM_IP}")
    @Getter
    private static String onmIp;

    @Option(names = {
            "--onm-url" }, description = "The URL of the ONM server in a deployed app.  Can also be set via the @|bold ONM_URL|@ environment variable.", paramLabel = "ONM_URL", defaultValue = "${ONM_URL}")
    @Getter
    private static String onmUrl;

    @Option(names = { "--verbose",
            "-v" }, description = "Turn on more verbose logging output. Can be given multiple times. When not given, print only warnings and error messages. With @|underline -v|@, print status messages. With @|underline -vvv|@, print everything.", scope = ScopeType.INHERIT)
    private boolean[] verbosity;

    /**
     * The ActiveMQ connector.
     *
     * @return the ActiveMQ connector wrapper, or null if running offline.
     */
    @Getter
    private static ExnConnector activeMQConnector = null;

    /**
     * PicoCLI execution strategy that uses common initialization.
     */
    private int executionStrategy(ParseResult parseResult) {
        init();
        return new CommandLine.RunLast().execute(parseResult);
    }

    /**
     * Initialization code shared between this class and any
     * subcommands: set logging level, create log directory and create
     * the ActiveMQ adapter. Note that we do not start the EXN
     * ActiveMQ middleware, so each main method needs to call
     * `activeMQConnector.start` if needed.
     */
    private void init() {
        final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("eu.nebulouscloud");
        if (!(logger instanceof ch.qos.logback.classic.Logger)) {
            log.info("Cannot set log level: logger not of class ch.qos.logback.classic.Logger");
        } else {
            ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) logger;
            if (verbosity != null) {
                switch (verbosity.length) {
                    case 0:
                        break;
                    case 1:
                        logbackLogger.setLevel(ch.qos.logback.classic.Level.INFO);
                        break;
                    case 2:
                        logbackLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
                        break;
                    case 3:
                        logbackLogger.setLevel(ch.qos.logback.classic.Level.TRACE);
                        break;
                    default:
                        logbackLogger.setLevel(ch.qos.logback.classic.Level.ALL);
                        break;
                }
            }
        }

        log.debug("Beginning common startup of sce-application");
        // Set up directory for file logs (dumps of contents of incoming or
        // outgoing messages).
        if (logDirectory != null) {
            if (!Files.exists(logDirectory)) {
                try {
                    Files.createDirectories(logDirectory);
                } catch (IOException e) {
                    log.warn("Could not create log directory {}. Continuing without file logging.");
                    logDirectory = null;
                }
            } else if (!Files.isDirectory(logDirectory) || !Files.isWritable(logDirectory)) {
                log.warn(
                        "Trying to use a file as log directory, or directory not writable: {}. Continuing without file logging.",
                        logDirectory);
                logDirectory = null;
            } else {
                log.info("Logging all messages to directory {}", logDirectory);
            }
        }

        SLAMonitoringModule monitoring = null;
        SLAProcessingModule processing = null;
        EventHandlerModule eventHandler = null;

        try {
            monitoring = new SLAMonitoringModule(); // constructor throws Exception
            processing = new SLAProcessingModule(); // if this also throws Exception
            eventHandler = new EventHandlerModule(); // if this also throws Exception
        } catch (Exception e) {
            log.error("Failed to initialize modules", e);
            // handle failure, maybe exit or continue with null modules
        }

        // Start connection to ActiveMQ if possible.
        if (activemq_user != null && activemq_password != null) {
            log.info("Preparing ActiveMQ connection: host={} port={}",
                    activemq_host, activemq_port);
            activeMQConnector = new ExnConnector(activemq_host, activemq_port,
                    activemq_user, activemq_password,
                    monitoring, processing, eventHandler);
        } else {
            log.debug("ActiveMQ login info not set, only operating locally.");
        }
    }

    /**
     * The main method of the main class.
     *
     * @return 0 if no error during execution, otherwise greater than 0
     */
    @Override
    public Integer call() {
        CountDownLatch exn_synchronizer = new CountDownLatch(1);
        if (activeMQConnector != null) {
            log.debug("Starting connection to ActiveMQ");
            activeMQConnector.start(exn_synchronizer);
        } else {
            log.error(
                    "ActiveMQ connector not initialized so we're unresponsive. Will keep running to keep CI/CD happy but don't expect anything more from me.");
        }
        // Note that we try to synchronize, even if we didn't connect to
        // ActiveMQ. This is so that the container can be deployed. (If the
        // container terminates, the build registers as unsuccessful.)
        log.info("Smart-Contract-Encapsulator Application Initialization complete, waiting for messages");
        try {
            exn_synchronizer.await();
        } catch (InterruptedException e) {
            // ignore
        }
        return 0;
    }

    /**
     * External entry point for the main class. Parses command-line
     * parameters and invokes the `call` method.
     *
     * @param args the command-line parameters as passed by the user
     */
    public static void main(String[] args) {
        Main main = new Main();
        int exitCode = new CommandLine(main)
                .setExecutionStrategy(main::executionStrategy) // perform common initialization
                .execute(args);
        System.exit(exitCode);
    }

    public static void logFile(String name, Object contents) {
        if (Main.logDirectory == null)
            return;
        String prefix = LocalDateTime.now().toString()
                .replace(":", "-"); // make Windows less unhappy
        Path path = logDirectory.resolve(prefix + "--" + name);
        try (FileWriter out = new FileWriter(path.toFile())) {
            out.write(contents.toString());
            log.trace("Wrote log file {}", path);
        } catch (IOException e) {
            log.warn("Error while trying to create data file in log directory", e);
        }
    }
}
