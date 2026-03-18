package com.flashnote.user.dto;

public class ContactSearchUserDto {
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String relationStatus;

    public ContactSearchUserDto() {
    }

    public ContactSearchUserDto(Long userId, String username, String nickname, String avatar, String relationStatus) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.avatar = avatar;
        this.relationStatus = relationStatus;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getRelationStatus() {
        return relationStatus;
    }

    public void setRelationStatus(String relationStatus) {
        this.relationStatus = relationStatus;
    }
}
