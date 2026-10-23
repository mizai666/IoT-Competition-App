package com.example.chapter05;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 上报/发布历史记录表。记录 MQTT 发送与 HTTP 上报。
 */
public class PublishHistoryDatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "publish_history.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_NAME = "publish_history";

    private static final String COL_ID = "id";
    private static final String COL_PROJECT_ID = "project_id";
    private static final String COL_TYPE = "type";                 // mqtt / http
    private static final String COL_TOPIC = "topic";
    private static final String COL_PAYLOAD = "payload";
    private static final String COL_QOS = "qos";
    private static final String COL_OK = "ok";                     // 1 / 0
    private static final String COL_STATUS = "status";             // http 状态码或 'ERR'
    private static final String COL_URL = "url";
    private static final String COL_RESPONSE_DATA = "response_data";
    private static final String COL_METHOD = "method";             // GET / POST
    private static final String COL_CREATED_AT = "created_at";

    public PublishHistoryDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + "("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_PROJECT_ID + " INTEGER NOT NULL,"
                + COL_TYPE + " TEXT NOT NULL,"
                + COL_TOPIC + " TEXT,"
                + COL_PAYLOAD + " TEXT,"
                + COL_QOS + " TEXT,"
                + COL_OK + " INTEGER DEFAULT 0,"
                + COL_STATUS + " TEXT,"
                + COL_URL + " TEXT,"
                + COL_RESPONSE_DATA + " TEXT,"
                + COL_METHOD + " TEXT,"
                + COL_CREATED_AT + " INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_publish_project ON " + TABLE_NAME + "(" + COL_PROJECT_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public long insert(PublishRecord record) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PROJECT_ID, record.projectId);
        values.put(COL_TYPE, record.type);
        values.put(COL_TOPIC, record.topic);
        values.put(COL_PAYLOAD, record.payload);
        values.put(COL_QOS, record.qos);
        values.put(COL_OK, record.ok ? 1 : 0);
        values.put(COL_STATUS, record.status);
        values.put(COL_URL, record.url);
        values.put(COL_RESPONSE_DATA, record.responseData);
        values.put(COL_METHOD, record.method);
        values.put(COL_CREATED_AT, record.createdAt);
        return db.insert(TABLE_NAME, null, values);
    }

    /**
     * 组合查询。
     * @param keyword 关键词（打 payload/response_data/url）
     * @param exact true=精确匹配，false=模糊匹配
     * @param asc true=升序（按 created_at），false=降序
     * @param type null 或 "mqtt"/"http"
     * @param okFilter null 或 1/0
     * @param startTime 开始时间戳（毫秒），0 表示不限
     * @param endTime 结束时间戳（毫秒），0 表示不限
     */
    public List<PublishRecord> query(int projectId, String keyword, boolean exact, boolean asc,
                                     String type, Integer okFilter, long startTime, long endTime) {
        List<PublishRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        List<String> args = new ArrayList<>();
        StringBuilder sel = new StringBuilder(COL_PROJECT_ID + "=?");
        args.add(String.valueOf(projectId));

        if (keyword != null && !keyword.isEmpty()) {
            if (exact) {
                sel.append(" AND (").append(COL_PAYLOAD).append("=? OR ")
                        .append(COL_RESPONSE_DATA).append("=? OR ").append(COL_URL).append("=?)");
                args.add(keyword);
                args.add(keyword);
                args.add(keyword);
            } else {
                sel.append(" AND (").append(COL_PAYLOAD).append(" LIKE ? OR ")
                        .append(COL_RESPONSE_DATA).append(" LIKE ? OR ").append(COL_URL).append(" LIKE ?)");
                String like = "%" + keyword + "%";
                args.add(like);
                args.add(like);
                args.add(like);
            }
        }
        if (type != null && !type.isEmpty()) {
            sel.append(" AND ").append(COL_TYPE).append("=?");
            args.add(type);
        }
        if (okFilter != null) {
            sel.append(" AND ").append(COL_OK).append("=?");
            args.add(String.valueOf(okFilter));
        }
        if (startTime > 0) {
            sel.append(" AND ").append(COL_CREATED_AT).append(">=?");
            args.add(String.valueOf(startTime));
        }
        if (endTime > 0) {
            sel.append(" AND ").append(COL_CREATED_AT).append("<=?");
            args.add(String.valueOf(endTime));
        }

        String order = asc ? COL_CREATED_AT + " ASC" : COL_CREATED_AT + " DESC";
        Cursor cursor = db.query(TABLE_NAME, null, sel.toString(), args.toArray(new String[0]),
                null, null, order);
        while (cursor.moveToNext()) {
            list.add(readRecord(cursor));
        }
        cursor.close();
        return list;
    }

    public List<PublishRecord> queryByProject(int projectId) {
        return query(projectId, null, false, false, null, null, 0, 0);
    }

    private PublishRecord readRecord(Cursor cursor) {
        PublishRecord r = new PublishRecord();
        r.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID));
        r.projectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
        r.type = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE));
        r.topic = cursor.getString(cursor.getColumnIndexOrThrow(COL_TOPIC));
        r.payload = cursor.getString(cursor.getColumnIndexOrThrow(COL_PAYLOAD));
        r.qos = cursor.getString(cursor.getColumnIndexOrThrow(COL_QOS));
        r.ok = cursor.getInt(cursor.getColumnIndexOrThrow(COL_OK)) == 1;
        r.status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS));
        r.url = cursor.getString(cursor.getColumnIndexOrThrow(COL_URL));
        r.responseData = cursor.getString(cursor.getColumnIndexOrThrow(COL_RESPONSE_DATA));
        r.method = cursor.getString(cursor.getColumnIndexOrThrow(COL_METHOD));
        r.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT));
        return r;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }

    public static class PublishRecord {
        public int id;
        public int projectId;
        public String type;
        public String topic;
        public String payload;
        public String qos;
        public boolean ok;
        public String status;
        public String url;
        public String responseData;
        public String method;
        public long createdAt;
    }
}
