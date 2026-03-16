package com.flashnote.message.service;

import com.flashnote.message.entity.Message;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface MessageService {
    List<Message> listMessages(String username, Long flashNoteId);

    Message sendMessage(String username, Message message);

    SseEmitter subscribe(String username);

    void deleteMessage(String username, Long messageId);
}
