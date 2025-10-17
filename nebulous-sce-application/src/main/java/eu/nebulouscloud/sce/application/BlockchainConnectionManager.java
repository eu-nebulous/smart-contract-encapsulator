package eu.nebulouscloud.sce.application;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;

public class BlockchainConnectionManager {
    private static final String CHANNEL = "sla-channel";
    private static final String CHAINCODE = "nebulous-smart-contract";

    private static BlockchainConnectionManager instance;

    private Gateway gateway;
    private Contract contract;
    private Network network;
    private ManagedChannel grpcChannel;
    private ObjectMapper mapper;

    private BlockchainConnectionManager() throws Exception {
        this.mapper = new ObjectMapper();
        initializeBlockchainConnection();
    }

    public static synchronized BlockchainConnectionManager getInstance() throws Exception {
        if (instance == null) {
            instance = new BlockchainConnectionManager();
        }
        return instance;
    }

    private void initializeBlockchainConnection() throws Exception {
        // Environment variables and default paths
        String mspId = System.getenv().getOrDefault("APP_MSP_ID", "BrokerOrgMSP");
        Path certPath = Paths.get(System.getenv().getOrDefault("APP_CERT_PATH",
                "wallet/user1@brokerorg.neb.com/msp/signcerts/cert.pem"));

        Path keyPath = null;
        String envKeyPath = System.getenv("APP_KEY_PATH");
        String envKeyDir = System.getenv("APP_KEY_DIR");
        if (envKeyPath != null && !envKeyPath.isBlank()) {
            keyPath = Paths.get(envKeyPath);
        } else if (envKeyDir != null && !envKeyDir.isBlank()) {
            keyPath = findPrivateKeyFile(Paths.get(envKeyDir));
        } else {
            keyPath = findPrivateKeyFile(Paths.get("wallet/user1@brokerorg.neb.com/msp/keystore"));
        }

        Path tlsCertPath = Paths.get(System.getenv().getOrDefault("CA_CERT_PATH",
                "wallet/certs/tlsca.brokerorg.neb.com-cert.pem"));
        Path connectionProfilePath = Paths.get(System.getenv().getOrDefault("APP_CONN_PROFILE",
                "connection-brokerorg.json"));

        // Load identity (certificate)
        X509Certificate certificate;
        try (Reader certReader = Files.newBufferedReader(certPath)) {
            certificate = Identities.readX509Certificate(certReader);
        }
        Identity identity = new X509Identity(mspId, certificate);

        // Load private key
        PrivateKey privateKey;
        try (Reader keyReader = Files.newBufferedReader(keyPath)) {
            privateKey = Identities.readPrivateKey(keyReader);
        }
        Signer signer = Signers.newPrivateKeySigner(privateKey);

        // Parse connection profile
        JsonNode conn = mapper.readTree(connectionProfilePath.toFile());
        JsonNode peersNode = conn.get("peers");
        if (peersNode == null || !peersNode.fieldNames().hasNext()) {
            throw new IllegalArgumentException("No peers found in connection profile");
        }

        String firstPeerName = peersNode.fieldNames().next();
        JsonNode peerData = peersNode.get(firstPeerName);

        String peerUrl = peerData.get("url").asText();
        if (peerUrl.startsWith("grpcs://")) {
            peerUrl = peerUrl.substring("grpcs://".length());
        } else if (peerUrl.startsWith("grpc://")) {
            peerUrl = peerUrl.substring("grpc://".length());
        }

        String authorityOverride = null;
        JsonNode grpcOptions = peerData.get("grpcOptions");
        if (grpcOptions != null && grpcOptions.has("ssl-target-name-override")) {
            authorityOverride = grpcOptions.get("ssl-target-name-override").asText();
        }

        // Create gRPC channel and gateway
        grpcChannel = NettyChannelBuilder.forTarget(peerUrl)
                .sslContext(GrpcSslContexts.forClient()
                        .trustManager(tlsCertPath.toFile())
                        .build())
                .overrideAuthority(authorityOverride == null ? "" : authorityOverride)
                .build();

        Gateway.Builder builder = Gateway.newInstance()
                .identity(identity)
                .signer(signer)
                .connection(grpcChannel);

        gateway = builder.connect();
        network = gateway.getNetwork(CHANNEL);
        contract = network.getContract(CHAINCODE);

        System.out.println("✅ Blockchain connection initialized successfully");
    }

    public Contract getContract() {
        return contract;
    }

    public Network getNetwork() {
        return network;
    }

    public Gateway getGateway() {
        return gateway;
    }

    private Path findPrivateKeyFile(Path keyDir) throws IOException {
        if (!Files.exists(keyDir) || !Files.isDirectory(keyDir)) {
            throw new IOException("Keystore directory does not exist: " + keyDir.toString());
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(keyDir)) {
            Path first = null;
            for (Path p : ds) {
                if (Files.isDirectory(p))
                    continue;
                String name = p.getFileName().toString();
                if (name.endsWith("_sk")) {
                    return p;
                }
                if (first == null)
                    first = p;
            }
            if (first != null)
                return first;
        }
        throw new IOException("No private key file found in keystore directory: " + keyDir.toString());
    }

    public void close() {
        try {
            if (gateway != null) {
                gateway.close();
            }
            if (grpcChannel != null) {
                grpcChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            System.err.println("Error closing Blockchain Connection Manager: " + e.getMessage());
        }
    }
}