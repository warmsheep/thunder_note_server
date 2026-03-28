package com.flashnote.common.constant;

import java.util.Arrays;
import java.util.Set;

/**
 * 媒体类型枚举，统一管理消息媒体类型的显示文本。
 */
public enum MediaType {

    TEXT(""),
    IMAGE("[图片]"),
    VIDEO("[视频]"),
    VOICE("[语音]"),
    FILE("[文件]"),
    COMPOSITE("[卡片消息]");

    private final String displayText;

    MediaType(String displayText) {
        this.displayText = displayText;
    }

    /**
     * 获取媒体类型的显示文本。
     *
     * @param mediaType 媒体类型字符串（大小写不敏感），可为 null
     * @return 媒体类型对应的中文显示文本；TEXT/null/空时返回 fallbackContent
     */
    public static String resolveDisplay(String mediaType, String fallbackContent) {
        if (mediaType == null || mediaType.isBlank() || "TEXT".equalsIgnoreCase(mediaType)) {
            return fallbackContent;
        }
        return Arrays.stream(values())
                .filter(m -> m.name().equalsIgnoreCase(mediaType))
                .findFirst()
                .map(m -> m.displayText)
                .orElse("[文件]");
    }

    /**
     * 检查媒体类型是否为支持的类型（忽略大小写）。
     */
    public static boolean isValid(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(m -> m.name().equalsIgnoreCase(mediaType));
    }
}
