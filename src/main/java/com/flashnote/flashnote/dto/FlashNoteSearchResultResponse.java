package com.flashnote.flashnote.dto;

import java.util.List;

public class FlashNoteSearchResultResponse {
    private FlashNoteResponse flashNote;
    private List<MatchedMessageInfoResponse> matchedMessages;
    private boolean noteMatched;

    public FlashNoteResponse getFlashNote() {
        return flashNote;
    }

    public void setFlashNote(FlashNoteResponse flashNote) {
        this.flashNote = flashNote;
    }

    public List<MatchedMessageInfoResponse> getMatchedMessages() {
        return matchedMessages;
    }

    public void setMatchedMessages(List<MatchedMessageInfoResponse> matchedMessages) {
        this.matchedMessages = matchedMessages;
    }

    public boolean isNoteMatched() {
        return noteMatched;
    }

    public void setNoteMatched(boolean noteMatched) {
        this.noteMatched = noteMatched;
    }
}
