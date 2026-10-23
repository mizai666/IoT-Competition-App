package com.example.chapter05;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class ProjectDatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "projects.db";
    private static final int DB_VERSION = 5; // 升级到5，增加 pinned 列
    private static final String TABLE_NAME = "projects";
    private static final String COL_PROJECT_ID = "project_id";
    private static final String COL_DISPLAY_NAME = "display_name";
    private static final String COL_SCHEMA_KEYS = "schema_keys";
    private static final String COL_IS_SPECIAL = "is_special";
    private static final String COL_PINNED = "pinned";  // 新增

    public ProjectDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + "("
                + COL_PROJECT_ID + " INTEGER PRIMARY KEY,"
                + COL_DISPLAY_NAME + " TEXT NOT NULL,"
                + COL_SCHEMA_KEYS + " TEXT,"
                + COL_IS_SPECIAL + " INTEGER DEFAULT 0,"
                + COL_PINNED + " INTEGER DEFAULT 0)";   // 新增列
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_SCHEMA_KEYS + " TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_IS_SPECIAL + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_PINNED + " INTEGER DEFAULT 0");
            } catch (Exception e) {
                // 列可能已存在，忽略
            }
        }
    }

    public void insertOrUpdate(int projectId, String displayName, String schemaKeys) {
        insertOrUpdate(projectId, displayName, schemaKeys, false, false);
    }

    public void insertOrUpdate(int projectId, String displayName, String schemaKeys, boolean isSpecial) {
        insertOrUpdate(projectId, displayName, schemaKeys, isSpecial, false);
    }

    public void insertOrUpdate(int projectId, String displayName, String schemaKeys, boolean isSpecial, boolean pinned) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PROJECT_ID, projectId);
        values.put(COL_DISPLAY_NAME, displayName);
        values.put(COL_SCHEMA_KEYS, schemaKeys);
        values.put(COL_IS_SPECIAL, isSpecial ? 1 : 0);
        values.put(COL_PINNED, pinned ? 1 : 0);
        db.replace(TABLE_NAME, null, values);
    }

    public List<ProjectRecord> getAllProjects() {
        List<ProjectRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        // 按置顶降序（置顶的在前），然后按项目ID升序
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null,
                COL_PINNED + " DESC, " + COL_PROJECT_ID + " ASC");
        while (cursor.moveToNext()) {
            ProjectRecord record = new ProjectRecord();
            record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PROJECT_ID));
            record.name = cursor.getString(cursor.getColumnIndexOrThrow(COL_DISPLAY_NAME));
            record.schemaKeys = cursor.getString(cursor.getColumnIndexOrThrow(COL_SCHEMA_KEYS));
            int isSpecialIndex = cursor.getColumnIndex(COL_IS_SPECIAL);
            record.isSpecial = (isSpecialIndex != -1 && cursor.getInt(isSpecialIndex) == 1);
            int pinnedIndex = cursor.getColumnIndex(COL_PINNED);
            record.pinned = (pinnedIndex != -1 && cursor.getInt(pinnedIndex) == 1);
            list.add(record);
        }
        cursor.close();
        return list;
    }

    public void updatePinned(int projectId, boolean pinned) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PINNED, pinned ? 1 : 0);
        db.update(TABLE_NAME, values, COL_PROJECT_ID + "=?", new String[]{String.valueOf(projectId)});
    }

    public void deleteProject(int projectId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, COL_PROJECT_ID + "=?", new String[]{String.valueOf(projectId)});
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }

    public static class ProjectRecord {
        public int id;
        public String name;
        public String schemaKeys;
        public boolean isSpecial;
        public boolean pinned;
    }
}