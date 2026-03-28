package com.flashnote.sync;

import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.collection.mapper.CollectionMapper;
import com.flashnote.collection.service.CollectionService;
import com.flashnote.favorite.mapper.FavoriteMessageMapper;
import com.flashnote.favorite.service.FavoriteService;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.flashnote.flashnote.service.FlashNoteService;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import com.flashnote.sync.dto.SyncPushRequest;
import com.flashnote.sync.service.impl.SyncServiceImpl;
import com.flashnote.user.entity.UserProfile;
import com.flashnote.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncServiceImplTest {

    private SyncServiceImpl buildService(FlashNoteService flashNoteService,
                                         CollectionService collectionService,
                                         UserService userService,
                                         UserMapper userMapper,
                                         FlashNoteMapper flashNoteMapper,
                                         CollectionMapper collectionMapper,
                                         MessageMapper messageMapper,
                                         FavoriteMessageMapper favoriteMessageMapper,
                                         FavoriteService favoriteService) {
        return new SyncServiceImpl(
                flashNoteService,
                collectionService,
                userService,
                userMapper,
                flashNoteMapper,
                collectionMapper,
                messageMapper,
                favoriteMessageMapper,
                favoriteService
        );
    }

    private SyncPushRequest buildRequest(List<SyncPushRequest.NotePushDto> notes,
                                         List<SyncPushRequest.CollectionPushDto> collections,
                                         List<SyncPushRequest.MessagePushDto> messages,
                                         List<SyncPushRequest.FavoritePushDto> favorites) {
        SyncPushRequest req = new SyncPushRequest();
        req.setNotes(notes);
        req.setCollections(collections);
        req.setMessages(messages);
        req.setFavorites(favorites);
        return req;
    }

    @Test
    void pullIncludesFavoritesPayload() {
        FlashNoteService flashNoteService = mock(FlashNoteService.class);
        CollectionService collectionService = mock(CollectionService.class);
        UserService userService = mock(UserService.class);
        UserMapper userMapper = mock(UserMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        FavoriteMessageMapper favoriteMessageMapper = mock(FavoriteMessageMapper.class);
        FavoriteService favoriteService = mock(FavoriteService.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userService.getProfile("alice")).thenReturn(new UserProfile());
        when(flashNoteService.listNotes("alice")).thenReturn(List.of());
        when(collectionService.listCollections("alice")).thenReturn(List.of());
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(favoriteService.listFavorites("alice")).thenReturn(List.of());

        SyncServiceImpl syncService = buildService(
                flashNoteService, collectionService, userService, userMapper,
                flashNoteMapper, collectionMapper, messageMapper, favoriteMessageMapper, favoriteService);

        Map<String, Object> result = syncService.pull("alice", null);
        assertTrue(result.containsKey("favorites"));
    }

    @Test
    void pushProcessesFavoritesPayload() {
        FlashNoteService flashNoteService = mock(FlashNoteService.class);
        CollectionService collectionService = mock(CollectionService.class);
        UserService userService = mock(UserService.class);
        UserMapper userMapper = mock(UserMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        FavoriteMessageMapper favoriteMessageMapper = mock(FavoriteMessageMapper.class);
        FavoriteService favoriteService = mock(FavoriteService.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userMapper.selectOne(any())).thenReturn(user);
        Message message = new Message();
        message.setId(3L);
        message.setSenderId(1L);
        message.setReceiverId(1L);
        when(messageMapper.selectById(3L)).thenReturn(message);

        SyncServiceImpl syncService = buildService(
                flashNoteService, collectionService, userService, userMapper,
                flashNoteMapper, collectionMapper, messageMapper, favoriteMessageMapper, favoriteService);

        SyncPushRequest.FavoritePushDto fav = new SyncPushRequest.FavoritePushDto();
        fav.setMessageId(3L);
        SyncPushRequest payload = buildRequest(List.of(), List.of(), List.of(), List.of(fav));

        Map<String, Object> result = syncService.push("alice", payload);
        Map<?, ?> processed = (Map<?, ?>) result.get("processed");

        assertEquals(1, processed.get("favorites"));
    }

    @Test
    void pushSkipsUpdatingForeignMessage() {
        FlashNoteService flashNoteService = mock(FlashNoteService.class);
        CollectionService collectionService = mock(CollectionService.class);
        UserService userService = mock(UserService.class);
        UserMapper userMapper = mock(UserMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        FavoriteMessageMapper favoriteMessageMapper = mock(FavoriteMessageMapper.class);
        FavoriteService favoriteService = mock(FavoriteService.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userMapper.selectOne(any())).thenReturn(user);

        Message foreignMessage = new Message();
        foreignMessage.setId(8L);
        foreignMessage.setSenderId(2L);
        foreignMessage.setReceiverId(2L);
        when(messageMapper.selectById(8L)).thenReturn(foreignMessage);

        SyncServiceImpl syncService = buildService(
                flashNoteService, collectionService, userService, userMapper,
                flashNoteMapper, collectionMapper, messageMapper, favoriteMessageMapper, favoriteService);

        SyncPushRequest.MessagePushDto msg = new SyncPushRequest.MessagePushDto();
        msg.setId(8L);
        msg.setContent("hijack");
        SyncPushRequest payload = buildRequest(List.of(), List.of(), List.of(msg), List.of());

        Map<String, Object> result = syncService.push("alice", payload);
        Map<?, ?> processed = (Map<?, ?>) result.get("processed");

        assertEquals(0, processed.get("messages"));
        verify(messageMapper, never()).updateById(any(Message.class));
    }

    @Test
    void pushIgnoresMissingFavoriteMessage() {
        FlashNoteService flashNoteService = mock(FlashNoteService.class);
        CollectionService collectionService = mock(CollectionService.class);
        UserService userService = mock(UserService.class);
        UserMapper userMapper = mock(UserMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        FavoriteMessageMapper favoriteMessageMapper = mock(FavoriteMessageMapper.class);
        FavoriteService favoriteService = mock(FavoriteService.class);

        User user = new User();
        user.setId(1L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(messageMapper.selectById(99L)).thenReturn(null);

        SyncServiceImpl syncService = buildService(
                flashNoteService, collectionService, userService, userMapper,
                flashNoteMapper, collectionMapper, messageMapper, favoriteMessageMapper, favoriteService);

        SyncPushRequest.FavoritePushDto fav = new SyncPushRequest.FavoritePushDto();
        fav.setMessageId(99L);
        SyncPushRequest payload = buildRequest(List.of(), List.of(), List.of(), List.of(fav));

        Map<String, Object> result = syncService.push("alice", payload);
        Map<?, ?> processed = (Map<?, ?>) result.get("processed");

        assertEquals(0, processed.get("favorites"));
    }

    @Test
    void pushFavoriteRemainsIdempotentWhenInsertRaces() {
        FlashNoteService flashNoteService = mock(FlashNoteService.class);
        CollectionService collectionService = mock(CollectionService.class);
        UserService userService = mock(UserService.class);
        UserMapper userMapper = mock(UserMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        FavoriteMessageMapper favoriteMessageMapper = mock(FavoriteMessageMapper.class);
        FavoriteService favoriteService = mock(FavoriteService.class);

        User user = new User();
        user.setId(1L);
        when(userMapper.selectOne(any())).thenReturn(user);

        Message message = new Message();
        message.setId(5L);
        message.setSenderId(1L);
        message.setReceiverId(1L);
        when(messageMapper.selectById(5L)).thenReturn(message);
        when(favoriteMessageMapper.selectOne(any())).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate")).when(favoriteMessageMapper).insert(any());

        SyncServiceImpl syncService = buildService(
                flashNoteService, collectionService, userService, userMapper,
                flashNoteMapper, collectionMapper, messageMapper, favoriteMessageMapper, favoriteService);

        SyncPushRequest.FavoritePushDto fav = new SyncPushRequest.FavoritePushDto();
        fav.setMessageId(5L);
        SyncPushRequest payload = buildRequest(List.of(), List.of(), List.of(), List.of(fav));

        Map<String, Object> result = syncService.push("alice", payload);
        Map<?, ?> processed = (Map<?, ?>) result.get("processed");

        assertEquals(1, processed.get("favorites"));
    }

    @Test
    void pushInsertsMessageWhenClientRequestIdIsNewEvenWithoutServerId() {
        FlashNoteService flashNoteService = mock(FlashNoteService.class);
        CollectionService collectionService = mock(CollectionService.class);
        UserService userService = mock(UserService.class);
        UserMapper userMapper = mock(UserMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        FavoriteMessageMapper favoriteMessageMapper = mock(FavoriteMessageMapper.class);
        FavoriteService favoriteService = mock(FavoriteService.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(messageMapper.selectById(any())).thenReturn(null);
        when(messageMapper.selectOne(any())).thenReturn(null);

        SyncServiceImpl syncService = buildService(
                flashNoteService, collectionService, userService, userMapper,
                flashNoteMapper, collectionMapper, messageMapper, favoriteMessageMapper, favoriteService);

        SyncPushRequest.MessagePushDto msg = new SyncPushRequest.MessagePushDto();
        msg.setClientRequestId("client-1");
        msg.setFlashNoteId(9L);
        msg.setContent("hello");
        msg.setCreatedAt("2026-03-27T10:00:00");
        SyncPushRequest payload = buildRequest(List.of(), List.of(), List.of(msg), List.of());

        Map<String, Object> result = syncService.push("alice", payload);
        Map<?, ?> processed = (Map<?, ?>) result.get("processed");

        assertEquals(1, processed.get("messages"));
        verify(messageMapper).insert(argThat(message -> "client-1".equals(message.getClientRequestId()) && Long.valueOf(9L).equals(message.getFlashNoteId())));
    }
}
