package com.example.chapter05;

/**
 * 主题配置项。themeCode 为白名单：normal/warning/danger/info/offline。
 * 同一项目仅一个主题处于激活态。
 */
public class ThemeItem {
    private String themeCode;     // normal / warning / danger / info / offline
    private String displayName;
    private String publishTopic;
    private boolean active;
    private String httpKeyName;   // HTTP 触发键名
    private String httpKeyValue;  // HTTP 触发键值
    private String mqttKeyName;   // MQTT 触发键名
    private String mqttKeyValue;  // MQTT 触发键值

    public ThemeItem(String themeCode, String displayName, String publishTopic) {
        this.themeCode = themeCode;
        this.displayName = displayName;
        this.publishTopic = publishTopic;
        this.active = false;
    }

    public String getThemeCode() { return themeCode; }
    public void setThemeCode(String themeCode) { this.themeCode = themeCode; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPublishTopic() { return publishTopic; }
    public void setPublishTopic(String publishTopic) { this.publishTopic = publishTopic; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getHttpKeyName() { return httpKeyName == null ? "" : httpKeyName; }
    public void setHttpKeyName(String httpKeyName) { this.httpKeyName = httpKeyName; }

    public String getHttpKeyValue() { return httpKeyValue == null ? "" : httpKeyValue; }
    public void setHttpKeyValue(String httpKeyValue) { this.httpKeyValue = httpKeyValue; }

    public String getMqttKeyName() { return mqttKeyName == null ? "" : mqttKeyName; }
    public void setMqttKeyName(String mqttKeyName) { this.mqttKeyName = mqttKeyName; }

    public String getMqttKeyValue() { return mqttKeyValue == null ? "" : mqttKeyValue; }
    public void setMqttKeyValue(String mqttKeyValue) { this.mqttKeyValue = mqttKeyValue; }
}
