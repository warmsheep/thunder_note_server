package com.flashnote.message.entity;

public class CardItem {
    private String type; // TEXT, IMAGE, VIDEO, FILE, AUDIO
    private String content;
    private String url;
    private String thumbnailUrl;
    private String fileName;
    private Long fileSize;
    private Long originalMsgId;
    private Long senderId;
    private String role;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getOriginalMsgId() {
        return originalMsgId;
    }

    public void setOriginalMsgId(Long originalMsgId) {
        this.originalMsgId = originalMsgId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
