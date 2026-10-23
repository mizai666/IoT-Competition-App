package com.example.chapter05;

/**
 * 定时任务配置项（对齐 Hchapter01 schedule_configs）。
 * 倒计时 scheduleTime 秒后，把 keyName 设为 targetState（开/关）发到 publishTopic。
 */
public class ScheduleItem {
    private String keyName;          // 开关键名
    private String displayName;      // 显示名称
    private String publishTopic;     // 发布主题
    private boolean activeHigh;      // 高电平（true: 开=1）
    private int targetState;         // 0=关, 1=开
    private String scheduleTime;     // 秒数倒计时
    private boolean enabled;         // 是否启用
    private String respArrayPath;    // 响应规则：数组路径
    private String respTargetKey;    // 响应规则：目标字段
    private String respCommandKey;   // 响应规则：命令字段
    private transient int remainingSeconds; // 剩余秒数（运行时，不持久化）

    public ScheduleItem(String keyName, String displayName, String publishTopic,
                        boolean activeHigh, int targetState, String scheduleTime,
                        boolean enabled, String respArrayPath, String respTargetKey, String respCommandKey) {
        this.keyName = keyName;
        this.displayName = displayName;
        this.publishTopic = publishTopic;
        this.activeHigh = activeHigh;
        this.targetState = targetState;
        this.scheduleTime = scheduleTime;
        this.enabled = enabled;
        this.respArrayPath = respArrayPath;
        this.respTargetKey = respTargetKey;
        this.respCommandKey = respCommandKey;
        this.remainingSeconds = getScheduleSeconds();
    }

    public int getScheduleSeconds() {
        try {
            return Integer.parseInt(scheduleTime == null ? "0" : scheduleTime.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean tick() {
        if (!enabled) return false;
        remainingSeconds--;
        if (remainingSeconds <= 0) {
            remainingSeconds = getScheduleSeconds();
            return true;
        }
        return false;
    }

    /** 发送值：开=1(高电平)/0(低电平)，关=0/1 */
    public int getSendValue() {
        return targetState == 1 ? (activeHigh ? 1 : 0) : (activeHigh ? 0 : 1);
    }

    public String getKeyName() { return keyName == null ? "" : keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }

    public String getDisplayName() { return displayName == null ? "" : displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPublishTopic() { return publishTopic == null ? "" : publishTopic; }
    public void setPublishTopic(String publishTopic) { this.publishTopic = publishTopic; }

    public boolean isActiveHigh() { return activeHigh; }
    public void setActiveHigh(boolean activeHigh) { this.activeHigh = activeHigh; }

    public int getTargetState() { return targetState; }
    public void setTargetState(int targetState) { this.targetState = targetState; }

    public String getScheduleTime() { return scheduleTime == null ? "" : scheduleTime; }
    public void setScheduleTime(String scheduleTime) { this.scheduleTime = scheduleTime; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRespArrayPath() { return respArrayPath == null ? "" : respArrayPath; }
    public void setRespArrayPath(String respArrayPath) { this.respArrayPath = respArrayPath; }

    public String getRespTargetKey() { return respTargetKey == null ? "" : respTargetKey; }
    public void setRespTargetKey(String respTargetKey) { this.respTargetKey = respTargetKey; }

    public String getRespCommandKey() { return respCommandKey == null ? "" : respCommandKey; }
    public void setRespCommandKey(String respCommandKey) { this.respCommandKey = respCommandKey; }

    public int getRemainingSeconds() { return remainingSeconds; }
    public void setRemainingSeconds(int remainingSeconds) { this.remainingSeconds = remainingSeconds; }
}
