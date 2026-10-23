package com.example.chapter05;

/**
 * 自动开关配置（每个项目一条）。
 * 收到 MQTT 消息时若 keyName 的值等于 autoValue 则切自动模式，
 * 等于 manualValue 则切手动模式。
 */
public class AutoSwitchItem {
    public static final String MODE_AUTO = "auto";
    public static final String MODE_MANUAL = "manual";

    private String keyName;      // 匹配键名
    private String autoValue;    // 自动模式对应值
    private String manualValue;  // 手动模式对应值
    private String publishTopic; // 发布主题
    private String currentMode;  // 当前模式：auto / manual

    public AutoSwitchItem(String keyName, String autoValue, String manualValue, String publishTopic) {
        this.keyName = keyName;
        this.autoValue = autoValue;
        this.manualValue = manualValue;
        this.publishTopic = publishTopic;
        this.currentMode = MODE_MANUAL;
    }

    public boolean isAuto() { return MODE_AUTO.equals(currentMode); }
    public boolean isManual() { return MODE_MANUAL.equals(currentMode); }

    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }

    public String getAutoValue() { return autoValue; }
    public void setAutoValue(String autoValue) { this.autoValue = autoValue; }

    public String getManualValue() { return manualValue; }
    public void setManualValue(String manualValue) { this.manualValue = manualValue; }

    public String getPublishTopic() { return publishTopic; }
    public void setPublishTopic(String publishTopic) { this.publishTopic = publishTopic; }

    public String getCurrentMode() { return currentMode; }
    public void setCurrentMode(String currentMode) { this.currentMode = currentMode; }
}
