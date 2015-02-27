package net.thechunk.playpen.utils;

import net.thechunk.playpen.protocol.Protocol;
import org.apache.commons.codec.binary.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthUtils {

    public static String createHash(String key, String message) {
        return createHash(key, message.getBytes(StandardCharsets.UTF_8));
    }

    public static String createHash(String key, byte[] message) {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }

        String mpk = message + key;

        return Hex.encodeHexString(digest.digest(mpk.getBytes()));
    }

    public static boolean validateHash(String hash, String key, String message) {
        return validateHash(hash, key, message.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean validateHash(String hash, String key, byte[] message) {
        return hash.equals(createHash(key, message));
    }

    public static boolean validateHash(Protocol.AuthenticatedMessage payload, String key) {
        return validateHash(payload.getHash(), key, payload.getPayload().toByteArray());
    }

    private AuthUtils() {}
}
