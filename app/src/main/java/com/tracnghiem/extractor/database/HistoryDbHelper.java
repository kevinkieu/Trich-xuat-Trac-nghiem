package com.tracnghiem.extractor.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.tracnghiem.extractor.model.HistoryItem;

import java.util.ArrayList;
import java.util.List;

public class HistoryDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "extraction_history.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_HISTORY = "history";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_IMAGE_COUNT = "image_count";

    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_HISTORY + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    COLUMN_TITLE + " TEXT, " +
                    COLUMN_CONTENT + " TEXT NOT NULL, " +
                    COLUMN_IMAGE_COUNT + " INTEGER DEFAULT 0)";

    public HistoryDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }

    public long insertHistory(String title, String content, int imageCount) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_CONTENT, content);
        values.put(COLUMN_IMAGE_COUNT, imageCount);
        return db.insert(TABLE_HISTORY, null, values);
    }

    public List<HistoryItem> getAllHistory() {
        List<HistoryItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_HISTORY,
                null,
                null,
                null,
                null,
                null,
                COLUMN_TIMESTAMP + " DESC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE));
                String content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT));
                int imageCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_COUNT));

                list.add(new HistoryItem(id, timestamp, title, content, imageCount));
            }
            cursor.close();
        }
        return list;
    }

    public void deleteHistory(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_HISTORY, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void clearAllHistory() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_HISTORY, null, null);
    }
}
