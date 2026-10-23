package com.example.chapter05;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements MqttManager.MessageListener, ProjectDataManager.OnDataChangeListener, AlarmCenter.AlarmListener {

    private TextView tvConfigStatus;
    private Button btnConfig, btnConnect;
    private TextInputEditText etSubscribeTopic;
    private RecyclerView recyclerViewProjects;

    private SharedPreferences mqttPrefs;
    private MqttManager mqttManager;
    private ProjectDataManager projectDataManager;

    private ProjectAdapter projectAdapter;
    private LinearLayout alarmContainer;
    private Handler refreshHandler = new Handler();
    private Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshProjectList();   // 刷新列表，重新计算在线状态
            refreshHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        View mainView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvConfigStatus = findViewById(R.id.tv_config_status);
        btnConfig = findViewById(R.id.btn_config);
        btnConnect = findViewById(R.id.btn_connect);
        etSubscribeTopic = findViewById(R.id.et_subscribe_topic);
        recyclerViewProjects = findViewById(R.id.recycler_view_projects);
        Button btnClearDb = findViewById(R.id.btn_clear_db);

        btnClearDb.setOnClickListener(v -> showClearDbConfirmDialog());

        mqttPrefs = getSharedPreferences("config_mqtt", Context.MODE_PRIVATE);
        mqttManager = MqttManager.getInstance(this);
        projectDataManager = ProjectDataManager.getInstance(this);

        mqttManager.addListener(this);
        projectDataManager.addListener(this);

        alarmContainer = findViewById(R.id.alarm_container);
        AlarmCenter.getInstance().addListener(this);

        String savedTopic = mqttManager.getLastSubscribedTopic();
        if (!savedTopic.isEmpty()) {
            etSubscribeTopic.setText(savedTopic);
        }

        updateConfigDisplay();
        updateConnectButtonState(mqttManager.isConnected());

        btnConfig.setOnClickListener(v -> showMqttConfigDialog());
        btnConnect.setOnClickListener(v -> {
            if (mqttManager.isConnected()) {
                mqttManager.disconnect();
            } else {
                String topic = etSubscribeTopic.getText().toString().trim();
                if (topic.isEmpty()) {
                    Toast.makeText(this, "请输入订阅主题", Toast.LENGTH_SHORT).show();
                    return;
                }
                mqttManager.connect();
            }
        });

        // 初始化 RecyclerView
        recyclerViewProjects.setLayoutManager(new LinearLayoutManager(this));
        projectAdapter = new ProjectAdapter();
        recyclerViewProjects.setAdapter(projectAdapter);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, LinearLayoutManager.VERTICAL);
        dividerItemDecoration.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider));
        recyclerViewProjects.addItemDecoration(dividerItemDecoration);
        refreshProjectList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHandler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void showClearDbConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清空数据库")
                .setMessage("确定要清空所有数据库记录吗？\n（消息历史、传感器历史、项目数据将被永久删除，MQTT配置不受影响）")
                .setPositiveButton("清空", (dialog, which) -> clearAllDatabases())
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearAllDatabases() {
        MessageDatabaseHelper msgHelper = new MessageDatabaseHelper(this);
        msgHelper.clearAll();

        SensorHistoryDatabaseHelper sensorHelper = new SensorHistoryDatabaseHelper(this);
        sensorHelper.clearAll();

        ProjectDatabaseHelper projectHelper = new ProjectDatabaseHelper(this);
        projectHelper.clearAll();

        // 清空特殊JSON数据库
        SpecialJsonDatabaseHelper specialJsonHelper = new SpecialJsonDatabaseHelper(this);
        specialJsonHelper.clearAll();

        AlarmHistoryDatabaseHelper alarmHelper = new AlarmHistoryDatabaseHelper(this);
        alarmHelper.clearAll();

        PublishHistoryDatabaseHelper publishHelper = new PublishHistoryDatabaseHelper(this);
        publishHelper.clearAll();

        ProjectDataManager dataManager = ProjectDataManager.getInstance(this);
        dataManager.clearAllProjects();

        // 清空配置（SharedPreferences 持久化的开关/阈值/定时/主题/自动开关）
        new SwitchManager(this).clearAll();
        new ThresholdManager(this).clearAll();
        ScheduleManager.getInstance(this).clearAll();
        ThemeManager.getInstance(this).clearAll();
        AutoSwitchManager.getInstance(this).clearAll();
        getSharedPreferences("sensor_alias", MODE_PRIVATE).edit().clear().apply();

        refreshProjectList();
        Toast.makeText(this, "数据库已清空", Toast.LENGTH_SHORT).show();
    }

    private void showProjectOptionsDialog(Project project) {
        String[] options = {"修改名称", "删除项目", project.isPinned() ? "取消置顶" : "置顶"};
        new AlertDialog.Builder(this)
                .setTitle(project.getDisplayName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameDialog(project);
                    } else if (which == 1) {
                        showDeleteConfirmDialog(project);
                    } else if (which == 2) {
                        boolean newPinned = !project.isPinned();
                        projectDataManager.updateProjectPinned(project.getProjectId(), newPinned);
                        refreshProjectList();
                    }
                })
                .show();
    }

    private void updateConfigDisplay() {
        String clientId = mqttPrefs.getString("client_id", "未配置");
        String server = mqttPrefs.getString("server", "未配置");
        String port = mqttPrefs.getString("port", "未配置");
        int qos = mqttPrefs.getInt("qos", -1);
        String qosStr = qos == -1 ? "未配置" : String.valueOf(qos);
        String status = String.format("客户端ID: %s\n服务器: %s\n端口: %s\nQoS: %s",
                clientId, server, port, qosStr);
        tvConfigStatus.setText(status);
    }

    private void updateConnectButtonState(boolean connected) {
        if (connected) {
            btnConnect.setText("断开连接");
            btnConnect.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
        } else {
            btnConnect.setText("连接并订阅");
            btnConnect.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
        }
    }

    private void showMqttConfigDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog, null);

        TextInputEditText etClientId = dialogView.findViewById(R.id.et_client_id);
        TextInputEditText etServer = dialogView.findViewById(R.id.et_server);
        TextInputEditText etPort = dialogView.findViewById(R.id.et_port);
        TextInputEditText etQos = dialogView.findViewById(R.id.et_qos);

        etClientId.setText(mqttPrefs.getString("client_id", ""));
        etServer.setText(mqttPrefs.getString("server", ""));
        etPort.setText(mqttPrefs.getString("port", ""));
        int savedQos = mqttPrefs.getInt("qos", 1);
        etQos.setText(String.valueOf(savedQos));

        new AlertDialog.Builder(this)
                .setTitle("MQTT 配置")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String clientId = etClientId.getText().toString().trim();
                    String server = etServer.getText().toString().trim();
                    String port = etPort.getText().toString().trim();
                    String qosStr = etQos.getText().toString().trim();
                    if (clientId.isEmpty() || server.isEmpty() || port.isEmpty() || qosStr.isEmpty()) {
                        Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int qos;
                    try {
                        qos = Integer.parseInt(qosStr);
                        if (qos < 0 || qos > 2) qos = 1;
                    } catch (NumberFormatException e) {
                        qos = 1;
                    }

                    SharedPreferences.Editor editor = mqttPrefs.edit();
                    editor.putString("client_id", clientId);
                    editor.putString("server", server);
                    editor.putString("port", port);
                    editor.putInt("qos", qos);
                    editor.apply();

                    updateConfigDisplay();
                    Toast.makeText(this, "MQTT 配置已保存", Toast.LENGTH_SHORT).show();

                    if (mqttManager.isConnected()) {
                        new AlertDialog.Builder(this)
                                .setTitle("配置已更改")
                                .setMessage("连接仍在使用旧配置，是否重新连接？")
                                .setPositiveButton("重新连接", (d, w) -> mqttManager.disconnect())
                                .setNegativeButton("稍后", null)
                                .show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshProjectList() {
        List<Project> projects = projectDataManager.getAllProjects();
        projectAdapter.setProjects(projects);
    }

    private void showRenameDialog(Project project) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("修改项目名称");
        final EditText input = new EditText(this);
        input.setText(project.getDisplayName());
        input.setSelection(input.getText().length());
        builder.setView(input);
        builder.setPositiveButton("保存", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            projectDataManager.updateProjectName(project.getProjectId(), newName);
            Toast.makeText(this, "名称已修改", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showDeleteConfirmDialog(Project project) {
        new AlertDialog.Builder(this)
                .setTitle("删除项目")
                .setMessage("确定要删除项目 \"" + project.getDisplayName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    projectDataManager.deleteProject(project.getProjectId());
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ========== MqttManager.MessageListener 回调 ==========
    @Override
    public void onConnectionStatusChanged(boolean connected) {
        runOnUiThread(() -> {
            updateConnectButtonState(connected);
            if (connected) {
                String topic = etSubscribeTopic.getText().toString().trim();
                if (!topic.isEmpty()) {
                    mqttManager.subscribe(topic);
                    Toast.makeText(MainActivity.this, "已连接并订阅: " + topic, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "MQTT 已连接，但未输入订阅主题", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "MQTT 已断开", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMessageArrived(String topic, String payload) {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "收到消息: " + topic, Toast.LENGTH_SHORT).show();
            refreshProjectList();
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAlarmAdded(AlarmCenter.AlarmItem alarm) {
        runOnUiThread(() -> AlarmCardHelper.show(alarmContainer, this, alarm));
    }

    // ========== ProjectDataManager.OnDataChangeListener 回调 ==========
    @Override
    public void onProjectListChanged() {
        runOnUiThread(this::refreshProjectList);
    }

    @Override
    public void onSensorDataUpdated(int projectId, String sensorName, String newValue) {
        runOnUiThread(this::refreshProjectList);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mqttManager.removeListener(this);
        projectDataManager.removeListener(this);
        AlarmCenter.getInstance().removeListener(this);
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    // ==================== RecyclerView 适配器 ====================
    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ViewHolder> {
        private List<Project> projects = new ArrayList<>();

        public void setProjects(List<Project> list) {
            this.projects = list;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_project, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Project p = projects.get(position);
            StringBuilder sb = new StringBuilder(p.getDisplayName());
            Map<String, String> sensors = p.getSensors();
            if (sensors != null && !sensors.isEmpty()) {
                sb.append("\n");
                int count = 0;
                for (Map.Entry<String, String> entry : sensors.entrySet()) {
                    if (count < 3) {
                        sb.append(entry.getKey()).append(": ").append(entry.getValue());
                        if (count < sensors.size() - 1 && count < 2) sb.append(", ");
                        count++;
                    }
                }
                if (sensors.size() > 3) sb.append("...");
            }
            holder.title.setText(sb.toString());
            holder.title.setTextColor(p.isSpecial() ? 0xFFB45309 : 0xFF1F2937);

            // 根据在线状态设置指示器颜色
            if (p.isOnline()) {
                holder.indicator.setBackgroundResource(R.drawable.circle_green);
            } else {
                holder.indicator.setBackgroundResource(R.drawable.circle_red);
            }

            // 置顶背景色
            if (p.isPinned()) {
                holder.itemView.setBackgroundColor(getResources().getColor(R.color.pinned_background));
            } else {
                holder.itemView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ProjectDetailActivity.class);
                intent.putExtra("project_id", p.getProjectId());
                startActivity(intent);
            });

            holder.itemView.setOnLongClickListener(v -> {
                showProjectOptionsDialog(p);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return projects.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View indicator;
            TextView title;
            ViewHolder(View itemView) {
                super(itemView);
                indicator = itemView.findViewById(R.id.indicator);
                title = itemView.findViewById(R.id.title);
            }
        }
    }
}