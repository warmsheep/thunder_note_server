package com.flashnote.auth.dto;

import java.time.LocalDateTime;

public class GestureLockBackupResponse {
    private boolean configured;
    private String version;
    private LocalDateTime updatedAt;

    public GestureLockBackupResponse() {
    }

    public GestureLockBackupResponse(boolean configured, String version, LocalDateTime updatedAt) {
        this.configured = configured;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
