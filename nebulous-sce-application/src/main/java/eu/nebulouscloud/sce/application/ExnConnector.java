package eu.nebulouscloud.sce.application;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.qpid.protonj2.client.Message;
import org.apache.qpid.protonj2.client.exceptions.ClientException;

import org.slf4j.MDC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A class that connects to the EXN middleware and starts listening to
 * messages from the ActiveMQ server.
 *
 * <p>
 * This class will drive the main behavior of the optimiser-controller: the
 * `Consumer` objects created in {@link ExnConnector#ExnConnector} receive
 * incoming messages and react to them, sending out messages in turn.
 */
@Slf4j
public class ExnConnector {

    /** The Connector used to talk with ActiveMQ */
    private final Connector conn;

    private Context context_ = null;

    private SLAMonitoringModule slaMonitoringModule;
    private SLAProcessingModule slaProcessingModule;
    private EventHandlerModule eventHandlerModule;

    /**
     * Map to store appId -> Set of metric names for each SLA
     * This helps us identify which metrics belong to which SLA
     */
    private final Map<String, Set<String>> slaMetrics = new ConcurrentHashMap<>();

    /**
     * Map to store dynamic consumers created for metric monitoring
     * Key: topic name, Value: Consumer instance
     */
    private final Map<String, List<Consumer>> dynamicConsumers = new ConcurrentHashMap<>();

    /**
     * Safely obtain the connection Context object. Since the {@link
     * #context_} field is set asynchronously after the ExnConnector
     * constructor has finished, there is a race condition where we might hit
     * a null value if using the field directly.
     */
    public Context getContext() {
        if (context_ == null) {
            synchronized (this) {
                while (context_ == null) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        log.error("Caught InterruptException while waiting for ActiveMQ connection Context; looping",
                                e);
                    }
                }
            }
        }
        return context_;
    }

    /** A counter to create unique names for SyncedPublisher instances. */
    // private AtomicInteger publisherNameCounter = new AtomicInteger(1);

    /** A counter to create unique names for dynamic Consumer instances. */
    private AtomicInteger consumerNameCounter = new AtomicInteger(1);

    /** if non-null, signals after the connector is stopped */
    private CountDownLatch synchronizer = null;

    private static final ObjectMapper mapper = new ObjectMapper();

    /** The topic where we listen for sla creation messages. */
    public static final String sla_creation_channel = "eu.nebulouscloud.ontology.sla";

    /** Base pattern for monitoring topics */
    public static final String monitoring_topic_base = "eu.nebulouscloud.monitoring.realtime.";

    public static final String violation_notification_channel = "eu.nebulouscloud.monitoring.sla.violation";

    public static final String tran_sett_notification_channel = "eu.nebulouscloud.monitoring.sla.level";

    @Getter
    private final Publisher ViolationPublisher;
    private final Publisher Tran_Sett_Publisher;

    /**
     * Create a connection to ActiveMQ via the exn middleware, and set up the
     * initial publishers and consumers.
     *
     * @param host     the host of the ActiveMQ server (probably "localhost")
     * @param port     the port of the ActiveMQ server (usually 5672)
     * @param name     the login name to use
     * @param password the login password to use
     */
    public ExnConnector(String host, int port, String name, String password,
            SLAMonitoringModule slaMonitoringModule,
            SLAProcessingModule slaProcessingModule,
            EventHandlerModule eventHandlerModule) {

        // store modules
        this.slaMonitoringModule = slaMonitoringModule;
        this.slaProcessingModule = slaProcessingModule;
        this.eventHandlerModule = eventHandlerModule;

        eventHandlerModule.setExnConnector(this);

        ViolationPublisher = new Publisher("violation_detection_notification", violation_notification_channel, true,
                true);
        Tran_Sett_Publisher = new Publisher("Transition_settlement_detection_notification",
                tran_sett_notification_channel, true,
                true);

        conn = new Connector(
                "Smart_Contract_Encapsulator",
                new ConnectorHandler() {
                    public void onReady(Context context) {
                        ExnConnector.this.context_ = context;
                        synchronized (ExnConnector.this) {
                            ExnConnector.this.notifyAll();
                        }
                        log.info("Smart-Contract-Encapsulator connected to ActiveMQ, got connection context {}",
                                context);
                    }
                },
                List.of(ViolationPublisher, Tran_Sett_Publisher),
                List.of(
                        new Consumer("sla_to_smart_contract", sla_creation_channel,
                                new SlaCreationMessageHandler(), true, true)),
                true,
                true,
                new StaticExnConfig(host, port, name, password, 15, "eu.nebulouscloud"));
    }

    /**
     * Connect to ActiveMQ and activate all publishers and consumers. It is
     * an error to start the controller more than once.
     *
     * @param synchronizer if non-null, a countdown latch that will be
     *                     signaled when the connector is stopped by calling {@link
     *                     CountDownLatch#countDown} once.
     */
    public synchronized void start(CountDownLatch synchronizer) {
        this.synchronizer = synchronizer;
        conn.start();
        log.debug("ExnConnector started.");
    }

    /**
     * Disconnect from ActiveMQ and stop all Consumer processes. Also count
     * down the countdown latch passed in the {@link
     * #start(CountDownLatch)} method if applicable.
     */
    public synchronized void stop() {
        conn.stop();
        if (synchronizer != null) {
            synchronizer.countDown();
        }
        log.debug("ExnConnector stopped.");
    }

    /**
     * Extract metric names from the SLA JSON structure
     * 
     * @param slaJson The SLA JSON as JsonNode
     * @return Set of metric names found in the SLA
     */
    private Set<String> extractMetricNames(JsonNode slaJson) {
        Set<String> metricNames = new HashSet<>();

        try {
            JsonNode metricsNode = slaJson.path("metrics");
            if (metricsNode.isArray()) {
                for (JsonNode metricNode : metricsNode) {
                    JsonNode nameNode = metricNode.path("name");
                    if (!nameNode.isMissingNode() && !nameNode.isNull()) {
                        String metricName = nameNode.asText();
                        if (metricName != null && !metricName.trim().isEmpty()) {
                            metricNames.add(metricName.trim());
                            log.debug("Extracted metric name: {}", metricName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting metric names from SLA JSON", e);
        }

        return metricNames;
    }

    /**
     * Create and register dynamic consumers for monitoring topics based on metric
     * names
     * 
     * @param appId       The application ID
     * @param metricNames Set of metric names to create consumers for
     */
    private void createDynamicConsumersForMetrics(String appId, Set<String> metricNames) {
        for (String metricName : metricNames) {
            String topicName = monitoring_topic_base + metricName;

            try {
                String consumerName = "dynamic_metric_consumer_" + consumerNameCounter.incrementAndGet();
                Consumer dynamicConsumer = new Consumer(
                        consumerName,
                        topicName,
                        new DynamicMetricMessageHandler(metricName, appId),
                        true,
                        true);

                // And modify the storage logic:
                dynamicConsumers.computeIfAbsent(topicName, k -> new ArrayList<>()).add(dynamicConsumer);

                // Register the consumer with the connection context
                Context context = getContext();
                if (context != null) {
                    try {
                        // Add the consumer to the active connection
                        context.registerConsumer(dynamicConsumer);
                        log.info("Successfully created dynamic consumer for topic: {} (metric: {}, appId: {})",
                                topicName, metricName, appId);
                    } catch (Exception e) {
                        log.error("Failed to register dynamic consumer for topic: {}", topicName, e);
                        List<Consumer> consumers = dynamicConsumers.get(topicName);
                        if (consumers != null) {
                            consumers.remove(dynamicConsumer);
                            if (consumers.isEmpty()) {
                                dynamicConsumers.remove(topicName);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error creating dynamic consumer for topic: {} (metric: {})", topicName, metricName, e);
            }

        }
    }

    /**
     * A message handler that processes sla creation messages coming in via
     * `eu.nebulouscloud.sla.creation`.
     */
    public class SlaCreationMessageHandler extends Handler {
        @Override
        public void onMessage(String key, String address, Map body, Message message, Context context) {
            try {

                final SLAProcessingModule sla_processing_module = ExnConnector.this.slaProcessingModule;

                log.info("SLA creation message received");
                final JsonNode appMessage = mapper.valueToTree(body);

                // Read appID safely
                final String appID = appMessage.path("slaName").asText(null);
                if (appID == null || appID.isEmpty()) {
                    log.error("Missing appID in incoming message; aborting");
                    return;
                }
                MDC.put("appId", appID);

                System.out.println("app-message-" + appID + ".json: " + appMessage.toPrettyString());
                log.info("Received SLA creation message, starting Convert SLA to Smart Contract");

                // Parse SLA JSON to extract metrics
                try {
                    Set<String> metricNames = extractMetricNames(appMessage);

                    if (!metricNames.isEmpty()) {
                        log.info("Extracted {} metrics for appId {}: {}", metricNames.size(), appID, metricNames);

                        // Store metrics for this SLA
                        slaMetrics.put(appID, metricNames);

                        // Create dynamic consumers for these metrics
                        createDynamicConsumersForMetrics(appID, metricNames);
                    } else {
                        log.warn("No metrics found in SLA for appId: {}", appID);
                    }

                } catch (Exception e) {
                    log.error("Failed to parse SLA JSON for metric extraction", e);
                }

                final Map<String, String> contextMap = MDC.getCopyOfContextMap();
                new Thread(() -> {
                    MDC.setContextMap(contextMap);
                    try {
                        // MainFunction may throw checked Exception
                        String createResult = sla_processing_module.createSLA(appMessage);
                        log.info("Event MainFunction result: {}", createResult);
                    } catch (Exception ex) {
                        log.error("Exception while running MainFunction for appId=" + appID, ex);
                    } finally {
                        // Clear MDC in this thread when done
                        MDC.clear();
                    }
                }).start();

            } catch (RuntimeException e) {
                log.error("Error while receiving sla creation message", e);
            } finally {
                MDC.clear();
            }
        }
    }

    /**
     * A dynamic message handler that processes metric messages coming in via
     * dynamically created monitoring topics.
     */
    public class DynamicMetricMessageHandler extends Handler {
        private final String metricName;
        private final String appId;

        public DynamicMetricMessageHandler(String metricName, String appId) {
            this.metricName = metricName;
            this.appId = appId;
        }

        @Override
        public void onMessage(String key, String address, Map body, Message message, Context context) {
            try {
                final EventHandlerModule event_handler_module = ExnConnector.this.eventHandlerModule;

                Object appIdObject = null;
                String messageAppId = null;
                try {
                    appIdObject = message.property("application");
                    if (appIdObject == null)
                        appIdObject = message.subject();
                } catch (ClientException e) {
                    log.error("Received event message {} without application property, aborting", body);
                    return;
                }

                if (appIdObject == null) {
                    log.error("Received event message {} without application property, aborting", body);
                    return;
                } else {
                    messageAppId = appIdObject.toString(); // should be a string already
                }

                if (messageAppId.equals(appId)) {

                    log.info("Dynamic metric message received for metric: {} with appId {}", metricName, appId);

                    // Convert incoming Map -> JsonNode safely
                    final JsonNode appMessage = mapper.valueToTree(body);

                    // Check if this appID has metrics that include this metric name
                    Set<String> appMetrics = slaMetrics.get(appId);
                    if (appMetrics == null || !appMetrics.contains(metricName)) {
                        log.debug("Ignoring metric message for appId: {} metric: {} - not in SLA", appId, metricName);
                        return;
                    }

                    MDC.put("appId", appId);
                    MDC.put("metricName", metricName);

                    // Build target JSON structure
                    ObjectNode root = mapper.createObjectNode();
                    root.put("slaName", appId);

                    // Read timestamp and metric_value safely from the Map/JsonNode
                    String timestamp = appMessage.path("timestamp").asText(null);
                    root.put("timestamp", timestamp);

                    String value = appMessage.path("metricValue").asText(null);
                    if (value == null) {
                        log.warn("metricValue missing for metric {}; defaulting to empty string", metricName);
                        value = "";
                    }

                    ObjectNode metric = mapper.createObjectNode();
                    metric.put(metricName, value); // Use the actual metric name dynamically
                    root.set("metric", metric);

                    // Convert to JSON string
                    final String event_message;
                    try {
                        event_message = mapper.writeValueAsString(root);
                    } catch (JsonProcessingException jpe) {
                        log.error("Failed to serialize event message to JSON for metric: {}", metricName, jpe);
                        return; // can't proceed if serialization fails
                    }

                    System.out
                            .println("event-" + metricName + "-message-" + appId + ".json: "
                                    + appMessage.toPrettyString());

                    log.info("Received Event message for metric: {}, starting processing Event", metricName);

                    final Map<String, String> contextMap = MDC.getCopyOfContextMap();
                    new Thread(() -> {
                        MDC.setContextMap(contextMap);
                        try {
                            // MainFunction may throw checked Exception
                            String createResult = event_handler_module.MainFunction(event_message);
                            log.info("Event MainFunction result for metric {}: {}", metricName, createResult);
                        } catch (Exception ex) {
                            log.error(
                                    "Exception while running MainFunction for appId=" + appId + " metric=" + metricName,
                                    ex);
                        } finally {
                            // Clear MDC in this thread when done
                            MDC.clear();
                        }
                    }).start();
                }

            } catch (RuntimeException e) {
                log.error("Error while receiving dynamic metric message for metric: {}", metricName, e);
            } finally {
                MDC.clear();
            }
        }
    }

    /**
     * Clean up resources for a specific SLA
     * 
     * @param appId The application ID to clean up
     */
    public void cleanupSLA(String appId) {
        Set<String> metrics = slaMetrics.remove(appId);
        if (metrics != null) {
            for (String metricName : metrics) {
                String topicName = monitoring_topic_base + metricName;
                List<Consumer> consumers = dynamicConsumers.get(topicName);
                if (consumers != null) {
                    // Remove consumers that belong to this appId
                    consumers.removeIf(consumer -> {

                        return false;
                    });

                    if (consumers.isEmpty()) {
                        dynamicConsumers.remove(topicName);
                    }
                }
            }
            log.info("Cleaned up SLA metrics for appId: {} - metrics: {}", appId, metrics);
        }
    }

    /**
     * Get all registered SLAs and their metrics
     * 
     * @return Map of appId -> Set of metric names
     */
    public Map<String, Set<String>> getSLAMetrics() {
        return new HashMap<>(slaMetrics);
    }

    public void sendViolationEvent(String appID, ObjectNode violation_event) {
        Map<String, Object> msg = mapper.convertValue(violation_event, Map.class);
        ViolationPublisher.send(msg, appID);
    }

    public void sendTransitionEvent(String appID, ObjectNode transition_event) {
        Map<String, Object> msg = mapper.convertValue(transition_event, Map.class);
        Tran_Sett_Publisher.send(msg, appID);
    }

    public void sendSettlementEvent(String appID, ObjectNode settlement_event) {
        Map<String, Object> msg = mapper.convertValue(settlement_event, Map.class);
        Tran_Sett_Publisher.send(msg, appID);
    }

}