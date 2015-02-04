package net.thechunk.playpen.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONUtils {

    public static JSONObject safeGetObject(JSONObject obj, String key) {
        try {
            return obj.getJSONObject(key);
        }
        catch(JSONException e) {
            return null;
        }
    }

    public static JSONObject safeGetObject(JSONArray obj, int key) {
        try {
            return obj.getJSONObject(key);
        }
        catch(JSONException e) {
            return null;
        }
    }

    public static JSONArray safeGetArray(JSONObject obj, String key) {
        try {
            return obj.getJSONArray(key);
        }
        catch(JSONException e) {
            return null;
        }
    }

    public static JSONArray safeGetArray(JSONArray obj, int key) {
        try {
            return obj.getJSONArray(key);
        }
        catch(JSONException e) {
            return null;
        }
    }

    public static String safeGetString(JSONObject obj, String key) {
        try {
            return obj.getString(key);
        }
        catch(JSONException e) {
            return null;
        }
    }

    public static String safeGetString(JSONArray obj, int key) {
        try {
            return obj.getString(key);
        }
        catch(JSONException e) {
            return null;
        }
    }

    public static Double safeGetDouble(JSONObject obj, String key) {
        try {
            return obj.getDouble(key);
        }
        catch(JSONException e) {
            return 0.0;
        }
    }

    public static Double safeGetDouble(JSONArray obj, int key) {
        try {
            return obj.getDouble(key);
        }
        catch(JSONException e) {
            return null;
        }
    }

    private JSONUtils() {}
}
