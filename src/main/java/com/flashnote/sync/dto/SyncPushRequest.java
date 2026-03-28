package com.flashnote.sync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class SyncPushRequest {

    @Valid
    @JsonProperty("notes")
    private List<NotePushDto> notes;

    @Valid
    @JsonProperty("collections")
    private List<CollectionPushDto> collections;

    @Valid
    @JsonProperty("messages")
    private List<MessagePushDto> messages;

    @Valid
    @JsonProperty("favorites")
    private List<FavoritePushDto> favorites;

    public List<NotePushDto> getNotes() {
        return notes;
    }

    public void setNotes(List<NotePushDto> notes) {
        this.notes = notes;
    }

    public List<CollectionPushDto> getCollections() {
        return collections;
    }

    public void setCollections(List<CollectionPushDto> collections) {
        this.collections = collections;
    }

    public List<MessagePushDto> getMessages() {
        return messages;
    }

    public void setMessages(List<MessagePushDto> messages) {
        this.messages = messages;
    }

    public List<FavoritePushDto> getFavorites() {
        return favorites;
    }

    public void setFavorites(List<FavoritePushDto> favorites) {
        this.favorites = favorites;
    }

    public static class NotePushDto {
        @NotNull
        private Long id;

        private String title;

        private String content;

        private String tags;

        @JsonProperty("is_deleted")
        private Boolean isDeleted;

        @JsonProperty("deleted")
        private Boolean deleted;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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

        public String getTags() {
            return tags;
        }

        public void setTags(String tags) {
            this.tags = tags;
        }

        public Boolean getIsDeleted() {
            return isDeleted;
        }

        public void setIsDeleted(Boolean isDeleted) {
            this.isDeleted = isDeleted;
        }

        public Boolean getDeleted() {
            return deleted;
        }

        public void setDeleted(Boolean deleted) {
            this.deleted = deleted;
        }

        public boolean resolveDeleted() {
            if (isDeleted != null) return isDeleted;
            if (deleted != null) return deleted;
            return false;
        }
    }

    public static class CollectionPushDto {
        @NotNull
        private Long id;

        private String name;

        private String description;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class MessagePushDto {
        private Long id;

        @JsonProperty("clientRequestId")
        private String clientRequestId;

        private Long senderId;

        @JsonProperty("receiver_id")
        private Long receiverId;

        @JsonProperty("receiverId")
        private Long receiverIdAlt;

        private String content;

        @JsonProperty("read_status")
        private Boolean readStatus;

        @JsonProperty("readStatus")
        private Boolean readStatusAlt;

        @JsonProperty("flash_note_id")
        private Long flashNoteId;

        @JsonProperty("flashNoteId")
        private Long flashNoteIdAlt;

        private String role;

        @JsonProperty("mediaType")
        private String mediaType;

        @JsonProperty("mediaUrl")
        private String mediaUrl;

        @JsonProperty("mediaDuration")
        private Integer mediaDuration;

        @JsonProperty("thumbnailUrl")
        private String thumbnailUrl;

        @JsonProperty("fileName")
        private String fileName;

        @JsonProperty("fileSize")
        private Long fileSize;

        private String createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getClientRequestId() {
            return clientRequestId;
        }

        public void setClientRequestId(String clientRequestId) {
            this.clientRequestId = clientRequestId;
        }

        public Long getSenderId() {
            return senderId;
        }

        public void setSenderId(Long senderId) {
            this.senderId = senderId;
        }

        public Long getReceiverId() {
            return receiverId != null ? receiverId : receiverIdAlt;
        }

        public void setReceiverId(Long receiverId) {
            this.receiverId = receiverId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Boolean getReadStatus() {
            if (readStatus != null) return readStatus;
            return readStatusAlt;
        }

        public void setReadStatus(Boolean readStatus) {
            this.readStatus = readStatus;
        }

        public Long getFlashNoteId() {
            return flashNoteId != null ? flashNoteId : flashNoteIdAlt;
        }

        public void setFlashNoteId(Long flashNoteId) {
            this.flashNoteId = flashNoteId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getMediaType() {
            return mediaType;
        }

        public void setMediaType(String mediaType) {
            this.mediaType = mediaType;
        }

        public String getMediaUrl() {
            return mediaUrl;
        }

        public void setMediaUrl(String mediaUrl) {
            this.mediaUrl = mediaUrl;
        }

        public Integer getMediaDuration() {
            return mediaDuration;
        }

        public void setMediaDuration(Integer mediaDuration) {
            this.mediaDuration = mediaDuration;
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

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class FavoritePushDto {
        @JsonProperty("messageId")
        private Long messageId;

        @JsonProperty("message_id")
        private Long messageIdAlt;

        public Long getMessageId() {
            return messageId != null ? messageId : messageIdAlt;
        }

        public void setMessageId(Long messageId) {
            this.messageId = messageId;
        }
    }
}
