package com.tracnghiem.extractor;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.tracnghiem.extractor.adapter.HistoryAdapter;
import com.tracnghiem.extractor.database.HistoryDbHelper;
import com.tracnghiem.extractor.databinding.ActivityHistoryBinding;
import com.tracnghiem.extractor.model.HistoryItem;

import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_CONTENT = "selected_history_content";

    private ActivityHistoryBinding binding;
    private HistoryDbHelper dbHelper;
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = new HistoryDbHelper(this);

        binding.topAppBar.setNavigationOnClickListener(v -> finish());
        binding.topAppBar.inflateMenu(R.menu.menu_history);
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_history) {
                confirmClearAll();
                return true;
            }
            return false;
        });

        adapter = new HistoryAdapter(this, new HistoryAdapter.OnHistoryActionListener() {
            @Override
            public void onDelete(HistoryItem item) {
                dbHelper.deleteHistory(item.getId());
                loadHistoryData();
                Toast.makeText(HistoryActivity.this, "Đã xóa mục lịch sử", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSelect(HistoryItem item) {
                Intent result = new Intent();
                result.putExtra(EXTRA_SELECTED_CONTENT, item.getContent());
                setResult(RESULT_OK, result);
                finish();
            }
        });

        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerHistory.setAdapter(adapter);

        loadHistoryData();
    }

    private void loadHistoryData() {
        List<HistoryItem> items = dbHelper.getAllHistory();
        adapter.setItems(items);
        if (items.isEmpty()) {
            binding.layoutEmpty.setVisibility(View.VISIBLE);
            binding.recyclerHistory.setVisibility(View.GONE);
        } else {
            binding.layoutEmpty.setVisibility(View.GONE);
            binding.recyclerHistory.setVisibility(View.VISIBLE);
        }
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa toàn bộ lịch sử trích xuất?")
                .setPositiveButton("Xóa tất cả", (dialog, which) -> {
                    dbHelper.clearAllHistory();
                    loadHistoryData();
                    Toast.makeText(this, "Đã xóa toàn bộ lịch sử", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}
