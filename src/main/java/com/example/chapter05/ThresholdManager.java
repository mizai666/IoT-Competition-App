package com.example.chapter05;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ThresholdManager {
    private static final String PREF_NAME = "threshold_config";
    private static final String KEY_PREFIX = "thresholds_";
    private SharedPreferences prefs;
    private Gson gson;

    public ThresholdManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public List<ThresholdItem> getThresholds(int projectId) {
        String key = KEY_PREFIX + projectId;
        String json = prefs.getString(key, "");
        Log.d("ThresholdManager", "项目 " + projectId + " 的阈值配置 JSON: " + json);
        if (json.isEmpty()) return new ArrayList<>();
        Type type = new TypeToken<List<ThresholdItem>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public void saveThresholds(int projectId, List<ThresholdItem> thresholds) {
        String key = KEY_PREFIX + projectId;
        String json = gson.toJson(thresholds);
        prefs.edit().putString(key, json).apply();
    }

    public void addThreshold(int projectId, ThresholdItem item) {
        List<ThresholdItem> list = getThresholds(projectId);
        list.add(item);
        saveThresholds(projectId, list);
    }

    public void updateThreshold(int projectId, int position, ThresholdItem item) {
        List<ThresholdItem> list = getThresholds(projectId);
        if (position >= 0 && position < list.size()) {
            list.set(position, item);
            saveThresholds(projectId, list);
        }
    }

    public void deleteThreshold(int projectId, int position) {
        List<ThresholdItem> list = getThresholds(projectId);
        if (position >= 0 && position < list.size()) {
            list.remove(position);
            saveThresholds(projectId, list);
        }
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}