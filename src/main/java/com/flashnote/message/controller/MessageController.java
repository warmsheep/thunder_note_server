package com.flashnote.message.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.message.dto.MessageBatchDeleteRequest;
import com.flashnote.message.dto.MessageListRequest;
import com.flashnote.message.entity.Message;
import com.flashnote.message.service.MessageService;
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

import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/list")
    public ApiResponse<IPage<Message>> list(Authentication authentication,
                                          @RequestBody(required = false) MessageListRequest request) {
        Long flashNoteId = request == null ? null : request.getFlashNoteId();
        Long peerUserId = request == null ? null : request.getPeerUserId();
        Integer page = request == null ? null : request.getPage();
        Integer limit = request == null ? null : request.getLimit();
        return ApiResponse.success(messageService.listMessages(
            authentication.getName(), flashNoteId, peerUserId, page, limit));
    }

    @PostMapping
    public ApiResponse<Message> send(Authentication authentication, @RequestBody Message message) {
        return ApiResponse.success(messageService.sendMessage(authentication.getName(), message));
    }

    @PostMapping("/merge")
    public ApiResponse<Message> merge(Authentication authentication, @RequestBody com.flashnote.message.dto.MessageMergeRequest request) {
        return ApiResponse.success(messageService.mergeMessages(authentication.getName(), request));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        return messageService.subscribe(authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable Long id) {
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
}
