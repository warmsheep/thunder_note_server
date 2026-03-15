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
import com.flashnote.sync.service.SyncService;
import com.flashnote.user.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
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
    public Map<String, Object> pull(String username) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserId(username);
        result.put("profile", userService.getProfile(username));
        result.put("notes", flashNoteService.listNotes(username));
        result.put("collections", collectionService.listCollections(username));
        result.put("messages", messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .and(wrapper -> wrapper
                        .eq(Message::getSenderId, userId)
                        .or()
                        .eq(Message::getReceiverId, userId))
                .orderByAsc(Message::getCreatedAt)));
        result.put("favorites", favoriteService.listFavorites(username));
        result.put("serverTime", Instant.now().toString());
        return result;
    }

    @Override
    public Map<String, Object> push(String username, Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        
        Long userId = getUserId(username);
        
        int notesProcessed = 0;
        int collectionsProcessed = 0;
        int messagesProcessed = 0;
        int favoritesProcessed = 0;
        
        List<Map<String, Object>> notes = (List<Map<String, Object>>) payload.get("notes");
        if (notes != null) {
            notesProcessed = processNotes(userId, notes);
        }
        
        List<Map<String, Object>> collections = (List<Map<String, Object>>) payload.get("collections");
        if (collections != null) {
            collectionsProcessed = processCollections(userId, collections);
        }
        
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        if (messages != null) {
            messagesProcessed = processMessages(userId, messages);
        }

        List<Map<String, Object>> favorites = (List<Map<String, Object>>) payload.get("favorites");
        if (favorites != null) {
            favoritesProcessed = processFavorites(userId, favorites);
        }

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

    private int processNotes(Long userId, List<Map<String, Object>> notes) {
        int count = 0;
        for (Map<String, Object> noteData : notes) {
            Object idObj = noteData.get("id");
            if (idObj == null) {
                continue;
            }
            Long noteId = ((Number) idObj).longValue();
            
            FlashNote existing = flashNoteMapper.selectOne(new LambdaQueryWrapper<FlashNote>()
                    .eq(FlashNote::getId, noteId)
                    .eq(FlashNote::getUserId, userId));
            
            if (existing != null) {
                if (noteData.get("title") != null) {
                    existing.setTitle((String) noteData.get("title"));
                }
                if (noteData.get("content") != null) {
                    existing.setContent((String) noteData.get("content"));
                }
                if (noteData.get("tags") != null) {
                    existing.setTags((String) noteData.get("tags"));
                }
                Object deletedValue = noteData.get("is_deleted");
                if (deletedValue == null) {
                    deletedValue = noteData.get("deleted");
                }
                if (deletedValue instanceof Boolean deleted) {
                    existing.setDeleted(deleted);
                }
                flashNoteMapper.updateById(existing);
            } else {
                FlashNote note = new FlashNote();
                note.setId(noteId);
                note.setUserId(userId);
                note.setTitle((String) noteData.get("title"));
                note.setContent((String) noteData.get("content"));
                note.setTags((String) noteData.get("tags"));
                Object deletedValue = noteData.get("is_deleted");
                if (deletedValue == null) {
                    deletedValue = noteData.get("deleted");
                }
                note.setDeleted(deletedValue instanceof Boolean deleted ? deleted : false);
                flashNoteMapper.insert(note);
            }
            count++;
        }
        return count;
    }

    private int processCollections(Long userId, List<Map<String, Object>> collections) {
        int count = 0;
        for (Map<String, Object> collectionData : collections) {
            Object idObj = collectionData.get("id");
            if (idObj == null) {
                continue;
            }
            Long collectionId = ((Number) idObj).longValue();
            
            Collection existing = collectionMapper.selectOne(new LambdaQueryWrapper<Collection>()
                    .eq(Collection::getId, collectionId)
                    .eq(Collection::getUserId, userId));
            
            if (existing != null) {
                if (collectionData.get("name") != null) {
                    existing.setName((String) collectionData.get("name"));
                }
                if (collectionData.get("description") != null) {
                    existing.setDescription((String) collectionData.get("description"));
                }
                collectionMapper.updateById(existing);
            } else {
                Collection collection = new Collection();
                collection.setId(collectionId);
                collection.setUserId(userId);
                collection.setName((String) collectionData.get("name"));
                collection.setDescription((String) collectionData.get("description"));
                collectionMapper.insert(collection);
            }
            count++;
        }
        return count;
    }

    private int processMessages(Long userId, List<Map<String, Object>> messages) {
        int count = 0;
        for (Map<String, Object> messageData : messages) {
            Object idObj = messageData.get("id");
            if (idObj == null) {
                continue;
            }
            Long messageId = ((Number) idObj).longValue();
            
            Message existing = messageMapper.selectById(messageId);

            if (existing != null) {
                if (!userId.equals(existing.getSenderId()) && !userId.equals(existing.getReceiverId())) {
                    continue;
                }
                if (messageData.get("content") != null) {
                    existing.setContent((String) messageData.get("content"));
                }
                if (messageData.get("read_status") != null) {
                    existing.setReadStatus((Boolean) messageData.get("read_status"));
                }
                Object flashNoteIdValue = messageData.get("flash_note_id");
                if (flashNoteIdValue == null) {
                    flashNoteIdValue = messageData.get("flashNoteId");
                }
                if (flashNoteIdValue instanceof Number flashNoteId) {
                    existing.setFlashNoteId(flashNoteId.longValue());
                }
                if (messageData.get("role") != null) {
                    existing.setRole((String) messageData.get("role"));
                }
                messageMapper.updateById(existing);
            } else {
                Message message = new Message();
                message.setId(messageId);
                message.setSenderId(userId);
                Object receiverIdObj = messageData.get("receiver_id");
                if (receiverIdObj != null) {
                    message.setReceiverId(((Number) receiverIdObj).longValue());
                } else {
                    message.setReceiverId(userId);
                }
                message.setContent((String) messageData.get("content"));
                message.setReadStatus(messageData.get("read_status") != null ? (Boolean) messageData.get("read_status") : false);
                Object flashNoteIdValue = messageData.get("flash_note_id");
                if (flashNoteIdValue == null) {
                    flashNoteIdValue = messageData.get("flashNoteId");
                }
                if (flashNoteIdValue instanceof Number flashNoteId) {
                    message.setFlashNoteId(flashNoteId.longValue());
                }
                if (messageData.get("role") != null) {
                    message.setRole((String) messageData.get("role"));
                } else {
                    message.setRole("user");
                }
                messageMapper.insert(message);
            }
            count++;
        }
        return count;
    }

    private int processFavorites(Long userId, List<Map<String, Object>> favorites) {
        int count = 0;
        for (Map<String, Object> favoriteData : favorites) {
            Object messageIdValue = favoriteData.get("messageId");
            if (messageIdValue == null) {
                messageIdValue = favoriteData.get("message_id");
            }
            if (!(messageIdValue instanceof Number messageIdNumber)) {
                continue;
            }

            Long messageId = messageIdNumber.longValue();
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
                } catch (DuplicateKeyException ignored) {
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
        Map<String, Object> result = pull(username);
        result.put("bootstrap", true);
        return result;
    }
}
