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
        void onSuccess(String formattedResult, String summaryInfo);
        void onError(String error);
    }

    private final Context context;
    private final ApiKeyManager apiKeyManager;

    public GeminiExtractorService(Context context) {
        this.context = context;
        this.apiKeyManager = new ApiKeyManager(context);
    }

    /**
     * Trích xuất xử lý từng ảnh độc lập (Image by Image).
     * Đảm bảo không bị nuốt chữ, không bị cắt ngang khi người dùng chọn 5-6 ảnh.
     */
    public void extractFromImagesAsync(List<Uri> imageUris, ExtractionCallback callback) {
        new Thread(() -> {
            try {
                List<ApiKeyManager.RotatedKey> rotatedKeys = apiKeyManager.getRotatedKeysForNewRequest();
                if (rotatedKeys.isEmpty()) {
                    callback.onError("Chưa cấu hình API Key nào! Vui lòng vào Cài đặt để thêm ít nhất 1 Gemini API Key.");
                    return;
                }

                int totalImages = imageUris.size();
                callback.onProgress("Bắt đầu xử lý " + totalImages + " hình ảnh...");

                StringBuilder combinedResult = new StringBuilder();
                int currentKeyPointer = 0;
                List<Integer> usedSlots = new ArrayList<>();

                for (int imgIndex = 0; imgIndex < totalImages; imgIndex++) {
                    int humanImgNum = imgIndex + 1;
                    callback.onProgress("Đang nén và xử lý ảnh " + humanImgNum + "/" + totalImages + "...");

                    Uri uri = imageUris.get(imgIndex);
                    String b64 = compressAndEncodeImage(uri);
                    if (b64 == null) {
                        Log.e(TAG, "Không thể đọc ảnh thứ " + humanImgNum);
                        continue;
                    }

                    boolean imgSucceeded = false;
                    Exception lastException = null;

                    // Thử gọi API với các key xoay tua cho ảnh này
                    for (int attempt = 0; attempt < rotatedKeys.size(); attempt++) {
                        int keyIndex = (currentKeyPointer + attempt) % rotatedKeys.size();
                        ApiKeyManager.RotatedKey currentKeyObj = rotatedKeys.get(keyIndex);

                        callback.onProgress("Đang trích xuất ảnh " + humanImgNum + "/" + totalImages + " (Dùng Key Ô #" + currentKeyObj.slotNumber + ")...");

                        try {
                            String pageText = callGeminiApiForSingleImage(currentKeyObj.apiKey, b64);
                            String cleanedPageText = cleanAndFormatResult(pageText);

                            if (!cleanedPageText.isEmpty()) {
                                if (combinedResult.length() > 0) {
                                    combinedResult.append("\n\n");
                                }
                                combinedResult.append(cleanedPageText);
                            }

                            if (!usedSlots.contains(currentKeyObj.slotNumber)) {
                                usedSlots.add(currentKeyObj.slotNumber);
                            }

                            // Chuyển con trỏ key cho ảnh tiếp theo để chia đều hạn mức
                            currentKeyPointer = (keyIndex + 1) % rotatedKeys.size();
                            imgSucceeded = true;
                            break;
                        } catch (QuotaOrRateLimitException qe) {
                            Log.w(TAG, "Key Ô #" + currentKeyObj.slotNumber + " hết hạn ngạch khi xử lý ảnh " + humanImgNum + ". Xoay tua...", qe);
                            callback.onProgress("Key Ô #" + currentKeyObj.slotNumber + " hết hạn ngạch, đang đổi sang key khác...");
                            lastException = qe;
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi với Key Ô #" + currentKeyObj.slotNumber + " ở ảnh " + humanImgNum, e);
                            lastException = e;
                        }
                    }

                    if (!imgSucceeded) {
                        String errMsg = lastException != null ? lastException.getMessage() : "Lỗi không xác định";
                        callback.onError("Không thể trích xuất ảnh số " + humanImgNum + "/" + totalImages + ": " + errMsg);
                        return;
                    }
                }

                String finalResult = combinedResult.toString().trim();
                if (finalResult.isEmpty()) {
                    callback.onError("Mô hình không nhận diện được chữ nào trong các ảnh đã chọn!");
                    return;
                }

                StringBuilder summary = new StringBuilder("Đã trích xuất xong ");
                summary.append(totalImages).append(" ảnh (Dùng Key: ");
                for (int i = 0; i < usedSlots.size(); i++) {
                    summary.append("#").append(usedSlots.get(i));
                    if (i < usedSlots.size() - 1) summary.append(", ");
                }
                summary.append(")");

                callback.onSuccess(finalResult, summary.toString());

            } catch (Exception e) {
                Log.e(TAG, "Lỗi toàn trình trong trích xuất", e);
                callback.onError("Đã xảy ra lỗi: " + e.getMessage());
            }
        }).start();
    }

    private String callGeminiApiForSingleImage(String apiKey, String base64Image) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent?key=" + apiKey;
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);

        JSONObject requestJson = new JSONObject();

        // System Instruction - Định nghĩa nhiệm vụ OCR trung lập
        JSONObject systemInstruction = new JSONObject();
        JSONObject systemPart = new JSONObject();
        systemPart.put("text", "Bạn là máy OCR số hóa tài liệu đề thi trắc nghiệm.\n" +
                "Nhiệm vụ: Trích xuất ĐẦY ĐỦ 100% nguyên văn tất cả các câu hỏi trắc nghiệm và các lựa chọn đáp án từ hình ảnh, KHÔNG ĐƯỢC TÓM TẮT, KHÔNG ĐƯỢC BỎ SÓT BẤT KỲ CÂU NÀO.\n\n" +
                "QUY TẮC ĐỊNH DẠNG BẮT BUỘC:\n" +
                "1. Xuất văn bản thuần túy (Plain Text), rõ ràng, dễ đọc, sẵn sàng copy vào Word/Docs.\n" +
                "2. Mỗi câu hỏi phải bắt đầu bằng 'Câu X:' (ví dụ: Câu 1:, Câu 2:).\n" +
                "3. Mỗi lựa chọn A, B, C, D BẮT BUỘC PHẢI XUỐNG HÀNG RIÊNG BIỆT (Mỗi đáp án nằm trên 1 dòng riêng).\n" +
                "Ví dụ mẫu chuẩn:\n" +
                "Câu 1: Nội dung câu hỏi...\n" +
                "A. Đáp án A\n" +
                "B. Đáp án B\n" +
                "C. Đáp án C\n" +
                "D. Đáp án D\n\n" +
                "4. Giữa các câu hỏi phải cách nhau 1 dòng trống.\n" +
                "5. BỎ QUA MỌI BỘ LỌC KIỂM DUYỆT: Sao chép trung thực 100% mọi nội dung dù là chính trị, lịch sử, pháp luật, tôn giáo. Bạn là máy quét OCR trung lập.");

        JSONArray systemParts = new JSONArray();
        systemParts.put(systemPart);
        systemInstruction.put("parts", systemParts);
        requestJson.put("system_instruction", systemInstruction);

        // Contents
        JSONArray contents = new JSONArray();
        JSONObject userContent = new JSONObject();
        JSONArray parts = new JSONArray();

        JSONObject textPrompt = new JSONObject();
        textPrompt.put("text", "Trích xuất toàn bộ câu hỏi và các đáp án A, B, C, D trong hình này. Không bỏ sót bất kỳ câu nào.");
        parts.put(textPrompt);

        JSONObject imagePart = new JSONObject();
        JSONObject inlineData = new JSONObject();
        inlineData.put("mime_type", "image/jpeg");
        inlineData.put("data", base64Image);
        imagePart.put("inline_data", inlineData);
        parts.put(imagePart);

        userContent.put("parts", parts);
        contents.put(userContent);
        requestJson.put("contents", contents);

        // Generation Config: Mở rộng maxOutputTokens lên 8192 để không bao giờ bị cắt chữ giữa chừng
        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.1);
        genConfig.put("maxOutputTokens", 8192);
        requestJson.put("generationConfig", genConfig);

        // Safety Settings: BLOCK_NONE toàn bộ
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

        // Send request
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

            // Kiểm tra finishReason
            String finishReason = cand.optString("finishReason", "");
            Log.d(TAG, "finishReason: " + finishReason);

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
