package com.example.chapter05;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

public class DetailMonitorFragment extends Fragment {

    private static final String PREF_NAME = "monitor_config";
    private static final String KEY_CAMERA_URL_PREFIX = "camera_url_";
    private static final String KEY_CAMERA_INDEX = "selected_camera_index";
    private int projectId;
    private WebView webView;
    private EditText etUrl;
    private Button btn_camera_data;
    private Spinner spinnerCamera;
    private SharedPreferences prefs;

    private int currentCameraIndex = 0;
    private String[] cameraNames = {"摄像头1", "摄像头2", "摄像头3"};

    public static DetailMonitorFragment newInstance(int projectId) {
        DetailMonitorFragment fragment = new DetailMonitorFragment();
        Bundle args = new Bundle();
        args.putInt("projectId", projectId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectId = getArguments().getInt("projectId");
        }
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @SuppressLint({"SetJavaScriptEnabled", "MissingInflatedId"})
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_detail_monitor, container, false);
        MaterialCardView putURLMateralCardView = view.findViewById(R.id.input_URL);
        Button inputAgain = view.findViewById(R.id.inputAgain);
        webView = view.findViewById(R.id.webview_camera);
        spinnerCamera = view.findViewById(R.id.spinner_camera);
        etUrl = view.findViewById(R.id.et_camera_url);
        Button btnConnect = view.findViewById(R.id.btn_connect_camera);

        // 初始化摄像头选择Spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, cameraNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCamera.setAdapter(spinnerAdapter);
        // 恢复上次选择的摄像头索引
        currentCameraIndex = prefs.getInt(KEY_CAMERA_INDEX, 0);
        spinnerCamera.setSelection(currentCameraIndex);

        // 加载当前摄像头的保存URL
        loadCurrentCameraUrl();

        // 配置 WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        // 允许 WebView 内部滚动（默认就是允许的，这里明确设置）
        settings.setSupportZoom(true);
        webView.setVerticalScrollBarEnabled(true);
        webView.setHorizontalScrollBarEnabled(true);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Toast.makeText(getContext(), "加载失败: " + description, Toast.LENGTH_SHORT).show();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        btn_camera_data = view.findViewById(R.id.btn_camera_data);
        btn_camera_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Intent intent = new Intent(this, CameraDataActivity.class);
//                startActivity(intent);
            }
        });
        // 摄像头切换监听
        spinnerCamera.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentCameraIndex = position;
                prefs.edit().putInt(KEY_CAMERA_INDEX, currentCameraIndex).apply();
                loadCurrentCameraUrl();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        inputAgain.setOnClickListener(v-> {

            inputAgain.setVisibility(View.GONE);
            putURLMateralCardView.setVisibility(View.VISIBLE);
//            btn_camera_data.setMargeleft;
        });

        // 连接网页
        btnConnect.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(getContext(), "请输入摄像头网页地址", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.startsWith("http")) {
                url = "http://" + url;
            }
            webView.loadUrl(url);
            inputAgain.setVisibility(View.VISIBLE);
            putURLMateralCardView.setVisibility(View.GONE);
            saveCurrentCameraUrl(url);
        });

        return view;
    }

    private void loadCurrentCameraUrl() {
        String key = KEY_CAMERA_URL_PREFIX + currentCameraIndex;
        String savedUrl = prefs.getString(key, "");
        etUrl.setText(savedUrl);
    }

    private void saveCurrentCameraUrl(String url) {
        String key = KEY_CAMERA_URL_PREFIX + currentCameraIndex;
        prefs.edit().putString(key, url).apply();
    }
}