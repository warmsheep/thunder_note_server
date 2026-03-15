package com.flashnote.flashnote.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.service.FlashNoteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/flash-notes")
public class FlashNoteController {
    private final FlashNoteService flashNoteService;

    public FlashNoteController(FlashNoteService flashNoteService) {
        this.flashNoteService = flashNoteService;
    }

    @PostMapping("/list")
    public ApiResponse<List<FlashNote>> list(Authentication authentication) {
        return ApiResponse.success(flashNoteService.listNotes(authentication.getName()));
    }

    @PostMapping
    public ApiResponse<FlashNote> create(Authentication authentication, @RequestBody FlashNote note) {
        return ApiResponse.success(flashNoteService.createNote(authentication.getName(), note));
    }

    @PutMapping("/{id}")
    public ApiResponse<FlashNote> update(Authentication authentication,
                                         @PathVariable Long id,
                                         @RequestBody FlashNote note) {
        return ApiResponse.success(flashNoteService.updateNote(authentication.getName(), id, note));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable Long id) {
        flashNoteService.deleteNote(authentication.getName(), id);
        return ApiResponse.success("Deleted", null);
    }
}
