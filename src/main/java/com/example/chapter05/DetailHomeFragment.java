package com.example.chapter05;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DetailHomeFragment extends Fragment implements ProjectDataManager.OnDataChangeListener {

    private int projectId;
    private Spinner spinnerSensor;
    private Button btnRefreshChart;
    private SensorChartManager chartManager;
    // 原始键名列表，索引 0 为 null 表示「全部」；与 sensorLabels 一一对应
    private List<String> sensorKeys = new ArrayList<>();
    // 显示名列表（含别名），索引 0 为「全部」
    private List<String> sensorLabels = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private ProjectDataManager dataManager;
    private RecyclerView rvSensorData;
    private SensorDataAdapter sensorDataAdapter;
    private BroadcastReceiver dataChangeReceiver;

    // 外部图例容器
    private LinearLayout legendContainer;
    private GridLayout legendItemsLayout;

    // 图表类型
    private RadioGroup rgChartType;
    private LineChart lineChart;
    private BarChart barChart;
    private PieChart pieChart;
    private BarChartManager barChartManager;
    private PieChartManager pieChartManager;
    private int chartType = 0; // 0=折线 1=柱状 2=饼图
    private TextView tvRealtimeJson;

    public static DetailHomeFragment newInstance(int projectId) {
        DetailHomeFragment fragment = new DetailHomeFragment();
        Bundle args = new Bundle();
        args.putInt("project_id", projectId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            projectId = getArguments().getInt("project_id", -1);
        }

        spinnerSensor = view.findViewById(R.id.spinner_sensor);
        btnRefreshChart = view.findViewById(R.id.btn_refresh_chart);
        rvSensorData = view.findViewById(R.id.rv_sensor_data);
        tvRealtimeJson = view.findViewById(R.id.tv_realtime_json);

        // 获取外部图例容器
        legendContainer = view.findViewById(R.id.legend_container);
        legendItemsLayout = view.findViewById(R.id.legend_items);

        dataManager = ProjectDataManager.getInstance(requireContext());
        dataManager.addListener(this);

        View sensorSelectCard = view.findViewById(R.id.sensor_select_card);
        View chartCard = view.findViewById(R.id.chart_card);
        View lineChartView = view.findViewById(R.id.line_chart);

        Project project = dataManager.getProject(projectId);
        boolean shouldHideChart = (project != null && !project.isShowChart());

        if (shouldHideChart) {
            if (sensorSelectCard != null) sensorSelectCard.setVisibility(View.GONE);
            if (chartCard != null) chartCard.setVisibility(View.GONE);
            if (btnRefreshChart != null) btnRefreshChart.setVisibility(View.GONE);
            chartManager = null;
        } else {
            if (sensorSelectCard != null) sensorSelectCard.setVisibility(View.VISIBLE);
            if (chartCard != null) chartCard.setVisibility(View.VISIBLE);
            if (lineChartView instanceof LineChart) {
                lineChart = (LineChart) lineChartView;
                chartManager = new SensorChartManager(requireActivity(), projectId, (LineChart) lineChartView);
                new Handler().postDelayed(() -> {
                    if (chartManager != null) {
                        chartManager.setUseTimeXAxis(true);  // 再次确保
                    }
                }, 100);// 使用时间轴true,使用索引轴false
                chartManager.setRootViewTouchListener(view);

                // 设置图例更新监听器
                chartManager.setLegendUpdateListener(legendItems -> {
                    requireActivity().runOnUiThread(() -> {
                        if (legendItems == null || legendItems.isEmpty()) {
                            legendContainer.setVisibility(View.GONE);
                            return;
                        }
                        legendContainer.setVisibility(View.VISIBLE);
                        legendItemsLayout.removeAllViews();
                        for (SensorChartManager.LegendItem item : legendItems) {
                            View legendView = createLegendItemView(item.name, item.color);
                            legendItemsLayout.addView(legendView);
                        }
                    });
                });

                // 柱状图 + 饼图 + 图表类型切换
                barChart = view.findViewById(R.id.bar_chart);
                pieChart = view.findViewById(R.id.pie_chart);
                if (barChart != null) barChartManager = new BarChartManager(requireActivity(), projectId, barChart);
                if (pieChart != null) pieChartManager = new PieChartManager(requireActivity(), projectId, pieChart);
                rgChartType = view.findViewById(R.id.rg_chart_type);
                if (rgChartType != null) {
                    rgChartType.setOnCheckedChangeListener((group, checkedId) -> {
                        int newType;
                        if (checkedId == R.id.rb_chart_bar) newType = 1;
                        else if (checkedId == R.id.rb_chart_pie) newType = 2;
                        else newType = 0;
                        if (newType == chartType) return;
                        chartType = newType;
                        switchChartView();
                    });
                }
            } else {
                if (sensorSelectCard != null) sensorSelectCard.setVisibility(View.GONE);
                if (chartCard != null) chartCard.setVisibility(View.GONE);
                if (btnRefreshChart != null) btnRefreshChart.setVisibility(View.GONE);
                chartManager = null;
            }
        }

        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, sensorLabels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSensor.setAdapter(spinnerAdapter);

        rvSensorData.setLayoutManager(new LinearLayoutManager(getContext()));
        sensorDataAdapter = new SensorDataAdapter(getContext(), projectId);
        sensorDataAdapter.setOnAliasChangedListener(() -> {
            // 改名后刷新下拉列表 + 图表
            requireActivity().runOnUiThread(this::loadSensorNames);
        });
        rvSensorData.setAdapter(sensorDataAdapter);

        loadSensorNames();
        registerDataChangeReceiver();

        spinnerSensor.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadChart();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnRefreshChart.setOnClickListener(v -> loadChart());
    }

    private void registerDataChangeReceiver() {
        dataChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int changedProjectId = intent.getIntExtra(DetailDatabaseFragment.EXTRA_PROJECT_ID, -1);
                if (changedProjectId == projectId) {
                    Log.d("DetailHome", "收到数据变更广播，刷新图表");
                    requireActivity().runOnUiThread(() -> {
                        refreshSensorDataList();
                        loadChart();
                    });
                }
            }
        };
        IntentFilter filter = new IntentFilter(DetailDatabaseFragment.ACTION_DATA_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(dataChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(dataChangeReceiver, filter);
        }
    }

    private void refreshSensorDataList() {
        Project project = dataManager.getProject(projectId);
        if (project != null) {
            Map<String, String> sensors = project.getSensors();
            Log.d("DetailHome", "项目ID=" + projectId + ", 传感器总数=" + sensors.size());
            if (sensors.isEmpty()) {
                Log.w("DetailHome", "传感器列表为空！请检查 ProjectDataManager 是否正确更新了传感器值。");
            } else {
                for (Map.Entry<String, String> entry : sensors.entrySet()) {
                    Log.d("DetailHome", "传感器: " + entry.getKey() + " = " + entry.getValue());
                }
            }
            sensorDataAdapter.updateData(sensors);
        } else {
            Log.d("DetailHome", "项目为空");
        }
        refreshRealtimeJson();
    }

    private void refreshRealtimeJson() {
        if (tvRealtimeJson == null) return;
        Project project = dataManager.getProject(projectId);
        if (project == null) {
            tvRealtimeJson.setText("{}");
            return;
        }
        String raw = project.getRawJson();
        try {
            tvRealtimeJson.setText(new JSONObject(raw).toString(2));
        } catch (JSONException e) {
            tvRealtimeJson.setText(raw == null || raw.isEmpty() ? "{}" : raw);
        }
    }

    private void loadSensorNames() {
        new Thread(() -> {
            SensorHistoryDatabaseHelper historyHelper = new SensorHistoryDatabaseHelper(requireContext());
            List<SensorHistoryDatabaseHelper.HistoryRecord> records = historyHelper.getAllHistoryByProject(projectId);
            historyHelper.close();
            // 从历史记录去重、排序，过滤 key数字 控制键
            java.util.Set<String> keySet = new java.util.TreeSet<>();
            for (SensorHistoryDatabaseHelper.HistoryRecord r : records) {
                if (r.sensorName == null || r.sensorName.isEmpty()) continue;
                if (r.sensorName.matches("^key\\d+$")) continue;
                keySet.add(r.sensorName);
            }
            List<String> keys = new ArrayList<>(keySet);
            List<String> labels = new ArrayList<>();
            labels.add("全部");
            for (String k : keys) {
                labels.add(SensorAliasHelper.resolve(requireContext(), projectId, k));
            }
            requireActivity().runOnUiThread(() -> {
                sensorKeys.clear();
                sensorKeys.add(null); // 索引 0 = 全部
                sensorKeys.addAll(keys);
                sensorLabels.clear();
                sensorLabels.addAll(labels);
                spinnerAdapter.notifyDataSetChanged();
                if (spinnerSensor != null) {
                    spinnerSensor.setSelection(0);
                }
                loadChart();
            });
        }).start();
    }

    private void switchChartView() {
        if (lineChart != null) lineChart.setVisibility(chartType == 0 ? View.VISIBLE : View.GONE);
        if (barChart != null) barChart.setVisibility(chartType == 1 ? View.VISIBLE : View.GONE);
        if (pieChart != null) pieChart.setVisibility(chartType == 2 ? View.VISIBLE : View.GONE);
        if (legendContainer != null) legendContainer.setVisibility(chartType == 0 ? View.VISIBLE : View.GONE);
        loadChart();
    }

    private void loadChart() {
        int position = spinnerSensor.getSelectedItemPosition();
        String selectedKey = (position >= 0 && position < sensorKeys.size()) ? sensorKeys.get(position) : null;
        boolean isAll = (selectedKey == null);
        if (chartType == 0) {
            if (chartManager == null) return;
            if (isAll) {
                chartManager.loadAllSensorsData();
            } else {
                chartManager.loadSingleSensorData(selectedKey);
            }
        } else if (chartType == 1) {
            if (barChartManager == null) return;
            if (isAll) {
                barChartManager.loadAllSensorsData();
            } else {
                barChartManager.loadSingleSensorData(selectedKey);
            }
        } else {
            if (pieChartManager == null) return;
            pieChartManager.loadData();
        }
    }

    /**
     * 创建单个图例项（每行显示2个）
     */
    private View createLegendItemView(String name, int color) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        // GridLayout 会自动处理布局参数
        layout.setLayoutParams(new GridLayout.LayoutParams());
        layout.setPadding(12, 8, 12, 8);

        View colorView = new View(getContext());
        colorView.setBackgroundColor(color);
        LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(
                dpToPx(24),
                dpToPx(12));
        colorParams.setMargins(0, 0, 8, 0);
        colorView.setLayoutParams(colorParams);

        TextView textView = new TextView(getContext());
        textView.setText(name);
        textView.setTextSize(12);
        textView.setTextColor(0xFF333333);
        textView.setMaxLines(1);
        textView.setEllipsize(android.text.TextUtils.TruncateAt.END);

        layout.addView(colorView);
        layout.addView(textView);
        return layout;
    }

    /**
     * dp 转 px
     */
    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onSensorDataUpdated(int updatedProjectId, String sensorName, String newValue) {
        Log.d("DetailHome", "onSensorDataUpdated 被调用: projectId=" + updatedProjectId + ", sensor=" + sensorName + ", value=" + newValue);
        if (updatedProjectId == projectId) {
            requireActivity().runOnUiThread(() -> {
                Log.d("DetailHome", "刷新传感器数据列表和图表");
                refreshSensorDataList();
                loadChart();
            });
        }
    }

    @Override
    public void onProjectListChanged() {
        loadSensorNames();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dataChangeReceiver != null) {
            requireContext().unregisterReceiver(dataChangeReceiver);
        }
        if (chartManager != null) {
            chartManager.onDestroy();
        }
        dataManager.removeListener(this);
    }
}