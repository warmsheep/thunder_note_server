package com.flashnote.flashnote.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("flash_notes")
@Getter
@Setter
public class FlashNote {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String icon;
    private String content;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tags;
    @TableField(exist = false)
    private String latestMessage;
    @TableField("is_deleted")
    private Boolean deleted;
    @TableField("is_pinned")
    private Boolean pinned;
    @TableField("is_hidden")
    private Boolean hidden;
    @TableField("is_inbox")
    private Boolean inbox;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
