package com.example.chapter05;

import android.app.Activity;
import android.graphics.Color;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 饼图管理：取最新一条记录的传感器数值分布（过滤控制键 keyN 和非数值项）。
 */
public class PieChartManager {
    private final int projectId;
    private final PieChart pieChart;
    private final SensorHistoryDatabaseHelper dbHelper;
    private final Activity activity;

    private final int[] colors = {
            0xFF6366F1, 0xFFF59E0B, 0xFF10B981, 0xFFEF4444,
            0xFF06B6D4, 0xFF8B5CF6, 0xFFEC4899, 0xFF84CC16
    };

    public PieChartManager(Activity activity, int projectId, PieChart pieChart) {
        this.activity = activity;
        this.projectId = projectId;
        this.pieChart = pieChart;
        this.dbHelper = new SensorHistoryDatabaseHelper(activity);
        pieChart.getDescription().setEnabled(false);
        pieChart.setUsePercentValues(true);
        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);
        pieChart.setDrawEntryLabels(true);
        pieChart.getLegend().setEnabled(true);
    }

    public void loadData() {
        new Thread(() -> {
            List<SensorHistoryDatabaseHelper.HistoryRecord> all = dbHelper.getAllHistoryByProject(projectId);
            if (all.isEmpty()) {
                activity.runOnUiThread(() -> {
                    pieChart.clear();
                    pieChart.setNoDataText("暂无数据");
                    pieChart.invalidate();
                });
                return;
            }

            long maxTs = Long.MIN_VALUE;
            for (SensorHistoryDatabaseHelper.HistoryRecord r : all) {
                if (r.timestamp > maxTs) maxTs = r.timestamp;
            }

            Map<String, Float> latest = new HashMap<>();
            for (SensorHistoryDatabaseHelper.HistoryRecord r : all) {
                if (r.timestamp != maxTs) continue;
                if (r.sensorName.matches("^key\\d+$")) continue; // 过滤控制键
                try {
                    latest.put(r.sensorName, Float.parseFloat(r.value));
                } catch (NumberFormatException ignored) {}
            }

            final List<PieEntry> entries = new ArrayList<>();
            for (Map.Entry<String, Float> e : latest.entrySet()) {
                entries.add(new PieEntry(e.getValue(), SensorAliasHelper.resolve(activity, projectId, e.getKey())));
            }
            final long ts = maxTs;

            activity.runOnUiThread(() -> {
                if (entries.isEmpty()) {
                    pieChart.clear();
                    pieChart.setNoDataText("暂无数据");
                    pieChart.invalidate();
                    return;
                }
                PieDataSet ds = new PieDataSet(entries, "");
                ds.setValueTextSize(12f);
                ds.setValueTextColor(Color.WHITE);
                ds.setColors(colors);
                PieData pieData = new PieData(ds);
                pieChart.setData(pieData);
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                pieChart.setCenterText("传感器分布\n" + sdf.format(new Date(ts)));
                pieChart.invalidate();
            });
        }).start();
    }
}
