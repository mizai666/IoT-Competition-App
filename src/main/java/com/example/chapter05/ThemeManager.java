package com.example.chapter05;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 主题管理器（单例）。每个项目一组主题，仅一个激活。
 */
public class ThemeManager {
    private static final String PREF_NAME = "theme_config";
    private static final String KEY_PREFIX = "themes_";

    public static final String[] THEME_CODES = {"normal", "warning", "danger", "info", "offline"};
    public static final String[] THEME_LABELS = {"常规", "警告", "危险", "信息", "离线"};

    private static ThemeManager instance;
    private SharedPreferences prefs;
    private Gson gson;

    private ThemeManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context.getApplicationContext());
        }
        return instance;
    }

    public List<ThemeItem> getThemes(int projectId) {
        String json = prefs.getString(KEY_PREFIX + projectId, "");
        if (json.isEmpty()) return new ArrayList<>();
        Type type = new TypeToken<List<ThemeItem>>() {}.getType();
        List<ThemeItem> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public void saveThemes(int projectId, List<ThemeItem> themes) {
        prefs.edit().putString(KEY_PREFIX + projectId, gson.toJson(themes)).apply();
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }

    public void addTheme(int projectId, ThemeItem item) {
        List<ThemeItem> list = getThemes(projectId);
        list.add(item);
        saveThemes(projectId, list);
    }

    public void updateTheme(int projectId, int position, ThemeItem item) {
        List<ThemeItem> list = getThemes(projectId);
        if (position >= 0 && position < list.size()) {
            list.set(position, item);
            saveThemes(projectId, list);
        }
    }

    public void deleteTheme(int projectId, int position) {
        List<ThemeItem> list = getThemes(projectId);
        if (position >= 0 && position < list.size()) {
            list.remove(position);
            saveThemes(projectId, list);
        }
    }

    public void activateTheme(int projectId, String themeCode) {
        List<ThemeItem> list = getThemes(projectId);
        for (ThemeItem item : list) {
            item.setActive(item.getThemeCode().equals(themeCode));
        }
        saveThemes(projectId, list);
    }

    /**
     * 主题代码对应的主色。
     */
    public static int getThemeColor(String themeCode) {
        if (themeCode == null) return 0xFF6366F1;
        switch (themeCode) {
            case "warning": return 0xFFF59E0B;
            case "danger": return 0xFFEF4444;
            case "info": return 0xFF06B6D4;
            case "offline": return 0xFF9CA3AF;
            case "normal":
            default: return 0xFF6366F1;
        }
    }

    /**
     * 主题代码对应的浅背景色（用于激活按钮底色）。
     */
    public static int getThemeLightColor(String themeCode) {
        if (themeCode == null) return 0xFFE0E7FF;
        switch (themeCode) {
            case "warning": return 0xFFFEF3C7;
            case "danger": return 0xFFFEE2E2;
            case "info": return 0xFFCFFAFE;
            case "offline": return 0xFFE5E7EB;
            case "normal":
            default: return 0xFFE0E7FF;
        }
    }
}
