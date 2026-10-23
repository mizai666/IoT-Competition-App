package com.example.chapter05;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Subscribe extends Fragment implements MqttManager.MessageListener {

    private static final String TAG = "SubscribeFragment";
    private TextView tvMessages;
    private MqttManager mqttManager;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subscribe, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvMessages = view.findViewById(R.id.tv_received_messages);
        tvMessages.setText("等待消息...\n");

        mqttManager = MqttManager.getInstance(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        mqttManager.addListener(this);
        Log.d(TAG, "注册监听器");
    }

    @Override
    public void onPause() {
        super.onPause();
        mqttManager.removeListener(this);
        Log.d(TAG, "注销监听器");
    }

    private void appendMessage(String topic, String payload) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            String timeStr = dateFormat.format(new Date());
            String messageText = timeStr + "\n接收 [" + topic + "]: " + payload + "\n\n";
            String currentText = tvMessages.getText().toString();
            if (currentText.equals("等待消息...\n")) {
                tvMessages.setText(messageText);
            } else {
                tvMessages.append(messageText);
            }
        });
    }

    @Override
    public void onConnectionStatusChanged(boolean connected) {
        Log.d(TAG, "连接状态变化: " + connected);
        if (connected) {
            appendSystemMessage("MQTT 已连接");
        } else {
            appendSystemMessage("MQTT 已断开");
        }
    }

    @Override
    public void onMessageArrived(String topic, String payload) {
        Log.d(TAG, "收到消息: " + topic + " -> " + payload);
        appendMessage(topic, payload);
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "错误: " + error);
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
        }
    }

    private void appendSystemMessage(String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            String timeStr = dateFormat.format(new Date());
            String messageText = timeStr + "\n" + msg + "\n\n";
            String currentText = tvMessages.getText().toString();
            if (currentText.equals("等待消息...\n")) {
                tvMessages.setText(messageText);
            } else {
                tvMessages.append(messageText);
            }
        });
    }
}