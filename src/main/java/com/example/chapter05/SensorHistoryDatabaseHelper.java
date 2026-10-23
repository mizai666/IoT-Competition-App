package com.example.chapter05;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class SensorHistoryDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "SensorHistory";
    private static final String DB_NAME = "sensor_history.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_NAME = "sensor_history";
    private static final String COL_ID = "id";
    private static final String COL_PROJECT_ID = "project_id";
    private static final String COL_SENSOR_NAME = "sensor_name";
    private static final String COL_VALUE = "value";
    private static final String COL_TIMESTAMP = "timestamp";

    public SensorHistoryDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + "("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_PROJECT_ID + " INTEGER NOT NULL,"
                + COL_SENSOR_NAME + " TEXT NOT NULL,"
                + COL_VALUE + " TEXT NOT NULL,"
                + COL_TIMESTAMP + " INTEGER NOT NULL)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertHistory(int projectId, String sensorName, String value, long timestamp) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PROJECT_ID, projectId);
        values.put(COL_SENSOR_NAME, sensorName);
        values.put(COL_VALUE, value);
        values.put(COL_TIMESTAMP, timestamp);
        db.insert(TABLE_NAME, null, values);
    }

    // 删除指定项目、传感器、时间戳的历史记录
    public void deleteHistory(int projectId, String sensorName, long timestamp) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, "project_id=? AND sensor_name=? AND timestamp=?",
                new String[]{String.valueOf(projectId), sensorName, String.valueOf(timestamp)});
    }

    public void updateHistory(int projectId, String sensorName, long timestamp, String newValue) {
        Log.d(TAG, "更新传感器历史 - 项目:" + projectId + ", 传感器:" + sensorName + ", 时间戳:" + timestamp + ", 新值:" + newValue);
        SQLiteDatabase db = getWritableDatabase();

        // 先尝试删除所有匹配的记录
        String whereClause = COL_PROJECT_ID + "=? AND " + COL_SENSOR_NAME + "=? AND " + COL_TIMESTAMP + "=?";
        String[] whereArgs = new String[]{String.valueOf(projectId), sensorName, String.valueOf(timestamp)};

        // 记录删除前的行数
        Cursor cursor = db.query(TABLE_NAME, new String[]{COL_ID}, whereClause, whereArgs, null, null, null);
        int countBefore = cursor.getCount();
        cursor.close();
        Log.d(TAG, "更新前数据库中存在 " + countBefore + " 条匹配记录");

        int deletedRows = db.delete(TABLE_NAME, whereClause, whereArgs);
        Log.d(TAG, "执行删除操作，删除行数: " + deletedRows);

        // 再插入新值
        ContentValues values = new ContentValues();
        values.put(COL_PROJECT_ID, projectId);
        values.put(COL_SENSOR_NAME, sensorName);
        values.put(COL_VALUE, newValue);
        values.put(COL_TIMESTAMP, timestamp);
        long newRowId = db.insert(TABLE_NAME, null, values);
        Log.d(TAG, "插入新记录完成，新记录 ID: " + newRowId);
    }

    public List<HistoryRecord> getHistoryByTimestamp(int projectId, long timestamp) {
        List<HistoryRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null,
                COL_PROJECT_ID + "=? AND " + COL_TIMESTAMP + "=?",
                new String[]{String.valueOf(projectId), String.valueOf(timestamp)},
                null, null, null);
        while (cursor.moveToNext()) {
            HistoryRecord record = new HistoryRecord();
            record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            record.projectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
            record.sensorName = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENSOR_NAME));
            record.value = cursor.getString(cursor.getColumnIndexOrThrow(COL_VALUE));
            record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            list.add(record);
        }
        cursor.close();
        return list;
    }

    public List<HistoryRecord> getHistoryByProject(int projectId, String sensorName) {
        List<HistoryRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String selection = COL_PROJECT_ID + "=?";
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(projectId));
        if (sensorName != null && !sensorName.isEmpty()) {
            selection += " AND " + COL_SENSOR_NAME + "=?";
            args.add(sensorName);
        }
        Cursor cursor = db.query(TABLE_NAME, null, selection, args.toArray(new String[0]),
                null, null, COL_TIMESTAMP + " DESC");
        while (cursor.moveToNext()) {
            HistoryRecord record = new HistoryRecord();
            record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            record.projectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
            record.sensorName = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENSOR_NAME));
            record.value = cursor.getString(cursor.getColumnIndexOrThrow(COL_VALUE));
            record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            list.add(record);
        }
        cursor.close();
        return list;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }

    // 获取指定项目的所有传感器历史（不限定传感器名称）
    public List<HistoryRecord> getAllHistoryByProject(int projectId) {
        List<HistoryRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null,
                COL_PROJECT_ID + "=?", new String[]{String.valueOf(projectId)},
                null, null, COL_TIMESTAMP + " DESC");
        while (cursor.moveToNext()) {
            HistoryRecord record = new HistoryRecord();
            record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            record.projectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
            record.sensorName = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENSOR_NAME));
            record.value = cursor.getString(cursor.getColumnIndexOrThrow(COL_VALUE));
            record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            list.add(record);
        }
        cursor.close();
        return list;
    }

    /**
     * 组合查询：数值范围、时间范围、排序。
     * 关键词匹配（原始键名 + 显示名）在 Java 层由调用方完成，因为别名存储在 SharedPreferences 而非本表。
     */
    public List<HistoryRecord> queryWithRange(int projectId, Float valueMin, Float valueMax,
                                              long startTime, long endTime, boolean asc) {
        List<HistoryRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        List<String> args = new ArrayList<>();
        StringBuilder sel = new StringBuilder(COL_PROJECT_ID + "=?");
        args.add(String.valueOf(projectId));

        if (valueMin != null) {
            sel.append(" AND CAST(").append(COL_VALUE).append(" AS REAL) >= ?");
            args.add(String.valueOf(valueMin));
        }
        if (valueMax != null) {
            sel.append(" AND CAST(").append(COL_VALUE).append(" AS REAL) <= ?");
            args.add(String.valueOf(valueMax));
        }
        if (startTime > 0) {
            sel.append(" AND ").append(COL_TIMESTAMP).append(">=?");
            args.add(String.valueOf(startTime));
        }
        if (endTime > 0) {
            sel.append(" AND ").append(COL_TIMESTAMP).append("<=?");
            args.add(String.valueOf(endTime));
        }

        String order = asc ? COL_TIMESTAMP + " ASC" : COL_TIMESTAMP + " DESC";
        Cursor cursor = db.query(TABLE_NAME, null, sel.toString(), args.toArray(new String[0]), null, null, order);
        while (cursor.moveToNext()) {
            HistoryRecord record = new HistoryRecord();
            record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            record.projectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
            record.sensorName = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENSOR_NAME));
            record.value = cursor.getString(cursor.getColumnIndexOrThrow(COL_VALUE));
            record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            list.add(record);
        }
        cursor.close();
        return list;
    }

    public static class HistoryRecord {
        public int id;
        public int projectId;
        public String sensorName;
        public String value;
        public long timestamp;
    }
}
