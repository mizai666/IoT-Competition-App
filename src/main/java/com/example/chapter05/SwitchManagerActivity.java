package com.example.chapter05;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SwitchManagerActivity extends AppCompatActivity {

    private int projectId;
    private RecyclerView rvList;
    private SwitchManager switchManager;
    private SwitchAdapter adapter;
    private MqttManager mqttManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager);

        projectId = getIntent().getIntExtra("project_id", 0);
        ((TextView) findViewById(R.id.tv_title)).setText("开关管理");
        ((Button) findViewById(R.id.btn_add)).setText("添加开关");
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        switchManager = new SwitchManager(this);
        mqttManager = MqttManager.getInstance(this);

        rvList = findViewById(R.id.rv_manager_list);
        rvList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SwitchAdapter(switchManager.getSwitches(projectId), false, new SwitchAdapter.Listener() {
            @Override
            public void onSwitchToggle(int position, boolean checked) {
                List<SwitchItem> list = switchManager.getSwitches(projectId);
                if (position < 0 || position >= list.size()) return;
                SwitchItem item = list.get(position);
                item.setState(checked);
                switchManager.updateSwitch(projectId, position, item);
                sendSwitchCommand(item);
            }

            @Override
            public void onSwitchLongClick(int position) {
                showEditDialog(position);
            }
        });
        rvList.setAdapter(adapter);

        findViewById(R.id.btn_add).setOnClickListener(v -> showAddDialog());
    }

    private void sendSwitchCommand(SwitchItem item) {
        if (!mqttManager.isConnected()) {
            Toast.makeText(this, "MQTT 未连接", Toast.LENGTH_SHORT).show();
            return;
        }
        String topic = item.getPublishTopic();
        if (topic.isEmpty()) {
            Toast.makeText(this, "该开关未配置发布主题", Toast.LENGTH_SHORT).show();
            return;
        }
        int value = item.getValueToSend();
        String json = "{\"" + item.getJsonKey() + "\":" + value + "}";
        mqttManager.publish(topic, json, mqttManager.getQos(), projectId);
        Toast.makeText(this, "已发送: " + json, Toast.LENGTH_SHORT).show();
    }

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_switch, null);
        EditText etName = dialogView.findViewById(R.id.et_display_name);
        EditText etKey = dialogView.findViewById(R.id.et_json_key);
        EditText etTopic = dialogView.findViewById(R.id.et_publish_topic);
        CheckBox cbActiveHigh = dialogView.findViewById(R.id.cb_active_high);
        EditText etRespArray = dialogView.findViewById(R.id.et_resp_array_path);
        EditText etRespTarget = dialogView.findViewById(R.id.et_resp_target_key);
        EditText etRespCommand = dialogView.findViewById(R.id.et_resp_command_key);
        EditText etRespValueMap = dialogView.findViewById(R.id.et_resp_value_map);
        cbActiveHigh.setChecked(true);
        new AlertDialog.Builder(this)
                .setTitle("添加开关")
                .setView(dialogView)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String key = etKey.getText().toString().trim();
                    if (name.isEmpty() || key.isEmpty()) {
                        Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SwitchItem item = new SwitchItem(name, key, cbActiveHigh.isChecked());
                    item.setPublishTopic(etTopic.getText().toString().trim());
                    item.setRespArrayPath(etRespArray.getText().toString().trim());
                    item.setRespTargetKey(etRespTarget.getText().toString().trim());
                    item.setRespCommandKey(etRespCommand.getText().toString().trim());
                    item.setRespValueMap(etRespValueMap.getText().toString().trim());
                    switchManager.addSwitch(projectId, item);
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditDialog(int position) {
        List<SwitchItem> list = switchManager.getSwitches(projectId);
        if (position < 0 || position >= list.size()) return;
        SwitchItem item = list.get(position);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_switch, null);
        EditText etName = dialogView.findViewById(R.id.et_display_name);
        EditText etKey = dialogView.findViewById(R.id.et_json_key);
        EditText etTopic = dialogView.findViewById(R.id.et_publish_topic);
        CheckBox cbActiveHigh = dialogView.findViewById(R.id.cb_active_high);
        EditText etRespArray = dialogView.findViewById(R.id.et_resp_array_path);
        EditText etRespTarget = dialogView.findViewById(R.id.et_resp_target_key);
        EditText etRespCommand = dialogView.findViewById(R.id.et_resp_command_key);
        EditText etRespValueMap = dialogView.findViewById(R.id.et_resp_value_map);

        etName.setText(item.getDisplayName());
        etKey.setText(item.getJsonKey());
        etTopic.setText(item.getPublishTopic());
        cbActiveHigh.setChecked(item.isActiveHigh());
        etRespArray.setText(item.getRespArrayPath());
        etRespTarget.setText(item.getRespTargetKey());
        etRespCommand.setText(item.getRespCommandKey());
        etRespValueMap.setText(item.getRespValueMap());

        new AlertDialog.Builder(this)
                .setTitle("编辑开关")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String key = etKey.getText().toString().trim();
                    if (name.isEmpty() || key.isEmpty()) {
                        Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    item.setDisplayName(name);
                    item.setJsonKey(key);
                    item.setPublishTopic(etTopic.getText().toString().trim());
                    item.setActiveHigh(cbActiveHigh.isChecked());
                    item.setRespArrayPath(etRespArray.getText().toString().trim());
                    item.setRespTargetKey(etRespTarget.getText().toString().trim());
                    item.setRespCommandKey(etRespCommand.getText().toString().trim());
                    item.setRespValueMap(etRespValueMap.getText().toString().trim());
                    switchManager.updateSwitch(projectId, position, item);
                    refresh();
                })
                .setNegativeButton("删除", (dialog, which) -> {
                    switchManager.deleteSwitch(projectId, position);
                    refresh();
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private void refresh() {
        adapter.updateData(switchManager.getSwitches(projectId));
    }
}
