package com.flashnote.sync.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.collection.entity.Collection;
import com.flashnote.collection.mapper.CollectionMapper;
import com.flashnote.collection.service.CollectionService;
import com.flashnote.favorite.entity.FavoriteMessage;
import com.flashnote.favorite.mapper.FavoriteMessageMapper;
import com.flashnote.favorite.service.FavoriteService;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.flashnote.flashnote.service.FlashNoteService;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import com.flashnote.sync.dto.SyncPushRequest;
import com.flashnote.sync.service.SyncService;
import com.flashnote.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SyncServiceImpl implements SyncService {
    private final FlashNoteService flashNoteService;
    private final CollectionService collectionService;
    private final UserService userService;
    private final UserMapper userMapper;
    private final FlashNoteMapper flashNoteMapper;
    private final CollectionMapper collectionMapper;
    private final MessageMapper messageMapper;
    private final FavoriteMessageMapper favoriteMessageMapper;
    private final FavoriteService favoriteService;

    public SyncServiceImpl(FlashNoteService flashNoteService,
                           CollectionService collectionService,
                           UserService userService,
                           UserMapper userMapper,
                           FlashNoteMapper flashNoteMapper,
                           CollectionMapper collectionMapper,
                           MessageMapper messageMapper,
                           FavoriteMessageMapper favoriteMessageMapper,
                           FavoriteService favoriteService) {
        this.flashNoteService = flashNoteService;
        this.collectionService = collectionService;
        this.userService = userService;
        this.userMapper = userMapper;
        this.flashNoteMapper = flashNoteMapper;
        this.collectionMapper = collectionMapper;
        this.messageMapper = messageMapper;
        this.favoriteMessageMapper = favoriteMessageMapper;
        this.favoriteService = favoriteService;
    }

    @Override
    public Map<String, Object> pull(String username, String lastMessageCreatedAt) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserId(username);
        result.put("profile", userService.getProfile(username));
        result.put("notes", flashNoteService.listNotes(username));
        result.put("collections", collectionService.listCollections(username));
        LambdaQueryWrapper<Message> messageQuery = new LambdaQueryWrapper<Message>()
                .and(wrapper -> wrapper
                        .eq(Message::getSenderId, userId)
                        .or()
                        .eq(Message::getReceiverId, userId))
                .orderByAsc(Message::getCreatedAt);
        if (lastMessageCreatedAt != null && !lastMessageCreatedAt.isBlank()) {
            messageQuery.gt(Message::getCreatedAt, LocalDateTime.parse(lastMessageCreatedAt));
        }
        result.put("messages", messageMapper.selectList(messageQuery));
        result.put("favorites", favoriteService.listFavorites(username));
        result.put("serverTime", Instant.now().toString());
        return result;
    }

    @Override
    public Map<String, Object> push(String username, SyncPushRequest payload) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserId(username);

        int notesProcessed = processNotes(userId, payload.getNotes());
        int collectionsProcessed = processCollections(userId, payload.getCollections());
        int messagesProcessed = processMessages(userId, payload.getMessages());
        int favoritesProcessed = processFavorites(userId, payload.getFavorites());

        result.put("accepted", true);
        result.put("processed", Map.of(
                "notes", notesProcessed,
                "collections", collectionsProcessed,
                "messages", messagesProcessed,
                "favorites", favoritesProcessed
        ));
        result.put("serverTime", Instant.now().toString());
        return result;
    }

    private int processNotes(Long userId, List<SyncPushRequest.NotePushDto> notes) {
        if (notes == null) return 0;
        int count = 0;
        for (SyncPushRequest.NotePushDto noteData : notes) {
            if (noteData.getId() == null) {
                continue;
            }
            FlashNote existing = flashNoteMapper.selectOne(new LambdaQueryWrapper<FlashNote>()
                    .eq(FlashNote::getId, noteData.getId())
                    .eq(FlashNote::getUserId, userId));

            if (existing != null) {
                if (noteData.getTitle() != null) {
                    existing.setTitle(noteData.getTitle());
                }
                if (noteData.getContent() != null) {
                    existing.setContent(noteData.getContent());
                }
                if (noteData.getTags() != null) {
                    existing.setTags(noteData.getTags());
                }
                existing.setDeleted(noteData.resolveDeleted());
                flashNoteMapper.updateById(existing);
            } else {
                FlashNote note = new FlashNote();
                note.setId(noteData.getId());
                note.setUserId(userId);
                note.setTitle(noteData.getTitle());
                note.setContent(noteData.getContent());
                note.setTags(noteData.getTags());
                note.setDeleted(noteData.resolveDeleted());
                flashNoteMapper.insert(note);
            }
            count++;
        }
        return count;
    }

    private int processCollections(Long userId, List<SyncPushRequest.CollectionPushDto> collections) {
        if (collections == null) return 0;
        int count = 0;
        for (SyncPushRequest.CollectionPushDto collectionData : collections) {
            if (collectionData.getId() == null) {
                continue;
            }
            Collection existing = collectionMapper.selectOne(new LambdaQueryWrapper<Collection>()
                    .eq(Collection::getId, collectionData.getId())
                    .eq(Collection::getUserId, userId));

            if (existing != null) {
                if (collectionData.getName() != null) {
                    existing.setName(collectionData.getName());
                }
                if (collectionData.getDescription() != null) {
                    existing.setDescription(collectionData.getDescription());
                }
                collectionMapper.updateById(existing);
            } else {
                Collection collection = new Collection();
                collection.setId(collectionData.getId());
                collection.setUserId(userId);
                collection.setName(collectionData.getName());
                collection.setDescription(collectionData.getDescription());
                collectionMapper.insert(collection);
            }
            count++;
        }
        return count;
    }

    private int processMessages(Long userId, List<SyncPushRequest.MessagePushDto> messages) {
        if (messages == null) return 0;
        int count = 0;
        for (SyncPushRequest.MessagePushDto msgData : messages) {
            Long messageId = msgData.getId();
            String clientRequestId = msgData.getClientRequestId();

            Message existing = null;
            if (messageId != null) {
                existing = messageMapper.selectById(messageId);
            }
            if (existing == null && clientRequestId != null && !clientRequestId.isBlank()) {
                existing = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                        .eq(Message::getClientRequestId, clientRequestId)
                        .and(wrapper -> wrapper
                                .eq(Message::getSenderId, userId)
                                .or()
                                .eq(Message::getReceiverId, userId)));
            }

            if (existing != null) {
                if (!userId.equals(existing.getSenderId()) && !userId.equals(existing.getReceiverId())) {
                    continue;
                }
                if (msgData.getContent() != null) {
                    existing.setContent(msgData.getContent());
                }
                Boolean readStatus = msgData.getReadStatus();
                if (readStatus != null) {
                    existing.setReadStatus(readStatus);
                }
                Long flashNoteId = msgData.getFlashNoteId();
                if (flashNoteId != null) {
                    existing.setFlashNoteId(flashNoteId);
                }
                if (msgData.getRole() != null) {
                    existing.setRole(msgData.getRole());
                }
                if (clientRequestId != null && !clientRequestId.isBlank()) {
                    existing.setClientRequestId(clientRequestId);
                }
                messageMapper.updateById(existing);
            } else {
                Message message = new Message();
                if (messageId != null) {
                    message.setId(messageId);
                }
                if (msgData.getSenderId() != null) {
                    message.setSenderId(msgData.getSenderId());
                } else {
                    message.setSenderId(userId);
                }
                Long receiverId = msgData.getReceiverId();
                if (receiverId != null) {
                    message.setReceiverId(receiverId);
                } else {
                    message.setReceiverId(userId);
                }
                if (clientRequestId != null && !clientRequestId.isBlank()) {
                    message.setClientRequestId(clientRequestId);
                }
                message.setContent(msgData.getContent());
                Boolean readStatus = msgData.getReadStatus();
                message.setReadStatus(readStatus != null ? readStatus : false);
                Long flashNoteId = msgData.getFlashNoteId();
                if (flashNoteId != null) {
                    message.setFlashNoteId(flashNoteId);
                }
                if (msgData.getRole() != null) {
                    message.setRole(msgData.getRole());
                } else {
                    message.setRole("user");
                }
                if (msgData.getMediaType() != null) {
                    message.setMediaType(msgData.getMediaType());
                }
                if (msgData.getMediaUrl() != null) {
                    message.setMediaUrl(msgData.getMediaUrl());
                }
                if (msgData.getMediaDuration() != null) {
                    message.setMediaDuration(msgData.getMediaDuration());
                }
                if (msgData.getThumbnailUrl() != null) {
                    message.setThumbnailUrl(msgData.getThumbnailUrl());
                }
                if (msgData.getFileName() != null) {
                    message.setFileName(msgData.getFileName());
                }
                if (msgData.getFileSize() != null) {
                    message.setFileSize(msgData.getFileSize());
                }
                if (msgData.getCreatedAt() != null && !msgData.getCreatedAt().isBlank()) {
                    message.setCreatedAt(LocalDateTime.parse(msgData.getCreatedAt()));
                }
                messageMapper.insert(message);
            }
            count++;
        }
        return count;
    }

    private int processFavorites(Long userId, List<SyncPushRequest.FavoritePushDto> favorites) {
        if (favorites == null) return 0;
        int count = 0;
        for (SyncPushRequest.FavoritePushDto favData : favorites) {
            Long messageId = favData.getMessageId();
            if (messageId == null) {
                continue;
            }

            Message message = messageMapper.selectById(messageId);
            if (message == null) {
                continue;
            }
            if (!userId.equals(message.getSenderId()) && !userId.equals(message.getReceiverId())) {
                continue;
            }

            FavoriteMessage existing = favoriteMessageMapper.selectOne(new LambdaQueryWrapper<FavoriteMessage>()
                    .eq(FavoriteMessage::getUserId, userId)
                    .eq(FavoriteMessage::getMessageId, messageId));
            if (existing == null) {
                FavoriteMessage favoriteMessage = new FavoriteMessage();
                favoriteMessage.setUserId(userId);
                favoriteMessage.setMessageId(messageId);
                try {
                    favoriteMessageMapper.insert(favoriteMessage);
                } catch (DuplicateKeyException e) {
                    log.debug("Favorite already synced for userId={}, messageId={}", userId, messageId);
                }
            }
            count++;
        }
        return count;
    }

    private Long getUserId(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return null;
        }
        return user.getId();
    }

    @Override
    public Map<String, Object> bootstrap(String username) {
        Map<String, Object> result = pull(username, null);
        result.put("bootstrap", true);
        return result;
    }
}
