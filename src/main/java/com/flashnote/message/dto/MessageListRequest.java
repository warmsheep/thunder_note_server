package com.flashnote.message.dto;

public class MessageListRequest {
    private Long flashNoteId;
    private Integer page;   // 页码，从1开始，默认1
    private Integer limit;  // 每页条数，默认30

    public Long getFlashNoteId() {
        return flashNoteId;
    }

    public void setFlashNoteId(Long flashNoteId) {
        this.flashNoteId = flashNoteId;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
