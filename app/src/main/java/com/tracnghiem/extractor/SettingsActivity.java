package com.tracnghiem.extractor;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.tracnghiem.extractor.databinding.ActivitySettingsBinding;
import com.tracnghiem.extractor.util.ApiKeyManager;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private ApiKeyManager apiKeyManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiKeyManager = new ApiKeyManager(this);

        binding.topAppBar.setNavigationOnClickListener(v -> finish());

        // Load saved keys into 5 input boxes
        TextInputEditText[] editTexts = new TextInputEditText[]{
                binding.etKey1,
                binding.etKey2,
                binding.etKey3,
                binding.etKey4,
                binding.etKey5
        };

        for (int i = 0; i < editTexts.length; i++) {
            editTexts[i].setText(apiKeyManager.getKey(i));
        }

        binding.btnSaveKeys.setOnClickListener(v -> {
            int validCount = 0;
            for (int i = 0; i < editTexts.length; i++) {
                String key = editTexts[i].getText() != null ? editTexts[i].getText().toString().trim() : "";
                apiKeyManager.saveKey(i, key);
                if (!key.isEmpty()) {
                    validCount++;
                }
            }

            Toast.makeText(this, "Đã lưu thành công " + validCount + "/5 API Keys!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
