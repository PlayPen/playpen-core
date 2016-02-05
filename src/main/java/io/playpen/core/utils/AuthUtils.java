package io.playpen.core.utils;

import com.google.common.io.ByteStreams;
import io.playpen.core.protocol.Protocol;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.output.NullOutputStream;
import org.jasypt.encryption.pbe.StandardPBEByteEncryptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

public class AuthUtils {

    private static StandardPBEByteEncryptor getEncryptor(String key)
    {
        StandardPBEByteEncryptor encryptor = new StandardPBEByteEncryptor();
        encryptor.setPassword(key);
        encryptor.setAlgorithm("PBEWithSHA1AndRC4_128");
        encryptor.setKeyObtentionIterations(4000);
        return encryptor;
    }

    public static byte[] encrypt(byte[] bytes, String key)
    {
        StandardPBEByteEncryptor encryptor = getEncryptor(key);
        return encryptor.encrypt(bytes);
    }

    public static byte[] decrypt(byte[] bytes, String key)
    {
        StandardPBEByteEncryptor encryptor = getEncryptor(key);
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

    public static String createPackageChecksum(String fp) throws IOException {
        Path path = Paths.get(fp);
        try (CheckedInputStream is = new CheckedInputStream(Files.newInputStream(path), new CRC32())) {
            ByteStreams.copy(is, NullOutputStream.NULL_OUTPUT_STREAM);
            return Long.toHexString(is.getChecksum().getValue());
        }
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
