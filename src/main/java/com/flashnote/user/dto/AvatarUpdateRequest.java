package com.flashnote.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * 头像更新请求 DTO
 * 替代原来的 Map<String, String>，提供类型安全和输入校验
 */
public class AvatarUpdateRequest {

    @NotBlank(message = "头像 URL 不能为空")
    @Size(max = 2048, message = "头像 URL 长度不能超过 2048 个字符")
    @URL(message = "头像 URL 格式不正确")
    private String avatar;

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
