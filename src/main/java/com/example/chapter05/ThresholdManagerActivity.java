package com.example.chapter05;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ThresholdManagerActivity extends AppCompatActivity {

    private int projectId;
    private RecyclerView rvList;
    private ThresholdManager thresholdManager;
    private ThresholdAdapter adapter;
    private List<ThresholdItem> thresholdList;
    private MqttManager mqttManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager);

        projectId = getIntent().getIntExtra("project_id", 0);
        ((TextView) findViewById(R.id.tv_title)).setText("阈值管理");
        ((Button) findViewById(R.id.btn_add)).setText("添加阈值");
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        thresholdManager = new ThresholdManager(this);
        mqttManager = MqttManager.getInstance(this);

        thresholdList = thresholdManager.getThresholds(projectId);
        rvList = findViewById(R.id.rv_manager_list);
        rvList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ThresholdAdapter(this, thresholdList,
                this::showEditDialog, this::showAlarmHistoryDialog);
        rvList.setAdapter(adapter);

        Project project = ProjectDataManager.getInstance(this).getProject(projectId);
        if (project != null) {
            for (ThresholdItem item : thresholdList) {
                String v = project.getSensors().get(item.getSensorName());
                if (v != null) {
                    try {
                        adapter.updateCurrentValue(item.getSensorName(), Float.parseFloat(v));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        findViewById(R.id.btn_add).setOnClickListener(v -> showAddDialog());
    }

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_threshold, null);
        EditText etSensor = dialogView.findViewById(R.id.et_sensor_name);
        EditText etDisplayName = dialogView.findViewById(R.id.et_display_name);
        EditText etMin = dialogView.findViewById(R.id.et_min_value);
        EditText etMax = dialogView.findViewById(R.id.et_max_value);
        EditText etMsg = dialogView.findViewById(R.id.et_message);
        CheckBox cbSendToDevice = dialogView.findViewById(R.id.cb_send_to_sensor);
        EditText etUpperKey = dialogView.findViewById(R.id.et_upper_key);
        EditText etLowerKey = dialogView.findViewById(R.id.et_lower_key);
        EditText etTopic = dialogView.findViewById(R.id.et_publish_topic);
        EditText etAlarmJson = dialogView.findViewById(R.id.et_alarm_send_json);
        EditText etRespMax = dialogView.findViewById(R.id.et_resp_max_path);
        EditText etRespMin = dialogView.findViewById(R.id.et_resp_min_path);
        SwitchCompat swEnabled = dialogView.findViewById(R.id.sw_enabled);

        new AlertDialog.Builder(this)
                .setTitle("添加阈值范围")
                .setView(dialogView)
                .setPositiveButton("添加", (dialog, which) -> {
                    String sensor = etSensor.getText().toString().trim();
                    String displayName = etDisplayName.getText().toString().trim();
                    String minStr = etMin.getText().toString().trim();
                    String maxStr = etMax.getText().toString().trim();
                    String upperKey = etUpperKey.getText().toString().trim();
                    String lowerKey = etLowerKey.getText().toString().trim();
                    if (sensor.isEmpty() || displayName.isEmpty() || minStr.isEmpty() || maxStr.isEmpty()
                            || upperKey.isEmpty() || lowerKey.isEmpty()) {
                        Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    float min, max;
                    try {
                        min = Float.parseFloat(minStr);
                        max = Float.parseFloat(maxStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "最小值和最大值必须是数字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (min >= max) {
                        Toast.makeText(this, "最小值必须小于最大值", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String topic = etTopic.getText().toString().trim();
                    if (cbSendToDevice.isChecked() && topic.isEmpty()) {
                        Toast.makeText(this, "发送至设备需填写发布主题", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ThresholdItem item = new ThresholdItem(sensor, displayName, min, max,
                            etMsg.getText().toString().trim(), cbSendToDevice.isChecked(), upperKey, lowerKey);
                    item.setPublishTopic(topic);
                    item.setAlarmSendJson(etAlarmJson.getText().toString().trim());
                    item.setRespMaxPath(etRespMax.getText().toString().trim());
                    item.setRespMinPath(etRespMin.getText().toString().trim());
                    item.setEnabled(swEnabled.isChecked());
                    thresholdManager.addThreshold(projectId, item);
                    refresh();
                    if (cbSendToDevice.isChecked()) {
                        sendThresholdCommand(topic, upperKey, lowerKey, min, max);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditDialog(int position) {
        ThresholdItem item = thresholdList.get(position);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_threshold, null);

        EditText etSensor = dialogView.findViewById(R.id.et_sensor_name);
        EditText etDisplayName = dialogView.findViewById(R.id.et_display_name);
        EditText etMin = dialogView.findViewById(R.id.et_min_value);
        EditText etMax = dialogView.findViewById(R.id.et_max_value);
        EditText etMsg = dialogView.findViewById(R.id.et_message);
        CheckBox cbSendToDevice = dialogView.findViewById(R.id.cb_send_to_sensor);
        EditText etUpperKey = dialogView.findViewById(R.id.et_upper_key);
        EditText etLowerKey = dialogView.findViewById(R.id.et_lower_key);
        EditText etTopic = dialogView.findViewById(R.id.et_publish_topic);
        EditText etAlarmJson = dialogView.findViewById(R.id.et_alarm_send_json);
        EditText etRespMax = dialogView.findViewById(R.id.et_resp_max_path);
        EditText etRespMin = dialogView.findViewById(R.id.et_resp_min_path);
        SwitchCompat swEnabled = dialogView.findViewById(R.id.sw_enabled);

        etSensor.setText(item.getSensorName());
        etDisplayName.setText(item.getDisplayName());
        etMin.setText(String.valueOf(item.getMinValue()));
        etMax.setText(String.valueOf(item.getMaxValue()));
        etMsg.setText(item.getMessage());
        cbSendToDevice.setChecked(item.isSendToSensor());
        etUpperKey.setText(item.getUpperKey());
        etLowerKey.setText(item.getLowerKey());
        etTopic.setText(item.getPublishTopic());
        etAlarmJson.setText(item.getAlarmSendJson());
        etRespMax.setText(item.getRespMaxPath());
        etRespMin.setText(item.getRespMinPath());
        swEnabled.setChecked(item.isEnabled());

        new AlertDialog.Builder(this)
                .setTitle("编辑阈值范围")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String sensor = etSensor.getText().toString().trim();
                    String displayName = etDisplayName.getText().toString().trim();
                    String minStr = etMin.getText().toString().trim();
                    String maxStr = etMax.getText().toString().trim();
                    String upperKey = etUpperKey.getText().toString().trim();
                    String lowerKey = etLowerKey.getText().toString().trim();
                    if (sensor.isEmpty() || displayName.isEmpty() || minStr.isEmpty() || maxStr.isEmpty()
                            || upperKey.isEmpty() || lowerKey.isEmpty()) {
                        Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    float min, max;
                    try {
                        min = Float.parseFloat(minStr);
                        max = Float.parseFloat(maxStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "最小值和最大值必须是数字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (min >= max) {
                        Toast.makeText(this, "最小值必须小于最大值", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String topic = etTopic.getText().toString().trim();
                    if (cbSendToDevice.isChecked() && topic.isEmpty()) {
                        Toast.makeText(this, "发送至设备需填写发布主题", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    item.setSensorName(sensor);
                    item.setDisplayName(displayName);
                    item.setMinValue(min);
                    item.setMaxValue(max);
                    item.setMessage(etMsg.getText().toString().trim());
                    item.setSendToSensor(cbSendToDevice.isChecked());
                    item.setUpperKey(upperKey);
                    item.setLowerKey(lowerKey);
                    item.setPublishTopic(topic);
                    item.setAlarmSendJson(etAlarmJson.getText().toString().trim());
                    item.setRespMaxPath(etRespMax.getText().toString().trim());
                    item.setRespMinPath(etRespMin.getText().toString().trim());
                    item.setEnabled(swEnabled.isChecked());
                    thresholdManager.updateThreshold(projectId, position, item);
                    refresh();
                    if (cbSendToDevice.isChecked()) {
                        sendThresholdCommand(topic, upperKey, lowerKey, min, max);
                    }
                })
                .setNegativeButton("删除", (dialog, which) -> {
                    thresholdManager.deleteThreshold(projectId, position);
                    refresh();
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private void sendThresholdCommand(String topic, String upperKey, String lowerKey, float min, float max) {
        if (!mqttManager.isConnected()) {
            Toast.makeText(this, "MQTT 未连接，无法发送阈值", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put(upperKey, max);
            json.put(lowerKey, min);
            String payload = json.toString();
            mqttManager.publish(topic, payload, mqttManager.getQos(), projectId);
            Toast.makeText(this, "阈值已发送", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "生成JSON失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAlarmHistoryDialog(int position) {
        ThresholdItem item = thresholdList.get(position);
        String realSensorName = item.getSensorName();
        new Thread(() -> {
            AlarmHistoryDatabaseHelper alarmHelper = new AlarmHistoryDatabaseHelper(this);
            List<AlarmHistoryDatabaseHelper.AlarmRecord> records = alarmHelper.getAlarmsBySensor(projectId, realSensorName);
            runOnUiThread(() -> {
                if (records.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setTitle("报警记录 - " + item.getDisplayName())
                            .setMessage("暂无报警记录")
                            .setPositiveButton("确定", null)
                            .show();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (AlarmHistoryDatabaseHelper.AlarmRecord record : records) {
                    sb.append(sdf.format(new Date(record.timestamp))).append("\n")
                            .append(record.alarmMessage).append("\n\n");
                }
                ScrollView scrollView = new ScrollView(this);
                TextView textView = new TextView(this);
                textView.setText(sb.toString());
                textView.setPadding(32, 32, 32, 32);
                textView.setTextSize(14);
                scrollView.addView(textView);
                new AlertDialog.Builder(this)
                        .setTitle("报警记录 - " + item.getDisplayName())
                        .setView(scrollView)
                        .setPositiveButton("关闭", null)
                        .show();
            });
        }).start();
    }

    private void refresh() {
        thresholdList = thresholdManager.getThresholds(projectId);
        adapter.updateData(thresholdList);
    }
}
