package com.flashnote.flashnote.dto;

import com.flashnote.message.entity.Message;

import java.util.List;

public class MatchedMessageInfo {
    private Long messageId;
    private String snippet;
    private List<Message> contextMessages;

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

    public List<Message> getContextMessages() {
        return contextMessages;
    }

    public void setContextMessages(List<Message> contextMessages) {
        this.contextMessages = contextMessages;
    }
}
