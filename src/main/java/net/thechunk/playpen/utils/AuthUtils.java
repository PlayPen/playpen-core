package net.thechunk.playpen.utils;

import net.thechunk.playpen.protocol.Protocol;
import org.apache.commons.codec.binary.Hex;
import org.jasypt.util.binary.BasicBinaryEncryptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthUtils {

    public static String createHash(String key, String message) {
        return createHash(key, message.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] encrypt(byte[] bytes, String key)
    {
        BasicBinaryEncryptor encryptor = new BasicBinaryEncryptor();
        encryptor.setPassword(key);
        return encryptor.encrypt(bytes);
    }

    public static byte[] decrypt(byte[] bytes, String key)
    {
        BasicBinaryEncryptor encryptor = new BasicBinaryEncryptor();
        encryptor.setPassword(key);
        return encryptor.decrypt(bytes);
    }

    public static String createHash(String key, byte[] message) {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(message);
            out.write(key.getBytes());
        }
        catch(IOException e) {
            throw new AssertionError(e);
        }

        digest.reset();
        digest.update(out.toByteArray());

        return Hex.encodeHexString(digest.digest());
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
