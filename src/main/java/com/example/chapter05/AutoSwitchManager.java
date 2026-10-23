package com.example.chapter05;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.Map;

/**
 * 自动开关管理器（单例）。
 * 每个项目一条配置，键值匹配自动/手动模式切换。
 */
public class AutoSwitchManager {
    private static final String TAG = "AutoSwitchManager";
    private static final String PREF_NAME = "auto_switch_config";
    private static final String KEY_PREFIX = "auto_switch_";

    private static AutoSwitchManager instance;
    private Context appContext;
    private SharedPreferences prefs;
    private Gson gson;

    private AutoSwitchManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public static synchronized AutoSwitchManager getInstance(Context context) {
        if (instance == null) {
            instance = new AutoSwitchManager(context.getApplicationContext());
        }
        return instance;
    }

    public AutoSwitchItem getConfig(int projectId) {
        String json = prefs.getString(KEY_PREFIX + projectId, "");
        if (json.isEmpty()) return null;
        return gson.fromJson(json, AutoSwitchItem.class);
    }

    public void saveConfig(int projectId, AutoSwitchItem item) {
        prefs.edit().putString(KEY_PREFIX + projectId, gson.toJson(item)).apply();
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }

    /**
     * 手动切换模式并向下位机发送指令。
     */
    public void setMode(int projectId, String mode) {
        AutoSwitchItem item = getConfig(projectId);
        if (item == null) return;
        item.setCurrentMode(mode);
        saveConfig(projectId, item);

        MqttManager mqtt = MqttManager.getInstance(appContext);
        if (mqtt.isConnected()) {
            String value = item.isAuto() ? item.getAutoValue() : item.getManualValue();
            String json = "{\"" + item.getKeyName() + "\":" + value + "}";
            mqtt.publish(item.getPublishTopic(), json, mqtt.getQos(), projectId);
            Log.d(TAG, "发送模式指令: " + json);
        }
    }

    /**
     * 收到消息时检查是否需要自动切换模式。
     * 命中 autoValue -> 自动，命中 manualValue -> 手动（不重复发指令）。
     */
    public void checkMode(String payload) {
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(KEY_PREFIX)) continue;
            int projectId;
            try {
                projectId = Integer.parseInt(key.substring(KEY_PREFIX.length()));
            } catch (NumberFormatException e) {
                continue;
            }
            AutoSwitchItem item = gson.fromJson((String) entry.getValue(), AutoSwitchItem.class);
            if (item == null) continue;
            String matched = matchValue(payload, item.getKeyName());
            if (matched == null) continue;
            if (matched.equals(item.getAutoValue()) && !item.isAuto()) {
                item.setCurrentMode(AutoSwitchItem.MODE_AUTO);
                saveConfig(projectId, item);
                Log.d(TAG, "项目 " + projectId + " 自动切换为自动模式");
            } else if (matched.equals(item.getManualValue()) && !item.isManual()) {
                item.setCurrentMode(AutoSwitchItem.MODE_MANUAL);
                saveConfig(projectId, item);
                Log.d(TAG, "项目 " + projectId + " 自动切换为手动模式");
            }
        }
    }

    private String matchValue(String payload, String keyName) {
        try {
            JSONObject json = new JSONObject(payload);
            if (json.has(keyName)) {
                return json.optString(keyName);
            }
        } catch (Exception ignored) { }
        return null;
    }
}
