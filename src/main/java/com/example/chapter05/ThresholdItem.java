package com.example.chapter05;

import java.io.Serializable;

public class ThresholdItem implements Serializable {
    private String sensorName;      // 底层 JSON 键名（如 Temp, Humi）
    private String displayName;     // 用户看到的名称（如 温度传感器）
    private float minValue;
    private float maxValue;
    private String message;

    // 新增字段：是否发送阈值到传感器
    private boolean sendToSensor;
    private String upperKey;        // 上限键名
    private String lowerKey;        // 下限键名

    // 报警增强：超限时发送的自定义 JSON 指令及主题（可空）
    private String alarmSendJson;
    private String publishTopic;
    private Boolean enabled;        // 是否启用（null 视为启用，兼容旧数据）
    private String respMaxPath;     // HTTP 响应上限数据路径
    private String respMinPath;     // HTTP 响应下限数据路径

    // 完整构造方法
    public ThresholdItem(String sensorName, String displayName, float minValue, float maxValue,
                         String message, boolean sendToSensor, String upperKey, String lowerKey) {
        this.sensorName = sensorName;
        this.displayName = displayName;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.message = message;
        this.sendToSensor = sendToSensor;
        this.upperKey = upperKey;
        this.lowerKey = lowerKey;
    }

    // 兼容原有构造（不发送阈值）
    public ThresholdItem(String sensorName, String displayName, float minValue, float maxValue, String message) {
        this(sensorName, displayName, minValue, maxValue, message, false, "", "");
    }

    // Getter / Setter
    public String getSensorName() { return sensorName; }
    public void setSensorName(String sensorName) { this.sensorName = sensorName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public float getMinValue() { return minValue; }
    public void setMinValue(float minValue) { this.minValue = minValue; }

    public float getMaxValue() { return maxValue; }
    public void setMaxValue(float maxValue) { this.maxValue = maxValue; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isSendToSensor() { return sendToSensor; }
    public void setSendToSensor(boolean sendToSensor) { this.sendToSensor = sendToSensor; }

    public String getUpperKey() { return upperKey; }
    public void setUpperKey(String upperKey) { this.upperKey = upperKey; }

    public String getLowerKey() { return lowerKey; }
    public void setLowerKey(String lowerKey) { this.lowerKey = lowerKey; }

    public String getAlarmSendJson() { return alarmSendJson == null ? "" : alarmSendJson; }
    public void setAlarmSendJson(String alarmSendJson) { this.alarmSendJson = alarmSendJson; }

    public String getPublishTopic() { return publishTopic == null ? "" : publishTopic; }
    public void setPublishTopic(String publishTopic) { this.publishTopic = publishTopic; }

    public boolean isEnabled() { return enabled == null || enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRespMaxPath() { return respMaxPath == null ? "" : respMaxPath; }
    public void setRespMaxPath(String respMaxPath) { this.respMaxPath = respMaxPath; }

    public String getRespMinPath() { return respMinPath == null ? "" : respMinPath; }
    public void setRespMinPath(String respMinPath) { this.respMinPath = respMinPath; }
}