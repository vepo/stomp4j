package dev.vepo.stomp4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.codec.binary.Hex;

public class ClientKey {
    private static final Random RANDOM = new SecureRandom();

    private static byte[] generateClientKey() {
        try {
            MessageDigest salt = MessageDigest.getInstance("SHA-256");
            salt.update(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            return salt.digest();
        } catch (NoSuchAlgorithmException e) {
            byte[] bytes = new byte[16];
            RANDOM.nextBytes(bytes);
            return bytes;
        }
    }

    private byte[] key;

    public ClientKey() {
        this.key = generateClientKey();
    }

    public String toHex() {
        return Hex.encodeHexString(key);
    }

    public String toString() {
        return String.format("ClientKey[key=%s]", toHex());
    }
}
