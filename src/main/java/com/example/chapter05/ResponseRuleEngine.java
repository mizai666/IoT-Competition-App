package com.example.chapter05;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 响应规则引擎（对齐 Hchapter01 的 getByPath / matchValue / 开关/主题/定时/阈值响应规则）。
 * HTTP 响应或 MQTT 上行数据到达时调用 applyAllRules 触发。
 */
public class ResponseRuleEngine {
    private static final String TAG = "ResponseRuleEngine";

    private static final Set<String> ON_SET = new HashSet<>(Arrays.asList(
            "on", "1", "true", "open", "enable", "enabled", "start", "high", "up", "run", "active", "turn_on", "开"));
    private static final Set<String> OFF_SET = new HashSet<>(Arrays.asList(
            "off", "0", "false", "close", "disable", "disabled", "stop", "low", "down", "idle", "inactive", "turn_off", "关"));

    // ========== 命令值 → 1/0 ==========
    public static Integer matchValue(Object cmd, String valueMap) {
        String norm = String.valueOf(cmd == null ? "" : cmd).trim().toLowerCase();
        if (norm.isEmpty()) return null;
        Map<String, Integer> custom = parseValueMap(valueMap);
        if (custom.containsKey(norm)) return custom.get(norm);
        if (ON_SET.contains(norm)) return 1;
        if (OFF_SET.contains(norm)) return 0;
        return null;
    }

    private static Map<String, Integer> parseValueMap(String text) {
        Map<String, Integer> map = new HashMap<>();
        if (text == null || text.isEmpty()) return map;
        Set<String> onSet = new HashSet<>(Arrays.asList("on", "1", "true", "开", "open", "enable", "enabled"));
        Set<String> offSet = new HashSet<>(Arrays.asList("off", "0", "false", "关", "close", "disable", "disabled"));
        String[] entries = text.split("[,;\\n，；]+");
        for (String e : entries) {
            String[] kv = e.split("[=:：]");
            if (kv.length < 2) continue;
            String k = kv[0].trim().toLowerCase();
            String v = kv[1].trim().toLowerCase();
            if (k.isEmpty()) continue;
            if (onSet.contains(v)) map.put(k, 1);
            else if (offSet.contains(v)) map.put(k, 0);
        }
        return map;
    }

    // ========== 通用路径取值（点路径 + [n] + * + **） ==========
    public static Object getByPath(Object obj, String path) {
        List<String> segs = parsePath(path);
        if (segs.isEmpty()) return obj;
        List<Object> results = new ArrayList<>();
        resolve(obj, segs, 0, results);
        boolean hasWild = path != null && path.contains("*");
        if (hasWild) return results;
        return results.isEmpty() ? null : results.get(0);
    }

    private static List<String> parsePath(String path) {
        List<String> segs = new ArrayList<>();
        String s = path == null ? "" : path.trim();
        Matcher m = Pattern.compile("([^.\\[\\]]+)|\\[([^\\]]*)\\]").matcher(s);
        while (m.find()) {
            if (m.group(1) != null) {
                segs.add(m.group(1));
            } else {
                String inner = (m.group(2) == null ? "" : m.group(2)).trim();
                if ((inner.startsWith("'") && inner.endsWith("'")) || (inner.startsWith("\"") && inner.endsWith("\""))) {
                    inner = inner.substring(1, inner.length() - 1);
                }
                segs.add(inner);
            }
        }
        return segs;
    }

