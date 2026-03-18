package com.flashnote.flashnote.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.flashnote.dto.FlashNoteSearchRequest;
import com.flashnote.flashnote.dto.FlashNoteSearchResult;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.service.FlashNoteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @PostMapping("/search")
    public ApiResponse<List<FlashNoteSearchResult>> search(Authentication authentication,
                                                           @RequestBody FlashNoteSearchRequest request) {
        String query = request == null ? null : request.getQuery();
        return ApiResponse.success(flashNoteService.searchNotes(authentication.getName(), query));
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

    @PutMapping("/{id}/pin")
    public ApiResponse<FlashNote> setPinned(Authentication authentication,
                                            @PathVariable Long id,
                                            @RequestParam(defaultValue = "true") boolean value) {
        return ApiResponse.success(flashNoteService.setPinned(authentication.getName(), id, value));
    }

    @PutMapping("/{id}/hide")
    public ApiResponse<FlashNote> setHidden(Authentication authentication,
                                            @PathVariable Long id,
                                            @RequestParam(defaultValue = "true") boolean value) {
        return ApiResponse.success(flashNoteService.setHidden(authentication.getName(), id, value));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable Long id) {
        flashNoteService.deleteNote(authentication.getName(), id);
        return ApiResponse.success("Deleted", null);
    }
}
