package net.thechunk.playpen.utils;

import net.thechunk.playpen.protocol.Protocol;
import org.apache.commons.codec.binary.Hex;
import org.jasypt.encryption.pbe.StandardPBEByteEncryptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthUtils {

    private static StandardPBEByteEncryptor getEncryptor(String key)
    {
        // Many of jasypt's defaults are truly scary. This makes things less scarier but I still have no confidence in it.
        StandardPBEByteEncryptor encryptor = new StandardPBEByteEncryptor();
        encryptor.setPassword(key);
        encryptor.setKeyObtentionIterations(1500);
        encryptor.setAlgorithm("PBEwithSHA1AndRC4_128");
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
