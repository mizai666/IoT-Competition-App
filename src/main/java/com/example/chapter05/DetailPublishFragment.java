package com.example.chapter05;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.textfield.TextInputLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DetailPublishFragment extends Fragment implements MqttManager.MessageListener, ProjectDataManager.OnDataChangeListener {
    private static final String ARG_PROJECT_ID = "project_id";
    private int projectId;

    private EditText etPublishTopic;
    private EditText etPublishMessage;
    private Button btnPublish;
    private RecyclerView rvSwitches;
    private TextView tvMessages;
    private SwitchManager switchManager;
    private MqttManager mqttManager;
    private SharedPreferences prefs;
    private static final String PREF_NAME = "detail_publish";
    private RecyclerView rvThresholds;
    private ThresholdManager thresholdManager;
    private ThresholdAdapter thresholdAdapter;
    private List<ThresholdItem> thresholdList;
    private ProjectDataManager dataManager;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // 定时任务
    private ScheduleManager scheduleManager;
    private RecyclerView rvSchedules;
    private ScheduleAdapter scheduleAdapter;
    private List<ScheduleItem> scheduleList;

    // 自动开关
    private AutoSwitchManager autoSwitchManager;
    private TextView tvAutoMode;
    private Button btnModeAuto;
    private Button btnModeManual;
    private Button btnAutoConfig;
    private boolean autoMode = false;
    private SwitchAdapter switchAdapter;

    // HTTP 上报
    private RadioGroup rgPublishMode;
    private LinearLayout llHttpArea;
    private TextInputLayout tilPublishTopic;
    private EditText etHttpUrl;
    private Spinner spinnerHttpMethod;
    private boolean httpMode = false;
    private PublishHistoryDatabaseHelper publishHistoryHelper;

    public static DetailPublishFragment newInstance(int projectId) {
        DetailPublishFragment fragment = new DetailPublishFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PROJECT_ID, projectId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail_publish, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            projectId = getArguments().getInt(ARG_PROJECT_ID, -1);
        }

        // 初始化控件
        etPublishTopic = view.findViewById(R.id.et_publish_topic);
        etPublishMessage = view.findViewById(R.id.et_publish_message);
        btnPublish = view.findViewById(R.id.btn_publish_send);
        rvSwitches = view.findViewById(R.id.rv_switches);
        tvMessages = view.findViewById(R.id.tv_publish_messages);
        publishHistoryHelper = new PublishHistoryDatabaseHelper(requireContext());

        // HTTP 上报控件
        rgPublishMode = view.findViewById(R.id.rg_publish_mode);
        llHttpArea = view.findViewById(R.id.ll_http_area);
        tilPublishTopic = view.findViewById(R.id.til_publish_topic);
        etHttpUrl = view.findViewById(R.id.et_http_url);
        spinnerHttpMethod = view.findViewById(R.id.spinner_http_method);
        setupHttpControls();

        // 功能管理入口
        view.findViewById(R.id.btn_manage_switch).setOnClickListener(v -> openManager(SwitchManagerActivity.class));
        view.findViewById(R.id.btn_manage_threshold).setOnClickListener(v -> openManager(ThresholdManagerActivity.class));
        view.findViewById(R.id.btn_manage_schedule).setOnClickListener(v -> openManager(ScheduleManagerActivity.class));
        view.findViewById(R.id.btn_manage_theme).setOnClickListener(v -> openManager(ThemeManagerActivity.class));
        view.findViewById(R.id.btn_auto_fill).setOnClickListener(v -> showAutoFillDialog());

        mqttManager = MqttManager.getInstance(requireContext());
        mqttManager.addListener(this);

        prefs = requireContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE);
        switchManager = new SwitchManager(requireContext());

        // 阈值管理
        rvThresholds = view.findViewById(R.id.rv_wran);
        thresholdManager = new ThresholdManager(requireContext());
        thresholdList = thresholdManager.getThresholds(projectId);
        thresholdAdapter = new ThresholdAdapter(getContext(), thresholdList,
                position -> openManager(ThresholdManagerActivity.class),
                position -> showAlarmHistoryDialog(position));
        rvThresholds.setLayoutManager(new LinearLayoutManager(getContext()));
        rvThresholds.setAdapter(thresholdAdapter);

        // 定时任务管理
        rvSchedules = view.findViewById(R.id.rv_schedules);
        scheduleManager = ScheduleManager.getInstance(requireContext());
        scheduleList = scheduleManager.getSchedules(projectId);
        scheduleAdapter = new ScheduleAdapter(scheduleList,
                (position, v) -> openManager(ScheduleManagerActivity.class),
                scheduleManager, projectId);
        rvSchedules.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSchedules.setAdapter(scheduleAdapter);
        scheduleAdapter.start();

        // 监听项目数据变化，实时更新阈值卡片当前值
        dataManager = ProjectDataManager.getInstance(requireContext());
        dataManager.addListener(this);

        // 加载保存的发布主题
        String savedTopic = prefs.getString("topic", "");
        etPublishTopic.setText(savedTopic);

        etPublishTopic.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                prefs.edit().putString("topic", s.toString()).apply();
            }
        });

        btnPublish.setOnClickListener(v -> {
            if (httpMode) {
                sendHttpRequest();
            } else {
                publishMessage();
            }
        });

        setupSwitchRecyclerView();
        setupAutoSwitch(view);

        tvMessages.setText("等待发送消息...\n");
    }

    // ========== 阈值管理 ==========




    // 报警记录对话框（保持不变）
    private void showAlarmHistoryDialog(int position) {
        ThresholdItem item = thresholdList.get(position);
        String sensorName = item.getDisplayName();
        String realSensorName = item.getSensorName();

        new Thread(() -> {
            AlarmHistoryDatabaseHelper alarmHelper = new AlarmHistoryDatabaseHelper(requireContext());
            List<AlarmHistoryDatabaseHelper.AlarmRecord> records = alarmHelper.getAlarmsBySensor(projectId, realSensorName);
            requireActivity().runOnUiThread(() -> {
                if (records.isEmpty()) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("报警记录 - " + sensorName)
                            .setMessage("暂无报警记录")
                            .setPositiveButton("确定", null)
                            .show();
                    return;
                }

                StringBuilder sb = new StringBuilder();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (AlarmHistoryDatabaseHelper.AlarmRecord record : records) {
                    String timeStr = sdf.format(new Date(record.timestamp));
                    sb.append(timeStr).append("\n").append(record.alarmMessage).append("\n\n");
                }
                ScrollView scrollView = new ScrollView(requireContext());
                TextView textView = new TextView(requireContext());
                textView.setText(sb.toString());
                textView.setPadding(32, 32, 32, 32);
                textView.setTextSize(14);
                scrollView.addView(textView);

                new AlertDialog.Builder(requireContext())
                        .setTitle("报警记录 - " + sensorName)
                        .setView(scrollView)
                        .setPositiveButton("关闭", null)
                        .show();
            });
        }).start();
    }

    // ========== 定时任务管理 ==========



    // ========== 开关管理 ==========
    private void setupSwitchRecyclerView() {
        List<SwitchItem> switches = switchManager.getSwitches(projectId);
        switchAdapter = new SwitchAdapter(switches, autoMode, new SwitchAdapter.Listener() {
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
                openManager(SwitchManagerActivity.class);
            }
        });
        rvSwitches.setLayoutManager(new GridLayoutManager(getContext(), 2));
        rvSwitches.setAdapter(switchAdapter);
    }

    // ========== 自动开关管理 ==========
    private void setupAutoSwitch(View view) {
        tvAutoMode = view.findViewById(R.id.tv_auto_mode);
        btnModeAuto = view.findViewById(R.id.btn_mode_auto);
        btnModeManual = view.findViewById(R.id.btn_mode_manual);
        btnAutoConfig = view.findViewById(R.id.btn_auto_config);
        autoSwitchManager = AutoSwitchManager.getInstance(requireContext());

        AutoSwitchItem config = autoSwitchManager.getConfig(projectId);
        autoMode = config != null && config.isAuto();

        btnModeAuto.setOnClickListener(v -> {
            if (autoSwitchManager.getConfig(projectId) == null) {
                showAutoSwitchConfigDialog();
                return;
            }
            autoSwitchManager.setMode(projectId, AutoSwitchItem.MODE_AUTO);
            autoMode = true;
            updateAutoModeUI();
            applyAutoModeToSwitches();
        });
        btnModeManual.setOnClickListener(v -> {
            if (autoSwitchManager.getConfig(projectId) == null) {
                showAutoSwitchConfigDialog();
                return;
            }
            autoSwitchManager.setMode(projectId, AutoSwitchItem.MODE_MANUAL);
            autoMode = false;
            updateAutoModeUI();
            applyAutoModeToSwitches();
        });
        btnAutoConfig.setOnClickListener(v -> showAutoSwitchConfigDialog());

        updateAutoModeUI();
        applyAutoModeToSwitches();
    }

    private void updateAutoModeUI() {
        if (tvAutoMode == null) return;
        tvAutoMode.setText(autoMode ? "模式：自动" : "模式：手动");
        int activeColor = 0xFF6366F1;
        if (btnModeAuto != null) {
            if (autoMode) {
                btnModeAuto.setBackgroundColor(activeColor);
                btnModeAuto.setTextColor(0xFFFFFFFF);
            } else {
                btnModeAuto.setBackgroundColor(0xFFFFFFFF);
                btnModeAuto.setTextColor(activeColor);
            }
        }
        if (btnModeManual != null) {
            if (!autoMode) {
                btnModeManual.setBackgroundColor(activeColor);
                btnModeManual.setTextColor(0xFFFFFFFF);
            } else {
                btnModeManual.setBackgroundColor(0xFFFFFFFF);
                btnModeManual.setTextColor(activeColor);
            }
        }
    }

    private void applyAutoModeToSwitches() {
        if (switchAdapter != null) {
            switchAdapter.setAutoMode(autoMode);
        }
    }

    private void showAutoSwitchConfigDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_auto_switch, null);
        EditText etKey = dialogView.findViewById(R.id.et_auto_key_name);
        EditText etAuto = dialogView.findViewById(R.id.et_auto_value);
        EditText etManual = dialogView.findViewById(R.id.et_manual_value);
        EditText etTopic = dialogView.findViewById(R.id.et_auto_publish_topic);

        AutoSwitchItem config = autoSwitchManager.getConfig(projectId);
        if (config != null) {
            etKey.setText(config.getKeyName());
            etAuto.setText(config.getAutoValue());
            etManual.setText(config.getManualValue());
            etTopic.setText(config.getPublishTopic());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("自动开关配置")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String key = etKey.getText().toString().trim();
                    String auto = etAuto.getText().toString().trim();
                    String manual = etManual.getText().toString().trim();
                    String topic = etTopic.getText().toString().trim();
                    if (key.isEmpty() || auto.isEmpty() || manual.isEmpty() || topic.isEmpty()) {
                        Toast.makeText(getContext(), "请填写完整", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    AutoSwitchItem item = new AutoSwitchItem(key, auto, manual, topic);
                    if (config != null) {
                        item.setCurrentMode(config.getCurrentMode());
                    }
                    autoSwitchManager.saveConfig(projectId, item);
                    autoMode = item.isAuto();
                    updateAutoModeUI();
                    applyAutoModeToSwitches();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void sendSwitchCommand(SwitchItem item) {
        if (!mqttManager.isConnected()) {
            Toast.makeText(getContext(), "MQTT 未连接，请返回主页连接", Toast.LENGTH_SHORT).show();
            return;
        }
        String topic = item.getPublishTopic();
        if (topic.isEmpty()) {
            Toast.makeText(getContext(), "该开关未配置发布主题", Toast.LENGTH_SHORT).show();
            return;
        }
        int value = item.getValueToSend();
        String json = "{\"" + item.getJsonKey() + "\":" + value + "}";
        mqttManager.publish(topic, json, mqttManager.getQos(), projectId);
        appendMessage("我 -> [" + topic + "]: " + json);
        Toast.makeText(getContext(), "已发送: " + json, Toast.LENGTH_SHORT).show();
    }




    private void openManager(Class<?> cls) {
        Intent intent = new Intent(requireContext(), cls);
        intent.putExtra("project_id", projectId);
        startActivity(intent);
    }

    private void showAutoFillDialog() {
        new Thread(() -> {
            final List<String> displayList = new ArrayList<>();
            final List<String> jsonList = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

            Project project = ProjectDataManager.getInstance(requireContext()).getProject(projectId);
            if (project != null && project.isSpecial()) {
                SpecialJsonDatabaseHelper specialHelper = new SpecialJsonDatabaseHelper(requireContext());
                for (SpecialJsonDatabaseHelper.SpecialJsonRecord r : specialHelper.getLatestRecords(projectId, 20)) {
                    displayList.add(sdf.format(new Date(r.timestamp)) + " - " + r.jsonData);
                    jsonList.add(r.jsonData);
                }
            } else {
                SensorHistoryDatabaseHelper sensorHelper = new SensorHistoryDatabaseHelper(requireContext());
                Map<Long, Map<String, String>> grouped = new LinkedHashMap<>();
                for (SensorHistoryDatabaseHelper.HistoryRecord r : sensorHelper.getAllHistoryByProject(projectId)) {
                    grouped.computeIfAbsent(r.timestamp, k -> new LinkedHashMap<>()).put(r.sensorName, r.value);
                }
                List<Long> tsList = new ArrayList<>(grouped.keySet());
                Collections.sort(tsList, Collections.reverseOrder());
                for (int i = 0; i < Math.min(20, tsList.size()); i++) {
                    long ts = tsList.get(i);
                    Map<String, String> sensors = grouped.get(ts);
                    try {
                        JSONObject json = new JSONObject();
                        json.put("timestamp", ts);
                        for (Map.Entry<String, String> e : sensors.entrySet()) {
                            json.put(e.getKey(), e.getValue());
                        }
                        String jsonStr = json.toString();
                        displayList.add(sdf.format(new Date(ts)) + " - " + jsonStr);
                        jsonList.add(jsonStr);
                    } catch (JSONException ignored) {}
                }
            }

            requireActivity().runOnUiThread(() -> {
                if (displayList.isEmpty()) {
                    Toast.makeText(getContext(), "暂无历史数据", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("选择历史数据填充")
                        .setItems(displayList.toArray(new String[0]), (dialog, which) -> {
                            etPublishMessage.setText(jsonList.get(which));
                            Toast.makeText(getContext(), "已填充", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        }).start();
    }

    // ========== HTTP 上报 ==========
    private void setupHttpControls() {
        ArrayAdapter<String> methodAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new String[]{"GET", "POST"});
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerHttpMethod.setAdapter(methodAdapter);

        rgPublishMode.setOnCheckedChangeListener((group, checkedId) -> {
            httpMode = (checkedId == R.id.rb_mode_http);
            tilPublishTopic.setVisibility(httpMode ? View.GONE : View.VISIBLE);
            llHttpArea.setVisibility(httpMode ? View.VISIBLE : View.GONE);
            btnPublish.setText(httpMode ? "上报" : "发送");
        });
    }

    private void sendHttpRequest() {
        String url = etHttpUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(getContext(), "请输入 HTTP 地址", Toast.LENGTH_SHORT).show();
            return;
        }
        String method = spinnerHttpMethod.getSelectedItem().toString();
        String body = etPublishMessage.getText().toString().trim();

        btnPublish.setEnabled(false);
        new Thread(() -> {
            HttpManager.HttpResult result;
            try {
                result = HttpManager.request(url, method, body);
            } catch (Exception e) {
                result = new HttpManager.HttpResult(-1, "请求失败: " + e.getMessage());
            }
            final HttpManager.HttpResult finalResult = result;

            // 记录到 publish_history
            PublishHistoryDatabaseHelper.PublishRecord record = new PublishHistoryDatabaseHelper.PublishRecord();
            record.projectId = projectId;
            record.type = "http";
            record.topic = url;
            record.payload = body;
            record.qos = "-";
            record.ok = finalResult.isOk();
            record.status = finalResult.status >= 0 ? String.valueOf(finalResult.status) : "ERR";
            record.url = url;
            record.responseData = finalResult.body;
            record.method = method;
            record.createdAt = System.currentTimeMillis();
            publishHistoryHelper.insert(record);

            // 响应规则触发（HTTP 响应）
            try {
                JSONObject respData = new JSONObject(finalResult.body);
                ResponseRuleEngine.applyAllRules(requireContext(), projectId, respData, true);
            } catch (JSONException ignored) {}

            requireActivity().runOnUiThread(() -> {
                btnPublish.setEnabled(true);
                String display = "我 -> HTTP " + method + " [" + url + "]\n" +
                        "状态: " + record.status + "\n" +
                        "响应: " + finalResult.body;
                appendMessage(display);
                Toast.makeText(getContext(), record.ok ? "上报成功" : "上报失败", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    // ========== 发布消息 ==========
    private void publishMessage() {
        if (!mqttManager.isConnected()) {
            Toast.makeText(getContext(), "MQTT 未连接，请返回主页连接", Toast.LENGTH_SHORT).show();
            return;
        }
        String topic = etPublishTopic.getText().toString().trim();
        if (topic.isEmpty()) {
            Toast.makeText(getContext(), "请输入发布主题", Toast.LENGTH_SHORT).show();
            return;
        }
        String message = etPublishMessage.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(getContext(), "请输入消息内容", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString("topic", topic).apply();
        mqttManager.publish(topic, message, mqttManager.getQos(), projectId);
        appendMessage("我 -> [" + topic + "]: " + message);
        Toast.makeText(getContext(), "消息已发送", Toast.LENGTH_SHORT).show();
        etPublishMessage.setText("");
    }

    private void appendMessage(String msg) {
        requireActivity().runOnUiThread(() -> {
            String timeStr = dateFormat.format(new Date());
            String newLine = timeStr + "\n" + msg + "\n\n";
            String current = tvMessages.getText().toString();
            if (current.equals("等待发送消息...\n")) {
                tvMessages.setText(newLine);
            } else {
                tvMessages.append(newLine);
            }
            tvMessages.postDelayed(() -> {
                ScrollView scrollView = (ScrollView) tvMessages.getParent();
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }, 100);
        });
    }

    // ========== MqttManager.MessageListener 实现 ==========
    @Override
    public void onConnectionStatusChanged(boolean connected) { }

    @Override
    public void onMessageArrived(String topic, String payload) {
        appendMessage("接收 [" + topic + "]: " + payload);
        try {
            JSONObject json = new JSONObject(payload);
            if (switchManager.applyJsonState(projectId, json)) {
                requireActivity().runOnUiThread(() -> {
                    if (switchAdapter != null) {
                        switchAdapter.updateData(switchManager.getSwitches(projectId));
                    }
                });
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 自动模式可能被 MqttManager 的 checkMode 切换，同步 UI
        AutoSwitchItem config = autoSwitchManager.getConfig(projectId);
        boolean newAutoMode = config != null && config.isAuto();
        if (newAutoMode != autoMode) {
            autoMode = newAutoMode;
            requireActivity().runOnUiThread(() -> {
                updateAutoModeUI();
                applyAutoModeToSwitches();
            });
        }

        // 响应规则触发（MQTT 上行）
        try {
            JSONObject respData = new JSONObject(payload);
            ResponseRuleEngine.applyAllRules(requireContext(), projectId, respData, false);
        } catch (JSONException ignored) {}
    }

    @Override
    public void onError(String error) {
        requireActivity().runOnUiThread(() ->
                Toast.makeText(getContext(), "MQTT错误: " + error, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onSwitchStateChanged(int projectId) {
        if (projectId == this.projectId) {
            requireActivity().runOnUiThread(() -> refreshSwitchList());
        }
    }

    // ========== ProjectDataManager.OnDataChangeListener 实现 ==========
    @Override
    public void onProjectListChanged() { }

    @Override
    public void onSensorDataUpdated(int updatedProjectId, String sensorName, String newValue) {
        if (updatedProjectId == projectId) {
            requireActivity().runOnUiThread(() -> {
                try {
                    float value = Float.parseFloat(newValue);
                    thresholdAdapter.updateCurrentValue(sensorName, value);
                } catch (NumberFormatException e) { }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSwitchList();
        refreshThresholdList();
        refreshScheduleList();
    }

    private void refreshSwitchList() {
        if (switchAdapter != null) {
            switchAdapter.updateData(switchManager.getSwitches(projectId));
        }
    }

    private void refreshThresholdList() {
        if (thresholdAdapter != null) {
            thresholdList = thresholdManager.getThresholds(projectId);
            thresholdAdapter.updateData(thresholdList);
        }
    }

    private void refreshScheduleList() {
        if (scheduleAdapter != null) {
            scheduleList = scheduleManager.getSchedules(projectId);
            scheduleAdapter.updateData(scheduleList);
        }
    }

    // ========== 生命周期 ==========
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scheduleAdapter != null) scheduleAdapter.stop();
        mqttManager.removeListener(this);
        dataManager.removeListener(this);
    }

}