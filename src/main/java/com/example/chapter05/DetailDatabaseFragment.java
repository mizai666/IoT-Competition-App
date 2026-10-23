package com.example.chapter05;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import com.loper7.date_time_picker.DateTimeConfig;
import com.loper7.date_time_picker.dialog.CardDatePickerDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DetailDatabaseFragment extends Fragment {

    public static final String ACTION_DATA_CHANGED = "com.example.chapter05.DATA_CHANGED";
    public static final String EXTRA_PROJECT_ID = "project_id";

    private int projectId;
    private RecyclerView recyclerViewHistory;
    private HistoryAdapter adapter;
    private List<String> historyDisplayList;
    private List<MessageItem> currentMessages;
    private List<SpecialJsonDatabaseHelper.SpecialJsonRecord> currentSpecialRecords;
    private MessageDatabaseHelper messageDbHelper;
    private SpecialJsonDatabaseHelper specialDbHelper;
    private SensorHistoryDatabaseHelper sensorDbHelper;
    private AlarmHistoryDatabaseHelper alarmDbHelper;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private EditText etKeyFilter;
    private Button btnSelectRange, btnClearTime, btnQuery;
    private Button btnAdd, btnEdit, btnDelete;

    // 组合查询控件
    private Spinner spinnerTableType;
    private Spinner spinnerOrder;
    private Spinner spinnerExact;
    private LinearLayout llValueRange;
    private LinearLayout llPublishFilter;
    private EditText etValueMin, etValueMax;
    private Spinner spinnerType, spinnerOk;
    private PublishHistoryDatabaseHelper publishDbHelper;

    private long selectedStartTime = 0;
    private long selectedEndTime = 0;
    private String keyFilter = "";

    private MessageItem selectedMessage = null;
    private SpecialJsonDatabaseHelper.SpecialJsonRecord selectedSpecialRecord = null;
    private int selectedPosition = -1;
    private View selectedView = null;

    public static DetailDatabaseFragment newInstance(int projectId) {
        DetailDatabaseFragment fragment = new DetailDatabaseFragment();
        Bundle args = new Bundle();
        args.putInt("project_id", projectId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail_database, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            projectId = getArguments().getInt("project_id", -1);
        }

        recyclerViewHistory = view.findViewById(R.id.listview_history);
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        historyDisplayList = new ArrayList<>();
        adapter = new HistoryAdapter(historyDisplayList);
        recyclerViewHistory.setAdapter(adapter);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(getContext(), LinearLayoutManager.VERTICAL);
        dividerItemDecoration.setDrawable(ContextCompat.getDrawable(getContext(), R.drawable.divider));
        recyclerViewHistory.addItemDecoration(dividerItemDecoration);

        messageDbHelper = new MessageDatabaseHelper(requireContext());
        specialDbHelper = new SpecialJsonDatabaseHelper(requireContext());
        sensorDbHelper = new SensorHistoryDatabaseHelper(requireContext());
        alarmDbHelper = new AlarmHistoryDatabaseHelper(requireContext());

        etKeyFilter = view.findViewById(R.id.et_key_filter);
        btnSelectRange = view.findViewById(R.id.btn_select_range);
        btnClearTime = view.findViewById(R.id.btn_clear_time);
        btnQuery = view.findViewById(R.id.btn_query);
        btnAdd = view.findViewById(R.id.btn_add);
        btnEdit = view.findViewById(R.id.btn_edit);
        btnDelete = view.findViewById(R.id.btn_delete);

        // 组合查询控件
        spinnerTableType = view.findViewById(R.id.spinner_table_type);
        spinnerOrder = view.findViewById(R.id.spinner_order);
        spinnerExact = view.findViewById(R.id.spinner_exact);
        llValueRange = view.findViewById(R.id.ll_value_range);
        llPublishFilter = view.findViewById(R.id.ll_publish_filter);
        etValueMin = view.findViewById(R.id.et_value_min);
        etValueMax = view.findViewById(R.id.et_value_max);
        spinnerType = view.findViewById(R.id.spinner_type);
        spinnerOk = view.findViewById(R.id.spinner_ok);
        publishDbHelper = new PublishHistoryDatabaseHelper(requireContext());

        ArrayAdapter<String> tableAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"传感器历史", "报警记录", "MQTT消息", "上报记录"});
        tableAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTableType.setAdapter(tableAdapter);

        ArrayAdapter<String> orderAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new String[]{"降序", "升序"});
        orderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOrder.setAdapter(orderAdapter);

        ArrayAdapter<String> exactAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new String[]{"模糊", "精确"});
        exactAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerExact.setAdapter(exactAdapter);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new String[]{"全部类型", "mqtt", "http"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);
        ArrayAdapter<String> okAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new String[]{"全部状态", "成功", "失败"});
        okAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOk.setAdapter(okAdapter);

        spinnerTableType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean isSensor = (position == 0);
                boolean isPublish = (position == 3);
                boolean showValueRange = isSensor && !isSpecialProject();
                llValueRange.setVisibility(showValueRange ? View.VISIBLE : View.GONE);
                llPublishFilter.setVisibility(isPublish ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSelectRange.setOnClickListener(v -> showDateTimeRangeDialog());
        btnClearTime.setOnClickListener(v -> {
            selectedStartTime = 0;
            selectedEndTime = 0;
            Toast.makeText(getContext(), "已清除时间过滤", Toast.LENGTH_SHORT).show();
        });
        btnQuery.setOnClickListener(v -> performQuery());

        btnAdd.setOnClickListener(v -> showAddDialog());
        btnEdit.setOnClickListener(v -> {
            if (isSpecialProject()) {
                if (selectedSpecialRecord == null) {
                    Toast.makeText(getContext(), "请先长按选择一条消息", Toast.LENGTH_SHORT).show();
                } else {
                    showEditDialogForSpecial();
                }
            } else {
                if (selectedMessage == null) {
                    Toast.makeText(getContext(), "请先长按选择一条消息", Toast.LENGTH_SHORT).show();
                } else {
                    showEditDialog(selectedMessage);
                }
            }
        });
        btnDelete.setOnClickListener(v -> {
            if (isSpecialProject()) {
                if (selectedSpecialRecord == null) {
                    Toast.makeText(getContext(), "请先长按选择一条消息", Toast.LENGTH_SHORT).show();
                } else {
                    confirmDeleteSpecial();
                }
            } else {
                if (selectedMessage == null) {
                    Toast.makeText(getContext(), "请先长按选择一条消息", Toast.LENGTH_SHORT).show();
                } else {
                    confirmDelete(selectedMessage);
                }
            }
        });

        // 修复：lambda 中不能有返回值，因为 onLongClick 返回 void
        adapter.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public void onLongClick(View viewItem, int position) {
                selectedPosition = position;
                if (isSpecialProject()) {
                    if (currentSpecialRecords != null && position >= 0 && position < currentSpecialRecords.size()) {
                        selectedSpecialRecord = currentSpecialRecords.get(position);
                        selectedView = viewItem;
                        Toast.makeText(getContext(), "已选中: " + dateFormat.format(new Date(selectedSpecialRecord.timestamp)), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "未找到记录", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if (currentMessages != null && position < currentMessages.size()) {
                        selectedMessage = currentMessages.get(position);
                        selectedView = viewItem;
                        Toast.makeText(getContext(), "已选中: " + selectedMessage.time, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        performQuery();
    }

    private boolean isSpecialProject() {
        Project project = ProjectDataManager.getInstance(requireContext()).getProject(projectId);
        return project != null && project.isSpecial();
    }

    private void showDateTimeRangeDialog() {
        showRangeTimePicker(true);
    }

    private void showRangeTimePicker(boolean isStart) {
        long current = isStart ? selectedStartTime : selectedEndTime;
        if (current == 0) current = System.currentTimeMillis();

        CardDatePickerDialog.Companion.builder(requireContext())
                .setTitle(isStart ? "选择开始时间" : "选择结束时间")
                .setDisplayType(DateTimeConfig.YEAR, DateTimeConfig.MONTH, DateTimeConfig.DAY,
                        DateTimeConfig.HOUR, DateTimeConfig.MIN)
                .setDefaultTime(current)
                .setOnChoose("确定", (kotlin.jvm.functions.Function1<Long, kotlin.Unit>) millisecond -> {
                    if (isStart) {
                        selectedStartTime = millisecond;
                        showRangeTimePicker(false);
                    } else {
                        selectedEndTime = millisecond;
                        Toast.makeText(getContext(),
                                dateFormat.format(new Date(selectedStartTime)) + " ~ " + dateFormat.format(new Date(selectedEndTime)),
                                Toast.LENGTH_LONG).show();
                    }
                    return kotlin.Unit.INSTANCE;
                })
                .setOnCancel("取消", (kotlin.jvm.functions.Function0<kotlin.Unit>) () -> kotlin.Unit.INSTANCE)
                .build()
                .show();
    }


    private void performQuery() {
        final int tableType = spinnerTableType.getSelectedItemPosition();
        final String keyword = etKeyFilter.getText().toString().trim();
        final boolean exact = spinnerExact.getSelectedItemPosition() == 1;
        final boolean asc = spinnerOrder.getSelectedItemPosition() == 1;

        new Thread(() -> {
            List<String> displayList;
            if (tableType == 0) {
                displayList = querySensorHistory(keyword, exact, asc);
            } else if (tableType == 1) {
                displayList = queryAlarmHistory(keyword, asc);
            } else if (tableType == 2) {
                displayList = queryMqttMessages(keyword, exact, asc);
            } else {
                displayList = queryPublishHistory(keyword, exact, asc);
            }

            final List<String> finalList = displayList;
            requireActivity().runOnUiThread(() -> {
                historyDisplayList.clear();
                if (finalList.isEmpty()) {
                    historyDisplayList.add("暂无数据");
                } else {
                    historyDisplayList.addAll(finalList);
                }
                adapter.updateData(historyDisplayList);
                selectedMessage = null;
                selectedSpecialRecord = null;
                selectedPosition = -1;
                adapter.clearSelection();
            });
        }).start();
    }

    private List<String> querySensorHistory(String keyword, boolean exact, boolean asc) {
        List<String> result = new ArrayList<>();
        long[] range = getTimeRange();
        if (isSpecialProject()) {
            // 特殊项目：查特殊JSON记录，关键词匹配整条 JSON，时间 + 排序
            List<SpecialJsonDatabaseHelper.SpecialJsonRecord> records =
                    specialDbHelper.getRecordsByTimeRange(projectId, range[0], range[1], asc);
            currentSpecialRecords = records;
            for (SpecialJsonDatabaseHelper.SpecialJsonRecord record : records) {
                if (!keyword.isEmpty()) {
                    boolean match = exact ? record.jsonData.equals(keyword) : record.jsonData.contains(keyword);
                    if (!match) continue;
                }
                String timeStr = dateFormat.format(new Date(record.timestamp));
                result.add(timeStr + " - " + record.jsonData);
            }
        } else {
            Float vMin = parseFloatOrNull(etValueMin.getText().toString().trim());
            Float vMax = parseFloatOrNull(etValueMax.getText().toString().trim());
            List<SensorHistoryDatabaseHelper.HistoryRecord> records = sensorDbHelper.queryWithRange(
                    projectId, vMin, vMax, range[0], range[1], asc);
            // 关键词在 Java 层匹配：原始键名 或 显示名（别名）
            Map<String, String> aliases = SensorAliasHelper.loadAll(requireContext(), projectId);
            Map<Long, List<SensorHistoryDatabaseHelper.HistoryRecord>> grouped = new LinkedHashMap<>();
            for (SensorHistoryDatabaseHelper.HistoryRecord r : records) {
                if (!keyword.isEmpty()) {
                    String alias = aliases.getOrDefault(r.sensorName, r.sensorName);
                    boolean match;
                    if (exact) {
                        match = r.sensorName.equals(keyword) || alias.equals(keyword);
                    } else {
                        // 双向包含：数据包含关键词 或 关键词包含数据（兼容带数字后缀的原始键名）
                        match = r.sensorName.contains(keyword) || keyword.contains(r.sensorName)
                                || alias.contains(keyword) || keyword.contains(alias);
                    }
                    if (!match) continue;
                }
                grouped.computeIfAbsent(r.timestamp, k -> new ArrayList<>()).add(r);
            }
            for (Map.Entry<Long, List<SensorHistoryDatabaseHelper.HistoryRecord>> e : grouped.entrySet()) {
                String timeStr = dateFormat.format(new Date(e.getKey()));
                StringBuilder sb = new StringBuilder(timeStr + " - ");
                for (SensorHistoryDatabaseHelper.HistoryRecord r : e.getValue()) {
                    if (sb.length() > timeStr.length() + 3) sb.append(", ");
                    sb.append(aliases.getOrDefault(r.sensorName, r.sensorName)).append(":").append(r.value);
                }
                result.add(sb.toString());
            }
        }
        return result;
    }

    private List<String> queryAlarmHistory(String keyword, boolean asc) {
        List<String> result = new ArrayList<>();
        long[] range = getTimeRange();
        List<AlarmHistoryDatabaseHelper.AlarmRecord> records = alarmDbHelper.query(
                projectId, keyword.isEmpty() ? null : keyword, asc, range[0], range[1]);
        for (AlarmHistoryDatabaseHelper.AlarmRecord r : records) {
            String timeStr = dateFormat.format(new Date(r.timestamp));
            result.add(timeStr + " - " + r.sensorName + ": " + r.alarmMessage);
        }
        return result;
    }

    private List<String> queryMqttMessages(String keyword, boolean exact, boolean asc) {
        List<String> result = new ArrayList<>();
        long[] range = getTimeRange();
        List<MessageItem> messages = messageDbHelper.queryMessages(
                keyword.isEmpty() ? null : keyword, exact, asc,
                range[0], range[1]);
        currentMessages = messages;
        for (MessageItem msg : messages) {
            String filtered = filterMessageForProject(msg.json, projectId, keyword);
            if (filtered != null && !filtered.isEmpty()) {
                result.add(msg.time + " - " + filtered);
            }
        }
        return result;
    }

    private List<String> queryPublishHistory(String keyword, boolean exact, boolean asc) {
        List<String> result = new ArrayList<>();
        long[] range = getTimeRange();
        String type = null;
        if (spinnerType.getSelectedItemPosition() == 1) type = "mqtt";
        else if (spinnerType.getSelectedItemPosition() == 2) type = "http";
        Integer ok = null;
        if (spinnerOk.getSelectedItemPosition() == 1) ok = 1;
        else if (spinnerOk.getSelectedItemPosition() == 2) ok = 0;

        List<PublishHistoryDatabaseHelper.PublishRecord> records = publishDbHelper.query(
                projectId, keyword.isEmpty() ? null : keyword, exact, asc, type, ok, range[0], range[1]);
        for (PublishHistoryDatabaseHelper.PublishRecord r : records) {
            String timeStr = dateFormat.format(new Date(r.createdAt));
            StringBuilder sb = new StringBuilder(timeStr + " - ");
            if ("http".equals(r.type)) {
                sb.append("[HTTP] ").append(r.method).append(" ").append(r.url)
                        .append(" 状态:").append(r.status).append(r.ok ? " 成功" : " 失败");
                if (r.responseData != null && !r.responseData.isEmpty()) {
                    sb.append("\n响应: ").append(r.responseData);
                }
            } else {
                sb.append("[MQTT] ").append(r.topic).append(" ").append(r.payload);
            }
            result.add(sb.toString());
        }
        return result;
    }

    private long[] getTimeRange() {
        return new long[]{selectedStartTime, selectedEndTime};
    }

    private Float parseFloatOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updateDisplayList() {
        historyDisplayList.clear();
        if (currentMessages == null || currentMessages.isEmpty()) {
            historyDisplayList.add("暂无数据");
        } else {
            for (MessageItem msg : currentMessages) {
                String filtered = filterMessageForProject(msg.json, projectId, keyFilter);
                if (filtered != null && !filtered.isEmpty()) {
                    historyDisplayList.add(msg.time + " - " + filtered);
                }
            }
        }
        adapter.updateData(historyDisplayList);
    }

    private String filterMessageForProject(String jsonStr, int projectId, String keyFilter) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            Iterator<String> keys = json.keys();
            StringBuilder sb = new StringBuilder();
            while (keys.hasNext()) {
                String key = keys.next();
                int id = extractProjectId(key);
                boolean matchProject = (projectId == 0 && id == -1) || (id == projectId);
                if (matchProject) {
                    String value = json.optString(key);
                    if (keyFilter != null && !keyFilter.isEmpty()) {
                        // 键名或值命中关键词均保留
                        if (key.contains(keyFilter) || value.contains(keyFilter)) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(key).append(":").append(value);
                        }
                    } else {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(key).append(":").append(value);
                    }
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private int extractProjectId(String key) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+$");
        java.util.regex.Matcher matcher = pattern.matcher(key);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return -1;
    }

    private void notifyDataChanged() {
        Intent intent = new Intent(ACTION_DATA_CHANGED);
        intent.putExtra(EXTRA_PROJECT_ID, projectId);
        requireContext().sendBroadcast(intent);
    }

    private void showAddDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("请输入JSON格式消息，例如: {\"Temp1\":25} 或 {\"wendu_num\":25}");
        new AlertDialog.Builder(requireContext())
                .setTitle("新增消息")
                .setView(input)
                .setPositiveButton("添加", (dialog, which) -> {
                    String json = input.getText().toString().trim();
                    if (json.isEmpty()) {
                        Toast.makeText(getContext(), "内容不能为空", Toast.LENGTH_SHORT).show(); // 修复：LENGTH_SHORT
                        return;
                    }
                    try {
                        new JSONObject(json);
                        if (isSpecialProject()) {
                            specialDbHelper.insertSpecialJson(projectId, json);
                            ProjectDataManager.getInstance(requireContext()).updateFromJson(json);
                            Toast.makeText(getContext(), "特殊项目添加成功", Toast.LENGTH_SHORT).show();
                        } else {
                            messageDbHelper.insertMessage(json);
                            Toast.makeText(getContext(), "添加成功", Toast.LENGTH_SHORT).show();
                        }
                        performQuery();
                        notifyDataChanged();
                    } catch (JSONException e) {
                        Toast.makeText(getContext(), "JSON格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditDialog(MessageItem item) {
        EditText input = new EditText(requireContext());
        input.setText(item.json);
        input.setSelection(item.json.length());
        new AlertDialog.Builder(requireContext())
                .setTitle("编辑消息 (ID: " + item.id + ")")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newJson = input.getText().toString().trim();
                    if (newJson.isEmpty()) {
                        Toast.makeText(getContext(), "内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        new JSONObject(newJson);
                        int rows = messageDbHelper.updateMessage(item.id, newJson);
                        if (rows > 0) {
                            Toast.makeText(getContext(), "修改成功", Toast.LENGTH_SHORT).show();
                            performQuery();
                            notifyDataChanged();
                        } else {
                            Toast.makeText(getContext(), "修改失败", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(getContext(), "JSON格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditDialogForSpecial() {
        if (selectedSpecialRecord == null) return;
        EditText input = new EditText(requireContext());
        input.setText(selectedSpecialRecord.jsonData);
        input.setSelection(selectedSpecialRecord.jsonData.length());
        new AlertDialog.Builder(requireContext())
                .setTitle("编辑特殊项目消息 (ID: " + selectedSpecialRecord.id + ")")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newJson = input.getText().toString().trim();
                    if (newJson.isEmpty()) {
                        Toast.makeText(getContext(), "内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        new JSONObject(newJson);
                        deleteSensorHistoryForSpecialRecord(selectedSpecialRecord);
                        int rows = specialDbHelper.updateSpecialJson(selectedSpecialRecord.id, newJson);
                        if (rows > 0) {
                            ProjectDataManager.getInstance(requireContext()).updateFromJson(newJson);
                            Toast.makeText(getContext(), "修改成功", Toast.LENGTH_SHORT).show();
                            performQuery();
                            notifyDataChanged();
                        } else {
                            Toast.makeText(getContext(), "修改失败", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(getContext(), "JSON格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(MessageItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除消息")
                .setMessage("确定要删除这条消息吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    int rows = messageDbHelper.deleteMessage(item.id);
                    if (rows > 0) {
                        Toast.makeText(getContext(), "删除成功", Toast.LENGTH_SHORT).show();
                        performQuery();
                        notifyDataChanged();
                    } else {
                        Toast.makeText(getContext(), "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeleteSpecial() {
        if (selectedSpecialRecord == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("删除消息")
                .setMessage("确定要删除这条消息吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    deleteSensorHistoryForSpecialRecord(selectedSpecialRecord);
                    int rows = specialDbHelper.deleteSpecialJsonById(selectedSpecialRecord.id);
                    if (rows > 0) {
                        reloadProjectDataFromSpecialRecords();
                        Toast.makeText(getContext(), "删除成功", Toast.LENGTH_SHORT).show();
                        performQuery();
                        notifyDataChanged();
                    } else {
                        Toast.makeText(getContext(), "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteSensorHistoryForSpecialRecord(SpecialJsonDatabaseHelper.SpecialJsonRecord record) {
        try {
            JSONObject json = new JSONObject(record.jsonData);
            SensorHistoryDatabaseHelper sensorHelper = new SensorHistoryDatabaseHelper(requireContext());
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                sensorHelper.deleteHistory(record.projectId, key, record.timestamp);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void reloadProjectDataFromSpecialRecords() {
        new Thread(() -> {
            List<SpecialJsonDatabaseHelper.SpecialJsonRecord> records = specialDbHelper.getRecordsByProjectId(projectId);
            ProjectDataManager dataManager = ProjectDataManager.getInstance(requireContext());
            Project project = dataManager.getProject(projectId);
            if (project != null) {
                project.getSensors().clear();
            }
            for (SpecialJsonDatabaseHelper.SpecialJsonRecord record : records) {
                dataManager.updateFromJson(record.jsonData);
            }
        }).start();
    }

    interface OnItemLongClickListener {
        void onLongClick(View view, int position);
    }

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<String> items;
        private OnItemLongClickListener longClickListener;
        private int selectedPos = -1;

        HistoryAdapter(List<String> items) {
            this.items = items;
        }

        void setOnItemLongClickListener(OnItemLongClickListener listener) {
            this.longClickListener = listener;
        }

        void updateData(List<String> newItems) {
            this.items = newItems;
            selectedPos = -1;
            notifyDataSetChanged();
        }

        void setSelectedPosition(int position) {
            int oldPos = selectedPos;
            selectedPos = position;
            if (oldPos != -1) notifyItemChanged(oldPos);
            if (selectedPos != -1) notifyItemChanged(selectedPos);
        }

        void clearSelection() {
            setSelectedPosition(-1);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String text = items.get(position);
            holder.textView.setText(text);
            if (position == selectedPos) {
                holder.itemView.setBackgroundResource(R.drawable.bg_threshold_normal);
            } else {
                holder.itemView.setBackgroundResource(0);
            }
            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    int oldPos = selectedPos;
                    selectedPos = holder.getAdapterPosition();
                    if (oldPos != -1) notifyItemChanged(oldPos);
                    notifyItemChanged(selectedPos);
                    longClickListener.onLongClick(v, selectedPos);
                }
                return true; // 这里 return true 是允许的，因为 View.OnLongClickListener 的 onLongClick 返回 boolean
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}