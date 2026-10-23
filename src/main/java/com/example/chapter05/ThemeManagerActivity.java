package com.example.chapter05;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ThemeManagerActivity extends AppCompatActivity {

    private int projectId;
    private RecyclerView rvList;
    private ThemeManager themeManager;
    private ThemeAdapter adapter;
    private List<ThemeItem> themeList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager);

        projectId = getIntent().getIntExtra("project_id", 0);
        ((TextView) findViewById(R.id.tv_title)).setText("主题管理");
        ((Button) findViewById(R.id.btn_add)).setText("添加主题");
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        themeManager = ThemeManager.getInstance(this);
        themeList = themeManager.getThemes(projectId);
        rvList = findViewById(R.id.rv_manager_list);
        rvList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ThemeAdapter(themeList, new ThemeAdapter.Listener() {
            @Override
            public void onThemeClick(int position) {
                activate(position);
            }

            @Override
            public void onThemeLongClick(int position) {
                showEditDialog(position);
            }
        });
        rvList.setAdapter(adapter);

        findViewById(R.id.btn_add).setOnClickListener(v -> showAddDialog());
    }

    private void activate(int position) {
        ThemeItem item = themeList.get(position);
        themeManager.activateTheme(projectId, item.getThemeCode());
        if (item.getPublishTopic() != null && !item.getPublishTopic().isEmpty()) {
            MqttManager mqtt = MqttManager.getInstance(this);
            if (mqtt.isConnected()) {
                mqtt.publish(item.getPublishTopic(), "{\"" + item.getThemeCode() + "\":1}", mqtt.getQos(), projectId);
            }
        }
        refresh();
    }

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_theme, null);
        Spinner spinner = dialogView.findViewById(R.id.spinner_theme_code);
        EditText etName = dialogView.findViewById(R.id.et_theme_display_name);
        EditText etTopic = dialogView.findViewById(R.id.et_theme_publish_topic);
        EditText etHttpKeyName = dialogView.findViewById(R.id.et_http_key_name);
        EditText etHttpKeyValue = dialogView.findViewById(R.id.et_http_key_value);
        EditText etMqttKeyName = dialogView.findViewById(R.id.et_mqtt_key_name);
        EditText etMqttKeyValue = dialogView.findViewById(R.id.et_mqtt_key_value);
        setupThemeSpinner(spinner);

        new AlertDialog.Builder(this)
                .setTitle("添加主题")
                .setView(dialogView)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String topic = etTopic.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "请填写显示名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String code = ThemeManager.THEME_CODES[spinner.getSelectedItemPosition()];
                    ThemeItem item = new ThemeItem(code, name, topic);
                    item.setHttpKeyName(etHttpKeyName.getText().toString().trim());
                    item.setHttpKeyValue(etHttpKeyValue.getText().toString().trim());
                    item.setMqttKeyName(etMqttKeyName.getText().toString().trim());
                    item.setMqttKeyValue(etMqttKeyValue.getText().toString().trim());
                    themeManager.addTheme(projectId, item);
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditDialog(int position) {
        if (position < 0 || position >= themeList.size()) return;
        ThemeItem item = themeList.get(position);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_theme, null);
        Spinner spinner = dialogView.findViewById(R.id.spinner_theme_code);
        EditText etName = dialogView.findViewById(R.id.et_theme_display_name);
        EditText etTopic = dialogView.findViewById(R.id.et_theme_publish_topic);
        EditText etHttpKeyName = dialogView.findViewById(R.id.et_http_key_name);
        EditText etHttpKeyValue = dialogView.findViewById(R.id.et_http_key_value);
        EditText etMqttKeyName = dialogView.findViewById(R.id.et_mqtt_key_name);
        EditText etMqttKeyValue = dialogView.findViewById(R.id.et_mqtt_key_value);
        setupThemeSpinner(spinner);

        int codeIndex = 0;
        for (int i = 0; i < ThemeManager.THEME_CODES.length; i++) {
            if (ThemeManager.THEME_CODES[i].equals(item.getThemeCode())) {
                codeIndex = i;
                break;
            }
        }
        spinner.setSelection(codeIndex);
        etName.setText(item.getDisplayName());
        etTopic.setText(item.getPublishTopic());
        etHttpKeyName.setText(item.getHttpKeyName());
        etHttpKeyValue.setText(item.getHttpKeyValue());
        etMqttKeyName.setText(item.getMqttKeyName());
        etMqttKeyValue.setText(item.getMqttKeyValue());

        new AlertDialog.Builder(this)
                .setTitle("编辑主题")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String topic = etTopic.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "请填写显示名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    item.setThemeCode(ThemeManager.THEME_CODES[spinner.getSelectedItemPosition()]);
                    item.setDisplayName(name);
                    item.setPublishTopic(topic);
                    item.setHttpKeyName(etHttpKeyName.getText().toString().trim());
                    item.setHttpKeyValue(etHttpKeyValue.getText().toString().trim());
                    item.setMqttKeyName(etMqttKeyName.getText().toString().trim());
                    item.setMqttKeyValue(etMqttKeyValue.getText().toString().trim());
                    themeManager.updateTheme(projectId, position, item);
                    refresh();
                })
                .setNegativeButton("删除", (dialog, which) -> {
                    themeManager.deleteTheme(projectId, position);
                    refresh();
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private void setupThemeSpinner(Spinner spinner) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ThemeManager.THEME_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void refresh() {
        themeList = themeManager.getThemes(projectId);
        adapter.updateData(themeList);
    }
}
