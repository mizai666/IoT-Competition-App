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
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ScheduleManagerActivity extends AppCompatActivity {

    private int projectId;
    private RecyclerView rvList;
    private ScheduleManager scheduleManager;
    private ScheduleAdapter adapter;
    private List<ScheduleItem> scheduleList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager);

        projectId = getIntent().getIntExtra("project_id", 0);
        ((TextView) findViewById(R.id.tv_title)).setText("定时管理");
        ((Button) findViewById(R.id.btn_add)).setText("添加定时任务");
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        scheduleManager = ScheduleManager.getInstance(this);
        scheduleList = scheduleManager.getSchedules(projectId);
        rvList = findViewById(R.id.rv_manager_list);
        rvList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScheduleAdapter(scheduleList,
                (position, view) -> showEditDialog(position),
                scheduleManager, projectId);
        rvList.setAdapter(adapter);
        adapter.start();

        findViewById(R.id.btn_add).setOnClickListener(v -> showAddDialog());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) adapter.stop();
    }

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_schedule, null);
        EditText etName = dialogView.findViewById(R.id.et_display_name);
        EditText etKey = dialogView.findViewById(R.id.et_key_name);
        EditText etTopic = dialogView.findViewById(R.id.et_publish_topic);
        EditText etTime = dialogView.findViewById(R.id.et_schedule_time);
        CheckBox cbActiveHigh = dialogView.findViewById(R.id.cb_active_high);
        SwitchCompat swTarget = dialogView.findViewById(R.id.sw_target_state);
        SwitchCompat swEnabled = dialogView.findViewById(R.id.sw_enabled);
        EditText etRespArray = dialogView.findViewById(R.id.et_resp_array_path);
        EditText etRespTarget = dialogView.findViewById(R.id.et_resp_target_key);
        EditText etRespCommand = dialogView.findViewById(R.id.et_resp_command_key);

        new AlertDialog.Builder(this)
                .setTitle("添加定时任务")
                .setView(dialogView)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String key = etKey.getText().toString().trim();
                    String topic = etTopic.getText().toString().trim();
                    String time = etTime.getText().toString().trim();
                    if (name.isEmpty() || key.isEmpty() || topic.isEmpty() || time.isEmpty()) {
                        Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ScheduleItem item = new ScheduleItem(
                            key, name, topic, cbActiveHigh.isChecked(),
                            swTarget.isChecked() ? 1 : 0, time, swEnabled.isChecked(),
                            etRespArray.getText().toString().trim(),
                            etRespTarget.getText().toString().trim(),
                            etRespCommand.getText().toString().trim());
                    scheduleManager.addSchedule(projectId, item);
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditDialog(int position) {
        if (position < 0 || position >= scheduleList.size()) return;
        ScheduleItem item = scheduleList.get(position);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_schedule, null);
        EditText etName = dialogView.findViewById(R.id.et_display_name);
        EditText etKey = dialogView.findViewById(R.id.et_key_name);
        EditText etTopic = dialogView.findViewById(R.id.et_publish_topic);
        EditText etTime = dialogView.findViewById(R.id.et_schedule_time);
        CheckBox cbActiveHigh = dialogView.findViewById(R.id.cb_active_high);
        SwitchCompat swTarget = dialogView.findViewById(R.id.sw_target_state);
        SwitchCompat swEnabled = dialogView.findViewById(R.id.sw_enabled);
        EditText etRespArray = dialogView.findViewById(R.id.et_resp_array_path);
        EditText etRespTarget = dialogView.findViewById(R.id.et_resp_target_key);
        EditText etRespCommand = dialogView.findViewById(R.id.et_resp_command_key);

        etName.setText(item.getDisplayName());
        etKey.setText(item.getKeyName());
        etTopic.setText(item.getPublishTopic());
        etTime.setText(item.getScheduleTime());
        cbActiveHigh.setChecked(item.isActiveHigh());
        swTarget.setChecked(item.getTargetState() == 1);
        swEnabled.setChecked(item.isEnabled());
        etRespArray.setText(item.getRespArrayPath());
        etRespTarget.setText(item.getRespTargetKey());
        etRespCommand.setText(item.getRespCommandKey());

        new AlertDialog.Builder(this)
                .setTitle("编辑定时任务")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String key = etKey.getText().toString().trim();
                    String topic = etTopic.getText().toString().trim();
                    String time = etTime.getText().toString().trim();
                    if (name.isEmpty() || key.isEmpty() || topic.isEmpty() || time.isEmpty()) {
                        Toast.makeText(this, "请填写完整", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    item.setDisplayName(name);
                    item.setKeyName(key);
                    item.setPublishTopic(topic);
                    item.setActiveHigh(cbActiveHigh.isChecked());
                    item.setTargetState(swTarget.isChecked() ? 1 : 0);
                    item.setScheduleTime(time);
                    item.setEnabled(swEnabled.isChecked());
                    item.setRespArrayPath(etRespArray.getText().toString().trim());
                    item.setRespTargetKey(etRespTarget.getText().toString().trim());
                    item.setRespCommandKey(etRespCommand.getText().toString().trim());
                    scheduleManager.updateSchedule(projectId, position, item);
                    refresh();
                })
                .setNegativeButton("删除", (dialog, which) -> {
                    scheduleManager.deleteSchedule(projectId, position);
                    refresh();
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private void refresh() {
        scheduleList = scheduleManager.getSchedules(projectId);
        adapter.updateData(scheduleList);
    }
}
