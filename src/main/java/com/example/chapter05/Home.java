package com.example.chapter05;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

public class Home extends Fragment implements MqttManager.MessageListener {

    private TextView tvConfigStatus;
    private Button btnConfigMqtt;
    private Button btnConnect;
    private TextInputEditText etSubscribeTopic;
    private RecyclerView recyclerProjects;

    private SharedPreferences sp;
    private MqttManager mqttManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvConfigStatus = view.findViewById(R.id.tv_config_status);
        btnConfigMqtt = view.findViewById(R.id.config_mqtt);
        btnConnect = view.findViewById(R.id.btn_connect);
        etSubscribeTopic = view.findViewById(R.id.et_subscribe_topic);
        recyclerProjects = view.findViewById(R.id.recycler_projects);
        recyclerProjects.setLayoutManager(new LinearLayoutManager(getContext()));

        sp = requireContext().getSharedPreferences("config_mqtt", android.content.Context.MODE_PRIVATE);
        mqttManager = MqttManager.getInstance(requireContext());
        mqttManager.addListener(this);

        // 加载已保存的订阅主题
        String savedTopic = mqttManager.getLastSubscribedTopic();
        if (!savedTopic.isEmpty()) {
            etSubscribeTopic.setText(savedTopic);
        }

        updateConfigDisplay();
        updateConnectionUI(mqttManager.isConnected());

        btnConfigMqtt.setOnClickListener(v -> showMqttConfigDialog());
        btnConnect.setOnClickListener(v -> {
            if (mqttManager.isConnected()) {
                // 断开连接
                mqttManager.disconnect();
            } else {
                // 连接并订阅
                String topic = etSubscribeTopic.getText().toString().trim();
                if (topic.isEmpty()) {
                    Toast.makeText(getContext(), "请输入订阅主题", Toast.LENGTH_SHORT).show();
                    return;
                }
                mqttManager.connect();
                // 注意：连接成功后会在 onConnectionStatusChanged 中自动订阅
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateConfigDisplay();
        updateConnectionUI(mqttManager.isConnected());
    }

    private void updateConfigDisplay() {
        String clientId = sp.getString("client_id", "未配置");
        String server = sp.getString("server", "未配置");
        String port = sp.getString("port", "未配置");
        int qos = sp.getInt("qos", -1);
        String qosStr = qos == -1 ? "未配置" : String.valueOf(qos);
        String status = String.format("客户端ID: %s\n服务器: %s\n端口号：%s\nQoS: %s",
                clientId, server, port, qosStr);
        tvConfigStatus.setText(status);
    }

    private void updateConnectionUI(boolean connected) {
        if (connected) {
            btnConnect.setText("断开连接");
            btnConnect.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
        } else {
            btnConnect.setText("连接并订阅");
            btnConnect.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
        }
    }

    private void showMqttConfigDialog() {
        // 使用你现有的 dialog 布局（R.layout.dialog）
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View dialogView = inflater.inflate(R.layout.dialog, null);

        com.google.android.material.textfield.TextInputEditText etClientId = dialogView.findViewById(R.id.et_client_id);
        com.google.android.material.textfield.TextInputEditText etServer = dialogView.findViewById(R.id.et_server);
        com.google.android.material.textfield.TextInputEditText etPort = dialogView.findViewById(R.id.et_port);
        com.google.android.material.textfield.TextInputEditText etQos = dialogView.findViewById(R.id.et_qos);

        etClientId.setText(sp.getString("client_id", "android_client"));
        etServer.setText(sp.getString("server", "broker.hivemq.com"));
        etPort.setText(sp.getString("port", "1883"));
        etQos.setText(String.valueOf(sp.getInt("qos", 1)));

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("MQTT 配置")
                .setView(dialogView)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String clientId = etClientId.getText().toString().trim();
                    String server = etServer.getText().toString().trim();
                    String port = etPort.getText().toString().trim();
                    int qos;
                    try {
                        qos = Integer.parseInt(etQos.getText().toString().trim());
                        if (qos < 0 || qos > 2) qos = 1;
                    } catch (NumberFormatException e) {
                        qos = 1;
                    }

                    SharedPreferences.Editor editor = sp.edit();
                    editor.putString("client_id", clientId);
                    editor.putString("server", server);
                    editor.putString("port", port);
                    editor.putInt("qos", qos);
                    editor.apply();

                    updateConfigDisplay();
                    Toast.makeText(getContext(), "配置已保存", Toast.LENGTH_SHORT).show();

                    // 如果当前已连接，提示需要重新连接
                    if (mqttManager.isConnected()) {
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("配置已更改")
                                .setMessage("MQTT 连接仍在使用旧配置，是否立即重新连接？")
                                .setPositiveButton("重新连接", (d, w) -> {
                                    mqttManager.disconnect();
                                    // 重新连接会自动读取新配置
                                })
                                .setNegativeButton("稍后", null)
                                .show();
                    }
                })
                .show();
    }

    // ==================== MqttManager.MessageListener 回调 ====================
    @Override
    public void onConnectionStatusChanged(boolean connected) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            updateConnectionUI(connected);
            if (connected) {
                // 连接成功，订阅用户输入的主题
                String topic = etSubscribeTopic.getText().toString().trim();
                if (!topic.isEmpty()) {
                    mqttManager.subscribe(topic);
                    Toast.makeText(getContext(), "已连接并订阅: " + topic, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "MQTT 已连接，但未输入订阅主题", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "MQTT 已断开", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMessageArrived(String topic, String payload) {
        // 收到消息时，更新项目列表或显示通知
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                // 这里可以调用 ProjectDataManager 来更新项目数据并刷新列表
                // 或者简单提示
                Toast.makeText(getContext(), "收到消息: " + topic, Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public void onError(String error) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mqttManager.removeListener(this);
    }
}