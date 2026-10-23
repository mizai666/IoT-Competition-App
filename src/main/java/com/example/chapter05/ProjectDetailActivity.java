package com.example.chapter05;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class ProjectDetailActivity extends AppCompatActivity implements AlarmCenter.AlarmListener {

    private int projectId;
    private RadioGroup rgNavigation;
    private TextView tvProjectTitle;
    private LinearLayout alarmContainer;
    private LinearLayout themeBar;
    private ThemeManager themeManager;
    private static final String TAG = "ProjectDetailAct";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_detail);

        projectId = getIntent().getIntExtra("project_id", 0);
        Log.d(TAG, "=== Activity 启动，项目 ID: " + projectId + " ===");

        tvProjectTitle = findViewById(R.id.tv_project_title);
        rgNavigation = findViewById(R.id.rg_navigation);
        alarmContainer = findViewById(R.id.alarm_container);
        AlarmCenter.getInstance().addListener(this);

        Project project = ProjectDataManager.getInstance(this).getProject(projectId);
        if (project != null) {
            tvProjectTitle.setText("项目名称: " + project.getDisplayName() + " (ID: " + projectId + ")");
        }

        setupThemeBar();

        // 首次加载
        if (savedInstanceState == null) {
            Log.d(TAG, "首次加载，切换到首页 Fragment");
            boolean isSpecial = (project != null && project.isSpecial());
            loadFragment(DetailHomeFragment.newInstance(projectId));
            RadioButton rbHome = findViewById(R.id.rb_home);
            if (rbHome != null) rbHome.setChecked(true);
        }

        rgNavigation.setOnCheckedChangeListener((group, checkedId) -> {
            Fragment fragment = null;
            String pageName = "";

            if (checkedId == R.id.rb_home) {
                boolean isSpecial = (project != null && project.isSpecial());
                fragment = DetailHomeFragment.newInstance(projectId);
                pageName = "首页";
            } else if (checkedId == R.id.rb_publish) {
                fragment = DetailPublishFragment.newInstance(projectId);
                pageName = "发布";
            } else if (checkedId == R.id.rb_database) {
                fragment = DetailDatabaseFragment.newInstance(projectId);
                pageName = "数据库";
            } else if (checkedId == R.id.rb_monitor) {
                fragment = DetailMonitorFragment.newInstance(projectId);
                pageName = "监控";
            }

            if (fragment != null) {
                Log.d(TAG, "用户点击切换至: " + pageName);
                loadFragment(fragment);
            }
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    // ========== 主题控制 ==========
    private void setupThemeBar() {
        themeBar = findViewById(R.id.theme_bar);
        themeManager = ThemeManager.getInstance(this);
        renderThemeBar();
    }

    private void renderThemeBar() {
        themeBar.removeAllViews();
        List<ThemeItem> themes = themeManager.getThemes(projectId);
        int activeColor = ThemeManager.getThemeColor("normal");

        for (int i = 0; i < themes.size(); i++) {
            final ThemeItem item = themes.get(i);
            final int pos = i;
            MaterialButton btn = new MaterialButton(this);
            btn.setText(item.getDisplayName());
            btn.setAllCaps(false);
            btn.setTextSize(13);
            btn.setCornerRadius(dp(20));
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            int color = ThemeManager.getThemeColor(item.getThemeCode());
            if (item.isActive()) {
                activeColor = color;
                btn.setTextColor(0xFFFFFFFF);
                btn.setBackgroundColor(color);
            } else {
                btn.setTextColor(0xFF6B7280);
                btn.setBackgroundColor(0xFFF3F4F6);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> activateTheme(item.getThemeCode()));
            btn.setOnLongClickListener(v -> {
                showThemeEditDialog(pos);
                return true;
            });
            themeBar.addView(btn);
        }

        applyThemeColor(activeColor);
    }

    private void activateTheme(String themeCode) {
        themeManager.activateTheme(projectId, themeCode);
        ThemeItem active = null;
        for (ThemeItem t : themeManager.getThemes(projectId)) {
            if (t.getThemeCode().equals(themeCode)) { active = t; break; }
        }
        if (active != null && active.getPublishTopic() != null && !active.getPublishTopic().isEmpty()) {
            MqttManager mqtt = MqttManager.getInstance(this);
            if (mqtt.isConnected()) {
                mqtt.publish(active.getPublishTopic(), "{\"" + themeCode + "\":1}", mqtt.getQos(), projectId);
            }
        }
        renderThemeBar();
    }

    private void applyThemeColor(int color) {
        tvProjectTitle.setBackgroundColor(color);
        tvProjectTitle.setTextColor(0xFFFFFFFF);
    }

    private void showAddThemeDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_theme, null);
        Spinner spinner = dialogView.findViewById(R.id.spinner_theme_code);
        android.widget.EditText etName = dialogView.findViewById(R.id.et_theme_display_name);
        android.widget.EditText etTopic = dialogView.findViewById(R.id.et_theme_publish_topic);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ThemeManager.THEME_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

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
                    themeManager.addTheme(projectId, item);
                    renderThemeBar();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showThemeEditDialog(int position) {
        List<ThemeItem> themes = themeManager.getThemes(projectId);
        if (position < 0 || position >= themes.size()) return;
        final ThemeItem item = themes.get(position);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_theme, null);
        Spinner spinner = dialogView.findViewById(R.id.spinner_theme_code);
        android.widget.EditText etName = dialogView.findViewById(R.id.et_theme_display_name);
        android.widget.EditText etTopic = dialogView.findViewById(R.id.et_theme_publish_topic);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, ThemeManager.THEME_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int codeIndex = 0;
        for (int i = 0; i < ThemeManager.THEME_CODES.length; i++) {
            if (ThemeManager.THEME_CODES[i].equals(item.getThemeCode())) { codeIndex = i; break; }
        }
        spinner.setSelection(codeIndex);
        etName.setText(item.getDisplayName());
        etTopic.setText(item.getPublishTopic());

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
                    String code = ThemeManager.THEME_CODES[spinner.getSelectedItemPosition()];
                    item.setThemeCode(code);
                    item.setDisplayName(name);
                    item.setPublishTopic(topic);
                    themeManager.updateTheme(projectId, position, item);
                    renderThemeBar();
                })
                .setNegativeButton("删除", (dialog, which) -> {
                    themeManager.deleteTheme(projectId, position);
                    renderThemeBar();
                })
                .setNeutralButton("取消", null)
                .show();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onAlarmAdded(AlarmCenter.AlarmItem alarm) {
        runOnUiThread(() -> AlarmCardHelper.show(alarmContainer, this, alarm));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (themeBar != null) {
            renderThemeBar();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AlarmCenter.getInstance().removeListener(this);
    }
}