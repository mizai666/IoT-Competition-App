package com.example.chapter05;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MqttManager implements MqttCallback {
    private static final String TAG = "MqttManager";
    private static final String PREF_MQTT_NAME = "config_mqtt";
    private static final String PREF_SUBSCRIBE = "mqtt_subscribe";
    private static final String KEY_SUBSCRIBED_TOPIC = "subscribed_topic";

    private static MqttManager instance;
    private Context appContext;
    private MqttClient mqttClient;
    private boolean connected = false;
    private MessageDatabaseHelper dbHelper;
    private SensorHistoryDatabaseHelper historyDbHelper;
    private ThresholdManager thresholdManager;

    private List<MessageListener> listeners = new ArrayList<>();

    public static synchronized MqttManager getInstance(Context context) {
        if (instance == null) {
            instance = new MqttManager(context.getApplicationContext());
        }
        return instance;
    }

    private MqttManager(Context context) {
        this.appContext = context;
        dbHelper = new MessageDatabaseHelper(appContext);
        historyDbHelper = new SensorHistoryDatabaseHelper(appContext);
        thresholdManager = new ThresholdManager(appContext);
    }

    public int getQos() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_MQTT_NAME, Context.MODE_PRIVATE);
        return sp.getInt("qos", 1);
    }

    public void connect() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_MQTT_NAME, Context.MODE_PRIVATE);
        String savedClientId = sp.getString("client_id", "");
        String server = sp.getString("server", "");
        String port = sp.getString("port", "");

        if (savedClientId.isEmpty() || server.isEmpty() || port.isEmpty()) {
            notifyError("MQTT 配置无效，请先在首页配置");
            return;
        }

        String clientId = savedClientId + "_" + System.currentTimeMillis();
        String brokerUrl = "tcp://" + server + ":" + port;

        if (connected) {
            Log.d(TAG, "已经连接");
            return;
        }

        new Thread(() -> {
            try {
                Log.d(TAG, "正在连接到 " + brokerUrl);
                mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);
                options.setConnectionTimeout(10);
                options.setKeepAliveInterval(60);
                options.setAutomaticReconnect(false);
                options.setCleanSession(true);

                mqttClient.setCallback(this);
                mqttClient.connect(options);
                connected = true;
                notifyConnectionStatus(true);
                String lastTopic = getLastSubscribedTopic();
                if (!lastTopic.isEmpty()) {
                    subscribe(lastTopic);
                }
            } catch (MqttException e) {
                Log.e(TAG, "连接失败", e);
                connected = false;
                notifyConnectionStatus(false);
                notifyError("连接失败: " + e.getMessage());
            }
        }).start();
    }

    public void disconnect() {
        if (!connected || mqttClient == null) return;
        new Thread(() -> {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                connected = false;
                notifyConnectionStatus(false);
            } catch (MqttException e) {
                Log.e(TAG, "断开失败", e);
                notifyError("断开失败: " + e.getMessage());
            }
        }).start();
    }

    public void subscribe(String topic) {
        if (!connected || mqttClient == null) {
            notifyError("未连接，无法订阅");
            return;
        }
        new Thread(() -> {
            try {
                int qos = getQos();
                mqttClient.subscribe(topic, qos);
                Log.d(TAG, "订阅成功: " + topic + " QoS=" + qos);
                saveSubscribedTopic(topic);
            } catch (MqttException e) {
                Log.e(TAG, "订阅失败", e);
                notifyError("订阅失败: " + e.getMessage());
            }
        }).start();
    }

    public void unsubscribe(String topic) {
        if (!connected || mqttClient == null) return;
        new Thread(() -> {
            try {
                mqttClient.unsubscribe(topic);
                Log.d(TAG, "取消订阅: " + topic);
                clearSubscribedTopic();
            } catch (MqttException e) {
                Log.e(TAG, "取消订阅失败", e);
            }
        }).start();
    }

    private void saveSubscribedTopic(String topic) {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_SUBSCRIBE, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_SUBSCRIBED_TOPIC, topic).apply();
    }

    public String getLastSubscribedTopic() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_SUBSCRIBE, Context.MODE_PRIVATE);
        return sp.getString(KEY_SUBSCRIBED_TOPIC, "");
    }

    private void clearSubscribedTopic() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_SUBSCRIBE, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_SUBSCRIBED_TOPIC).apply();
    }

    public void publish(String topic, String message, int qos) {
        publish(topic, message, qos, -1);
    }

    public void publish(String topic, String message, int qos, int projectId) {
        if (!connected || mqttClient == null) {
            notifyError("未连接，无法发布");
            return;
        }
        new Thread(() -> {
            try {
                MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                mqttMessage.setQos(qos);
                mqttClient.publish(topic, mqttMessage);
                Log.d(TAG, "发布成功");
                if (projectId >= 0) {
                    recordPublish(projectId, "mqtt", topic, message, String.valueOf(qos), true, "", "", "", "");
                    // 发送的开关指令同步回显本地开关状态（对齐 Hchapter01）
                    syncSwitchState(projectId, message);
                }
            } catch (MqttException e) {
                Log.e(TAG, "发布失败", e);
                if (projectId >= 0) {
                    recordPublish(projectId, "mqtt", topic, message, String.valueOf(qos), false, "ERR", "", "", "");
                }
                notifyError("发布失败: " + e.getMessage());
            }
        }).start();
    }

    private void recordPublish(int projectId, String type, String topic, String payload, String qos,
                               boolean ok, String status, String url, String responseData, String method) {
        PublishHistoryDatabaseHelper helper = new PublishHistoryDatabaseHelper(appContext);
        PublishHistoryDatabaseHelper.PublishRecord record = new PublishHistoryDatabaseHelper.PublishRecord();
        record.projectId = projectId;
        record.type = type;
        record.topic = topic;
        record.payload = payload;
        record.qos = qos;
        record.ok = ok;
        record.status = status;
        record.url = url;
        record.responseData = responseData;
        record.method = method;
        record.createdAt = System.currentTimeMillis();
        helper.insert(record);
    }

    /**
     * 发送开关指令后回显本地开关状态，变化时广播通知 UI 刷新。
     */
    private void syncSwitchState(int projectId, String message) {
        Log.d(TAG, "syncSwitchState 调用: projectId=" + projectId + ", message=" + message);
        try {
            JSONObject json = new JSONObject(message);
            SwitchManager switchManager = new SwitchManager(appContext);
            boolean changed = switchManager.applyJsonState(projectId, json);
            Log.d(TAG, "syncSwitchState 结果: changed=" + changed);
            if (changed) {
                notifySwitchStateChanged(projectId);
                Log.d(TAG, "syncSwitchState 已通知开关状态变化 projectId=" + projectId);
            }
        } catch (JSONException e) {
            Log.e(TAG, "syncSwitchState JSON解析失败", e);
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void addListener(MessageListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    private void notifyConnectionStatus(boolean connected) {
        for (MessageListener l : listeners) {
            l.onConnectionStatusChanged(connected);
        }
    }

    private void notifyMessageArrived(String topic, String payload) {
        for (MessageListener l : listeners) {
            l.onMessageArrived(topic, payload);
        }
    }

    private void notifyError(String error) {
        for (MessageListener l : listeners) {
            l.onError(error);
        }
    }

    private void notifySwitchStateChanged(int projectId) {
        for (MessageListener l : listeners) {
            l.onSwitchStateChanged(projectId);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.e(TAG, "连接丢失", cause);
        connected = false;
        notifyConnectionStatus(false);
        notifyError("连接丢失: " + cause.getMessage());
    }

    private int extractProjectId(String key) {
        Pattern pattern = Pattern.compile("\\d+$");
        Matcher matcher = pattern.matcher(key);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return -1;
    }

    private String extractSensorName(String key) {
        return key.replaceAll("\\d+$", "");
    }

    private void checkThresholdRange(int projectId, String sensorName, float value) {
        List<ThresholdItem> thresholds = thresholdManager.getThresholds(projectId);
        Log.d(TAG, "阈值检查: projectId=" + projectId + ", sensor=" + sensorName + ", value=" + value + ", 阈值数量=" + thresholds.size());
        for (ThresholdItem threshold : thresholds) {
            if (threshold.getSensorName().equals(sensorName)) {
                boolean outOfRange = (value < threshold.getMinValue() || value > threshold.getMaxValue());
                if (outOfRange) {
                    String alarmTopic = "alarm/" + projectId + "/" + sensorName;
                    String defaultMsg = String.format("阈值告警: %s 超出范围 [%.2f, %.2f], 当前值 %.2f",
                            sensorName, threshold.getMinValue(), threshold.getMaxValue(), value);
                    String alarmMsg = (threshold.getMessage() != null && !threshold.getMessage().isEmpty())
                            ? threshold.getMessage() : defaultMsg;
                    publish(alarmTopic, alarmMsg, 1, projectId);
                    Log.d(TAG, "触发阈值告警: " + alarmMsg);

                    AlarmHistoryDatabaseHelper alarmHelper = new AlarmHistoryDatabaseHelper(appContext);
                    alarmHelper.insertAlarm(projectId, sensorName, alarmMsg, System.currentTimeMillis());

                    // 报警增强：发送自定义 JSON 指令到指定主题
                    String alarmJson = threshold.getAlarmSendJson();
                    String publishTopic = threshold.getPublishTopic();
                    if (!alarmJson.isEmpty() && !publishTopic.isEmpty()) {
                        if (isValidJson(alarmJson)) {
                            publish(publishTopic, alarmJson, 1, projectId);
                            Log.d(TAG, "发送报警 JSON 指令 -> [" + publishTopic + "] " + alarmJson);
                        } else {
                            Log.w(TAG, "报警 JSON 指令格式错误，跳过发送: " + alarmJson);
                        }
                    }

                    // 全局报警通知
                    AlarmCenter.getInstance().addAlarm(projectId, sensorName, alarmMsg);
                }
            }
        }
    }

    private boolean isValidJson(String json) {
        try {
            new JSONObject(json);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    // 在 MqttManager.java 中找到 messageArrived 方法，替换为以下内容
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        final String payload = new String(message.getPayload());
        Log.d(TAG, "收到消息: " + payload);

        boolean isSpecial = SpecialJsonIdentifier.isSpecialJson(payload);

        if (!isSpecial) {
            // 普通消息：存入原始消息数据库
            dbHelper.insertMessage(payload);
            // 原有阈值处理（带数字后缀）
            try {
                JSONObject json = new JSONObject(payload);
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = json.optString(key);
                    int projectId = extractProjectId(key);
                    if (projectId != -1) {
                        try {
                            float floatValue = Float.parseFloat(value);
                            checkThresholdRange(projectId, key, floatValue);
                        } catch (NumberFormatException ignored) { }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "解析 JSON 失败", e);
            }
        } else {
            // 特殊JSON：不存入普通消息表，只更新项目数据
            Log.d(TAG, "特殊JSON，不存入普通消息表，调用 updateFromJson");
            ProjectDataManager dataManager = ProjectDataManager.getInstance(appContext);
            dataManager.updateFromJson(payload);

            int projectId = dataManager.getLastSpecialProjectId();
            Log.d(TAG, "特殊JSON获取项目ID: " + projectId);
            if (projectId != -1) {
                try {
                    JSONObject json = new JSONObject(payload);
                    Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object valueObj = json.get(key);
                        if (valueObj instanceof Number) {
                            float floatValue = ((Number) valueObj).floatValue();
                            checkThresholdRange(projectId, key, floatValue);
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "特殊JSON阈值检查失败", e);
                }
            } else {
                Log.w(TAG, "特殊JSON未获取到项目ID");
            }
        }

        // 自动开关：键值匹配切换自动/手动模式
        AutoSwitchManager.getInstance(appContext).checkMode(payload);

        notifyMessageArrived(topic, payload);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 无需处理
    }

    public interface MessageListener {
        void onConnectionStatusChanged(boolean connected);
        void onMessageArrived(String topic, String payload);
        void onError(String error);
        default void onSwitchStateChanged(int projectId) {}
    }
}