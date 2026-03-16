package com.flashnote.flashnote.dto;

import com.flashnote.flashnote.entity.FlashNote;
import java.util.List;

public class FlashNoteSearchResult {
    private FlashNote flashNote;
    private List<MatchedMessageInfo> matchedMessages;

    public FlashNoteSearchResult(FlashNote flashNote, List<MatchedMessageInfo> matchedMessages) {
        this.flashNote = flashNote;
        this.matchedMessages = matchedMessages;
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
}
