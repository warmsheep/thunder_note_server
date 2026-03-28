package com.flashnote.flashnote.service;

import com.flashnote.flashnote.dto.FlashNoteCreateRequest;
import com.flashnote.flashnote.dto.FlashNoteSearchResponse;
import com.flashnote.flashnote.dto.FlashNoteUpdateRequest;
import com.flashnote.flashnote.entity.FlashNote;

import java.util.List;

public interface FlashNoteService {
    List<FlashNote> listNotes(String username);

    FlashNoteSearchResponse searchNotes(String username, String query);

    FlashNote createNote(String username, FlashNoteCreateRequest request);

    FlashNote updateNote(String username, Long noteId, FlashNoteUpdateRequest request);

    FlashNote setPinned(String username, Long noteId, boolean pinned);

    FlashNote setHidden(String username, Long noteId, boolean hidden);

    void deleteNote(String username, Long noteId);
}
