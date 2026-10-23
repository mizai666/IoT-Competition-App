package com.example.chapter05;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class CameraDatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "camera_data.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_NAME = "camera_records";
    private static final String COL_ID = "id";
    private static final String COL_PROJECT_ID = "project_id";
    private static final String COL_JSON = "json_data";
    private static final String COL_TIMESTAMP = "timestamp";

    public CameraDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + "("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_PROJECT_ID + " INTEGER NOT NULL,"
                + COL_JSON + " TEXT NOT NULL,"
                + COL_TIMESTAMP + " INTEGER NOT NULL)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void insertCameraData(int projectId, String json) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PROJECT_ID, projectId);
        values.put(COL_JSON, json);
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        db.insert(TABLE_NAME, null, values);
    }
}