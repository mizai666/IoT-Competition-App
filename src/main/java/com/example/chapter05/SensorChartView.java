package com.example.chapter05;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SensorChartView extends LinearLayout {

    private Spinner spinnerSensor;
    private Button btnRefreshChart;
    private ListView listViewSensors;
    private LineChart lineChart;

    private SensorHistoryDatabaseHelper historyDbHelper;
    private ProjectDataManager dataManager;

    private int projectId;
    private List<String> sensorNames = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private String currentSensor = null; // "全部" 或具体传感器名
    private static final String OPTION_ALL = "全部";

    // 用于显示传感器最新值的适配器
    private ArrayAdapter<String> sensorListAdapter;
    private List<String> sensorDisplayList = new ArrayList<>();

    public SensorChartView(Context context) {
        super(context);
        init(context);
    }

    public SensorChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_sensor_chart, this, true);
        spinnerSensor = findViewById(R.id.spinner_sensor);
        btnRefreshChart = findViewById(R.id.btn_refresh_chart);
        listViewSensors = findViewById(R.id.listview_sensors);
        lineChart = findViewById(R.id.line_chart);

        historyDbHelper = new SensorHistoryDatabaseHelper(context);
        dataManager = ProjectDataManager.getInstance(context);

        // 初始化 Spinner 适配器
        spinnerAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, sensorNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSensor.setAdapter(spinnerAdapter);

        // 初始化传感器最新值列表适配器
        sensorListAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, sensorDisplayList);
        listViewSensors.setAdapter(sensorListAdapter);

        spinnerSensor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentSensor = sensorNames.get(position);
                if (OPTION_ALL.equals(currentSensor)) {
                    showSensorList();
                    loadAllSensorLatestValues();
                } else {
                    showLineChart();
                    loadChartData();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnRefreshChart.setOnClickListener(v -> {
            if (OPTION_ALL.equals(currentSensor)) {
                loadAllSensorLatestValues();
            } else {
                loadChartData();
            }
        });
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
        loadSensorNames();
        // 同时监听数据更新
        dataManager.addListener(new ProjectDataManager.OnDataChangeListener() {
            @Override
            public void onProjectListChanged() {}

            @Override
            public void onSensorDataUpdated(int updatedProjectId, String sensorName, String newValue) {
                if (updatedProjectId == projectId) {
                    if (OPTION_ALL.equals(currentSensor)) {
                        // 更新最新值列表
                        loadAllSensorLatestValues();
                    } else if (sensorName.equals(currentSensor)) {
                        loadChartData();
                    }
                    // 如果出现新传感器，刷新 Spinner 列表
                    loadSensorNames();
                }
            }
        });
    }

    private void loadSensorNames() {
        new Thread(() -> {
            List<SensorHistoryDatabaseHelper.HistoryRecord> allRecords = historyDbHelper.getAllHistoryByProject(projectId);
            List<String> names = new ArrayList<>();
            names.add(OPTION_ALL);
            for (SensorHistoryDatabaseHelper.HistoryRecord record : allRecords) {
                if (!names.contains(record.sensorName)) {
                    names.add(record.sensorName);
                }
            }
            post(() -> {
                sensorNames.clear();
                sensorNames.addAll(names);
                spinnerAdapter.notifyDataSetChanged();
                if (!sensorNames.isEmpty() && currentSensor == null) {
                    currentSensor = OPTION_ALL;
                    spinnerSensor.setSelection(0);
                    showSensorList();
                    loadAllSensorLatestValues();
                }
            });
        }).start();
    }

    private void showSensorList() {
        listViewSensors.setVisibility(VISIBLE);
        lineChart.setVisibility(GONE);
    }

    private void showLineChart() {
        listViewSensors.setVisibility(GONE);
        lineChart.setVisibility(VISIBLE);
    }

    private void loadAllSensorLatestValues() {
        Project project = dataManager.getProject(projectId);
        post(() -> {
            sensorDisplayList.clear();
            if (project != null) {
                Map<String, String> sensors = project.getSensors();
                for (Map.Entry<String, String> entry : sensors.entrySet()) {
                    sensorDisplayList.add(entry.getKey() + ": " + entry.getValue());
                }
            }
            sensorListAdapter.notifyDataSetChanged();
        });
    }

    private void loadChartData() {
        if (currentSensor == null || OPTION_ALL.equals(currentSensor)) return;
        new Thread(() -> {
            List<SensorHistoryDatabaseHelper.HistoryRecord> records = historyDbHelper.getHistoryByProject(projectId, currentSensor);
            int total = records.size();
            int start = Math.max(0, total - 15);
            List<SensorHistoryDatabaseHelper.HistoryRecord> last15 = records.subList(start, total);
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < last15.size(); i++) {
                SensorHistoryDatabaseHelper.HistoryRecord record = last15.get(i);
                float value;
                try {
                    value = Float.parseFloat(record.value);
                } catch (NumberFormatException e) {
                    value = 0;
                }
                entries.add(new Entry(i, value));
            }
            post(() -> {
                if (entries.isEmpty()) {
                    lineChart.clear();
                    lineChart.setNoDataText("暂无数据");
                    lineChart.invalidate();
                    return;
                }
                LineDataSet dataSet = new LineDataSet(entries, currentSensor);
                dataSet.setColor(getResources().getColor(android.R.color.holo_blue_dark));
                dataSet.setValueTextSize(10f);
                dataSet.setLineWidth(2f);
                dataSet.setCircleRadius(4f);
                dataSet.setDrawCircles(true);
                dataSet.setDrawValues(true);

                LineData lineData = new LineData(dataSet);
                lineChart.setData(lineData);

                XAxis xAxis = lineChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                xAxis.setDrawGridLines(true);
                xAxis.setGranularity(1f);
                String[] xLabels = new String[entries.size()];
                for (int i = 0; i < entries.size(); i++) {
                    xLabels[i] = String.valueOf(i + 1);
                }
                xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));
                xAxis.setLabelRotationAngle(-45);

                Description description = new Description();
                description.setText("");
                lineChart.setDescription(description);
                lineChart.invalidate();
            });
        }).start();
    }
}