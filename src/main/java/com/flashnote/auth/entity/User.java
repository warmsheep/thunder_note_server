package com.flashnote.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("users")
@Getter
@Setter
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private String password;
    private String nickname;
    private String avatar;
    private String gestureLockCiphertext;
    private String gestureLockNonce;
    private String gestureLockKdfParams;
    private String gestureLockVersion;
    private LocalDateTime gestureLockUpdatedAt;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
