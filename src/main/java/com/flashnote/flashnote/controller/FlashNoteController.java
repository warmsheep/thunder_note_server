package com.flashnote.flashnote.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.flashnote.dto.FlashNoteCreateRequest;
import com.flashnote.flashnote.dto.FlashNoteResponse;
import com.flashnote.flashnote.dto.FlashNoteSearchRequest;
import com.flashnote.flashnote.dto.FlashNoteSearchResponseData;
import com.flashnote.flashnote.dto.FlashNoteSearchResponse;
import com.flashnote.flashnote.dto.FlashNoteSearchResultResponse;
import com.flashnote.flashnote.dto.FlashNoteUpdateRequest;
import com.flashnote.flashnote.dto.FlashNoteSearchResult;
import com.flashnote.flashnote.dto.MatchedMessageInfo;
import com.flashnote.flashnote.dto.MatchedMessageInfoResponse;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.message.dto.MessageResponse;
import com.flashnote.message.entity.Message;
import com.flashnote.flashnote.service.FlashNoteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/flash-notes")
public class FlashNoteController {
    private final FlashNoteService flashNoteService;

    public FlashNoteController(FlashNoteService flashNoteService) {
        this.flashNoteService = flashNoteService;
    }

    @PostMapping("/list")
    public ApiResponse<List<FlashNoteResponse>> list(Authentication authentication) {
        return ApiResponse.success(flashNoteService.listNotes(authentication.getName()).stream()
                .map(this::toResponse)
                .toList());
    }

    @PostMapping("/search")
    public ApiResponse<FlashNoteSearchResponseData> search(Authentication authentication,
                                                            @RequestBody FlashNoteSearchRequest request) {
        String query = request == null ? null : request.getQuery();
        return ApiResponse.success(toSearchResponse(flashNoteService.searchNotes(authentication.getName(), query)));
    }

    @PostMapping
    public ApiResponse<FlashNoteResponse> create(Authentication authentication,
                                        @Valid @RequestBody FlashNoteCreateRequest request) {
        return ApiResponse.success(toResponse(flashNoteService.createNote(authentication.getName(), request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<FlashNoteResponse> update(Authentication authentication,
                                          @PathVariable @Positive Long id,
                                          @Valid @RequestBody FlashNoteUpdateRequest request) {
        return ApiResponse.success(toResponse(flashNoteService.updateNote(authentication.getName(), id, request)));
    }

    @PutMapping("/{id}/pin")
    public ApiResponse<FlashNoteResponse> setPinned(Authentication authentication,
                                              @PathVariable @Positive Long id,
                                              @RequestParam(defaultValue = "true") boolean value) {
        return ApiResponse.success(toResponse(flashNoteService.setPinned(authentication.getName(), id, value)));
    }

    @PutMapping("/{id}/hide")
    public ApiResponse<FlashNoteResponse> setHidden(Authentication authentication,
                                              @PathVariable @Positive Long id,
                                              @RequestParam(defaultValue = "true") boolean value) {
        return ApiResponse.success(toResponse(flashNoteService.setHidden(authentication.getName(), id, value)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Authentication authentication,
                                    @PathVariable @Positive Long id) {
        flashNoteService.deleteNote(authentication.getName(), id);
        return ApiResponse.success("Deleted", null);
    }

    private FlashNoteResponse toResponse(FlashNote note) {
        if (note == null) {
            return null;
        }
        FlashNoteResponse response = new FlashNoteResponse();
        response.setId(note.getId());
        response.setUserId(note.getUserId());
        response.setTitle(note.getTitle());
        response.setIcon(note.getIcon());
        response.setContent(note.getContent());
        response.setTags(note.getTags());
        response.setLatestMessage(note.getLatestMessage());
        response.setDeleted(note.getDeleted());
        response.setPinned(note.getPinned());
        response.setHidden(note.getHidden());
        response.setInbox(note.getInbox());
        response.setCreatedAt(note.getCreatedAt());
        response.setUpdatedAt(note.getUpdatedAt());
        return response;
    }

    private FlashNoteSearchResponseData toSearchResponse(FlashNoteSearchResponse response) {
        FlashNoteSearchResponseData data = new FlashNoteSearchResponseData();
        data.setNoteNameMatched(toSearchResults(response == null ? null : response.getNoteNameMatched()));
        data.setMessageContentMatched(toSearchResults(response == null ? null : response.getMessageContentMatched()));
        return data;
    }

    private List<FlashNoteSearchResultResponse> toSearchResults(List<FlashNoteSearchResult> results) {
        if (results == null) {
            return null;
        }
        return results.stream().map(this::toSearchResult).collect(Collectors.toList());
    }

    private FlashNoteSearchResultResponse toSearchResult(FlashNoteSearchResult result) {
        FlashNoteSearchResultResponse response = new FlashNoteSearchResultResponse();
        response.setFlashNote(toResponse(result.getFlashNote()));
        response.setMatchedMessages(result.getMatchedMessages() == null ? null : result.getMatchedMessages().stream()
                .map(this::toMatchedMessageInfo)
                .toList());
        response.setNoteMatched(result.isNoteMatched());
        return response;
    }

    private MatchedMessageInfoResponse toMatchedMessageInfo(MatchedMessageInfo info) {
        MatchedMessageInfoResponse response = new MatchedMessageInfoResponse();
        response.setMessageId(info.getMessageId());
        response.setSnippet(info.getSnippet());
        response.setContextMessages(info.getContextMessages() == null ? null : info.getContextMessages().stream()
                .map(this::toMessageResponse)
                .toList());
        return response;
    }

    private MessageResponse toMessageResponse(Message message) {
        if (message == null) {
            return null;
        }
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setSenderId(message.getSenderId());
        response.setReceiverId(message.getReceiverId());
        response.setContent(message.getContent());
        response.setReadStatus(message.getReadStatus());
        response.setFlashNoteId(message.getFlashNoteId());
        response.setClientRequestId(message.getClientRequestId());
        response.setRole(message.getRole());
        response.setCreatedAt(message.getCreatedAt());
        response.setMediaType(message.getMediaType());
        response.setMediaUrl(message.getMediaUrl());
        response.setMediaDuration(message.getMediaDuration());
        response.setThumbnailUrl(message.getThumbnailUrl());
        response.setFileName(message.getFileName());
        response.setFileSize(message.getFileSize());
        response.setPayload(message.getPayload());
        return response;
    }
}
