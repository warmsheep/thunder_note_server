package com.flashnote.message.entity;

import lombok.Data;

@Data
public class CardItem {
    private String type;
    private String content;
    private String url;
    private String thumbnailUrl;
    private String fileName;
    private Long fileSize;
    private Long originalMsgId;
    private Long senderId;
    private String role;
}
