package com.flashnote.message.dto;

import java.util.List;

/**
 * D1-W22-03 多媒体卡片新建请求。
 *
 * 用于客户端从零创建一条 mediaType=COMPOSITE 的卡片消息：
 *   - 卡片标题（必填，maxLength 50）
 *   - 卡片正文文本（可选）
 *   - 目标会话二选一：flashNoteId 或 receiverId
 *   - items：1~9 个媒体附件，每个 item 的 mediaUrl 必须是当前用户已上传的对象名
 *     （objectName 形如 "<userId>/<uuid>.<ext>"，由 FileServiceImpl 生成）
 *
 * 与 {@link MessageMergeRequest} 的差异：
 *   - merge 是把已有消息合并成卡片（items 通过 messageId 引用历史消息）
 *   - composite 是直接用客户端预上传的媒体文件创建新卡片，无中间消息
 */
public class CompositeMessageRequest {
    private String title;
    private String content;
    private Long flashNoteId;
    private Long receiverId;
    private List<Item> items;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getFlashNoteId() { return flashNoteId; }
    public void setFlashNoteId(Long flashNoteId) { this.flashNoteId = flashNoteId; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public static class Item {
        private String type;          // image / video / audio / file
        private String mediaUrl;      // objectName，必须以 "<currentUserId>/" 开头
        private String thumbnailUrl;
        private String fileName;
        private Long fileSize;
        private String content;       // 可选 caption

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getMediaUrl() { return mediaUrl; }
        public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

        public String getThumbnailUrl() { return thumbnailUrl; }
        public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
