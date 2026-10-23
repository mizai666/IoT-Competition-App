package com.example.chapter05;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Publish extends Fragment implements MqttManager.MessageListener {

    private static final String TAG = "PublishFragment";
    private static final String PREF_PUBLISH_TOPIC = "config_publish_topic";

    private EditText etPublishTopic;
    private EditText etPublishMessage;
    private Button btnSend;
    private TextView tvMessages;
    private LinearLayout buttonContainer;

    private MqttManager mqttManager;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_publish, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        etPublishTopic = view.findViewById(R.id.et_publish_topic);
        etPublishMessage = view.findViewById(R.id.et_publish_message);
        btnSend = view.findViewById(R.id.btn_publish_send);
        tvMessages = view.findViewById(R.id.tv_publish_messages);
        buttonContainer = view.findViewById(R.id.button_container);

        mqttManager = MqttManager.getInstance(requireContext());
        mqttManager.addListener(this);

        loadSavedPublishTopic();
        updateSendButtonState(mqttManager.isConnected());

        btnSend.setOnClickListener(v -> publishMessage());
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSendButtonState(mqttManager.isConnected());
    }

    private void loadSavedPublishTopic() {
        SharedPreferences sp = requireContext().getSharedPreferences(PREF_PUBLISH_TOPIC, android.content.Context.MODE_PRIVATE);
        String savedTopic = sp.getString("publish_topic", "");
        if (!savedTopic.isEmpty()) {
            etPublishTopic.setText(savedTopic);
        }
    }

    private void saveCurrentPublishTopic() {
        String topic = etPublishTopic.getText().toString().trim();
        if (!topic.isEmpty()) {
            SharedPreferences sp = requireContext().getSharedPreferences(PREF_PUBLISH_TOPIC, android.content.Context.MODE_PRIVATE);
            sp.edit().putString("publish_topic", topic).apply();
        }
    }

    private void updateSendButtonState(boolean connected) {
        if (connected) {
            btnSend.setEnabled(true);
        } else {
            btnSend.setEnabled(false);
        }
    }

    private void publishMessage() {
        if (!mqttManager.isConnected()) {
            Toast.makeText(getContext(), "MQTT 未连接，请先回主页连接", Toast.LENGTH_SHORT).show();
            return;
        }

        String topic = etPublishTopic.getText().toString().trim();
        if (topic.isEmpty()) {
            Toast.makeText(getContext(), "请输入发布主题", Toast.LENGTH_SHORT).show();
            return;
        }
        saveCurrentPublishTopic();

        String msg = etPublishMessage.getText().toString().trim();
        if (msg.isEmpty()) {
            Toast.makeText(getContext(), "请输入消息内容", Toast.LENGTH_SHORT).show();
            return;
        }

        mqttManager.publish(topic, msg, mqttManager.getQos());
        appendSentMessage(topic, msg);
        Toast.makeText(getContext(), "消息已发送", Toast.LENGTH_SHORT).show();
    }

    private void appendSentMessage(String topic, String content) {
        String timeStr = dateFormat.format(new Date());
        String messageText = timeStr + "\n我 -> [" + topic + "]: " + content + "\n\n";
        updateMessageTextView(messageText);
    }

    private void appendReceivedMessage(String topic, String payload) {
        String timeStr = dateFormat.format(new Date());
        String messageText = timeStr + "\n接收 [" + topic + "]: " + payload + "\n\n";
        updateMessageTextView(messageText);
    }

    private void updateMessageTextView(String newText) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            String currentText = tvMessages.getText().toString();
            if (currentText.equals("等待发送消息...\n")) {
                tvMessages.setText(newText);
            } else {
                tvMessages.append(newText);
            }
        });
    }

    @Override
    public void onConnectionStatusChanged(boolean connected) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                updateSendButtonState(connected);
                if (connected) {
                    appendSystemMessage("MQTT 已连接，可以发布消息");
                } else {
                    appendSystemMessage("MQTT 已断开，无法发布");
                }
            });
        }
    }

    @Override
    public void onMessageArrived(String topic, String payload) {
        appendReceivedMessage(topic, payload);
    }

    @Override
    public void onError(String error) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
        }
    }

    private void appendSystemMessage(String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            String timeStr = dateFormat.format(new Date());
            String messageText = timeStr + "\n" + msg + "\n\n";
            updateMessageTextView(messageText);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mqttManager.removeListener(this);
    }
}