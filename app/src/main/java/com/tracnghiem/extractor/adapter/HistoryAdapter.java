package com.tracnghiem.extractor.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.tracnghiem.extractor.R;
import com.tracnghiem.extractor.model.HistoryItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnHistoryActionListener {
        void onDelete(HistoryItem item);
        void onSelect(HistoryItem item);
    }

    private final Context context;
    private final List<HistoryItem> items = new ArrayList<>();
    private final OnHistoryActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

    public HistoryAdapter(Context context, OnHistoryActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<HistoryItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = items.get(position);

        holder.tvHistoryDate.setText(dateFormat.format(new Date(item.getTimestamp())));
        holder.tvHistoryImageCount.setText(item.getImageCount() + " ảnh");
        holder.tvHistoryPreview.setText(item.getContent());

        holder.btnCopyHistory.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Bộ câu hỏi trắc nghiệm", item.getContent());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Đã sao chép nội dung vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show();
        });

        holder.btnDeleteHistory.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(item);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSelect(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvHistoryDate;
        final TextView tvHistoryImageCount;
        final TextView tvHistoryPreview;
        final MaterialButton btnDeleteHistory;
        final MaterialButton btnCopyHistory;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHistoryDate = itemView.findViewById(R.id.tvHistoryDate);
            tvHistoryImageCount = itemView.findViewById(R.id.tvHistoryImageCount);
            tvHistoryPreview = itemView.findViewById(R.id.tvHistoryPreview);
            btnDeleteHistory = itemView.findViewById(R.id.btnDeleteHistory);
            btnCopyHistory = itemView.findViewById(R.id.btnCopyHistory);
        }
    }
}
