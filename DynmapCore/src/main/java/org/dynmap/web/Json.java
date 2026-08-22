package org.dynmap.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

public class Json {
    private static final Gson gson = new Gson();

    public static String stringifyJson(Object o) {
        return gson.toJson(o);
    }

    public static JsonElement toJsonTree(Object value) {
        return gson.toJsonTree(value);
    }
}