    private static void resolve(Object node, List<String> segs, int idx, List<Object> results) {
        if (idx >= segs.size()) {
            results.add(node);
            return;
        }
        String key = segs.get(idx);
        if ("**".equals(key)) {
            resolve(node, segs, idx + 1, results);
            if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                for (int i = 0; i < arr.length(); i++) resolve(arr.opt(i), segs, idx, results);
            } else if (node instanceof JSONObject) {
                JSONObject obj = (JSONObject) node;
                Iterator<String> it = obj.keys();
                while (it.hasNext()) resolve(obj.opt(it.next()), segs, idx, results);
            } else if (node instanceof List) {
                for (Object o : (List<?>) node) resolve(o, segs, idx, results);
            } else if (node instanceof Map) {
                for (Object o : ((Map<?, ?>) node).values()) resolve(o, segs, idx, results);
            }
            return;
        }
        if ("*".equals(key)) {
            if (node instanceof JSONArray) {
                JSONArray arr = (JSONArray) node;
                for (int i = 0; i < arr.length(); i++) resolve(arr.opt(i), segs, idx + 1, results);
            } else if (node instanceof List) {
                for (Object o : (List<?>) node) resolve(o, segs, idx + 1, results);
            }
            return;
        }
        Object child = null;
        if (node instanceof JSONArray) {
            try {
                int n = Integer.parseInt(key);
                JSONArray arr = (JSONArray) node;
                if (n >= 0 && n < arr.length()) child = arr.opt(n);
            } catch (NumberFormatException ignored) {}
        } else if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            if (obj.has(key)) child = obj.opt(key);
        } else if (node instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) node;
            if (map.containsKey(key)) child = map.get(key);
        } else if (node instanceof List) {
            try {
                int n = Integer.parseInt(key);
                List<?> list = (List<?>) node;
                if (n >= 0 && n < list.size()) child = list.get(n);
            } catch (NumberFormatException ignored) {}
        }
        if (child != null && child != JSONObject.NULL) resolve(child, segs, idx + 1, results);
    }

    // ========== 定时任务目标匹配 ==========
    public static boolean matchScheduleTarget(JSONObject data, ScheduleItem sch) {
        String targetLower = sch.getKeyName().trim().toLowerCase();
        String respTargetKey = sch.getRespTargetKey();
        String respArrayPath = sch.getRespArrayPath();
        if (respTargetKey != null && !respTargetKey.isEmpty()) {
            if (data.has(respTargetKey) && String.valueOf(data.opt(respTargetKey)).trim().toLowerCase().equals(targetLower)) {
                return true;
            }
            String esc = Pattern.quote(respTargetKey);
            Iterator<String> it = data.keys();
            while (it.hasNext()) {
                String key = it.next();
                Matcher m = Pattern.compile("^" + esc + "_(\\d+)$").matcher(key);
                if (m.find() && String.valueOf(data.opt(key)).trim().toLowerCase().equals(targetLower)) return true;
            }
        }
        if (respArrayPath != null && !respArrayPath.isEmpty()) {
            Object arrVal = getByPath(data, respArrayPath);
            List<Object> items = toList(arrVal);
            for (Object item : items) {
                Object tv = (item instanceof JSONObject && respTargetKey != null && !respTargetKey.isEmpty())
                        ? getByPath(item, respTargetKey) : item;
                if (String.valueOf(tv).trim().toLowerCase().equals(targetLower)) return true;
            }
        }
        return data.has(sch.getKeyName());
    }

    private static List<Object> toList(Object val) {
        List<Object> list = new ArrayList<>();
        if (val instanceof JSONArray) {
            JSONArray arr = (JSONArray) val;
            for (int i = 0; i < arr.length(); i++) list.add(arr.opt(i));
        } else if (val instanceof List) {
            list.addAll((List<?>) val);
        } else if (val != null) {
            list.add(val);
        }
        return list;
    }

    // ========== 统一入口：HTTP 响应 / MQTT 上行 ==========
    public static void applyAllRules(Context context, int projectId, JSONObject data, boolean fromHttp) {
        applySwitchRules(context, projectId, data);
        applyThemeRules(context, projectId, data, fromHttp);
        applyScheduleRules(context, projectId, data);
        applyThresholdRules(context, projectId, data);
    }

    // ========== 开关规则 ==========
    private static void applySwitchRules(Context context, int projectId, JSONObject data) {
        SwitchManager switchManager = new SwitchManager(context);
        List<SwitchItem> switches = switchManager.getSwitches(projectId);
        List<String[]> triggered = new ArrayList<>(); // {keyName, value}
        for (SwitchItem sw : switches) {
            String respTargetKey = sw.getRespTargetKey();
            String respCommandKey = sw.getRespCommandKey();
            String respArrayPath = sw.getRespArrayPath();
            // 标量模式
            if ((respTargetKey == null || respTargetKey.isEmpty()) && (respCommandKey == null || respCommandKey.isEmpty())
                    && respArrayPath != null && !respArrayPath.isEmpty()) {
                Object cmdVal = getByPath(data, respArrayPath);
                Integer val = matchValue(cmdVal, sw.getRespValueMap());
                if (val != null) triggered.add(new String[]{sw.getJsonKey(), String.valueOf(val)});
                continue;
            }
            if (respTargetKey == null || respTargetKey.isEmpty() || respCommandKey == null || respCommandKey.isEmpty()) {
                continue;
            }
            // 平铺模式
            if (respArrayPath == null || respArrayPath.isEmpty()) {
                String keyNameLower = sw.getJsonKey().trim().toLowerCase();
                List<String[]> matches = new ArrayList<>();
                if (data.has(respTargetKey) && data.has(respCommandKey)) {
                    matches.add(new String[]{String.valueOf(data.opt(respTargetKey)), String.valueOf(data.opt(respCommandKey))});
                } else {
                    String esc = Pattern.quote(respTargetKey);
                    Iterator<String> it = data.keys();
                    while (it.hasNext()) {
                        String key = it.next();
                        Matcher m = Pattern.compile("^" + esc + "_(\\d+)$").matcher(key);
                        if (!m.find()) continue;
                        String cmdKey = respCommandKey + "_" + m.group(1);
                        if (!data.has(cmdKey)) continue;
                        matches.add(new String[]{String.valueOf(data.opt(key)), String.valueOf(data.opt(cmdKey))});
                    }
                }
                for (String[] mt : matches) {
                    if (!mt[0].trim().toLowerCase().equals(keyNameLower)) continue;
                    Integer val = matchValue(mt[1], sw.getRespValueMap());
                    if (val != null) triggered.add(new String[]{sw.getJsonKey(), String.valueOf(val)});
                }
                continue;
            }
            // 数组模式
            Object arrVal = getByPath(data, respArrayPath);
            List<Object> items = toList(arrVal);
            for (Object item : items) {
                Object targetVal = (item instanceof JSONObject) ? getByPath(item, respTargetKey) : item;
                String targetStr = String.valueOf(targetVal == null ? "" : targetVal).trim();
                if (!targetStr.toLowerCase().equals(sw.getJsonKey().trim().toLowerCase())) continue;
                Object cmdVal = (item instanceof JSONObject) ? getByPath(item, respCommandKey) : item;
                Integer val = matchValue(cmdVal, sw.getRespValueMap());
                if (val == null) continue;
                triggered.add(new String[]{sw.getJsonKey(), String.valueOf(val)});
                break;
            }
        }
        if (triggered.isEmpty()) return;

        MqttManager mqtt = MqttManager.getInstance(context);
        Map<String, Map<String, Integer>> topicMap = new HashMap<>();
        for (String[] t : triggered) {
            SwitchItem sw = findSwitch(switches, t[0]);
            if (sw == null) continue;
            String topic = sw.getPublishTopic();
            if (topic.isEmpty()) continue;
            Map<String, Integer> payload = topicMap.get(topic);
            if (payload == null) {
                payload = new HashMap<>();
                topicMap.put(topic, payload);
            }
            payload.put(t[0], Integer.parseInt(t[1]));
        }
        for (Map.Entry<String, Map<String, Integer>> e : topicMap.entrySet()) {
            JSONObject payload = new JSONObject();
            for (Map.Entry<String, Integer> kv : e.getValue().entrySet()) {
                try { payload.put(kv.getKey(), kv.getValue()); } catch (JSONException ignored) {}
            }
            mqtt.publish(e.getKey(), payload.toString(), mqtt.getQos(), projectId);
        }
        // 更新本地开关状态
        for (String[] t : triggered) {
            SwitchItem sw = findSwitch(switches, t[0]);
            if (sw != null) {
                sw.setState("1".equals(t[1]));
                int idx = switches.indexOf(sw);
                if (idx >= 0) switchManager.updateSwitch(projectId, idx, sw);
            }
        }
        Log.d(TAG, "触发 " + triggered.size() + " 个开关");
    }

    private static SwitchItem findSwitch(List<SwitchItem> list, String keyName) {
        for (SwitchItem s : list) {
            if (s.getJsonKey().equals(keyName)) return s;
        }
        return null;
    }

    // ========== 主题规则 ==========
    private static void applyThemeRules(Context context, int projectId, JSONObject data, boolean fromHttp) {
        ThemeManager themeManager = ThemeManager.getInstance(context);
        List<ThemeItem> themes = themeManager.getThemes(projectId);
        ThemeItem matched = null;
        for (ThemeItem theme : themes) {
            String keyPath = fromHttp ? theme.getHttpKeyName() : theme.getMqttKeyName();
            String expected = fromHttp ? theme.getHttpKeyValue() : theme.getMqttKeyValue();
            if (keyPath == null || keyPath.isEmpty() || expected == null || expected.isEmpty()) continue;
            Object actual = getByPath(data, keyPath);
            if (actual != null && String.valueOf(actual).equals(expected)) {
                matched = theme;
                break;
            }
        }
        if (matched == null || matched.isActive()) return;
        themeManager.activateTheme(projectId, matched.getThemeCode());
        MqttManager mqtt = MqttManager.getInstance(context);
        if (matched.getMqttKeyName() != null && !matched.getMqttKeyName().isEmpty()
                && matched.getMqttKeyValue() != null && !matched.getMqttKeyValue().isEmpty()
                && matched.getPublishTopic() != null && !matched.getPublishTopic().isEmpty()) {
            try {
                JSONObject payload = new JSONObject();
                payload.put(matched.getMqttKeyName(), matched.getMqttKeyValue());
                mqtt.publish(matched.getPublishTopic(), payload.toString(), mqtt.getQos(), projectId);
            } catch (JSONException ignored) {}
        }
        Log.d(TAG, "触发主题: " + matched.getDisplayName());
    }

    // ========== 定时规则 ==========
    private static void applyScheduleRules(Context context, int projectId, JSONObject data) {
        ScheduleManager scheduleManager = ScheduleManager.getInstance(context);
        List<ScheduleItem> schedules = scheduleManager.getSchedules(projectId);
        for (ScheduleItem sch : schedules) {
            if (!sch.isEnabled()) continue;
            if ((sch.getRespTargetKey() == null || sch.getRespTargetKey().isEmpty())
                    && (sch.getRespArrayPath() == null || sch.getRespArrayPath().isEmpty())) continue;
            if (matchScheduleTarget(data, sch)) {
                scheduleManager.resetSchedule(projectId, sch.getKeyName());
            }
        }
    }

    // ========== 阈值规则 ==========
    private static void applyThresholdRules(Context context, int projectId, JSONObject data) {
        ThresholdManager thresholdManager = new ThresholdManager(context);
        List<ThresholdItem> thresholds = thresholdManager.getThresholds(projectId);
        boolean changed = false;
        for (int i = 0; i < thresholds.size(); i++) {
            ThresholdItem th = thresholds.get(i);
            if ((th.getRespMaxPath() == null || th.getRespMaxPath().isEmpty())
                    && (th.getRespMinPath() == null || th.getRespMinPath().isEmpty())) continue;
            Object rawMax = !th.getRespMaxPath().isEmpty() ? getByPath(data, th.getRespMaxPath()) : null;
            Object rawMin = !th.getRespMinPath().isEmpty() ? getByPath(data, th.getRespMinPath()) : null;
            float newMax = parseFloatOrNaN(rawMax);
            float newMin = parseFloatOrNaN(rawMin);
            if (Float.isNaN(newMax) && Float.isNaN(newMin)) continue;
            float finalMax = Float.isNaN(newMax) ? th.getMaxValue() : newMax;
            float finalMin = Float.isNaN(newMin) ? th.getMinValue() : newMin;
            th.setMaxValue(finalMax);
            th.setMinValue(finalMin);
            thresholdManager.updateThreshold(projectId, i, th);
            // 发下位机
            if (th.isSendToSensor() && !th.getPublishTopic().isEmpty()
                    && !th.getUpperKey().isEmpty() && !th.getLowerKey().isEmpty()) {
                try {
                    JSONObject payload = new JSONObject();
                    payload.put(th.getUpperKey(), finalMax);
                    payload.put(th.getLowerKey(), finalMin);
                    MqttManager.getInstance(context).publish(th.getPublishTopic(), payload.toString(),
                            MqttManager.getInstance(context).getQos(), projectId);
                } catch (JSONException ignored) {}
            }
            changed = true;
        }
        if (changed) Log.d(TAG, "更新了 " + thresholds.size() + " 个阈值");
    }

    private static float parseFloatOrNaN(Object v) {
        if (v == null) return Float.NaN;
        try {
            return Float.parseFloat(String.valueOf(v));
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }
}
