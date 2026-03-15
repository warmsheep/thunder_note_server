package com.flashnote.favorite.dto;

import java.time.LocalDateTime;

public class FavoriteMessageItem {
    private Long id;
    private Long messageId;
    private Long flashNoteId;
    private String flashNoteTitle;
    private String role;
    private String content;
    private LocalDateTime messageCreatedAt;
    private LocalDateTime favoritedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getFlashNoteId() {
        return flashNoteId;
    }

    public void setFlashNoteId(Long flashNoteId) {
        this.flashNoteId = flashNoteId;
    }

    public String getFlashNoteTitle() {
        return flashNoteTitle;
    }

    public void setFlashNoteTitle(String flashNoteTitle) {
        this.flashNoteTitle = flashNoteTitle;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getMessageCreatedAt() {
        return messageCreatedAt;
    }

    public void setMessageCreatedAt(LocalDateTime messageCreatedAt) {
        this.messageCreatedAt = messageCreatedAt;
    }

    public LocalDateTime getFavoritedAt() {
        return favoritedAt;
    }

    public void setFavoritedAt(LocalDateTime favoritedAt) {
        this.favoritedAt = favoritedAt;
    }
}
