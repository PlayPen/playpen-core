package net.thechunk.playpen.utils;

import com.google.protobuf.Message;
import net.thechunk.playpen.protocol.Protocol;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

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

        digest.update(key.getBytes(StandardCharsets.UTF_8));
        digest.update(message);

        return Hex.encodeHexString(digest.digest());
    }

    public static boolean validateHash(String hash, String key, String message) {
        return validateHash(hash, key, message.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean validateHash(String hash, String key, byte[] message) {
        return hash.equals(createHash(key, message));
    }

    public static boolean validateHash(Protocol.AuthenticatedMessage payload) {
        return validateHash(payload.getHash(), payload.getUuid(), payload.getPayload().toByteArray());
    }

    private AuthUtils() {}
}
