package com.tracnghiem.extractor;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.tracnghiem.extractor.database.HistoryDbHelper;
import com.tracnghiem.extractor.databinding.ActivityMainBinding;
import com.tracnghiem.extractor.service.GeminiExtractorService;
import com.tracnghiem.extractor.util.ApiKeyManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ApiKeyManager apiKeyManager;
    private GeminiExtractorService extractorService;
    private HistoryDbHelper dbHelper;

    private final List<Uri> selectedUris = new ArrayList<>();
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMediaLauncher;
    private ActivityResultLauncher<Intent> historyLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiKeyManager = new ApiKeyManager(this);
        extractorService = new GeminiExtractorService(this);
        dbHelper = new HistoryDbHelper(this);

        initViews();
        setupPhotoPicker();
        setupHistoryLauncher();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateKeyBadge();
    }

    private void initViews() {
        binding.topAppBar.inflateMenu(R.menu.menu_main);
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_history) {
                historyLauncher.launch(new Intent(this, HistoryActivity.class));
                return true;
            } else if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        // Pick Images
        View.OnClickListener pickClick = v -> {
            pickMultipleMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        };
        binding.cardPickImages.setOnClickListener(pickClick);
        binding.btnPickImages.setOnClickListener(pickClick);

        // Extract action
        binding.btnExtract.setOnClickListener(v -> startExtraction());

        // Copy All directly from the unified EditText
        binding.btnCopyAll.setOnClickListener(v -> {
            String fullText = binding.etResultText.getText() != null ? binding.etResultText.getText().toString().trim() : "";
            if (fullText.isEmpty()) {
                Toast.makeText(this, "Không có nội dung để sao chép", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Bộ câu hỏi trắc nghiệm", fullText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Đã sao chép toàn bộ văn bản vào bộ nhớ tạm!", Toast.LENGTH_LONG).show();
        });
    }

    private void updateKeyBadge() {
        int validCount = apiKeyManager.getValidConfiguredKeys().size();
        binding.tvActiveKeysBadge.setText(validCount + "/5 Keys");
    }

    private void setupPhotoPicker() {
        pickMultipleMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(20),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        selectedUris.clear();
                        selectedUris.addAll(uris);
                        binding.tvImageCount.setText("Đã chọn: " + selectedUris.size() + " ảnh");
                    }
                }
        );
    }

    private void setupHistoryLauncher() {
        historyLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String restoredContent = result.getData().getStringExtra(HistoryActivity.EXTRA_SELECTED_CONTENT);
                        if (restoredContent != null && !restoredContent.trim().isEmpty()) {
                            binding.cardResultContainer.setVisibility(View.VISIBLE);
                            binding.etResultText.setText(restoredContent);
                            Toast.makeText(this, "Đã khôi phục nội dung từ lịch sử!", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void startExtraction() {
        if (!apiKeyManager.hasAnyKey()) {
            Toast.makeText(this, "Bạn chưa thêm API Key! Vui lòng vào Cài đặt (nút góc trên).", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ít nhất 1 hình ảnh trước khi trích xuất!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        binding.layoutProgress.setVisibility(View.VISIBLE);
        binding.btnExtract.setEnabled(false);
        binding.cardResultContainer.setVisibility(View.GONE);
        binding.tvStatus.setText("Đang chuẩn bị trích xuất...");

        final int imgCount = selectedUris.size();

        extractorService.extractFromImagesAsync(selectedUris, new GeminiExtractorService.ExtractionCallback() {
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> binding.tvStatus.setText(message));
            }

            @Override
            public void onSuccess(String formattedResult, int usedSlotNumber) {
                runOnUiThread(() -> {
                    binding.layoutProgress.setVisibility(View.GONE);
                    binding.btnExtract.setEnabled(true);

                    // Hiển thị toàn bộ vào 1 khung văn bản duy nhất
                    binding.cardResultContainer.setVisibility(View.VISIBLE);
                    binding.etResultText.setText(formattedResult);

                    // Tự động lưu vào lịch sử SQLite
                    String firstLine = formattedResult.split("\n")[0];
                    if (firstLine.length() > 40) {
                        firstLine = firstLine.substring(0, 40) + "...";
                    }
                    dbHelper.insertHistory(firstLine, formattedResult, imgCount);

                    Toast.makeText(MainActivity.this, "Trích xuất thành công bằng Key Ô #" + usedSlotNumber + "! Đã lưu vào Lịch sử.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    binding.layoutProgress.setVisibility(View.GONE);
                    binding.btnExtract.setEnabled(true);
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
