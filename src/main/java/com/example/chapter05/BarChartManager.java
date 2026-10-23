package com.example.chapter05;

import android.app.Activity;
import android.graphics.Color;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 柱状图管理。单传感器历史或全部传感器历史（多系列）。
 */
public class BarChartManager {
    private final int projectId;
    private final BarChart barChart;
    private final SensorHistoryDatabaseHelper dbHelper;
    private final Activity activity;

    private final int[] colors = {
            android.R.color.holo_blue_dark,
            android.R.color.holo_red_dark,
            android.R.color.holo_green_dark,
            android.R.color.holo_orange_dark,
            android.R.color.holo_purple,
            android.R.color.holo_blue_light,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light
    };

    public BarChartManager(Activity activity, int projectId, BarChart barChart) {
        this.activity = activity;
        this.projectId = projectId;
        this.barChart = barChart;
        this.dbHelper = new SensorHistoryDatabaseHelper(activity);
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setScaleEnabled(false);
        barChart.setPinchZoom(false);
    }

    public void loadAllSensorsData() {
        new Thread(() -> {
            List<SensorHistoryDatabaseHelper.HistoryRecord> allRecords = dbHelper.getAllHistoryByProject(projectId);
            if (allRecords.isEmpty()) {
                activity.runOnUiThread(() -> showEmpty());
                return;
            }
            List<SensorHistoryDatabaseHelper.HistoryRecord> ascending = new ArrayList<>(allRecords);
            Collections.reverse(ascending);
            List<Long> timestamps = new ArrayList<>();
            Map<Long, Map<String, String>> timestampData = new HashMap<>();
            for (SensorHistoryDatabaseHelper.HistoryRecord r : ascending) {
                if (!timestamps.contains(r.timestamp)) timestamps.add(r.timestamp);
                timestampData.computeIfAbsent(r.timestamp, k -> new HashMap<>()).put(r.sensorName, r.value);
            }
            Collections.sort(timestamps);
            int start = Math.max(0, timestamps.size() - 20);
            List<Long> recent = timestamps.subList(start, timestamps.size());

            Map<String, List<BarEntry>> sensorEntries = new HashMap<>();
            for (int i = 0; i < recent.size(); i++) {
                Map<String, String> data = timestampData.get(recent.get(i));
                if (data == null) continue;
                for (Map.Entry<String, String> e : data.entrySet()) {
                    float value;
                    try {
                        value = Float.parseFloat(e.getValue());
                    } catch (NumberFormatException ex) {
                        value = 0;
                    }
                    sensorEntries.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new BarEntry(i, value));
                }
            }

            List<IBarDataSet> dataSets = new ArrayList<>();
            int idx = 0;
            for (Map.Entry<String, List<BarEntry>> e : sensorEntries.entrySet()) {
                String displayName = SensorAliasHelper.resolve(activity, projectId, e.getKey());
                BarDataSet ds = new BarDataSet(e.getValue(), displayName);
                ds.setColor(activity.getResources().getColor(colors[idx % colors.length]));
                ds.setValueTextSize(10f);
                ds.setDrawValues(true);
                dataSets.add(ds);
                idx++;
            }

            final BarData barData = new BarData(dataSets);
            final int count = recent.size();
            activity.runOnUiThread(() -> {
                if (dataSets.isEmpty()) {
                    showEmpty();
                    return;
                }
                barChart.setData(barData);
                configureXAxis(count);
                if (count > 0) barChart.moveViewToX(count - 1);
                barChart.invalidate();
            });
        }).start();
    }

    public void loadSingleSensorData(String sensorName) {
        new Thread(() -> {
            final String displayName = SensorAliasHelper.resolve(activity, projectId, sensorName);
            List<SensorHistoryDatabaseHelper.HistoryRecord> records = dbHelper.getHistoryByProject(projectId, sensorName);
            List<SensorHistoryDatabaseHelper.HistoryRecord> ascending = new ArrayList<>(records);
            Collections.reverse(ascending);
            int total = ascending.size();
            int start = Math.max(0, total - 20);
            List<SensorHistoryDatabaseHelper.HistoryRecord> recent = ascending.subList(start, total);
            List<BarEntry> entries = new ArrayList<>();
            for (int i = 0; i < recent.size(); i++) {
                float value;
                try {
                    value = Float.parseFloat(recent.get(i).value);
                } catch (NumberFormatException e) {
                    value = 0;
                }
                entries.add(new BarEntry(i, value));
            }
            final List<BarEntry> finalEntries = entries;
            final int count = entries.size();
            activity.runOnUiThread(() -> {
                if (finalEntries.isEmpty()) {
                    showEmpty();
                    return;
                }
                BarDataSet ds = new BarDataSet(finalEntries, displayName);
                ds.setColor(Color.rgb(0, 122, 255));
                ds.setValueTextSize(10f);
                ds.setDrawValues(true);
                BarData barData = new BarData(ds);
                barChart.setData(barData);
                configureXAxis(count);
                if (count > 0) barChart.moveViewToX(count - 1);
                barChart.invalidate();
            });
        }).start();
    }

    private void configureXAxis(int count) {
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(5, count), true);
        barChart.setVisibleXRangeMaximum(10);
    }

    private void showEmpty() {
        barChart.clear();
        barChart.setNoDataText("暂无数据");
        barChart.invalidate();
    }
}
