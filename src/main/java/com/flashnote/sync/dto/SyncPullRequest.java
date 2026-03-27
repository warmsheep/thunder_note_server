package com.flashnote.sync.dto;

public class SyncPullRequest {
    private String lastMessageCreatedAt;

    public String getLastMessageCreatedAt() {
        return lastMessageCreatedAt;
    }

    public void setLastMessageCreatedAt(String lastMessageCreatedAt) {
        this.lastMessageCreatedAt = lastMessageCreatedAt;
    }
}
