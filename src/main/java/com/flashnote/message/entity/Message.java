package com.flashnote.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flashnote.message.typehandler.CardPayloadJsonbTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName(value = "messages", autoResultMap = true)
@Getter
@Setter
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Boolean readStatus;
    private Long flashNoteId;
    private String clientRequestId;
    private String role;
    private LocalDateTime createdAt;
    private String mediaType;
    private String mediaUrl;
    private Integer mediaDuration;
    private String thumbnailUrl;
    private String fileName;
    private Long fileSize;

    @TableField(typeHandler = CardPayloadJsonbTypeHandler.class)
    private CardPayload payload;
}
