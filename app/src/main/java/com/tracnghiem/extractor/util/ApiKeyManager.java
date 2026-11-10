package com.tracnghiem.extractor.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public class ApiKeyManager {
    private static final String PREF_NAME = "gemini_api_prefs";
    private static final String KEY_PREFIX = "api_key_";
    private static final String PREF_LAST_USED_INDEX = "last_used_key_index";
    public static final int KEY_COUNT = 5;

    private final SharedPreferences prefs;

    public ApiKeyManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveKey(int index, String key) {
        if (index >= 0 && index < KEY_COUNT) {
            prefs.edit().putString(KEY_PREFIX + index, key != null ? key.trim() : "").apply();
        }
    }

    public String getKey(int index) {
        if (index >= 0 && index < KEY_COUNT) {
            return prefs.getString(KEY_PREFIX + index, "");
        }
        return "";
    }

    public List<String> getAllKeys() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < KEY_COUNT; i++) {
            list.add(getKey(i));
        }
        return list;
    }

    public List<String> getValidConfiguredKeys() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < KEY_COUNT; i++) {
            String k = getKey(i);
            if (k != null && !k.trim().isEmpty()) {
                list.add(k.trim());
            }
        }
        return list;
    }

    public boolean hasAnyKey() {
        for (int i = 0; i < KEY_COUNT; i++) {
            String k = getKey(i);
            if (k != null && !k.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lấy danh sách các key hợp lệ nhưng được sắp xếp theo vòng xoay tua (Round-Robin).
     * Mỗi lần gọi hàm này, vị trí bắt đầu sẽ tịnh tiến sang key kế tiếp (0 -> 1 -> 2 -> ... -> 0),
     * giúp chia đều quota giữa các keys, tránh việc key đầu tiên bị dùng liên tục.
     */
    public synchronized List<RotatedKey> getRotatedKeysForNewRequest() {
        List<RotatedKey> allConfigured = new ArrayList<>();
        for (int i = 0; i < KEY_COUNT; i++) {
            String k = getKey(i);
            if (k != null && !k.trim().isEmpty()) {
                allConfigured.add(new RotatedKey(i + 1, k.trim()));
            }
        }

        if (allConfigured.isEmpty()) {
            return new ArrayList<>();
        }

        int lastIndex = prefs.getInt(PREF_LAST_USED_INDEX, -1);
        int startIndex = (lastIndex + 1) % allConfigured.size();
        prefs.edit().putInt(PREF_LAST_USED_INDEX, startIndex).apply();

        List<RotatedKey> rotated = new ArrayList<>();
        for (int i = 0; i < allConfigured.size(); i++) {
            int targetIdx = (startIndex + i) % allConfigured.size();
            rotated.add(allConfigured.get(targetIdx));
        }
        return rotated;
    }

    public static class RotatedKey {
        public final int slotNumber; // Số thứ tự ô từ 1 -> 5
        public final String apiKey;

        public RotatedKey(int slotNumber, String apiKey) {
            this.slotNumber = slotNumber;
            this.apiKey = apiKey;
        }
    }
}
