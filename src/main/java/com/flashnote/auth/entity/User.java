package com.flashnote.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("users")
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getGestureLockCiphertext() {
        return gestureLockCiphertext;
    }

    public void setGestureLockCiphertext(String gestureLockCiphertext) {
        this.gestureLockCiphertext = gestureLockCiphertext;
    }

    public String getGestureLockNonce() {
        return gestureLockNonce;
    }

    public void setGestureLockNonce(String gestureLockNonce) {
        this.gestureLockNonce = gestureLockNonce;
    }

    public String getGestureLockKdfParams() {
        return gestureLockKdfParams;
    }

    public void setGestureLockKdfParams(String gestureLockKdfParams) {
        this.gestureLockKdfParams = gestureLockKdfParams;
    }

    public String getGestureLockVersion() {
        return gestureLockVersion;
    }

    public void setGestureLockVersion(String gestureLockVersion) {
        this.gestureLockVersion = gestureLockVersion;
    }

    public LocalDateTime getGestureLockUpdatedAt() {
        return gestureLockUpdatedAt;
    }

    public void setGestureLockUpdatedAt(LocalDateTime gestureLockUpdatedAt) {
        this.gestureLockUpdatedAt = gestureLockUpdatedAt;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
