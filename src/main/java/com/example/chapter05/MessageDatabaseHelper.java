package com.example.chapter05;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "mqtt_messages.db";
    private static final int DATABASE_VERSION = 2; // 升级版本以添加 timestamp 列
    private static final String TABLE_NAME = "messages";
    private static final String COL_ID = "id";
    private static final String COL_JSON = "json_data";
    private static final String COL_TIME = "time";
    private static final String COL_TIMESTAMP = "timestamp"; // 新增列

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final Context context;

    public MessageDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + "("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_JSON + " TEXT NOT NULL,"
                + COL_TIME + " TEXT NOT NULL,"
                + COL_TIMESTAMP + " INTEGER NOT NULL)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_TIMESTAMP + " INTEGER DEFAULT 0");
            // 为已有数据填充 timestamp（从 time 字符串解析）
            Cursor cursor = db.query(TABLE_NAME, new String[]{COL_ID, COL_TIME}, null, null, null, null, null);
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String timeStr = cursor.getString(1);
                long timestamp = 0;
                try {
                    timestamp = dateFormat.parse(timeStr).getTime();
                } catch (Exception e) {
                    timestamp = System.currentTimeMillis();
                }
                ContentValues values = new ContentValues();
                values.put(COL_TIMESTAMP, timestamp);
                db.update(TABLE_NAME, values, COL_ID + "=?", new String[]{String.valueOf(id)});
            }
            cursor.close();
        }
    }

    // ==================== 核心方法 ====================

    /**
     * 插入原始消息（仅存储，不解析传感器历史，不更新项目数据）
     * 注意：传感器历史和项目数据由 MqttManager 处理，避免重复调用
     */
    /**
     * 插入消息（同时插入传感器历史，使用统一时间戳）
     */
    /**
     * 插入消息
     * @param json JSON字符串
     * @param isSpecial 是否为特殊JSON
     * @return 插入的行ID
     */
    public long insertMessage(String json, boolean isSpecial) {
        if (isSpecial) {
            // 特殊JSON存储到专用数据库
            SpecialJsonDatabaseHelper specialHelper = new SpecialJsonDatabaseHelper(context);
            return specialHelper.insertSpecialJson(0, json); // projectId 会在后续更新时确定
        } else {
            // 普通消息存储到 messages 表
            SQLiteDatabase db = this.getWritableDatabase();
            long timestamp = System.currentTimeMillis();
            String timeStr = dateFormat.format(new Date(timestamp));
            ContentValues values = new ContentValues();
            values.put(COL_JSON, json);
            values.put(COL_TIME, timeStr);
            values.put(COL_TIMESTAMP, timestamp);
            long id = db.insert(TABLE_NAME, null, values);
            Log.d("MessageDBHelper", "插入 ID: " + id + ", JSON: " + json + ", 时间戳: " + timestamp);

            // 插入传感器历史（用于折线图）
            insertSensorHistoryFromJson(json, timestamp);
            // 更新项目数据
            ProjectDataManager.getInstance(context).updateFromJson(json);
            return id;
        }
    }
    public long insertMessage(String json) {
        return insertMessage(json, false);
    }
    /**
     * 更新消息（保留原时间戳，同步传感器历史：先删除旧记录，再插入新记录）
     */
    /**
     * 更新消息（保留原时间戳，同步传感器历史：先删除旧记录，再插入新记录）
     */
    public int updateMessage(int id, String newJson) {
        Log.d("MessageDBHelper", "开始更新消息 ID: " + id + ", JSON: " + newJson);
        SQLiteDatabase db = this.getWritableDatabase();
        // 获取旧消息的时间戳
        Cursor cursor = db.query(TABLE_NAME, new String[]{COL_TIMESTAMP}, COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (!cursor.moveToFirst()) {
            cursor.close();
            Log.e("MessageDBHelper", "找不到消息 ID: " + id);
            return 0;
        }
        long oldTimestamp = cursor.getLong(0);
        cursor.close();
        Log.d("MessageDBHelper", "获取到旧时间戳: " + oldTimestamp);

        // 更新消息表（保持时间戳不变）
        String newTimeStr = dateFormat.format(new Date(oldTimestamp));
        ContentValues values = new ContentValues();
        values.put(COL_JSON, newJson);
        values.put(COL_TIME, newTimeStr);
        // 不更新时间戳
        int rows = db.update(TABLE_NAME, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        Log.d("MessageDBHelper", "消息表更新行数: " + rows);

        // 解析新 JSON，更新传感器历史（覆盖旧值）
        try {
            JSONObject json = new JSONObject(newJson);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = json.optString(key);
                int projectId = extractProjectId(key);
                if (projectId != -1) {
                    String sensorName = extractSensorName(key);
                    Log.d("MessageDBHelper", "准备更新传感器历史 - 项目:" + projectId + ", 传感器:" + sensorName + ", 时间戳:" + oldTimestamp + ", 值:" + value);
                    SensorHistoryDatabaseHelper sensorHelper = new SensorHistoryDatabaseHelper(context);
                    sensorHelper.updateHistory(projectId, sensorName, oldTimestamp, value);
                }
            }
        } catch (JSONException e) {
            Log.e("MessageDBHelper", "解析 JSON 失败", e);
        }

        // 更新项目数据（刷新内存中的最新值）
        ProjectDataManager.getInstance(context).updateFromJson(newJson);
        Log.d("MessageDBHelper", "消息更新完成");
        return rows;
    }


    /**
     * 删除消息（同时删除对应的传感器历史）
     */
    public int deleteMessage(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        // 获取时间戳和 JSON
        Cursor cursor = db.query(TABLE_NAME, new String[]{COL_JSON, COL_TIMESTAMP}, COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (!cursor.moveToFirst()) {
            cursor.close();
            return 0;
        }
        String json = cursor.getString(0);
        long timestamp = cursor.getLong(1);
        cursor.close();

        // 删除传感器历史
        deleteSensorHistoryByJson(json, timestamp);

        // 删除消息
        int rows = db.delete(TABLE_NAME, COL_ID + "=?", new String[]{String.valueOf(id)});
        return rows;
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 JSON 解析并插入传感器历史
     */
    private void insertSensorHistoryFromJson(String jsonStr, long timestamp) {
        SensorHistoryDatabaseHelper sensorHelper = new SensorHistoryDatabaseHelper(context);
        try {
            JSONObject json = new JSONObject(jsonStr);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = json.optString(key);
                int projectId = extractProjectId(key);
                if (projectId != -1) {
                    String sensorName = extractSensorName(key);
                    sensorHelper.insertHistory(projectId, sensorName, value, timestamp);
                }
            }
        } catch (JSONException e) {
            Log.e("MessageDBHelper", "解析JSON失败", e);
        }
    }

    /**
     * 根据 JSON 和时间戳删除对应的传感器历史
     */
    private void deleteSensorHistoryByJson(String jsonStr, long timestamp) {
        SensorHistoryDatabaseHelper sensorHelper = new SensorHistoryDatabaseHelper(context);
        try {
            JSONObject json = new JSONObject(jsonStr);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                int projectId = extractProjectId(key);
                if (projectId != -1) {
                    String sensorName = extractSensorName(key);
                    sensorHelper.deleteHistory(projectId, sensorName, timestamp);
                }
            }
        } catch (JSONException e) {
            Log.e("MessageDBHelper", "解析JSON失败", e);
        }
    }

    // ==================== 查询方法 ====================

    public List<MessageItem> getAllMessages() {
        List<MessageItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, COL_ID + " DESC");
        while (cursor.moveToNext()) {
            MessageItem item = new MessageItem();
            item.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            item.json = cursor.getString(cursor.getColumnIndexOrThrow(COL_JSON));
            item.time = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME));
            item.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            list.add(item);
        }
        cursor.close();
        return list;
    }

    public List<MessageItem> queryMessages(String keyName, int minutesBefore, long dateTimestamp) {
        List<MessageItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        List<String> selectionArgs = new ArrayList<>();
        StringBuilder selection = new StringBuilder("1=1");

        // 按字段名过滤
        if (keyName != null && !keyName.isEmpty()) {
            selection.append(" AND ").append(COL_JSON).append(" LIKE ?");
            selectionArgs.add("%\"" + keyName + "\"%");
        }
        
        // 时间范围过滤（优先级高于日期选择）
        if (minutesBefore > 0) {
            long currentTime = System.currentTimeMillis();
            long startTime = currentTime - minutesBefore * 60 * 1000L;
            selection.append(" AND ").append(COL_TIMESTAMP).append(" >= ?");
            selectionArgs.add(String.valueOf(startTime));
        } else if (dateTimestamp > 0) {
            // 只有当没有选择时间范围时，才使用日期过滤
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(dateTimestamp);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            long endOfDay = cal.getTimeInMillis();
            selection.append(" AND ").append(COL_TIMESTAMP).append(" BETWEEN ? AND ?");
            selectionArgs.add(String.valueOf(startOfDay));
            selectionArgs.add(String.valueOf(endOfDay));
        }

        Cursor cursor = db.query(TABLE_NAME, null, selection.toString(), selectionArgs.toArray(new String[0]), null, null, COL_ID + " DESC");
        while (cursor.moveToNext()) {
            MessageItem item = new MessageItem();
            item.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            item.json = cursor.getString(cursor.getColumnIndexOrThrow(COL_JSON));
            item.time = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME));
            item.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            list.add(item);
        }
        cursor.close();
        return list;
    }

    /**
     * 组合查询：关键词（精确/模糊）、排序、时间范围（分钟前 或 日期）。
     */
    public List<MessageItem> queryMessages(String keyName, boolean exact, boolean asc, long startTime, long endTime) {
        List<MessageItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        List<String> selectionArgs = new ArrayList<>();
        StringBuilder selection = new StringBuilder("1=1");

        if (keyName != null && !keyName.isEmpty()) {
            if (exact) {
                selection.append(" AND ").append(COL_JSON).append(" LIKE ?");
                selectionArgs.add("%\"" + keyName + "\":%");
            } else {
                selection.append(" AND ").append(COL_JSON).append(" LIKE ?");
                selectionArgs.add("%" + keyName + "%");
            }
        }

        if (startTime > 0) {
            selection.append(" AND ").append(COL_TIMESTAMP).append(" >= ?");
            selectionArgs.add(String.valueOf(startTime));
        }
        if (endTime > 0) {
            selection.append(" AND ").append(COL_TIMESTAMP).append(" <= ?");
            selectionArgs.add(String.valueOf(endTime));
        }

        String order = asc ? COL_ID + " ASC" : COL_ID + " DESC";
        Cursor cursor = db.query(TABLE_NAME, null, selection.toString(), selectionArgs.toArray(new String[0]), null, null, order);
        while (cursor.moveToNext()) {
            MessageItem item = new MessageItem();
            item.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            item.json = cursor.getString(cursor.getColumnIndexOrThrow(COL_JSON));
            item.time = cursor.getString(cursor.getColumnIndexOrThrow(COL_TIME));
            item.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            list.add(item);
        }
        cursor.close();
        return list;
    }

    public void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
        // 同时清空传感器历史
        SensorHistoryDatabaseHelper sensorHelper = new SensorHistoryDatabaseHelper(context);
        sensorHelper.clearAll();
        // 清空项目数据管理器中的项目
        ProjectDataManager.getInstance(context).clearAllProjects();
    }

    // ==================== 工具方法 ====================
    private int extractProjectId(String key) {
        Pattern pattern = Pattern.compile("\\d+$");
        Matcher matcher = pattern.matcher(key);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return -1;
    }

    private String extractSensorName(String key) {
        return key.replaceAll("\\d+$", "");
    }
}