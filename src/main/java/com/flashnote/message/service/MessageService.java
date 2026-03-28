package com.flashnote.message.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashnote.message.dto.MessageMergeRequest;
import com.flashnote.message.entity.Message;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface MessageService {
    IPage<Message> listMessages(String username, Long flashNoteId, Long peerUserId, Integer page, Integer limit);

    Message sendMessage(String username, Message message);
    
    Message mergeMessages(String username, MessageMergeRequest request);

    SseEmitter subscribe(String username);

    void deleteMessage(String username, Long messageId);

    void deleteMessages(String username, List<Long> messageIds);

    void clearInboxMessages(String username);

    Long countMessages(String username);
}
