package com.flashnote.message;

import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.common.service.CurrentUserService;
import com.flashnote.file.service.FileService;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.flashnote.message.dto.CompositeMessageRequest;
import com.flashnote.message.dto.MessageMergeRequest;
import com.flashnote.message.entity.CardItem;
import com.flashnote.message.entity.CardPayload;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import com.flashnote.message.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceImplTest {

    @Test
    void subscribeUsesFiniteTimeoutAndKeepsLatestEmitter() throws Exception {
        MessageServiceImpl service = new MessageServiceImpl(
                mock(MessageMapper.class), mockUserMapper(), mock(FlashNoteMapper.class),
                mock(FileService.class), mockCurrentUserService());

        SseEmitter first = service.subscribe("alice");
        SseEmitter second = service.subscribe("alice");

        assertEquals(30000L, first.getTimeout());
        assertEquals(30000L, second.getTimeout());
        assertSame(second, currentEmitter(service, 1L));
    }

    @Test
    void sendMessagePreservesClientRequestId() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);

        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "alice"));

        MessageServiceImpl service = new MessageServiceImpl(
                messageMapper, userMapper, flashNoteMapper,
                mock(FileService.class), mockCurrentUserService());
        Message message = new Message();
        message.setContent("hello");
        message.setClientRequestId("req-123");

        Message result = service.sendMessage("alice", message);

        assertSame(message, result);
        assertEquals("req-123", result.getClientRequestId());
        verify(messageMapper).insert(message);
    }

    @Test
    void sendMessageRejectsForeignFlashNote() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);

        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "alice"));

        FlashNote flashNote = new FlashNote();
        flashNote.setId(9L);
        flashNote.setUserId(2L);
        when(flashNoteMapper.selectById(9L)).thenReturn(flashNote);

        MessageServiceImpl service = new MessageServiceImpl(
                messageMapper, userMapper, flashNoteMapper,
                mock(FileService.class), mockCurrentUserService());
        Message message = new Message();
        message.setFlashNoteId(9L);
        message.setContent("hello");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.sendMessage("alice", message));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void mergeMessagesRejectsEmptyMessageIds() {
        MessageServiceImpl service = new MessageServiceImpl(
                mock(MessageMapper.class), mockUserMapper(), mock(FlashNoteMapper.class),
                mock(FileService.class), mockCurrentUserService());

        MessageMergeRequest request = new MessageMergeRequest();
        request.setTitle("卡片标题");
        request.setFlashNoteId(1L);
        request.setMessageIds(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.mergeMessages("alice", request));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
        assertEquals("No messages to merge", exception.getMessage());
    }

    @Test
    void mergeMessagesRejectsTooManyMessages() {
        MessageServiceImpl service = new MessageServiceImpl(
                mock(MessageMapper.class), mockUserMapper(), mock(FlashNoteMapper.class),
                mock(FileService.class), mockCurrentUserService());

        List<Long> messageIds = new ArrayList<>();
        for (long i = 1; i <= 51; i++) {
            messageIds.add(i);
        }

        MessageMergeRequest request = new MessageMergeRequest();
        request.setTitle("卡片标题");
        request.setFlashNoteId(1L);
        request.setMessageIds(messageIds);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.mergeMessages("alice", request));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
        assertEquals("Cannot merge more than 50 messages", exception.getMessage());
    }

    @Test
    void mergeMessagesRejectsUnauthorizedSourceMessages() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        when(flashNoteMapper.selectById(99L)).thenReturn(buildFlashNote(99L, 1L));
        when(messageMapper.selectBatchIds(List.of(10L, 11L))).thenReturn(List.of(
                buildMessage(10L, 2L, 3L, 99L, "TEXT", "a"),
                buildMessage(11L, 2L, 3L, 99L, "TEXT", "b")
        ));

        MessageServiceImpl service = new MessageServiceImpl(
                messageMapper, mockUserMapper(), flashNoteMapper,
                mock(FileService.class), mockCurrentUserService());
        MessageMergeRequest request = new MessageMergeRequest();
        request.setTitle("卡片标题");
        request.setFlashNoteId(99L);
        request.setMessageIds(List.of(10L, 11L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.mergeMessages("alice", request));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        assertEquals("You can only merge your own conversation messages", exception.getMessage());
        verify(messageMapper, never()).insert(any());
    }

    @Test
    void mergeMessagesCreatesCompositePayloadInRequestedOrder() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        when(flashNoteMapper.selectById(88L)).thenReturn(buildFlashNote(88L, 1L));

        Message second = buildMessage(2L, 1L, 1L, 88L, "IMAGE", "第二条");
        second.setMediaUrl("image-2.png");
        second.setThumbnailUrl("thumb-2.png");

        Message first = buildMessage(1L, 1L, 1L, 88L, "TEXT", "第一条");

        when(messageMapper.selectBatchIds(List.of(2L, 1L))).thenReturn(List.of(first, second));

        MessageServiceImpl service = new MessageServiceImpl(
                messageMapper, mockUserMapper(), flashNoteMapper,
                mock(FileService.class), mockCurrentUserService());
        MessageMergeRequest request = new MessageMergeRequest();
        request.setTitle("周报卡片");
        request.setFlashNoteId(88L);
        request.setMessageIds(List.of(2L, 1L));

        service.mergeMessages("alice", request);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper).insert(captor.capture());
        Message composite = captor.getValue();

        assertEquals("COMPOSITE", composite.getMediaType());
        assertEquals("周报卡片", composite.getContent());
        assertEquals("周报卡片", composite.getFileName());
        assertEquals(88L, composite.getFlashNoteId());
        assertEquals(1L, composite.getSenderId());
        assertEquals(1L, composite.getReceiverId());

        CardPayload payload = composite.getPayload();
        assertNotNull(payload);
        assertEquals("MESSAGE_COLLECTION", payload.getCardType());
        assertEquals("周报卡片", payload.getTitle());
        assertEquals("第二条 等2条消息", payload.getSummary());
        assertEquals(2, payload.getItems().size());

        CardItem firstItem = payload.getItems().get(0);
        assertEquals(2L, firstItem.getOriginalMsgId());
        assertEquals("IMAGE", firstItem.getType());
        assertEquals("第二条", firstItem.getContent());
        assertEquals("image-2.png", firstItem.getUrl());
        assertEquals("thumb-2.png", firstItem.getThumbnailUrl());

        CardItem secondItem = payload.getItems().get(1);
        assertEquals(1L, secondItem.getOriginalMsgId());
        assertEquals("TEXT", secondItem.getType());
        assertEquals("第一条", secondItem.getContent());

        verify(flashNoteMapper).updateById(any(FlashNote.class));
    }

    @Test
    void createCompositeMessageBuildsCardWithUserOwnedItems() {
        // D1-W22-03 单类型 image → IMAGE_COLLECTION
        MessageMapper messageMapper = mock(MessageMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        when(flashNoteMapper.selectById(7L)).thenReturn(buildFlashNote(7L, 1L));

        MessageServiceImpl service = new MessageServiceImpl(
                messageMapper, mockUserMapper(), flashNoteMapper,
                mock(FileService.class), mockCurrentUserService());

        CompositeMessageRequest req = new CompositeMessageRequest();
        req.setTitle("旅行图集");
        req.setFlashNoteId(7L);
        CompositeMessageRequest.Item a = new CompositeMessageRequest.Item();
        a.setType("image");
        a.setMediaUrl("1/aaa.jpg");
        a.setFileName("aaa.jpg");
        CompositeMessageRequest.Item b = new CompositeMessageRequest.Item();
        b.setType("image");
        b.setMediaUrl("1/bbb.jpg");
        b.setFileName("bbb.jpg");
        req.setItems(List.of(a, b));

        Message composite = service.createCompositeMessage("alice", req);

        assertNotNull(composite);
        assertEquals("COMPOSITE", composite.getMediaType());
        assertEquals(7L, composite.getFlashNoteId());
        assertEquals(1L, composite.getSenderId());
        assertEquals(1L, composite.getReceiverId());
        CardPayload payload = composite.getPayload();
        assertNotNull(payload);
        assertEquals("IMAGE_COLLECTION", payload.getCardType());
        assertEquals("旅行图集", payload.getTitle());
        assertEquals(2, payload.getItems().size());
        assertEquals("1/aaa.jpg", payload.getItems().get(0).getUrl());
        assertEquals("旅行图集 等2项", payload.getSummary());
    }

    @Test
    void createCompositeMessageMixedTypesYieldsCompositeCard() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        when(flashNoteMapper.selectById(7L)).thenReturn(buildFlashNote(7L, 1L));

        MessageServiceImpl service = new MessageServiceImpl(
                messageMapper, mockUserMapper(), flashNoteMapper,
                mock(FileService.class), mockCurrentUserService());

        CompositeMessageRequest req = new CompositeMessageRequest();
        req.setTitle("混合卡片");
        req.setContent("简短摘要");
        req.setFlashNoteId(7L);
        CompositeMessageRequest.Item a = new CompositeMessageRequest.Item();
        a.setType("image");
        a.setMediaUrl("1/x.jpg");
        CompositeMessageRequest.Item b = new CompositeMessageRequest.Item();
        b.setType("file");
        b.setMediaUrl("1/y.pdf");
        req.setItems(List.of(a, b));

        Message composite = service.createCompositeMessage("alice", req);
        assertEquals("COMPOSITE_CARD", composite.getPayload().getCardType());
        assertEquals("简短摘要", composite.getPayload().getSummary());
    }

    @Test
    void createCompositeMessageRejectsForeignMediaUrl() {
        // 安全校验：item.mediaUrl 必须以 "<userId>/" 开头
        MessageMapper messageMapper = mock(MessageMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        when(flashNoteMapper.selectById(7L)).thenReturn(buildFlashNote(7L, 1L));

        MessageServiceImpl service = new MessageServiceImpl(
                messageMapper, mockUserMapper(), flashNoteMapper,
                mock(FileService.class), mockCurrentUserService());

        CompositeMessageRequest req = new CompositeMessageRequest();
        req.setTitle("非法引用");
        req.setFlashNoteId(7L);
        CompositeMessageRequest.Item a = new CompositeMessageRequest.Item();
        a.setType("image");
        a.setMediaUrl("99/other.jpg"); // 别人的 userId
        req.setItems(List.of(a));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createCompositeMessage("alice", req));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(messageMapper, never()).insert(any(Message.class));
    }

    @Test
    void createCompositeMessageRejectsEmptyItemsAndOverflow() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        MessageServiceImpl service = new MessageServiceImpl(
                messageMapper, mockUserMapper(), mock(FlashNoteMapper.class),
                mock(FileService.class), mockCurrentUserService());

        // 空 items
        CompositeMessageRequest empty = new CompositeMessageRequest();
        empty.setTitle("t");
        empty.setFlashNoteId(7L);
        empty.setItems(List.of());
        assertThrows(BusinessException.class,
                () -> service.createCompositeMessage("alice", empty));

        // 10 个 items 溢出
        CompositeMessageRequest tooMany = new CompositeMessageRequest();
        tooMany.setTitle("t");
        tooMany.setFlashNoteId(7L);
        List<CompositeMessageRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            CompositeMessageRequest.Item it = new CompositeMessageRequest.Item();
            it.setType("image");
            it.setMediaUrl("1/img-" + i + ".jpg");
            items.add(it);
        }
        tooMany.setItems(items);
        assertThrows(BusinessException.class,
                () -> service.createCompositeMessage("alice", tooMany));

        // title 缺失
        CompositeMessageRequest noTitle = new CompositeMessageRequest();
        noTitle.setFlashNoteId(7L);
        CompositeMessageRequest.Item ok = new CompositeMessageRequest.Item();
        ok.setType("image");
        ok.setMediaUrl("1/a.jpg");
        noTitle.setItems(List.of(ok));
        assertThrows(BusinessException.class,
                () -> service.createCompositeMessage("alice", noTitle));

        verify(messageMapper, never()).insert(any(Message.class));
    }

    private UserMapper mockUserMapper() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "alice"));
        return userMapper;
    }

    private CurrentUserService mockCurrentUserService() {
        CurrentUserService cs = mock(CurrentUserService.class);
        when(cs.getRequiredUserId("alice")).thenReturn(1L);
        return cs;
    }

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private FlashNote buildFlashNote(Long id, Long userId) {
        FlashNote flashNote = new FlashNote();
        flashNote.setId(id);
        flashNote.setUserId(userId);
        return flashNote;
    }

    private Message buildMessage(Long id,
                                 Long senderId,
                                 Long receiverId,
                                 Long flashNoteId,
                                 String mediaType,
                                 String content) {
        Message message = new Message();
        message.setId(id);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setFlashNoteId(flashNoteId);
        message.setMediaType(mediaType);
        message.setContent(content);
        return message;
    }

    private SseEmitter currentEmitter(MessageServiceImpl service, Long userId) throws Exception {
        Field emitterMapField = MessageServiceImpl.class.getDeclaredField("emitterMap");
        emitterMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, SseEmitter> emitterMap = (Map<Long, SseEmitter>) emitterMapField.get(service);
        return emitterMap.get(userId);
    }
}
