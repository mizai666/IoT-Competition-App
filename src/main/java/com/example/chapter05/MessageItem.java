package com.example.chapter05;

public class MessageItem {
    public int id;
    public String json;
    public String time;
    public long timestamp;  // 新增：毫秒时间戳

    public MessageItem() { }

    public MessageItem(int id, String json, String time, long timestamp) {
        this.id = id;
        this.json = json;
        this.time = time;
        this.timestamp = timestamp;
    }
}