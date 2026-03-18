package com.flashnote.flashnote.dto;

import com.flashnote.flashnote.entity.FlashNote;
import java.util.List;

public class FlashNoteSearchResult {
    private FlashNote flashNote;
    private List<MatchedMessageInfo> matchedMessages;
    private boolean noteMatched;

    public FlashNoteSearchResult(FlashNote flashNote, List<MatchedMessageInfo> matchedMessages) {
        this.flashNote = flashNote;
        this.matchedMessages = matchedMessages;
    }

    public FlashNoteSearchResult(FlashNote flashNote, List<MatchedMessageInfo> matchedMessages, boolean noteMatched) {
        this.flashNote = flashNote;
        this.matchedMessages = matchedMessages;
        this.noteMatched = noteMatched;
    }
    
    public FlashNoteSearchResult() {}

    public FlashNote getFlashNote() {
        return flashNote;
    }

    public void setFlashNote(FlashNote flashNote) {
        this.flashNote = flashNote;
    }

    public List<MatchedMessageInfo> getMatchedMessages() {
        return matchedMessages;
    }

    public void setMatchedMessages(List<MatchedMessageInfo> matchedMessages) {
        this.matchedMessages = matchedMessages;
    }

    public boolean isNoteMatched() {
        return noteMatched;
    }

    public void setNoteMatched(boolean noteMatched) {
        this.noteMatched = noteMatched;
    }
}
