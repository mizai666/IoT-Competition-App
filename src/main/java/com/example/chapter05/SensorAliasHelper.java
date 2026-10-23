package com.example.chapter05;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * 传感器显示名称（别名）统一读写工具。
 * 存储位置：SharedPreferences "sensor_alias"，键格式 alias_{projectId}_{原始键名}。
 */
public class SensorAliasHelper {

    public static final String PREFS_NAME = "sensor_alias";
    private static final String KEY_PREFIX = "alias_";

    private SensorAliasHelper() {
    }

    /** 生成存储键。 */
    public static String keyFor(int projectId, String originalKey) {
        return KEY_PREFIX + projectId + "_" + originalKey;
    }

    /** 解析显示名：有别名返回别名，否则返回原始键名。 */
    public static String resolve(Context ctx, int projectId, String originalKey) {
        if (originalKey == null) return null;
        String alias = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(keyFor(projectId, originalKey), null);
        return (alias == null || alias.isEmpty()) ? originalKey : alias;
    }

    /** 加载某个项目下全部「原始键名 -> 显示名」映射。 */
    public static Map<String, String> loadAll(Context ctx, int projectId) {
        Map<String, String> map = new HashMap<>();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String prefix = KEY_PREFIX + projectId + "_";
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue() != null) {
                map.put(e.getKey().substring(prefix.length()), e.getValue().toString());
            }
        }
        return map;
    }

    /** 保存显示名。 */
    public static void save(Context ctx, int projectId, String originalKey, String alias) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(keyFor(projectId, originalKey), alias).apply();
    }

    /** 清除某个项目的全部别名。 */
    public static void clearProject(Context ctx, int projectId) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String prefix = KEY_PREFIX + projectId + "_";
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.apply();
    }
}
