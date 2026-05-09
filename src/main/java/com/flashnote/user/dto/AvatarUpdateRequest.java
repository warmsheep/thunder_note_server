package com.flashnote.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 头像更新请求 DTO
 * 替代原来的 Map<String, String>，提供类型安全和输入校验
 *
 * D1-W23-01 改造：取消 @URL 强校验，允许 emoji 作为头像值。
 *   - URL：file 上传后包成 {origin}/api/files/download?objectName=... 的合法 URL
 *   - Emoji：AvatarPickerDialog 中选中的 emoji 字符串（如 '💼'、'🌟'），
 *     AuthenticatedAvatar 会走 fallback 分支直接渲染
 *   - Service 层再做更细粒度的内容合法性校验（长度与黑名单）
 */
public class AvatarUpdateRequest {

    @NotBlank(message = "头像不能为空")
    @Size(max = 2048, message = "头像长度不能超过 2048 个字符")
    private String avatar;

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
