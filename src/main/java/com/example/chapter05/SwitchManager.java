package com.example.chapter05;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONObject;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SwitchManager {
    private static final String PREF_NAME = "switch_config";
    private static final String KEY_PREFIX = "switches_";

    // 开关状态变化广播（发送/接收回显后通知 UI 刷新）
    public static final String ACTION_SWITCH_STATE_CHANGED = "com.example.chapter05.SWITCH_STATE_CHANGED";
    public static final String EXTRA_PROJECT_ID = "project_id";

    private SharedPreferences prefs;
    private Gson gson;

    public SwitchManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    // 获取指定项目的开关列表
    public List<SwitchItem> getSwitches(int projectId) {
        String key = KEY_PREFIX + projectId;
        String json = prefs.getString(key, "");
        if (json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<SwitchItem>>(){}.getType();
        return gson.fromJson(json, type);
    }

    // 保存指定项目的开关列表
    public void saveSwitches(int projectId, List<SwitchItem> switches) {
        String key = KEY_PREFIX + projectId;
        String json = gson.toJson(switches);
        prefs.edit().putString(key, json).apply();
    }

    // 添加新开关
    public void addSwitch(int projectId, SwitchItem newSwitch) {
        List<SwitchItem> list = getSwitches(projectId);
        list.add(newSwitch);
        saveSwitches(projectId, list);
    }

    // 更新开关（按位置）
    public void updateSwitch(int projectId, int position, SwitchItem updated) {
        List<SwitchItem> list = getSwitches(projectId);
        if (position >= 0 && position < list.size()) {
            list.set(position, updated);
            saveSwitches(projectId, list);
        }
    }

    // 删除开关
    public void deleteSwitch(int projectId, int position) {
        List<SwitchItem> list = getSwitches(projectId);
        if (position >= 0 && position < list.size()) {
            list.remove(position);
            saveSwitches(projectId, list);
        }
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }

    /**
     * 根据 JSON 消息（发送或接收）回显开关状态。
     * 精确匹配开关 jsonKey，值 1/on 视为开、0/off 视为关（结合 activeHigh），有变化才写回。
     * @return 是否有开关状态发生变化
     */
    public boolean applyJsonState(int projectId, JSONObject json) {
        List<SwitchItem> switches = getSwitches(projectId);
        Log.d("SwitchManager", "applyJsonState: projectId=" + projectId + ", 开关数=" + switches.size() + ", json=" + json);
        boolean changed = false;
        for (int i = 0; i < switches.size(); i++) {
            SwitchItem item = switches.get(i);
            String key = item.getJsonKey();
            if (!json.has(key)) continue;
            String valueStr = json.optString(key);
            Integer val = ResponseRuleEngine.matchValue(valueStr, null);
            boolean newState;
            if (val != null) {
                newState = item.isActiveHigh() ? (val == 1) : (val == 0);
            } else {
                newState = item.isActiveHigh() ? ("1".equals(valueStr)) : ("0".equals(valueStr));
            }
            Log.d("SwitchManager", "applyJsonState: 匹配开关 key=" + key + ", value=" + valueStr + ", oldState=" + item.isState() + ", newState=" + newState);
            if (item.isState() != newState) {
                item.setState(newState);
                updateSwitch(projectId, i, item);
                changed = true;
            }
        }
        return changed;
    }
}