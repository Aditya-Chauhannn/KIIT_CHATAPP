package com.example.kiit_chatapp.models;

public class Message {
    private String messageId;
    private String senderId;
    private String senderName; // <-- Add this
    private String text;
    private String type;
    private String timeStamp;
    private String fileUrl;

    public Message() {}

    public Message(String messageId, String senderId, String senderName, String text, String type, String timeStamp, String fileUrl) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.type = type;
        this.timeStamp = timeStamp;
        this.fileUrl = fileUrl;
    }

    // getters and setters for all fields (including senderName)

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }
}
