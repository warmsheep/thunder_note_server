package com.flashnote.favorite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("favorite_messages")
@Getter
@Setter
public class FavoriteMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long messageId;
    private LocalDateTime createdAt;
}
