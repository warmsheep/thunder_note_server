package com.flashnote.flashnote.dto;

import java.util.List;


public class FlashNoteSearchResponse {
    private List<FlashNoteSearchResult> noteNameMatched;
    private List<FlashNoteSearchResult> messageContentMatched;

    public FlashNoteSearchResponse() {}

    public FlashNoteSearchResponse(List<FlashNoteSearchResult> noteNameMatched, 
                                   List<FlashNoteSearchResult> messageContentMatched) {
        this.noteNameMatched = noteNameMatched;
        this.messageContentMatched = messageContentMatched;
    }

    public List<FlashNoteSearchResult> getNoteNameMatched() {
        return noteNameMatched;
    }

    public void setNoteNameMatched(List<FlashNoteSearchResult> noteNameMatched) {
        this.noteNameMatched = noteNameMatched;
    }

    public List<FlashNoteSearchResult> getMessageContentMatched() {
        return messageContentMatched;
    }

    public void setMessageContentMatched(List<FlashNoteSearchResult> messageContentMatched) {
        this.messageContentMatched = messageContentMatched;
    }
}
