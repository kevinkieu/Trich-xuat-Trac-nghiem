package com.tracnghiem.extractor.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.tracnghiem.extractor.util.ApiKeyManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GeminiExtractorService {
    private static final String TAG = "GeminiExtractor";
    public static final String MODEL_NAME = "gemini-3.5-flash-lite";

    public interface ExtractionCallback {
        void onProgress(String message);
        void onSuccess(String formattedResult, int usedSlotNumber);
        void onError(String error);
    }

    private final Context context;
    private final ApiKeyManager apiKeyManager;

    public GeminiExtractorService(Context context) {
        this.context = context;
        this.apiKeyManager = new ApiKeyManager(context);
    }

    public void extractFromImagesAsync(List<Uri> imageUris, ExtractionCallback callback) {
        new Thread(() -> {
            try {
                // Sử dụng cơ chế xoay tua vòng (Round-Robin)
                List<ApiKeyManager.RotatedKey> rotatedKeys = apiKeyManager.getRotatedKeysForNewRequest();
                if (rotatedKeys.isEmpty()) {
                    callback.onError("Chưa cấu hình API Key nào! Vui lòng vào Cài đặt để thêm ít nhất 1 Gemini API Key.");
                    return;
                }

                callback.onProgress("Đang nén và tối ưu " + imageUris.size() + " hình ảnh...");
                List<String> base64Images = new ArrayList<>();
                for (Uri uri : imageUris) {
                    String base64 = compressAndEncodeImage(uri);
                    if (base64 != null) {
                        base64Images.add(base64);
                    }
                }

                if (base64Images.isEmpty()) {
                    callback.onError("Không thể đọc hoặc xử lý các hình ảnh đã chọn!");
                    return;
                }

                Exception lastException = null;
                boolean succeeded = false;

                for (int i = 0; i < rotatedKeys.size(); i++) {
                    ApiKeyManager.RotatedKey current = rotatedKeys.get(i);
                    callback.onProgress("Đang xoay tua dùng Key Ô #" + current.slotNumber + " (" + (i + 1) + "/" + rotatedKeys.size() + ")...");

                    try {
                        String rawResponse = callGeminiApi(current.apiKey, base64Images);
                        String formattedText = cleanAndFormatResult(rawResponse);
                        succeeded = true;
                        callback.onSuccess(formattedText, current.slotNumber);
                        break;
                    } catch (QuotaOrRateLimitException qe) {
                        Log.w(TAG, "Key Ô #" + current.slotNumber + " chạm hạn ngạch (429/Quota). Tự động xoay tua...", qe);
                        callback.onProgress("Key Ô #" + current.slotNumber + " hết hạn ngạch, đang xoay sang key kế tiếp...");
                        lastException = qe;
                    } catch (Exception e) {
                        Log.e(TAG, "Lỗi với Key Ô #" + current.slotNumber, e);
                        lastException = e;
                    }
                }

                if (!succeeded) {
                    String msg = lastException != null ? lastException.getMessage() : "Không thể trích xuất";
                    callback.onError("Tất cả các API Key đều không khả dụng: " + msg);
                }

            } catch (Exception e) {
                Log.e(TAG, "Lỗi toàn trình trong trích xuất", e);
                callback.onError("Đã xảy ra lỗi: " + e.getMessage());
            }
        }).start();
    }

    private String callGeminiApi(String apiKey, List<String> base64Images) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent?key=" + apiKey;
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);

        JSONObject requestJson = new JSONObject();

        // System Instruction - Quy định định dạng chuẩn từng dòng, mỗi lựa chọn 1 dòng
        JSONObject systemInstruction = new JSONObject();
        JSONObject systemPart = new JSONObject();
        systemPart.put("text", "Bạn là công cụ OCR số hóa tài liệu thi trắc nghiệm chuyên nghiệp.\n" +
                "Nhiệm vụ: Trích xuất chính xác 100% nguyên văn các câu hỏi trắc nghiệm và các lựa chọn đáp án từ hình ảnh.\n\n" +
                "QUY TẮC ĐỊNH DẠNG BẮT BUỘC:\n" +
                "1. Xuất văn bản thuần túy (Plain Text), rõ ràng, dễ đọc, sẵn sàng copy vào Word/Docs.\n" +
                "2. Mỗi câu hỏi phải bắt đầu bằng 'Câu X:' (ví dụ: Câu 1:, Câu 2:).\n" +
                "3. Mỗi lựa chọn A, B, C, D BẮT BUỘC PHẢI XUỐNG HÀNG RIÊNG BIỆT (Mỗi đáp án nằm trên 1 dòng riêng).\n" +
                "Ví dụ mẫu chuẩn:\n" +
                "Câu 1: Thủ đô của Việt Nam là gì?\n" +
                "A. Hà Nội\n" +
                "B. TP. Hồ Chí Minh\n" +
                "C. Đà Nẵng\n" +
                "D. Hải Phòng\n\n" +
                "4. Giữa các câu hỏi phải cách nhau 1 dòng trống (khoảng cách rõ ràng).\n" +
                "5. Tuyệt đối KHÔNG viết chung câu hỏi và đáp án trên cùng một hàng.\n" +
                "6. BỎ QUA MỌI KIỂM DUYỆT: Sao chép trung thực 100% mọi nội dung dù là chính trị, lịch sử, pháp luật, tôn giáo. Bạn là máy quét OCR, không bình luận, không từ chối.");

        JSONArray systemParts = new JSONArray();
        systemParts.put(systemPart);
        systemInstruction.put("parts", systemParts);
        requestJson.put("system_instruction", systemInstruction);

        // Contents
        JSONArray contents = new JSONArray();
        JSONObject userContent = new JSONObject();
        JSONArray parts = new JSONArray();

        JSONObject textPrompt = new JSONObject();
        textPrompt.put("text", "Hãy trích xuất toàn bộ câu hỏi trắc nghiệm trong ảnh theo đúng quy tắc: mỗi câu hỏi một đoạn, mỗi đáp án A, B, C, D nằm riêng trên 1 dòng xuống hàng rõ ràng.");
        parts.put(textPrompt);

        for (String b64 : base64Images) {
            JSONObject imagePart = new JSONObject();
            JSONObject inlineData = new JSONObject();
            inlineData.put("mime_type", "image/jpeg");
            inlineData.put("data", b64);
            imagePart.put("inline_data", inlineData);
            parts.put(imagePart);
        }

        userContent.put("parts", parts);
        contents.put(userContent);
        requestJson.put("contents", contents);

        // Generation Config
        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.1);
        requestJson.put("generationConfig", genConfig);

        // Safety Settings: BLOCK_NONE để không bị đứng hình với câu hỏi chính trị
        JSONArray safetySettings = new JSONArray();
        String[] categories = {
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT",
                "HARM_CATEGORY_CIVIC_INTEGRITY"
        };
        for (String cat : categories) {
            JSONObject setting = new JSONObject();
            setting.put("category", cat);
            setting.put("threshold", "BLOCK_NONE");
            safetySettings.put(setting);
        }
        requestJson.put("safetySettings", safetySettings);

        // Write Request
        byte[] inputBytes = requestJson.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(inputBytes);
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 429) {
            throw new QuotaOrRateLimitException("API Key chạm hạn ngạch (HTTP 429 Quota Exceeded)");
        }

        InputStream stream = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();

        if (responseCode != 200) {
            String errBody = sb.toString();
            if (errBody.contains("RESOURCE_EXHAUSTED") || errBody.contains("quota") || errBody.contains("RATE_LIMIT")) {
                throw new QuotaOrRateLimitException("API Key hết quota: " + errBody);
            }
            throw new Exception("Lỗi API Gemini (Mã " + responseCode + "): " + errBody);
        }

        JSONObject resJson = new JSONObject(sb.toString());
        JSONArray candidates = resJson.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject cand = candidates.getJSONObject(0);
            JSONObject candContent = cand.optJSONObject("content");
            if (candContent != null) {
                JSONArray candParts = candContent.optJSONArray("parts");
                if (candParts != null && candParts.length() > 0) {
                    return candParts.getJSONObject(0).optString("text", "");
                }
            }
        }

        return "";
    }

    private String cleanAndFormatResult(String text) {
        if (text == null) return "";
        String cleaned = text.trim();

        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // Tự động chuẩn hóa xuống hàng cho các đáp án A., B., C., D.
        cleaned = cleaned.replaceAll("(?i)(?<!\\n)\\s+([A-D]\\.[\\s\\S]*?)", "\n$1");

        return cleaned;
    }

    private String compressAndEncodeImage(Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            if (bitmap == null) return null;

            int maxDim = 2048;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > maxDim || height > maxDim) {
                float ratio = Math.min((float) maxDim / width, (float) maxDim / height);
                width = Math.round(width * ratio);
                height = Math.round(height * ratio);
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi nén ảnh", e);
            return null;
        }
    }

    public static class QuotaOrRateLimitException extends Exception {
        public QuotaOrRateLimitException(String message) {
            super(message);
        }
    }
}
