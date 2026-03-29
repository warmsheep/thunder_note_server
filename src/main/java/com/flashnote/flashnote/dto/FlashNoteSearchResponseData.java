package com.flashnote.flashnote.dto;

import java.util.List;

public class FlashNoteSearchResponseData {
    private List<FlashNoteSearchResultResponse> noteNameMatched;
    private List<FlashNoteSearchResultResponse> messageContentMatched;

    public List<FlashNoteSearchResultResponse> getNoteNameMatched() {
        return noteNameMatched;
    }

    public void setNoteNameMatched(List<FlashNoteSearchResultResponse> noteNameMatched) {
        this.noteNameMatched = noteNameMatched;
    }

    public List<FlashNoteSearchResultResponse> getMessageContentMatched() {
        return messageContentMatched;
    }

    public void setMessageContentMatched(List<FlashNoteSearchResultResponse> messageContentMatched) {
        this.messageContentMatched = messageContentMatched;
    }
}
