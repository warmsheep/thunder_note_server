package com.flashnote.flashnote.service;

import com.flashnote.flashnote.dto.FlashNoteSearchResult;
import com.flashnote.flashnote.entity.FlashNote;

import java.util.List;

public interface FlashNoteService {
    List<FlashNote> listNotes(String username);

    List<FlashNoteSearchResult> searchNotes(String username, String query);

    FlashNote createNote(String username, FlashNote note);

    FlashNote updateNote(String username, Long noteId, FlashNote note);

    void deleteNote(String username, Long noteId);
}
