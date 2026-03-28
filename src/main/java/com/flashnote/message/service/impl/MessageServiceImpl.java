package com.flashnote.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.constant.NoteConstants;
import com.flashnote.common.constant.MediaType;
import com.flashnote.common.service.CurrentUserService;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.flashnote.file.service.FileService;
import com.flashnote.message.entity.CardItem;
import com.flashnote.message.entity.CardPayload;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import com.flashnote.message.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageServiceImpl implements MessageService {
    private static final long SSE_TIMEOUT_MILLIS = 30_000L;

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final FlashNoteMapper flashNoteMapper;
    private final FileService fileService;
    private final CurrentUserService currentUserService;
    private final Map<Long, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    @Autowired
    public MessageServiceImpl(MessageMapper messageMapper,
                              UserMapper userMapper,
                              FlashNoteMapper flashNoteMapper,
                              FileService fileService,
                              CurrentUserService currentUserService) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.flashNoteMapper = flashNoteMapper;
        this.fileService = fileService;
        this.currentUserService = currentUserService;
    }

    public MessageServiceImpl(MessageMapper messageMapper,
                              UserMapper userMapper,
                              FlashNoteMapper flashNoteMapper) {
        this(messageMapper, userMapper, flashNoteMapper, null, null);
    }

    @Override
    public IPage<Message> listMessages(String username, Long flashNoteId, Long peerUserId, Integer page, Integer limit) {
        Long userId = currentUserService.getRequiredUserId(username);
        LambdaQueryWrapper<Message> queryWrapper = new LambdaQueryWrapper<Message>()
                .and(wrapper -> wrapper
                        .eq(Message::getSenderId, userId)
                        .or()
                        .eq(Message::getReceiverId, userId))
                .orderByDesc(Message::getCreatedAt)
                .orderByDesc(Message::getId);

        if (flashNoteId != null) {
            queryWrapper.eq(Message::getFlashNoteId, flashNoteId);
            if (flashNoteId == NoteConstants.COLLECTION_BOX_NOTE_ID) {
                queryWrapper.eq(Message::getSenderId, userId)
                        .eq(Message::getReceiverId, userId);
            }
        } else if (peerUserId != null) {
            queryWrapper.and(wrapper -> wrapper
                    .and(pair -> pair.eq(Message::getSenderId, userId).eq(Message::getReceiverId, peerUserId))
                    .or(pair -> pair.eq(Message::getSenderId, peerUserId).eq(Message::getReceiverId, userId)));
        }

        int actualLimit = (limit != null && limit > 0) ? limit : 20;
        int actualPage = (page != null && page > 0) ? page : 1;

        Page<Message> pageObj = new Page<>(actualPage, actualLimit);
        messageMapper.selectPage(pageObj, queryWrapper);
        return pageObj;
    }

    @Override
    public Message sendMessage(String username, Message message) {
        Long senderId = currentUserService.getRequiredUserId(username);
        message.setSenderId(senderId);
        if (message.getFlashNoteId() != null && message.getFlashNoteId() == NoteConstants.COLLECTION_BOX_NOTE_ID) {
            message.setReceiverId(senderId);
        } else if (message.getReceiverId() == null) {
            message.setReceiverId(senderId);
        }
        if (message.getRole() == null || message.getRole().isBlank()) {
            message.setRole("user");
        }
        if (message.getFlashNoteId() != null) {
            if (message.getFlashNoteId() != NoteConstants.COLLECTION_BOX_NOTE_ID) {
                FlashNote flashNote = flashNoteMapper.selectById(message.getFlashNoteId());
                if (flashNote == null || !senderId.equals(flashNote.getUserId())) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "Flash note not found");
                }
            }
        }
        message.setReadStatus(false);
        // 设置默认 content，避免媒体消息时 content 为 null 违反 NOT NULL 约束
        if (message.getContent() == null || message.getContent().isBlank()) {
            message.setContent(MediaType.resolveDisplay(message.getMediaType(), ""));
        }
        messageMapper.insert(message);
        if (message.getFlashNoteId() != null && message.getFlashNoteId() != NoteConstants.COLLECTION_BOX_NOTE_ID) {
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
    public Message mergeMessages(String username, com.flashnote.message.dto.MessageMergeRequest request) {
        Long userId = currentUserService.getRequiredUserId(username);
        if (request.getMessageIds() == null || request.getMessageIds().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "No messages to merge");
        }
        if (request.getMessageIds().size() > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot merge more than 50 messages");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Title is required");
        }
        if (request.getFlashNoteId() == null && request.getReceiverId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Flash note or receiver is required");
        }

        Long targetFlashNoteId = request.getFlashNoteId();
        Long targetReceiverId = request.getReceiverId();
        if (targetFlashNoteId != null && targetFlashNoteId != NoteConstants.COLLECTION_BOX_NOTE_ID) {
            FlashNote flashNote = flashNoteMapper.selectById(targetFlashNoteId);
            if (flashNote == null || !userId.equals(flashNote.getUserId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Flash note not found");
            }
        }
        
        List<Message> originalMessages = messageMapper.selectBatchIds(request.getMessageIds());
        if (originalMessages.size() != request.getMessageIds().size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Some messages not found");
        }

        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < request.getMessageIds().size(); i++) {
            orderMap.put(request.getMessageIds().get(i), i);
        }
        originalMessages = new ArrayList<>(originalMessages);
        originalMessages.sort(Comparator.comparingInt(message -> orderMap.getOrDefault(message.getId(), Integer.MAX_VALUE)));

        for (Message originalMessage : originalMessages) {
            boolean relatedToCurrentUser = userId.equals(originalMessage.getSenderId()) || userId.equals(originalMessage.getReceiverId());
            if (!relatedToCurrentUser) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "You can only merge your own conversation messages");
            }

            if (targetFlashNoteId != null) {
                Long messageFlashNoteId = originalMessage.getFlashNoteId();
                if (!java.util.Objects.equals(messageFlashNoteId, targetFlashNoteId)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "All messages must belong to the same flash note");
                }
            } else {
                boolean inTargetConversation = (userId.equals(originalMessage.getSenderId()) && targetReceiverId.equals(originalMessage.getReceiverId()))
                        || (targetReceiverId.equals(originalMessage.getSenderId()) && userId.equals(originalMessage.getReceiverId()));
                if (!inTargetConversation) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "All messages must belong to the same contact conversation");
                }
            }
        }
        
        com.flashnote.message.entity.CardPayload payload = new com.flashnote.message.entity.CardPayload();
        payload.setCardType("MESSAGE_COLLECTION");
        payload.setTitle(request.getTitle());
        
        java.util.List<com.flashnote.message.entity.CardItem> items = new java.util.ArrayList<>();
        for (Message msg : originalMessages) {
            com.flashnote.message.entity.CardItem item = new com.flashnote.message.entity.CardItem();
            item.setOriginalMsgId(msg.getId());
            item.setContent(msg.getContent());
            if (msg.getMediaType() != null) {
                item.setType(msg.getMediaType());
            } else {
                item.setType("TEXT");
            }
            item.setUrl(msg.getMediaUrl());
            item.setThumbnailUrl(msg.getThumbnailUrl());
            item.setFileName(msg.getFileName());
            item.setFileSize(msg.getFileSize());
            item.setSenderId(msg.getSenderId());
            item.setRole(msg.getRole());
            items.add(item);
        }
        payload.setItems(items);
        
        String summary = "";
        if (!items.isEmpty()) {
            summary = items.get(0).getContent();
            if (summary == null || summary.isEmpty()) {
                summary = "[" + items.get(0).getType() + "]";
            }
            if (items.size() > 1) {
                summary += " 等" + items.size() + "条消息";
            }
        }
        payload.setSummary(summary);
        
        Message compositeMsg = new Message();
        compositeMsg.setSenderId(userId);
        Long receiverId = targetReceiverId;
        if (targetFlashNoteId != null && targetFlashNoteId == NoteConstants.COLLECTION_BOX_NOTE_ID) {
            receiverId = userId;
        } else if (receiverId == null) {
            receiverId = userId;
        }
        compositeMsg.setReceiverId(receiverId);
        compositeMsg.setFlashNoteId(targetFlashNoteId);
        compositeMsg.setRole("user");
        compositeMsg.setReadStatus(false);
        compositeMsg.setMediaType("COMPOSITE");
        compositeMsg.setContent(request.getTitle() != null && !request.getTitle().isEmpty() ? request.getTitle() : "[卡片消息]");
        compositeMsg.setFileName(request.getTitle());
        compositeMsg.setPayload(payload);
        
        messageMapper.insert(compositeMsg);
        
        if (compositeMsg.getFlashNoteId() != null && compositeMsg.getFlashNoteId() != NoteConstants.COLLECTION_BOX_NOTE_ID) {
            FlashNote flashNote = flashNoteMapper.selectById(compositeMsg.getFlashNoteId());
            if (flashNote != null && userId.equals(flashNote.getUserId())) {
                flashNote.setContent(compositeMsg.getContent());
                flashNoteMapper.updateById(flashNote);
            }
        }

        SseEmitter receiverEmitter = emitterMap.get(compositeMsg.getReceiverId());
        if (receiverEmitter != null) {
            try {
                receiverEmitter.send(SseEmitter.event()
                        .name("message")
                        .data(compositeMsg));
            } catch (IOException ex) {
                emitterMap.remove(compositeMsg.getReceiverId());
            }
        }
        
        return compositeMsg;
    }

    @Override
    public SseEmitter subscribe(String username) {
        Long userId = currentUserService.getRequiredUserId(username);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        SseEmitter previousEmitter = emitterMap.put(userId, emitter);
        if (previousEmitter != null) {
            previousEmitter.complete();
        }
        emitter.onCompletion(() -> emitterMap.remove(userId, emitter));
        emitter.onTimeout(() -> {
            emitterMap.remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitterMap.remove(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    @Override
    public void deleteMessage(String username, Long messageId) {
        Long userId = currentUserService.getRequiredUserId(username);
        
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

    @Override
    public void deleteMessages(String username, List<Long> messageIds) {
        Long userId = currentUserService.getRequiredUserId(username);
        if (messageIds == null || messageIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "No messages selected");
        }
        if (messageIds.size() > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot delete more than 50 messages");
        }
        List<Message> messages = messageMapper.selectBatchIds(messageIds);
        if (messages.size() != messageIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Some messages not found");
        }
        for (Message message : messages) {
            boolean isOwner = message.getSenderId() != null && message.getSenderId().equals(userId);
            boolean isReceiver = message.getReceiverId() != null && message.getReceiverId().equals(userId);
            if (!isOwner && !isReceiver) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "You can only delete your own messages");
            }
            deleteMediaIfNecessary(message);
        }
        messageMapper.delete(new LambdaQueryWrapper<Message>().in(Message::getId, messageIds));
    }

    @Override
    public void clearInboxMessages(String username) {
        Long userId = currentUserService.getRequiredUserId(username);
        List<Message> inboxMessages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getFlashNoteId, NoteConstants.COLLECTION_BOX_NOTE_ID)
                .eq(Message::getSenderId, userId)
                .eq(Message::getReceiverId, userId));
        if (inboxMessages.isEmpty()) {
            return;
        }
        for (Message message : inboxMessages) {
            deleteMediaIfNecessary(message);
        }
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getFlashNoteId, NoteConstants.COLLECTION_BOX_NOTE_ID)
                .eq(Message::getSenderId, userId)
                .eq(Message::getReceiverId, userId));
    }

    private void deleteMediaIfNecessary(Message message) {
        if (fileService == null || message == null) {
            return;
        }

        Set<String> objectsToDelete = new HashSet<>();
        collectObjectName(objectsToDelete, message.getMediaUrl());
        collectObjectName(objectsToDelete, message.getThumbnailUrl());

        CardPayload payload = message.getPayload();
        if (payload != null && payload.getItems() != null) {
            for (CardItem item : payload.getItems()) {
                if (item == null || item.getOriginalMsgId() != null) {
                    continue;
                }
                collectObjectName(objectsToDelete, item.getUrl());
                collectObjectName(objectsToDelete, item.getThumbnailUrl());
            }
        }

        for (String objectName : objectsToDelete) {
            fileService.deleteObject(objectName);
        }
    }

    private void collectObjectName(Set<String> objectNames, String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
        objectNames.add(objectName);
    }

    @Override
    public Long countMessages(String username) {
        Long userId = currentUserService.getRequiredUserId(username);
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .and(wrapper -> wrapper
                        .eq(Message::getSenderId, userId)
                        .or()
                        .eq(Message::getReceiverId, userId)));
    }
}
