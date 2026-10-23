package com.example.chapter05;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectDataManager {
    private static final String TAG = "ProjectDataManager";
    private static final int SPECIAL_PROJECT_THRESHOLD = 10000;
    private static ProjectDataManager instance;
    private Context appContext;
    private Map<Integer, Project> projectMap = new HashMap<>();
    private Map<String, Integer> schemaToProjectIdMap = new HashMap<>();
    private ProjectDatabaseHelper dbHelper;
    private SpecialJsonDatabaseHelper specialJsonDbHelper;
    private List<OnDataChangeListener> listeners = new ArrayList<>();
    private int nextAutoProjectId = SPECIAL_PROJECT_THRESHOLD;
    private int lastSpecialProjectId = -1;

    public int getLastSpecialProjectId() {
        return lastSpecialProjectId;
    }

    private ProjectDataManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.dbHelper = new ProjectDatabaseHelper(appContext);
        this.specialJsonDbHelper = new SpecialJsonDatabaseHelper(appContext);
        loadAllProjectsFromDB();
    }

    public static synchronized ProjectDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProjectDataManager(context);
        }
        return instance;
    }

    private void loadAllProjectsFromDB() {
        projectMap.clear();
        schemaToProjectIdMap.clear();
        List<ProjectDatabaseHelper.ProjectRecord> records = dbHelper.getAllProjects();
        for (ProjectDatabaseHelper.ProjectRecord record : records) {
            Project project = new Project(record.id, record.name, record.isSpecial);
            // 修复：特殊设备也要根据传感器数据判断是否显示图表
            // 非特殊设备始终显示图表，特殊设备需要检查是否有数字传感器
            if (!record.isSpecial) {
                project.setShowChart(true);
            } else {
                // 特殊设备：检查传感器历史数据中是否有数字值
                project.setShowChart(checkSpecialProjectHasNumericSensors(record.id));
            }
            projectMap.put(record.id, project);
            if (record.schemaKeys != null && !record.schemaKeys.isEmpty()) {
                schemaToProjectIdMap.put(record.schemaKeys, record.id);
            }
            if (record.id >= nextAutoProjectId) {
                nextAutoProjectId = record.id + 1;
            }
        }
    }

    /**
     * 检查特殊项目是否有数字类型的传感器
     */
    private boolean checkSpecialProjectHasNumericSensors(int projectId) {
        try {
            SensorHistoryDatabaseHelper sensorHelper = new SensorHistoryDatabaseHelper(appContext);
            List<SensorHistoryDatabaseHelper.HistoryRecord> records = sensorHelper.getAllHistoryByProject(projectId);
            sensorHelper.close();
            // 只要有历史记录，就说明有数字传感器
            return !records.isEmpty();
        } catch (Exception e) {
            Log.e(TAG, "检查特殊项目传感器失败: " + e.getMessage());
            return false;
        }
    }

    public void updateProjectPinned(int projectId, boolean pinned) {
        Project project = projectMap.get(projectId);
        if (project != null) {
            project.setPinned(pinned);
            String oldSchema = null;
            for (Map.Entry<String, Integer> entry : schemaToProjectIdMap.entrySet()) {
                if (entry.getValue() == projectId) {
                    oldSchema = entry.getKey();
                    break;
                }
            }
            dbHelper.insertOrUpdate(projectId, project.getDisplayName(), oldSchema, project.isSpecial(), pinned);
            notifyDataChanged(true);
        }
    }

    public void updateFromJson(String jsonStr) {
        Log.d(TAG, "updateFromJson 接收: " + jsonStr);
        try {
            JSONObject json = new JSONObject(jsonStr);
            Set<String> keys = new HashSet<>();
            Iterator<String> keyIterator = json.keys();
            List<String> keyList = new ArrayList<>();
            boolean allHaveNumberSuffix = true;
            boolean anyHaveNumberSuffix = false;

            while (keyIterator.hasNext()) {
                String k = keyIterator.next();
                keys.add(k);
                keyList.add(k);
                boolean hasSuffix = Pattern.compile("\\d+$").matcher(k).find();
                if (hasSuffix) {
                    anyHaveNumberSuffix = true;
                } else {
                    allHaveNumberSuffix = false;
                }
            }

            // 修复：更精确地判断是否为特殊JSON
            // 多设备JSON特征：所有key都有数字后缀，且有多个不同前缀（如 Temp1-20, Humi1-20）
            // 特殊JSON特征：不是所有key都有数字后缀（如 Soil_Water, CO2, Mode）
            boolean isSpecialJson = !allHaveNumberSuffix;
            boolean isMultiDevice = allHaveNumberSuffix && anyHaveNumberSuffix;
            
            Log.d(TAG, "JSON识别结果 - isMultiDevice=" + isMultiDevice + ", isSpecialJson=" + isSpecialJson + ", allHaveNumberSuffix=" + allHaveNumberSuffix + ", key数量=" + keys.size());

            Map<Integer, Map<String, String>> groupedData = new HashMap<>();
            Map<Integer, Map<String, String>> rawGroupedData = new HashMap<>();
            Map<Integer, Boolean> specialProjectAllNumeric = new HashMap<>();

            if (isMultiDevice) {
                Log.d(TAG, "进入多设备模式处理");
                for (String key : keyList) {
                    int pid = extractProjectId(key);
                    if (pid == -1) {
                        Log.w(TAG, "跳过没有数字后缀的键: " + key);
                        continue;  // 关键：跳过没有数字后缀的键，防止混入多设备项目
                    }
                    String val = json.optString(key);
                    String sensor = extractSensorName(key);
                    groupedData.computeIfAbsent(pid, k -> new HashMap<>()).put(sensor, val);
                    rawGroupedData.computeIfAbsent(pid, k -> new HashMap<>()).put(key, val);
                    Log.d(TAG, "多设备分组: PID=" + pid + ", 传感器=" + sensor + ", 值=" + val);
                }
                Log.d(TAG, "多设备分组完成，共" + groupedData.size() + "个设备");
            } else {
                int pid = matchOrCreateSchemaProject(keys, true);
                Map<String, String> sensorMap = new HashMap<>();
                Map<String, String> rawMap = new HashMap<>();
                for (String key : keyList) {
                    String val = json.optString(key);
                    sensorMap.put(key, val);
                    rawMap.put(key, val);
                }
                groupedData.put(pid, sensorMap);
                rawGroupedData.put(pid, rawMap);
                boolean allNumeric = true;
                for (String value : sensorMap.values()) {
                    try {
                        Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        allNumeric = false;
                        break;
                    }
                }
                specialProjectAllNumeric.put(pid, allNumeric);
                Log.d(TAG, "特殊项目 " + pid + " 是否全数字: " + allNumeric);
            }

            boolean projectListChanged = false;
            Map<Integer, Map<String, String>> updatedSensors = new HashMap<>();

            for (Map.Entry<Integer, Map<String, String>> entry : groupedData.entrySet()) {
                int pid = entry.getKey();
                Map<String, String> sensors = entry.getValue();

                boolean isSpecial = !isMultiDevice;
                boolean showChart;
                if (isMultiDevice) {
                    showChart = true;
                } else {
                    boolean hasNumeric = false;
                    for (String val : sensors.values()) {
                        try {
                            Double.parseDouble(val);
                            hasNumeric = true;
                            break;
                        } catch (NumberFormatException ignored) {}
                    }
                    showChart = hasNumeric;
                }

                Project project = projectMap.get(pid);
                if (project == null) {
                    String displayName = isSpecial ? ("特殊设备 " + pid) : ("设备 " + pid);
                    String schemaStr = String.join(",", sortKeysToArrayList(sensors.keySet()));
                    dbHelper.insertOrUpdate(pid, displayName, schemaStr, isSpecial);
                    project = new Project(pid, displayName, isSpecial);
                    project.setShowChart(showChart);
                    projectMap.put(pid, project);
                    if (!isMultiDevice) {
                        schemaToProjectIdMap.put(schemaStr, pid);
                    }
                    projectListChanged = true;
                    Log.d(TAG, "创建项目: pid=" + pid + ", 特殊=" + isSpecial + ", 显示图表=" + showChart);
                } else {
                    if (isSpecial && !project.isSpecial()) {
                        project.setSpecial(true);
                        dbHelper.insertOrUpdate(pid, project.getDisplayName(), null, true);
                    }
                    if (isSpecial && project.isShowChart() != showChart) {
                        project.setShowChart(showChart);
                        Log.d(TAG, "更新项目 " + pid + " 显示图表标志为: " + showChart);
                    }
                }

                for (Map.Entry<String, String> s : sensors.entrySet()) {
                    project.updateSensor(s.getKey(), s.getValue());
                    updatedSensors.computeIfAbsent(pid, k -> new HashMap<>())
                            .put(s.getKey(), s.getValue());
                    Log.d(TAG, "更新传感器: 项目=" + pid + ", 键=" + s.getKey() + ", 值=" + s.getValue());
                }
                // 保存原始 JSON（带数字后缀）
                Map<String, String> rawMap = rawGroupedData.get(pid);
                if (rawMap != null && !rawMap.isEmpty()) {
                    project.setRawJson(new JSONObject(rawMap).toString());
                }
                updateProjectLastActiveTime(pid);
            }

            if (isSpecialJson && !groupedData.isEmpty()) {
                int specialProjectId = groupedData.keySet().iterator().next();
                lastSpecialProjectId = specialProjectId;
                updateProjectLastActiveTime(specialProjectId);
                specialJsonDbHelper.insertSpecialJson(specialProjectId, jsonStr);
                Log.d(TAG, "特殊JSON已存入专用数据库，项目ID: " + specialProjectId);

                Map<String, String> sensorMap = groupedData.get(specialProjectId);
                long baseTimestamp = System.currentTimeMillis();
                SensorHistoryDatabaseHelper sensorHelper = new SensorHistoryDatabaseHelper(appContext);
                // 修复：同一批次的所有传感器使用相同的时间戳
                for (Map.Entry<String, String> entry : sensorMap.entrySet()) {
                    String value = entry.getValue();
                    try {
                        Double.parseDouble(value);
                        sensorHelper.insertHistory(specialProjectId, entry.getKey(), value, baseTimestamp);
                        Log.d(TAG, "插入传感器历史: " + entry.getKey() + " = " + value + ", 时间戳=" + baseTimestamp);
                    } catch (NumberFormatException e) {
                        Log.d(TAG, "非数字传感器，跳过历史: " + entry.getKey() + " = " + value);
                    }
                }
                List<SensorHistoryDatabaseHelper.HistoryRecord> checkRecords = sensorHelper.getAllHistoryByProject(specialProjectId);
                Log.d(TAG, "插入后查询到传感器历史记录数: " + checkRecords.size());
            }

            for (OnDataChangeListener listener : listeners) {
                if (projectListChanged) listener.onProjectListChanged();
                for (Map.Entry<Integer, Map<String, String>> projEntry : updatedSensors.entrySet()) {
                    int pid = projEntry.getKey();
                    for (Map.Entry<String, String> sensorEntry : projEntry.getValue().entrySet()) {
                        listener.onSensorDataUpdated(pid, sensorEntry.getKey(), sensorEntry.getValue());
                    }
                }
            }

            for (int pid : groupedData.keySet()) {
                Intent intent = new Intent(DetailDatabaseFragment.ACTION_DATA_CHANGED);
                intent.putExtra(DetailDatabaseFragment.EXTRA_PROJECT_ID, pid);
                appContext.sendBroadcast(intent);
            }

        } catch (JSONException e) {
            Log.e(TAG, "JSON解析失败", e);
        }
    }
    /**
     * 根据 JSON 字符串获取对应的项目 ID（不修改项目数据，不创建新项目）
     * @param jsonStr JSON 字符串
     * @return 项目ID，如果解析失败或无法匹配则返回 -1
     */
    public int getProjectIdForJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            Set<String> keys = new HashSet<>();
            Iterator<String> it = json.keys();
            while (it.hasNext()) {
                keys.add(it.next());
            }
            return findProjectIdByKeys(keys);  // 只查询，不创建
        } catch (JSONException e) {
            Log.e(TAG, "解析 JSON 获取项目 ID 失败", e);
            return -1;
        }
    }
    /**
     * 根据字段集合查找已存在的项目 ID（不创建新项目）
     * @param keys 字段集合
     * @return 项目ID，如果未找到返回 -1
     */
    public int findProjectIdByKeys(Set<String> keys) {
        List<String> sortedList = sortKeysToArrayList(keys);
        String sortedKeys = String.join(",", sortedList);
        Integer matchedId = schemaToProjectIdMap.get(sortedKeys);
        return matchedId != null ? matchedId : -1;
    }
    private List<String> sortKeysToArrayList(Set<String> keys) {
        List<String> list = new ArrayList<>(keys);
        Collections.sort(list);
        return list;
    }

    public int matchOrCreateSchemaProject(Set<String> keys, boolean isSpecial) {
        List<String> sortedList = sortKeysToArrayList(keys);
        String sortedKeys = String.join(",", sortedList);
        Integer matchedId = schemaToProjectIdMap.get(sortedKeys);
        if (matchedId != null) return matchedId;
        int newId = nextAutoProjectId++;
        dbHelper.insertOrUpdate(newId, "特殊设备 " + newId, sortedKeys, isSpecial);
        schemaToProjectIdMap.put(sortedKeys, newId);
        Log.d(TAG, "创建新项目: PID " + newId + ", 特殊=" + isSpecial);
        return newId;
    }

    private int extractProjectId(String key) {
        Pattern pattern = Pattern.compile("\\d+$");
        Matcher matcher = pattern.matcher(key);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        } else {
            return -1;  // 没有数字后缀，不作为多设备项目的ID
        }
    }

    private String extractSensorName(String key) {
        return key.replaceAll("\\d+$", "");
    }

    public void updateProjectName(int projectId, String newName) {
        Project project = projectMap.get(projectId);
        if (project != null) {
            project.setDisplayName(newName);
            String oldSchema = "";
            for (Map.Entry<String, Integer> entry : schemaToProjectIdMap.entrySet()) {
                if (entry.getValue() == projectId) {
                    oldSchema = entry.getKey();
                    break;
                }
            }
            dbHelper.insertOrUpdate(projectId, newName, oldSchema, project.isSpecial());
            notifyDataChanged(false);
        }
    }

    public void deleteProject(int projectId) {
        Project removed = projectMap.remove(projectId);
        if (removed != null) {
            dbHelper.deleteProject(projectId);
            schemaToProjectIdMap.entrySet().removeIf(entry -> entry.getValue() == projectId);
            notifyDataChanged(true);
        }
    }

    public void clearAllProjects() {
        projectMap.clear();
        schemaToProjectIdMap.clear();
        dbHelper.clearAll();
        notifyDataChanged(true);
    }
    // ProjectDataManager.java
    public void updateProjectLastActiveTime(int projectId) {
        Project project = projectMap.get(projectId);
        if (project != null) {
            project.updateLastActiveTime();
            // 不立即通知列表刷新，因为定时器每秒会刷新
        }
    }

    public interface OnDataChangeListener {
        void onProjectListChanged();
        void onSensorDataUpdated(int projectId, String sensorName, String newValue);
    }

    public void addListener(OnDataChangeListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(OnDataChangeListener listener) {
        listeners.remove(listener);
    }

    public List<Project> getAllProjects() {
        List<Project> list = new ArrayList<>(projectMap.values());
        // 按置顶降序（置顶的在前），然后按项目ID升序
        Collections.sort(list, (a, b) -> {
            if (a.isPinned() != b.isPinned()) {
                return Boolean.compare(b.isPinned(), a.isPinned());
            }
            return Integer.compare(a.getProjectId(), b.getProjectId());
        });
        return list;
    }

    public Project getProject(int projectId) {
        return projectMap.get(projectId);
    }

    private void notifyDataChanged(boolean projectListChanged) {
        for (OnDataChangeListener listener : listeners) {
            if (projectListChanged) listener.onProjectListChanged();
            else listener.onSensorDataUpdated(-1, null, null);
        }
    }
}