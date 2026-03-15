package com.flashnote.auth.dto;

public class UserInfo {
    private Long id;
    private String username;
    private String email;
    private String nickname;
    private String avatar;

    public UserInfo() {
    }

    public UserInfo(Long id, String username, String email, String nickname, String avatar) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.nickname = nickname;
        this.avatar = avatar;
    }

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
