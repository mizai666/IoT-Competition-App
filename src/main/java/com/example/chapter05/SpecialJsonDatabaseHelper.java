package com.example.chapter05;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 特殊JSON数据存储助手类
 * 用于存储具有固定格式的特殊项目JSON数据
 */
public class  SpecialJsonDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "SpecialJsonDB";
    private static final String DB_NAME = "special_json_data.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_NAME = "special_json_records";
    
    // 列名常量
    private static final String COL_ID = "id";
    private static final String COL_PROJECT_ID = "project_id";
    private static final String COL_JSON_DATA = "json_data";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_CREATED_AT = "created_at";

    public SpecialJsonDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + "("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_PROJECT_ID + " INTEGER NOT NULL,"
                + COL_JSON_DATA + " TEXT NOT NULL,"
                + COL_TIMESTAMP + " INTEGER NOT NULL,"
                + COL_CREATED_AT + " INTEGER DEFAULT (strftime('%s', 'now') * 1000))";
        db.execSQL(createTable);
        
        // 创建索引以加快查询速度
        db.execSQL("CREATE INDEX idx_project_id ON " + TABLE_NAME + "(" + COL_PROJECT_ID + ")");
        db.execSQL("CREATE INDEX idx_timestamp ON " + TABLE_NAME + "(" + COL_TIMESTAMP + ")");
        
        Log.d(TAG, "特殊JSON数据表创建成功");
    }
    /**
     * 更新特殊JSON记录（按ID）
     * @param id 记录ID
     * @param newJsonData 新的JSON字符串
     * @return 更新的行数
     */
    public int updateSpecialJson(int id, String newJsonData) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_JSON_DATA, newJsonData);
        // 更新时间戳为当前时间（也可保留原时间戳，这里建议更新）
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        int rows = db.update(TABLE_NAME, values, COL_ID + "=?", new String[]{String.valueOf(id)});
        Log.d(TAG, "更新特殊JSON ID=" + id + ", 影响行数=" + rows);
        return rows;
    }

    /**
     * 按ID删除单条特殊JSON记录
     * @param id 记录ID
     * @return 删除的行数
     */
    public int deleteSpecialJsonById(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_NAME, COL_ID + "=?", new String[]{String.valueOf(id)});
        Log.d(TAG, "删除特殊JSON ID=" + id + ", 影响行数=" + rows);
        return rows;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
        Log.d(TAG, "特殊JSON数据表升级完成");
    }

    /**
     * 插入特殊JSON数据
     * @param projectId 项目ID
     * @param jsonData JSON字符串
     * @return 插入的行ID
     */
    public long insertSpecialJson(int projectId, String jsonData) {
        SQLiteDatabase db = this.getWritableDatabase();
        long timestamp = System.currentTimeMillis();
        
        ContentValues values = new ContentValues();
        values.put(COL_PROJECT_ID, projectId);
        values.put(COL_JSON_DATA, jsonData);
        values.put(COL_TIMESTAMP, timestamp);
        
        long id = db.insert(TABLE_NAME, null, values);
        Log.d(TAG, "插入特殊JSON - ID: " + id + ", 项目ID: " + projectId);
        return id;
    }

    /**
     * 根据项目ID查询所有特殊JSON记录
     * @param projectId 项目ID
     * @return 特殊JSON记录列表
     */
    public List<SpecialJsonRecord> getRecordsByProjectId(int projectId) {
        List<SpecialJsonRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(
                TABLE_NAME,
                null,
                COL_PROJECT_ID + "=?",
                new String[]{String.valueOf(projectId)},
                null,
                null,
                COL_TIMESTAMP + " DESC"
        );
        
        while (cursor.moveToNext()) {
            SpecialJsonRecord record = new SpecialJsonRecord();
            record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            record.projectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
            record.jsonData = cursor.getString(cursor.getColumnIndexOrThrow(COL_JSON_DATA));
            record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            record.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
            records.add(record);
        }
        cursor.close();
        
        Log.d(TAG, "查询到项目 " + projectId + " 的特殊JSON记录数: " + records.size());
        return records;
    }

    /**
     * 根据时间范围查询特殊JSON记录（startTime/endTime 为 0 表示不限）。
     * @param projectId 项目ID
     * @param startTime 开始时间戳（毫秒），0 表示不限
     * @param endTime 结束时间戳（毫秒），0 表示不限
     * @param asc true=升序，false=降序
     * @return 特殊JSON记录列表
     */
    public List<SpecialJsonRecord> getRecordsByTimeRange(int projectId, long startTime, long endTime, boolean asc) {
        List<SpecialJsonRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        List<String> args = new ArrayList<>();
        StringBuilder sel = new StringBuilder(COL_PROJECT_ID + "=?");
        args.add(String.valueOf(projectId));
        if (startTime > 0) {
            sel.append(" AND ").append(COL_TIMESTAMP).append(">=?");
            args.add(String.valueOf(startTime));
        }
        if (endTime > 0) {
            sel.append(" AND ").append(COL_TIMESTAMP).append("<=?");
            args.add(String.valueOf(endTime));
        }
        String order = asc ? COL_TIMESTAMP + " ASC" : COL_TIMESTAMP + " DESC";
        Cursor cursor = db.query(
                TABLE_NAME,
                null,
                sel.toString(),
                args.toArray(new String[0]),
                null,
                null,
                order
        );

        while (cursor.moveToNext()) {
            SpecialJsonRecord record = new SpecialJsonRecord();
            record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            record.projectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
            record.jsonData = cursor.getString(cursor.getColumnIndexOrThrow(COL_JSON_DATA));
            record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            record.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
            records.add(record);
        }
        cursor.close();
        
        return records;
    }

    /**
     * 获取最新的N条特殊JSON记录
     * @param projectId 项目ID
     * @param limit 限制数量
     * @return 特殊JSON记录列表
     */
    public List<SpecialJsonRecord> getLatestRecords(int projectId, int limit) {
        List<SpecialJsonRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(
                TABLE_NAME,
                null,
                COL_PROJECT_ID + "=?",
                new String[]{String.valueOf(projectId)},
                null,
                null,
                COL_TIMESTAMP + " DESC",
                String.valueOf(limit)
        );
        
        while (cursor.moveToNext()) {
            SpecialJsonRecord record = new SpecialJsonRecord();
            record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
            record.projectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
            record.jsonData = cursor.getString(cursor.getColumnIndexOrThrow(COL_JSON_DATA));
            record.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
            record.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
            records.add(record);
        }
        cursor.close();
        
        return records;
    }

    /**
     * 删除指定项目的特殊JSON记录
     * @param projectId 项目ID
     * @return 删除的行数
     */
    public int deleteRecordsByProjectId(int projectId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_NAME, COL_PROJECT_ID + "=?", new String[]{String.valueOf(projectId)});
        Log.d(TAG, "删除项目 " + projectId + " 的特殊JSON记录数: " + rows);
        return rows;
    }

    /**
     * 清空所有特殊JSON记录
     */
    public void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_NAME);
        Log.d(TAG, "已清空所有特殊JSON记录");
    }

    /**
     * 统计项目的特殊JSON记录数量
     * @param projectId 项目ID
     * @return 记录数量
     */
    public int countRecordsByProjectId(int projectId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE " + COL_PROJECT_ID + "=?",
                new String[]{String.valueOf(projectId)}
        );
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    /**
     * 特殊JSON记录数据类
     */
    public static class SpecialJsonRecord {
        public int id;
        public int projectId;
        public String jsonData;
        public long timestamp;
        public long createdAt;

        @Override
        public String toString() {
            return "SpecialJsonRecord{" +
                    "id=" + id +
                    ", projectId=" + projectId +
                    ", jsonData='" + jsonData + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
