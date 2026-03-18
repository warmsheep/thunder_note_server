package com.flashnote.user.dto;

public class FriendRequestDto {
    private Long requestId;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;

    public FriendRequestDto() {
    }

    public FriendRequestDto(Long requestId, Long userId, String username, String nickname, String avatar) {
        this.requestId = requestId;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.avatar = avatar;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
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
}
