package com.example.chapter05;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务管理器（单例）。
 * 内存缓存所有项目的定时任务，内部 Handler 每秒 tick，
 * 倒计时归零时通过 MqttManager 向发布主题发送 JSON 指令并重置。
 */
public class ScheduleManager {
    private static final String TAG = "ScheduleManager";
    private static final String PREF_NAME = "schedule_config";
    private static final String KEY_PREFIX = "schedules_";

    private static ScheduleManager instance;
    private Context appContext;
    private SharedPreferences prefs;
    private Gson gson;
    private final Map<Integer, List<ScheduleItem>> cache = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tickRunnable;

    private ScheduleManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        loadAllFromPrefs();
        startTicking();
    }

    public static synchronized ScheduleManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScheduleManager(context.getApplicationContext());
        }
        return instance;
    }

    private void loadAllFromPrefs() {
        cache.clear();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(KEY_PREFIX)) {
                try {
                    int projectId = Integer.parseInt(key.substring(KEY_PREFIX.length()));
                    List<ScheduleItem> list = parse((String) entry.getValue());
                    for (ScheduleItem item : list) {
                        item.setRemainingSeconds(item.getScheduleSeconds());
                    }
                    cache.put(projectId, list);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "无法解析项目ID的 key: " + key);
                }
            }
        }
    }

    private List<ScheduleItem> parse(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        Type type = new TypeToken<List<ScheduleItem>>() {}.getType();
        List<ScheduleItem> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    private void startTicking() {
        if (tickRunnable != null) return;
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                tick();
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(tickRunnable, 1000);
    }

    private void tick() {
        boolean fired = false;
        for (Map.Entry<Integer, List<ScheduleItem>> entry : cache.entrySet()) {
            for (ScheduleItem item : entry.getValue()) {
                if (item.tick()) {
                    fire(item, entry.getKey());
                    fired = true;
                }
            }
        }
        if (fired) {
            Log.d(TAG, "定时任务触发，已发送指令");
        }
    }

    private void fire(ScheduleItem item, int projectId) {
        MqttManager mqtt = MqttManager.getInstance(appContext);
        if (!mqtt.isConnected()) {
            Log.w(TAG, "MQTT 未连接，跳过定时任务发送: " + item.getDisplayName());
            return;
        }
        String payload = "{\"" + item.getKeyName() + "\":" + item.getSendValue() + "}";
        mqtt.publish(item.getPublishTopic(), payload, mqtt.getQos(), projectId);
        Log.d(TAG, "定时任务执行: " + item.getDisplayName() + " -> [" + item.getPublishTopic() + "] " + payload);
    }

    /** 响应规则匹配后重置倒计时 */
    public void resetSchedule(int projectId, String keyName) {
        List<ScheduleItem> list = getSchedules(projectId);
        for (ScheduleItem item : list) {
            if (item.getKeyName().equals(keyName)) {
                item.setRemainingSeconds(item.getScheduleSeconds());
                Log.d(TAG, "重置定时任务倒计时: " + item.getDisplayName());
            }
        }
    }

    // ========== CRUD ==========

    public List<ScheduleItem> getSchedules(int projectId) {
        List<ScheduleItem> list = cache.get(projectId);
        if (list == null) {
            list = new ArrayList<>();
            cache.put(projectId, list);
        }
        return list;
    }

    public void addSchedule(int projectId, ScheduleItem item) {
        List<ScheduleItem> list = getSchedules(projectId);
        list.add(item);
        save(projectId, list);
    }

    public void updateSchedule(int projectId, int position, ScheduleItem item) {
        List<ScheduleItem> list = getSchedules(projectId);
        if (position >= 0 && position < list.size()) {
            item.setRemainingSeconds(item.getScheduleSeconds());
            list.set(position, item);
            save(projectId, list);
        }
    }

    public void deleteSchedule(int projectId, int position) {
        List<ScheduleItem> list = getSchedules(projectId);
        if (position >= 0 && position < list.size()) {
            list.remove(position);
            save(projectId, list);
        }
    }

    public void setScheduleEnabled(int projectId, int position, boolean enabled) {
        List<ScheduleItem> list = getSchedules(projectId);
        if (position >= 0 && position < list.size()) {
            list.get(position).setEnabled(enabled);
            list.get(position).setRemainingSeconds(list.get(position).getScheduleSeconds());
            save(projectId, list);
        }
    }

    private void save(int projectId, List<ScheduleItem> list) {
        prefs.edit().putString(KEY_PREFIX + projectId, gson.toJson(list)).apply();
    }

    public void clearAll() {
        cache.clear();
        prefs.edit().clear().apply();
    }
}
