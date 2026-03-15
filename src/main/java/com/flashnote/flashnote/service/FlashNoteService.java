package com.flashnote.flashnote.service;

import com.flashnote.flashnote.entity.FlashNote;

import java.util.List;

public interface FlashNoteService {
    List<FlashNote> listNotes(String username);

    FlashNote createNote(String username, FlashNote note);

    FlashNote updateNote(String username, Long noteId, FlashNote note);

    void deleteNote(String username, Long noteId);
}
