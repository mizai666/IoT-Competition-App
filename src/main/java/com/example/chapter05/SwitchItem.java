package com.example.chapter05;

import java.io.Serializable;

public class SwitchItem implements Serializable {
    private String displayName;   // 用户看到的名称，如“客厅灯”
    private String jsonKey;       // JSON 键名，如 "light1"
    private boolean state;        // 当前状态（开/关）
    private boolean activeHigh;   // 是否高电平点亮（true: 开=1, 关=0; false: 开=0, 关=1）
    private String publishTopic;  // 发布主题（切换和响应规则下发都用它）
    private String respArrayPath; // 响应规则：数组路径
    private String respTargetKey; // 响应规则：目标字段
    private String respCommandKey;// 响应规则：命令字段
    private String respValueMap;  // 响应规则：自定义值映射（如 on:开,off:关）

    public SwitchItem(String displayName, String jsonKey) {
        this.displayName = displayName;
        this.jsonKey = jsonKey;
        this.state = false;
        this.activeHigh = true;  // 默认高电平点亮
    }

    public SwitchItem(String displayName, String jsonKey, boolean activeHigh) {
        this.displayName = displayName;
        this.jsonKey = jsonKey;
        this.state = false;
        this.activeHigh = activeHigh;
    }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getJsonKey() { return jsonKey; }
    public void setJsonKey(String jsonKey) { this.jsonKey = jsonKey; }
    public boolean isState() { return state; }
    public void setState(boolean state) { this.state = state; }
    public boolean isActiveHigh() { return activeHigh; }
    public void setActiveHigh(boolean activeHigh) { this.activeHigh = activeHigh; }

    public String getPublishTopic() { return publishTopic == null ? "" : publishTopic; }
    public void setPublishTopic(String publishTopic) { this.publishTopic = publishTopic; }

    public String getRespArrayPath() { return respArrayPath == null ? "" : respArrayPath; }
    public void setRespArrayPath(String respArrayPath) { this.respArrayPath = respArrayPath; }

    public String getRespTargetKey() { return respTargetKey == null ? "" : respTargetKey; }
    public void setRespTargetKey(String respTargetKey) { this.respTargetKey = respTargetKey; }

    public String getRespCommandKey() { return respCommandKey == null ? "" : respCommandKey; }
    public void setRespCommandKey(String respCommandKey) { this.respCommandKey = respCommandKey; }

    public String getRespValueMap() { return respValueMap == null ? "" : respValueMap; }
    public void setRespValueMap(String respValueMap) { this.respValueMap = respValueMap; }

    // 根据状态和activeHigh获取要发送的值
    public int getValueToSend() {
        if (activeHigh) {
            return state ? 1 : 0;  // 高电平：开=1, 关=0
        } else {
            return state ? 0 : 1;  // 低电平：开=0, 关=1
        }
    }
}