package net.thechunk.playpen.utils;

import org.apache.commons.codec.digest.DigestUtils;

public class AuthUtils {

    public static String createHash(String key, String payload) {
        return DigestUtils.md5Hex(payload + key);
    }

    public static boolean validateHash(String hash, String key, String payload) {
        String localHash = DigestUtils.md5Hex(payload + key);
        return localHash.equals(hash);
    }

    private AuthUtils() {}
}
