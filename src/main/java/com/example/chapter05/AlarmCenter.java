package com.example.chapter05;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局报警中心（单例）。
 * 阈值超限等事件触发 addAlarm，通知所有监听器（Activity）在顶部渲染报警卡片。
 */
public class AlarmCenter {
    private static final int MAX_ALARMS = 3;

    public static class AlarmItem {
        public int projectId;
        public String sensorName;
        public String message;
        public long timestamp;

        public AlarmItem(int projectId, String sensorName, String message, long timestamp) {
            this.projectId = projectId;
            this.sensorName = sensorName;
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    public interface AlarmListener {
        void onAlarmAdded(AlarmItem alarm);
    }

    private static AlarmCenter instance;
    private final List<AlarmItem> alarms = new ArrayList<>();
    private final List<AlarmListener> listeners = new ArrayList<>();

    private AlarmCenter() {}

    public static synchronized AlarmCenter getInstance() {
        if (instance == null) {
            instance = new AlarmCenter();
        }
        return instance;
    }

    public void addAlarm(int projectId, String sensorName, String message) {
        AlarmItem item = new AlarmItem(projectId, sensorName, message, System.currentTimeMillis());
        synchronized (alarms) {
            alarms.add(item);
            if (alarms.size() > MAX_ALARMS) {
                alarms.remove(0);
            }
        }
        for (AlarmListener l : listeners) {
            l.onAlarmAdded(item);
        }
    }

    public void addListener(AlarmListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(AlarmListener listener) {
        listeners.remove(listener);
    }
}
