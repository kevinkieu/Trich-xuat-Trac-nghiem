package com.tracnghiem.extractor.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.tracnghiem.extractor.R;
import com.tracnghiem.extractor.model.ExamQuestion;

import java.util.ArrayList;
import java.util.List;

public class ExamQuestionAdapter extends RecyclerView.Adapter<ExamQuestionAdapter.ViewHolder> {

    private final List<ExamQuestion> questions = new ArrayList<>();
    private final Context context;

    public ExamQuestionAdapter(Context context) {
        this.context = context;
    }

    public void setQuestions(List<ExamQuestion> newQuestions) {
        this.questions.clear();
        if (newQuestions != null) {
            this.questions.addAll(newQuestions);
        }
        notifyDataSetChanged();
    }

    public List<ExamQuestion> getQuestions() {
        return questions;
    }

    public String getAllFormattedText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            ExamQuestion q = questions.get(i);
            sb.append(q.getRawText()).append("\n\n");
        }
        return sb.toString().trim();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exam_question, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExamQuestion item = questions.get(position);

        String calculatedBadge = "Câu " + (position + 1);
        if (item.getQuestionNumber() != null && !item.getQuestionNumber().isEmpty()) {
            calculatedBadge = "Câu " + item.getQuestionNumber();
        }
        final String badgeText = calculatedBadge;
        holder.tvQuestionBadge.setText(badgeText);

        holder.etQuestionContent.setText(item.getRawText());

        holder.btnCopySingle.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Câu trắc nghiệm", holder.etQuestionContent.getText().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Đã sao chép " + badgeText, Toast.LENGTH_SHORT).show();
        });

        // Sync user edits back to question object
        holder.etQuestionContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION && adapterPos < questions.size()) {
                    questions.get(adapterPos).setRawText(s.toString());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvQuestionBadge;
        final EditText etQuestionContent;
        final MaterialButton btnCopySingle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuestionBadge = itemView.findViewById(R.id.tvQuestionBadge);
            etQuestionContent = itemView.findViewById(R.id.etQuestionContent);
            btnCopySingle = itemView.findViewById(R.id.btnCopySingle);
        }
    }
}
