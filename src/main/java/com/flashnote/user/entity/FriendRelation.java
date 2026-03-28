package com.flashnote.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("friend_relations")
@Getter
@Setter
public class FriendRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requesterId;
    private Long addresseeId;
    private String status;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
