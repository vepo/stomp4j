package dev.vepo.stomp4j.server.tests.infra;

import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class TestSsl {
    private static final String KEYSTORE_RESOURCE = "/ssl/keystore.p12";
    private static final String PASSWORD = "changeit";

    private TestSsl() {}

    public static SSLContext serverSslContext() {
        return createSslContext(true);
    }

    public static SSLContext trustingClientSslContext() {
        return createSslContext(false);
    }

    public static String keyStorePath() {
        try (InputStream inputStream = TestSsl.class.getResourceAsStream(KEYSTORE_RESOURCE)) {
            var tempFile = Files.createTempFile("stomp4j-test", ".p12");
            Files.write(tempFile, inputStream.readAllBytes());
            tempFile.toFile().deleteOnExit();
            return tempFile.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load test keystore", ex);
        }
    }

    public static String keyStorePassword() {
        return PASSWORD;
    }

    private static SSLContext createSslContext(boolean keyManagers) {
        try (InputStream inputStream = TestSsl.class.getResourceAsStream(KEYSTORE_RESOURCE)) {
            var keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(inputStream, PASSWORD.toCharArray());
            var sslContext = SSLContext.getInstance("TLS");
            if (keyManagers) {
                var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, PASSWORD.toCharArray());
                sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            } else {
                var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore);
                sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            }
            return sslContext;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create SSL context", ex);
        }
    }
}
