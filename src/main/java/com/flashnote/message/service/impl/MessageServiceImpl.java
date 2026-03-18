package com.flashnote.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.flashnote.file.service.FileService;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import com.flashnote.message.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageServiceImpl implements MessageService {
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final FlashNoteMapper flashNoteMapper;
    private final FileService fileService;
    private final Map<Long, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    @Autowired
    public MessageServiceImpl(MessageMapper messageMapper,
                              UserMapper userMapper,
                              FlashNoteMapper flashNoteMapper,
                              FileService fileService) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.flashNoteMapper = flashNoteMapper;
        this.fileService = fileService;
    }

    public MessageServiceImpl(MessageMapper messageMapper,
                              UserMapper userMapper,
                              FlashNoteMapper flashNoteMapper) {
        this(messageMapper, userMapper, flashNoteMapper, null);
    }

    @Override
    public List<Message> listMessages(String username, Long flashNoteId, Integer page, Integer limit) {
        Long userId = getRequiredUserId(username);
        LambdaQueryWrapper<Message> queryWrapper = new LambdaQueryWrapper<Message>()
                .and(wrapper -> wrapper
                        .eq(Message::getSenderId, userId)
                        .or()
                        .eq(Message::getReceiverId, userId))
                .orderByDesc(Message::getId);

        if (flashNoteId != null) {
            queryWrapper.eq(Message::getFlashNoteId, flashNoteId);
        }

        int actualLimit = (limit != null && limit > 0) ? limit : 20;
        int actualPage = (page != null && page > 0) ? page : 1;
        int offset = (actualPage - 1) * actualLimit;

        queryWrapper.last("LIMIT " + actualLimit + " OFFSET " + offset);

        List<Message> messages = messageMapper.selectList(queryWrapper);
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public Message sendMessage(String username, Message message) {
        Long senderId = getRequiredUserId(username);
        message.setSenderId(senderId);
        if (message.getReceiverId() == null) {
            message.setReceiverId(senderId);
        }
        if (message.getRole() == null || message.getRole().isBlank()) {
            message.setRole("user");
        }
        if (message.getFlashNoteId() != null) {
            FlashNote flashNote = flashNoteMapper.selectById(message.getFlashNoteId());
            if (flashNote == null || !senderId.equals(flashNote.getUserId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Flash note not found");
            }
        }
        message.setReadStatus(false);
        // 设置默认 content，避免媒体消息时 content 为 null 违反 NOT NULL 约束
        if (message.getContent() == null || message.getContent().isBlank()) {
            String mediaType = message.getMediaType();
            if (mediaType != null) {
                switch (mediaType) {
                    case "IMAGE": message.setContent("[图片]"); break;
                    case "VIDEO": message.setContent("[视频]"); break;
                    case "VOICE": message.setContent("[语音]"); break;
                    case "FILE": message.setContent("[文件]"); break;
                    default: message.setContent(""); break;
                }
            } else {
                message.setContent("");
            }
        }
        messageMapper.insert(message);
        if (message.getFlashNoteId() != null) {
            FlashNote flashNote = flashNoteMapper.selectById(message.getFlashNoteId());
            if (flashNote != null && senderId.equals(flashNote.getUserId())) {
                flashNote.setContent(message.getContent());
                flashNoteMapper.updateById(flashNote);
            }
        }

        SseEmitter receiverEmitter = emitterMap.get(message.getReceiverId());
        if (receiverEmitter != null) {
            try {
                receiverEmitter.send(SseEmitter.event()
                        .name("message")
                        .data(message));
            } catch (IOException ex) {
                emitterMap.remove(message.getReceiverId());
            }
        }
        return message;
    }

    @Override
    public SseEmitter subscribe(String username) {
        Long userId = getRequiredUserId(username);
        SseEmitter emitter = new SseEmitter(0L);
        emitterMap.put(userId, emitter);
        emitter.onCompletion(() -> emitterMap.remove(userId));
        emitter.onTimeout(() -> emitterMap.remove(userId));
        emitter.onError(error -> emitterMap.remove(userId));

        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private Long getRequiredUserId(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        return user.getId();
    }

    @Override
    public void deleteMessage(String username, Long messageId) {
        Long userId = getRequiredUserId(username);
        
        // 查询消息是否存在且属于当前用户
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Message not found");
        }
        
        // 检查是否是发送者或接收者
        boolean isOwner = message.getSenderId() != null && message.getSenderId().equals(userId);
        boolean isReceiver = message.getReceiverId() != null && message.getReceiverId().equals(userId);
        
        if (!isOwner && !isReceiver) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You can only delete your own messages");
        }

        deleteMediaIfNecessary(message);
        
        messageMapper.deleteById(messageId);
    }

    private void deleteMediaIfNecessary(Message message) {
        if (fileService == null || message == null || message.getMediaUrl() == null || message.getMediaUrl().isBlank()) {
            return;
        }
        fileService.deleteObject(message.getMediaUrl());
        if (message.getThumbnailUrl() != null && !message.getThumbnailUrl().isBlank()) {
            fileService.deleteObject(message.getThumbnailUrl());
        }
    }

    @Override
    public Long countMessages(String username) {
        Long userId = getRequiredUserId(username);
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .and(wrapper -> wrapper
                        .eq(Message::getSenderId, userId)
                        .or()
                        .eq(Message::getReceiverId, userId)));
    }
}
