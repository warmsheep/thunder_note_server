package com.flashnote.message.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.message.dto.MessageResponse;
import com.flashnote.message.dto.MessageBatchDeleteRequest;
import com.flashnote.message.dto.MessageListRequest;
import com.flashnote.message.entity.Message;
import com.flashnote.message.service.MessageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/list")
    public ApiResponse<IPage<MessageResponse>> list(Authentication authentication,
                                           @RequestBody(required = false) MessageListRequest request) {
        Long flashNoteId = request == null ? null : request.getFlashNoteId();
        Long peerUserId = request == null ? null : request.getPeerUserId();
        Integer page = request == null ? null : request.getPage();
        Integer limit = request == null ? null : request.getLimit();
        return ApiResponse.success(toPageResponse(messageService.listMessages(
            authentication.getName(), flashNoteId, peerUserId, page, limit)));
    }

    @PostMapping
    public ApiResponse<MessageResponse> send(Authentication authentication, @RequestBody Message message) {
        return ApiResponse.success(toResponse(messageService.sendMessage(authentication.getName(), message)));
    }

    @PostMapping("/merge")
    public ApiResponse<MessageResponse> merge(Authentication authentication, @RequestBody com.flashnote.message.dto.MessageMergeRequest request) {
        return ApiResponse.success(toResponse(messageService.mergeMessages(authentication.getName(), request)));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        return messageService.subscribe(authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Authentication authentication,
                                   @PathVariable @Positive Long id) {
        messageService.deleteMessage(authentication.getName(), id);
        return ApiResponse.success("Deleted", null);
    }

    @PostMapping("/delete-batch")
    public ApiResponse<Void> deleteBatch(Authentication authentication,
                                         @RequestBody(required = false) MessageBatchDeleteRequest request) {
        messageService.deleteMessages(authentication.getName(), request == null ? null : request.getIds());
        return ApiResponse.success("Deleted", null);
    }

    @DeleteMapping("/clear-inbox")
    public ApiResponse<Void> clearInbox(Authentication authentication) {
        messageService.clearInboxMessages(authentication.getName());
        return ApiResponse.success("Cleared", null);
    }

    @GetMapping("/count")
    public ApiResponse<Long> count(Authentication authentication) {
        return ApiResponse.success(messageService.countMessages(authentication.getName()));
    }

    private IPage<MessageResponse> toPageResponse(IPage<Message> page) {
        Page<MessageResponse> responsePage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        responsePage.setPages(page.getPages());
        responsePage.setRecords(page.getRecords().stream().map(this::toResponse).toList());
        return responsePage;
    }

    private MessageResponse toResponse(Message message) {
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
