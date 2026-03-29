package com.flashnote.flashnote.dto;

import com.flashnote.message.dto.MessageResponse;

import java.util.List;

public class MatchedMessageInfoResponse {
    private Long messageId;
    private String snippet;
    private List<MessageResponse> contextMessages;

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

    public List<MessageResponse> getContextMessages() {
        return contextMessages;
    }

    public void setContextMessages(List<MessageResponse> contextMessages) {
        this.contextMessages = contextMessages;
    }
}
