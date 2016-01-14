package io.playpen.core.utils;

import io.playpen.core.protocol.Protocol;
import org.apache.commons.codec.binary.Hex;
import org.jasypt.util.binary.BasicBinaryEncryptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthUtils {

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

        digest.update(message);
        digest.update(key.getBytes(StandardCharsets.UTF_8));

        return Hex.encodeHexString(digest.digest());
    }

    public static String createPackageChecksum(String path) throws IOException {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }

        try (InputStream is = Files.newInputStream(Paths.get(path));
            DigestInputStream dis = new DigestInputStream(is, digest))
        {
            while (dis.read() != -1) {}
        }

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
