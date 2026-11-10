package com.tracnghiem.extractor.model;

public class HistoryItem {
    private long id;
    private long timestamp;
    private String title;
    private String content;
    private int imageCount;

    public HistoryItem(long id, long timestamp, String title, String content, int imageCount) {
        this.id = id;
        this.timestamp = timestamp;
        this.title = title;
        this.content = content;
        this.imageCount = imageCount;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getImageCount() {
        return imageCount;
    }

    public void setImageCount(int imageCount) {
        this.imageCount = imageCount;
    }
}
