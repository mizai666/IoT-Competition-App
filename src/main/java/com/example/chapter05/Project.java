package com.example.chapter05;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Project implements Serializable {
    private int projectId;
    private String displayName;
    private boolean showChart;
    private Map<String, String> sensors;
    private String rawJson = "{}";  // 最新原始 JSON（带数字后缀的原始键）
    private boolean isSpecial;
    private boolean pinned;  // 新增：是否置顶
    private transient long lastActiveTime = 0;  // 最后收到任何消息的时间戳（毫秒）

    public void updateLastActiveTime() {
        lastActiveTime = System.currentTimeMillis();
    }

    public boolean isOnline() {
        // 6秒内有过消息则在线
        return System.currentTimeMillis() - lastActiveTime <= 10000;
    }

    public Project(int projectId, String displayName) {
        this(projectId, displayName, false);
    }

    public Project(int projectId, String displayName, boolean isSpecial) {
        this.projectId = projectId;
        this.displayName = displayName;
        this.sensors = new HashMap<>();
        this.isSpecial = isSpecial;
        this.pinned = false;  // 默认未置顶
    }

    public boolean isShowChart() { return showChart; }
    public void setShowChart(boolean showChart) { this.showChart = showChart; }

    public int getProjectId() { return projectId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Map<String, String> getSensors() { return sensors; }
    public void updateSensor(String name, String value) { sensors.put(name, value); }

    public String getRawJson() { return rawJson == null ? "{}" : rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }

    public boolean isSpecial() { return isSpecial; }
    public void setSpecial(boolean special) { isSpecial = special; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
}