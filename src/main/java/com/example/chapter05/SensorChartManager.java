package com.example.chapter05;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.FragmentActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SensorChartManager {

    private static final int DEFAULT_VISIBLE_X_RANGE = 3;  // 初始可见范围最多个点
    private static final int Keduxian = 4;  // 最大可见范围10个点
    private final int projectId;
    private final LineChart lineChart;
    private final SensorHistoryDatabaseHelper dbHelper;
    private final FragmentActivity activity;
    private final ViewGroup rootView;
    private final CustomMarkerView floatingMarkerView;
    private final Handler hideHandler = new Handler();
    private final Runnable hideRunnable;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private List<Long> currentTimestamps = new ArrayList<>();
    private List<Long> currentTimestampsForX = new ArrayList<>();
    private boolean useTimeXAxis = false;  // 默认 false（索引模式）

    // 图例回调接口
    public interface LegendUpdateListener {
        void onLegendUpdate(List<LegendItem> legendItems);
    }
    private LegendUpdateListener legendListener;

    public void setLegendUpdateListener(LegendUpdateListener listener) {
        this.legendListener = listener;
    }

    public static class LegendItem {
        public String name;
        public int color;
        public LegendItem(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

    public SensorChartManager(FragmentActivity activity, int projectId, LineChart lineChart) {
        this.activity = activity;
        this.projectId = projectId;
        this.lineChart = lineChart;
        this.dbHelper = new SensorHistoryDatabaseHelper(activity);
        this.rootView = (ViewGroup) activity.getWindow().getDecorView();
        this.floatingMarkerView = new CustomMarkerView(activity);
        this.rootView.addView(floatingMarkerView);
        this.floatingMarkerView.setVisibility(View.GONE);
        this.hideRunnable = () -> floatingMarkerView.setVisibility(View.GONE);

        setupChart();
        setupTouchListener();
    }

    private void setupChart() {
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.getDescription().setEnabled(false);
        lineChart.setVisibleXRangeMaximum(DEFAULT_VISIBLE_X_RANGE);
        lineChart.getLegend().setEnabled(false);

        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                hideHandler.removeCallbacks(hideRunnable);
                int index = (int) e.getX();
                if (index >= 0 && index < currentTimestamps.size()) {
                    long timestamp = currentTimestamps.get(index);
                    showBubble(timestamp, e, h);
                }
            }
            @Override
            public void onNothingSelected() {
                hideHandler.postDelayed(hideRunnable, 200);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchListener() {
        lineChart.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                hideHandler.postDelayed(() -> {
                    if (lineChart.getHighlighted() == null || lineChart.getHighlighted().length == 0) {
                        floatingMarkerView.setVisibility(View.GONE);
                    }
                }, 50);
                v.performClick();
            }
            return false;
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setRootViewTouchListener(View fragmentRoot) {
        fragmentRoot.setOnTouchListener((v, event) -> {
            if (floatingMarkerView.getVisibility() != View.VISIBLE) return false;
            float rawX = event.getRawX();
            float rawY = event.getRawY();
            int[] chartLoc = new int[2];
            lineChart.getLocationOnScreen(chartLoc);
            boolean insideChart = rawX >= chartLoc[0] && rawX <= chartLoc[0] + lineChart.getWidth()
                    && rawY >= chartLoc[1] && rawY <= chartLoc[1] + lineChart.getHeight();
            if (!insideChart) {
                floatingMarkerView.setVisibility(View.GONE);
                hideHandler.removeCallbacks(hideRunnable);
            }
            return false;
        });
    }

    private void showBubble(long timestamp, Entry e, Highlight h) {
        new Thread(() -> {
            List<SensorHistoryDatabaseHelper.HistoryRecord> records = dbHelper.getHistoryByTimestamp(projectId, timestamp);
            StringBuilder sb = new StringBuilder();
            sb.append(dateFormat.format(new Date(timestamp))).append("\n");
            for (SensorHistoryDatabaseHelper.HistoryRecord record : records) {
                sb.append(SensorAliasHelper.resolve(activity, projectId, record.sensorName))
                        .append(": ").append(record.value).append("\n");
            }
            activity.runOnUiThread(() -> {
                floatingMarkerView.setContent(sb.toString());
                floatingMarkerView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                floatingMarkerView.layout(0, 0, floatingMarkerView.getMeasuredWidth(), floatingMarkerView.getMeasuredHeight());

                int[] location = new int[2];
                lineChart.getLocationOnScreen(location);
                float screenX = location[0] + h.getXPx();
                float screenY = location[1] + h.getYPx();

                float bubbleX = screenX - floatingMarkerView.getWidth() / 2;
                float bubbleY;
                int markerHeight = floatingMarkerView.getMeasuredHeight();
                int screenHeight = rootView.getHeight();

                if (screenY + markerHeight + 20 <= screenHeight) {
                    bubbleY = screenY + 20;
                } else {
                    bubbleY = screenY - markerHeight - 20;
                }

                if (bubbleX < 0) bubbleX = 10;
                if (bubbleX + floatingMarkerView.getWidth() > rootView.getWidth()) {
                    bubbleX = rootView.getWidth() - floatingMarkerView.getWidth() - 10;
                }

                floatingMarkerView.setTranslationX(bubbleX);
                floatingMarkerView.setTranslationY(bubbleY);
                floatingMarkerView.setVisibility(View.VISIBLE);
            });
        }).start();
    }

    // ========== 全部传感器模式 ==========
    public void loadAllSensorsData() {
        Log.d("SensorChartManager", "loadAllSensorsData 被调用, projectId=" + projectId);
        new Thread(() -> {
            List<SensorHistoryDatabaseHelper.HistoryRecord> allRecords = dbHelper.getAllHistoryByProject(projectId);
            if (allRecords.isEmpty()) {
                activity.runOnUiThread(() -> {
                    lineChart.clear();
                    lineChart.setNoDataText("暂无数据");
                    lineChart.invalidate();
                    currentTimestamps.clear();
                    currentTimestampsForX.clear();
                    if (legendListener != null) legendListener.onLegendUpdate(new ArrayList<>());
                });
                return;
            }

            List<SensorHistoryDatabaseHelper.HistoryRecord> ascending = new ArrayList<>(allRecords);
            Collections.reverse(ascending);

            List<Long> timestamps = new ArrayList<>();
            Map<Long, Map<String, String>> timestampData = new HashMap<>();
            for (SensorHistoryDatabaseHelper.HistoryRecord record : ascending) {
                if (!timestamps.contains(record.timestamp)) timestamps.add(record.timestamp);
                timestampData.computeIfAbsent(record.timestamp, k -> new HashMap<>())
                        .put(record.sensorName, record.value);
            }
            Collections.sort(timestamps);
            currentTimestamps = timestamps;
            currentTimestampsForX = new ArrayList<>(timestamps);

            Map<String, List<Entry>> sensorEntries = new HashMap<>();
            for (int i = 0; i < timestamps.size(); i++) {
                long ts = timestamps.get(i);
                Map<String, String> data = timestampData.get(ts);
                if (data != null) {
                    for (Map.Entry<String, String> entry : data.entrySet()) {
                        String sensor = entry.getKey();
                        float value;
                        try {
                            value = Float.parseFloat(entry.getValue());
                        } catch (NumberFormatException e) {
                            value = 0;
                        }
                        sensorEntries.computeIfAbsent(sensor, k -> new ArrayList<>()).add(new Entry(i, value));
                    }
                }
            }

            List<ILineDataSet> dataSets = new ArrayList<>();
            List<LegendItem> legendItems = new ArrayList<>();
            int[] colors = {
                    android.R.color.holo_blue_dark,
                    android.R.color.holo_red_dark,
                    android.R.color.holo_green_dark,
                    android.R.color.holo_orange_dark,
                    android.R.color.holo_purple,
                    android.R.color.holo_blue_light,
                    android.R.color.holo_green_light,
                    android.R.color.holo_orange_light
            };
            int idx = 0;
            for (Map.Entry<String, List<Entry>> entry : sensorEntries.entrySet()) {
                String sensorName = entry.getKey();
                String displayName = SensorAliasHelper.resolve(activity, projectId, sensorName);
                int color = activity.getResources().getColor(colors[idx % colors.length]);
                legendItems.add(new LegendItem(displayName, color));

                LineDataSet dataSet = new LineDataSet(entry.getValue(), displayName);
                dataSet.setColor(color);
                dataSet.setValueTextSize(10f);
                dataSet.setLineWidth(2f);
                dataSet.setCircleRadius(4f);
                dataSet.setDrawCircles(true);
                dataSet.setDrawValues(false);
                dataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
                dataSet.setDrawFilled(true);
                dataSet.setFillColor((color & 0x00FFFFFF) | 0x0A000000);
                dataSets.add(dataSet);
                idx++;
            }

            final int dataCount = timestamps.size();
            final List<ILineDataSet> finalDataSets = dataSets;
            final List<LegendItem> finalLegendItems = legendItems;

            activity.runOnUiThread(() -> {
                if (finalDataSets.isEmpty()) {
                    lineChart.clear();
                    lineChart.setNoDataText("暂无数据");
                    lineChart.invalidate();
                    if (legendListener != null) legendListener.onLegendUpdate(new ArrayList<>());
                    return;
                }
                LineData lineData = new LineData(finalDataSets);
                lineChart.setData(lineData);
                configureXAxis(dataCount);
                lineChart.setVisibleXRangeMaximum(DEFAULT_VISIBLE_X_RANGE);
                if (dataCount > 0) lineChart.moveViewToX(dataCount - 1);
                lineChart.invalidate();

                // 根据当前模式设置 X 轴格式
                if (useTimeXAxis) {
                    setXAxisToTimeMode();
                } else {
                    setXAxisToIndexMode();
                }
                Log.d("SensorChartManager", "时间戳列表大小=" + currentTimestampsForX.size());
                if (!currentTimestampsForX.isEmpty()) {
                    Log.d("SensorChartManager", "首个时间戳=" + currentTimestampsForX.get(0) + " -> "
                            + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(currentTimestampsForX.get(0))));
                }
                if (legendListener != null) legendListener.onLegendUpdate(finalLegendItems);
            });
        }).start();
    }

    // ========== 单个传感器模式 ==========
    public void loadSingleSensorData(String sensorName) {
        new Thread(() -> {
            final String displayName = SensorAliasHelper.resolve(activity, projectId, sensorName);
            final int blue = Color.rgb(0, 122, 255);
            List<SensorHistoryDatabaseHelper.HistoryRecord> records = dbHelper.getHistoryByProject(projectId, sensorName);
            List<SensorHistoryDatabaseHelper.HistoryRecord> ascending = new ArrayList<>(records);
            Collections.reverse(ascending);
            List<Entry> entries = new ArrayList<>();
            List<Long> timestamps = new ArrayList<>();
            for (int i = 0; i < ascending.size(); i++) {
                float value;
                try {
                    value = Float.parseFloat(ascending.get(i).value);
                } catch (NumberFormatException e) {
                    value = 0;
                }
                entries.add(new Entry(i, value));
                timestamps.add(ascending.get(i).timestamp);
            }

            final List<Entry> finalEntries = entries;
            final List<Long> finalTimestamps = timestamps;
            final int dataCount = finalEntries.size();

            activity.runOnUiThread(() -> {
                if (finalEntries.isEmpty()) {
                    lineChart.clear();
                    lineChart.setNoDataText("暂无数据");
                    lineChart.invalidate();
                    currentTimestamps.clear();
                    currentTimestampsForX.clear();
                    if (legendListener != null) legendListener.onLegendUpdate(new ArrayList<>());
                    return;
                }
                currentTimestamps = finalTimestamps;
                currentTimestampsForX = new ArrayList<>(finalTimestamps);
                LineDataSet dataSet = new LineDataSet(finalEntries, displayName);
                dataSet.setColor(blue);
                dataSet.setValueTextSize(10f);
                dataSet.setLineWidth(1.5f);
                dataSet.setCircleRadius(3f);
                dataSet.setDrawCircles(true);
                dataSet.setDrawValues(false);
                dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
                dataSet.setDrawFilled(true);
                dataSet.setFillColor(0x0A007AFF);
                lineChart.setData(new LineData(dataSet));
                configureXAxis(dataCount);
                lineChart.setVisibleXRangeMaximum(DEFAULT_VISIBLE_X_RANGE);
                if (dataCount > 0) lineChart.moveViewToX(dataCount - 1);
                lineChart.invalidate();

                // 根据当前模式设置 X 轴格式
                if (useTimeXAxis) {
                    setXAxisToTimeMode();
                } else {
                    setXAxisToIndexMode();
                }

                if (legendListener != null) {
                    List<LegendItem> single = new ArrayList<>();
                    single.add(new LegendItem(displayName, blue));
                    legendListener.onLegendUpdate(single);
                }
            });
        }).start();
    }

    private void configureXAxis(int dataCount) {
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGranularity(1f);
        // 注意：这里不设置 ValueFormatter，留给 setXAxisToIndexMode/TimeMode
        xAxis.setLabelCount(Math.min( Keduxian , dataCount), true);
    }

    /**
     * 设置 X 轴为索引模式（显示数字索引），原有默认模式
     */
    public void setXAxisToIndexMode() {
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(null);
        lineChart.invalidate();
    }

    /**
     * 设置 X 轴为时间模式（精确到秒）
     */
    public void setXAxisToTimeMode() {
        Log.d("SensorChartManager", "setXAxisToTimeMode 被调用，时间戳列表大小=" + currentTimestampsForX.size());
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value, com.github.mikephil.charting.components.AxisBase axis) {
                int index = Math.round(value);
                if (index >= 0 && index < currentTimestampsForX.size()) {
                    long timestamp = currentTimestampsForX.get(index);
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    return sdf.format(new Date(timestamp));
                }
                return "";
            }
        });
        xAxis.setLabelCount(5, true);
        // 强制重新设置数据以触发轴重绘
        LineData data = lineChart.getData();
        if (data != null) {
            lineChart.setData(data);
        }
        lineChart.invalidate();
        Log.d("SensorChartManager", "时间模式已强制刷新");
    }

    /**
     * 设置是否使用时间 X 轴（精确到秒）
     * @param useTime true 使用时间模式，false 使用索引模式（默认）
     */
    public void setUseTimeXAxis(boolean useTime) {
        if (this.useTimeXAxis == useTime) return;
        this.useTimeXAxis = useTime;
        // 如果已经有数据，立即刷新显示
        if (!currentTimestampsForX.isEmpty()) {
            if (useTime) {
                setXAxisToTimeMode();
            } else {
                setXAxisToIndexMode();
            }
        }
    }

    public boolean isUseTimeXAxis() {
        return useTimeXAxis;
    }

    public void onDestroy() {
        if (floatingMarkerView != null) {
            rootView.removeView(floatingMarkerView);
        }
        hideHandler.removeCallbacksAndMessages(null);
    }
}