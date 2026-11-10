package com.tracnghiem.extractor.model;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ExamQuestion {
    private String questionNumber;
    private String questionText;
    private List<String> options;
    private String rawText;

    public ExamQuestion(String questionNumber, String questionText, List<String> options, String rawText) {
        this.questionNumber = questionNumber;
        this.questionText = questionText;
        this.options = options != null ? options : new ArrayList<>();
        this.rawText = rawText;
    }

    public String getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(String questionNumber) {
        this.questionNumber = questionNumber;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public String getRawText() {
        if (rawText != null && !rawText.trim().isEmpty()) {
            return rawText;
        }
        // Fallback build formatted text
        StringBuilder sb = new StringBuilder();
        if (questionNumber != null && !questionNumber.isEmpty()) {
            sb.append("Câu ").append(questionNumber).append(": ");
        }
        sb.append(questionText).append("\n");
        for (String opt : options) {
            sb.append(opt).append("\n");
        }
        return sb.toString().trim();
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public static ExamQuestion fromJson(JSONObject obj) {
        String number = obj.optString("number", "");
        String question = obj.optString("question", "");
        String raw = obj.optString("raw_text", "");
        List<String> opts = new ArrayList<>();
        JSONArray arr = obj.optJSONArray("options");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                opts.add(arr.optString(i));
            }
        }
        return new ExamQuestion(number, question, opts, raw);
    }
}
