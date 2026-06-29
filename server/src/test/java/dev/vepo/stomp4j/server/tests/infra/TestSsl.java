package dev.vepo.stomp4j.server.tests.infra;

import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class TestSsl {
    private static final String KEYSTORE_RESOURCE = "/ssl/keystore.p12";
    private static final String PASSWORD = "changeit";

    private TestSsl() {}

    public static SSLContext serverSslContext() {
        return createServerSslContext();
    }

    public static SSLContext trustingClientSslContext() {
        try {
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { new TrustAllManager() }, new SecureRandom());
            return sslContext;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create trusting client SSL context", ex);
        }
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

    private static SSLContext createServerSslContext() {
        try (InputStream inputStream = TestSsl.class.getResourceAsStream(KEYSTORE_RESOURCE)) {
            var keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(inputStream, PASSWORD.toCharArray());
            var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, PASSWORD.toCharArray());
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            return sslContext;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create server SSL context", ex);
        }
    }

    private static final class TrustAllManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
