package as.studio.facebook_plugin;

import org.godotengine.godot.Dictionary;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

class GodotFacebookUtils {
    public static Dictionary jsonToDictionary(JSONObject object) throws JSONException {
        Dictionary map = new Dictionary();
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            map.put(key, fromJson(object.get(key)));
        }
        return map;
    }

    private static Object fromJson(Object json) throws JSONException {
        if (json == JSONObject.NULL) {
            return null;
        } else if (json instanceof JSONObject) {
            return jsonToDictionary((JSONObject) json);
        } else if (json instanceof JSONArray) {
            ArrayList<Object> list = jsonToList((JSONArray) json);
            return list.toArray();
        } else {
            return json;
        }
    }

    public static ArrayList<Object> jsonToList(JSONArray array) throws JSONException {
        ArrayList<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(fromJson(array.get(i)));
        }
        return list;
    }
}
