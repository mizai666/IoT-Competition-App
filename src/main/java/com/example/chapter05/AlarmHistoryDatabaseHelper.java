package com.example.chapter05;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class AlarmHistoryDatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "alarm_history.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_NAME = "alarm_history";
    private static final String COL_ID = "id";
    private static final String COL_PROJECT_ID = "project_id";
    private static final String COL_SENSOR_NAME = "sensor_name";
    private static final String COL_ALARM_MESSAGE = "alarm_message";
    private static final String COL_TIMESTAMP = "timestamp";

    public AlarmHistoryDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + "("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_PROJECT_ID + " INTEGER NOT NULL,"
                + COL_SENSOR_NAME + " TEXT NOT NULL,"
                + COL_ALARM_MESSAGE + " TEXT NOT NULL,"
                + COL_TIMESTAMP + " INTEGER NOT NULL)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertAlarm(int projectId, String sensorName, String alarmMessage, long timestamp) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PROJECT_ID, projectId);
        values.put(COL_SENSOR_NAME, sensorName);
        values.put(COL_ALARM_MESSAGE, alarmMessage);
        values.put(COL_TIMESTAMP, timestamp);
        db.insert(TABLE_NAME, null, values);
    }

    public List<AlarmRecord> getAlarmsBySensor(int projectId, String sensorName) {
        List<AlarmRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null,
                COL_PROJECT_ID + "=? AND " + COL_SENSOR_NAME + "=?",
                new String[]{String.valueOf(projectId), sensorName},
                null, null, COL_TIMESTAMP + " DESC");
        while (cursor.moveToNext()) {
            AlarmRecord record = new AlarmRecord();
            record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            record.projectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
            record.sensorName = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENSOR_NAME));
            record.alarmMessage = cursor.getString(cursor.getColumnIndexOrThrow(COL_ALARM_MESSAGE));
            record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            list.add(record);
        }
        cursor.close();
        return list;
    }

    /**
     * 组合查询：关键词（打 sensor_name/alarm_message）、时间范围、排序。
     */
    public List<AlarmRecord> query(int projectId, String keyword, boolean asc, long startTime, long endTime) {
        List<AlarmRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        List<String> args = new ArrayList<>();
        StringBuilder sel = new StringBuilder(COL_PROJECT_ID + "=?");
        args.add(String.valueOf(projectId));

        if (keyword != null && !keyword.isEmpty()) {
            sel.append(" AND (").append(COL_SENSOR_NAME).append(" LIKE ? OR ").append(COL_ALARM_MESSAGE).append(" LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
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
            AlarmRecord record = new AlarmRecord();
            record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            record.projectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
            record.sensorName = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENSOR_NAME));
            record.alarmMessage = cursor.getString(cursor.getColumnIndexOrThrow(COL_ALARM_MESSAGE));
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

    public static class AlarmRecord {
        public int id;
        public int projectId;
        public String sensorName;
        public String alarmMessage;
        public long timestamp;
    }
}