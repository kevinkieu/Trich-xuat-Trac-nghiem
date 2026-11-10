# Trích Xuất Trắc Nghiệm (Android App)

Ứng dụng Android hỗ trợ trích xuất nguyên văn các câu hỏi trắc nghiệm và các lựa chọn đáp án từ hình ảnh chụp màn hình / ảnh chụp đề thi bằng mô hình **Gemini 3.5 Flash Lite**.

## Tính năng nổi bật
- **Bỏ qua kiểm duyệt (Anti-censorship):** Cấu hình `BLOCK_NONE` cho toàn bộ các danh mục an toàn, trích xuất nguyên văn các nội dung chính trị, pháp luật, lịch sử hay triết học mà không bị từ chối hay đứng hình.
- **Xoay tua API Key tự động (Round-Robin & Fallback):**
  - Cấu hình 5 ô nhập Gemini API Key.
  - Mỗi lần bấm trích xuất, ứng dụng tự động tịnh tiến sang key kế tiếp (Key 1 -> Key 2 -> ... -> Key 5) để chia đều hạn ngạch sử dụng, tránh tốn quota của một key.
  - Khi một key chạm giới hạn `HTTP 429` (Quota Limit), app tự động chuyển ngay sang key dự phòng tiếp theo.
- **Lưu lịch sử trích xuất (SQLite):**
  - Tự động lưu lại các đợt trích xuất thành công kèm thời gian và số lượng ảnh.
  - Xem lại, sao chép hoặc khôi phục nội dung cũ về màn hình chính bất cứ lúc nào.
- **Giao diện chuẩn hóa:**
  - Gộp chung toàn bộ nội dung vào 1 khung văn bản duy nhất để dễ theo dõi và chỉnh sửa.
  - Mỗi câu hỏi và từng đáp án A, B, C, D đều xuống hàng ngay ngắn, trật tự, chuẩn form Word / Docs.
  - 1 chạm sao chép toàn bộ văn bản vào clipboard.
- **Tương thích:** Tối ưu hoàn toàn cho Android 14, 15 và **Android 16** (Photo Picker không cần quyền bộ nhớ).
