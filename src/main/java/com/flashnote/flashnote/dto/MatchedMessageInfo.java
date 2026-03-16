package com.flashnote.flashnote.dto;

public class MatchedMessageInfo {
    private Long messageId;
    private String snippet;

    public MatchedMessageInfo() {}

    public MatchedMessageInfo(Long messageId, String snippet) {
        this.messageId = messageId;
        this.snippet = snippet;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
