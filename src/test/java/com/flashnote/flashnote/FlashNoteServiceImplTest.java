package com.flashnote.flashnote;

import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.flashnote.dto.FlashNoteSearchResponse;
import com.flashnote.flashnote.dto.FlashNoteSearchResult;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.flashnote.flashnote.service.impl.FlashNoteServiceImpl;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlashNoteServiceImplTest {

    private UserMapper createMockUserMapper(Long userId, String username) {
        UserMapper userMapper = mock(UserMapper.class);
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        when(userMapper.selectOne(any())).thenReturn(user);
        return userMapper;
    }

    @Test
    void listNotes_returnsUserNotes() {
        // Given
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        FlashNote note1 = new FlashNote();
        note1.setId(1L);
        note1.setUserId(1L);
        note1.setTitle("Note 1");
        note1.setContent("Content 1");
        note1.setDeleted(false);

        FlashNote note2 = new FlashNote();
        note2.setId(2L);
        note2.setUserId(1L);
        note2.setTitle("Note 2");
        note2.setContent("Content 2");
        note2.setDeleted(false);

        when(flashNoteMapper.selectList(any())).thenReturn(List.of(note1, note2));

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        // When
        List<FlashNote> result = service.listNotes("alice");

        // Then
        assertEquals(3, result.size());
        assertEquals("收集箱", result.get(0).getTitle());
        assertEquals("Note 1", result.get(1).getTitle());
        assertEquals("Note 2", result.get(2).getTitle());
        verify(flashNoteMapper).selectList(any());
    }

    @Test
    void listNotes_returnsEmptyListWhenNoNotes() {
        // Given
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        when(flashNoteMapper.selectList(any())).thenReturn(Collections.emptyList());

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        // When
        List<FlashNote> result = service.listNotes("alice");

        // Then
        assertEquals(1, result.size());
        assertEquals("收集箱", result.get(0).getTitle());
    }

    @Test
    void searchNotes_filtersByTitleOrContent() {
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        FlashNote titleMatch = new FlashNote();
        titleMatch.setId(1L);
        titleMatch.setTitle("旅行计划");
        titleMatch.setContent("五月出发");
        titleMatch.setDeleted(false);
        titleMatch.setUpdatedAt(LocalDateTime.now());

        FlashNote contentMatch = new FlashNote();
        contentMatch.setId(2L);
        contentMatch.setTitle("周会");
        contentMatch.setContent("旅行预算确认");
        contentMatch.setDeleted(false);
        contentMatch.setUpdatedAt(LocalDateTime.now().minusHours(1));

        when(flashNoteMapper.selectList(any())).thenReturn(List.of(titleMatch, contentMatch));
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        FlashNoteSearchResponse response = service.searchNotes("alice", "旅行");
        List<FlashNoteSearchResult> result = new java.util.ArrayList<>();
        if (response.getNoteNameMatched() != null) result.addAll(response.getNoteNameMatched());
        if (response.getMessageContentMatched() != null) result.addAll(response.getMessageContentMatched());

        assertEquals(2, result.size());
        assertEquals("旅行计划", result.get(0).getFlashNote().getTitle());
        assertNull(result.get(0).getMatchedMessages());
        verify(flashNoteMapper).selectList(any());
    }

    @Test
    void searchNotes_withBlankQueryFallsBackToList() {
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        
        FlashNote note = new FlashNote();
        note.setId(1L);
        note.setTitle("Test");
        note.setUpdatedAt(LocalDateTime.now());
        
        when(flashNoteMapper.selectList(any())).thenReturn(List.of(note));

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        FlashNoteSearchResponse response = service.searchNotes("alice", "   ");
        List<FlashNoteSearchResult> result = new java.util.ArrayList<>();
        if (response.getNoteNameMatched() != null) result.addAll(response.getNoteNameMatched());
        if (response.getMessageContentMatched() != null) result.addAll(response.getMessageContentMatched());

        assertEquals(2, result.size());
        assertEquals("收集箱", result.get(0).getFlashNote().getTitle());
        assertEquals("Test", result.get(1).getFlashNote().getTitle());
    }

    @Test
    void searchNotes_matchesByMessageContent() {
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        FlashNote note = new FlashNote();
        note.setId(1L);
        note.setUserId(1L);
        note.setTitle("Note Title");
        note.setContent("Note Content");
        note.setDeleted(false);
        note.setUpdatedAt(LocalDateTime.now());

        Message matchedMessage = new Message();
        matchedMessage.setId(10L);
        matchedMessage.setSenderId(1L);
        matchedMessage.setReceiverId(2L);
        matchedMessage.setContent("This is a message about budget");
        matchedMessage.setFlashNoteId(1L);

        when(messageMapper.selectList(any())).thenReturn(List.of(matchedMessage));
        when(flashNoteMapper.selectList(any())).thenReturn(List.of(note));

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        FlashNoteSearchResponse response = service.searchNotes("alice", "budget");
        List<FlashNoteSearchResult> result = new java.util.ArrayList<>();
        if (response.getNoteNameMatched() != null) result.addAll(response.getNoteNameMatched());
        if (response.getMessageContentMatched() != null) result.addAll(response.getMessageContentMatched());

        assertEquals(1, result.size());
        assertEquals("Note Title", result.get(0).getFlashNote().getTitle());
        assertNotNull(result.get(0).getMatchedMessages());
        assertEquals(1, result.get(0).getMatchedMessages().size());
        assertEquals(10L, result.get(0).getMatchedMessages().get(0).getMessageId());
    }

    @Test
    void searchNotes_deduplicatesResults() {
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        FlashNote note = new FlashNote();
        note.setId(1L);
        note.setUserId(1L);
        note.setTitle("旅行计划");
        note.setContent("五月出发");
        note.setDeleted(false);
        note.setUpdatedAt(LocalDateTime.now());

        Message matchedMessage = new Message();
        matchedMessage.setId(10L);
        matchedMessage.setSenderId(1L);
        matchedMessage.setReceiverId(2L);
        matchedMessage.setContent("讨论旅行预算");
        matchedMessage.setFlashNoteId(1L);

        when(flashNoteMapper.selectList(any())).thenReturn(List.of(note));
        when(messageMapper.selectList(any())).thenReturn(List.of(matchedMessage));

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        FlashNoteSearchResponse response = service.searchNotes("alice", "旅行");
        List<FlashNoteSearchResult> result = new java.util.ArrayList<>();
        if (response.getNoteNameMatched() != null) result.addAll(response.getNoteNameMatched());
        if (response.getMessageContentMatched() != null) result.addAll(response.getMessageContentMatched());

        assertEquals(1, result.size());
        assertEquals("旅行计划", result.get(0).getFlashNote().getTitle());
        assertNotNull(result.get(0).getMatchedMessages());
    }

    @Test
    void searchNotes_returnsSnippet() {
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        FlashNote note = new FlashNote();
        note.setId(1L);
        note.setUserId(1L);
        note.setTitle("Note");
        note.setContent("Content");
        note.setDeleted(false);
        note.setUpdatedAt(LocalDateTime.now());

        Message matchedMessage = new Message();
        matchedMessage.setId(10L);
        matchedMessage.setSenderId(1L);
        matchedMessage.setReceiverId(2L);
        matchedMessage.setContent("This is a long test message about the budget planning for the project");
        matchedMessage.setFlashNoteId(1L);

        when(flashNoteMapper.selectList(any())).thenReturn(List.of(note));
        when(messageMapper.selectList(any())).thenReturn(List.of(matchedMessage));

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        FlashNoteSearchResponse response = service.searchNotes("alice", "budget");
        List<FlashNoteSearchResult> result = new java.util.ArrayList<>();
        if (response.getNoteNameMatched() != null) result.addAll(response.getNoteNameMatched());
        if (response.getMessageContentMatched() != null) result.addAll(response.getMessageContentMatched());

        assertEquals(1, result.size());
        String snippet = result.get(0).getMatchedMessages().get(0).getSnippet();
        assertNotNull(snippet);
        assertTrue(snippet.contains("budget"));
    }

    @Test
    void createNote_withValidData_createsNote() {
        // Given
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        FlashNote inputNote = new FlashNote();
        inputNote.setTitle("New Note");
        inputNote.setIcon("💡");
        inputNote.setContent("New Content");
        inputNote.setTags("tag1,tag2");

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        // When
        FlashNote result = service.createNote("alice", inputNote);

        // Then
        assertEquals(1L, result.getUserId());
        assertEquals("New Note", result.getTitle());
        assertEquals("💡", result.getIcon());
        assertEquals("New Content", result.getContent());
        assertEquals("tag1,tag2", result.getTags());
        assertFalse(result.getDeleted());
        verify(flashNoteMapper).insert(result);
    }

    @Test
    void updateNote_withValidData_updatesNote() {
        // Given
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        FlashNote existingNote = new FlashNote();
        existingNote.setId(1L);
        existingNote.setUserId(1L);
        existingNote.setTitle("Old Title");
        existingNote.setIcon("📝");
        existingNote.setContent("Old Content");
        existingNote.setTags("old");
        existingNote.setDeleted(false);

        when(flashNoteMapper.selectOne(any())).thenReturn(existingNote);

        FlashNote updateData = new FlashNote();
        updateData.setTitle("Updated Title");
        updateData.setIcon("📚");
        updateData.setContent("Updated Content");
        updateData.setTags("updated");

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        // When
        FlashNote result = service.updateNote("alice", 1L, updateData);

        // Then
        assertEquals("Updated Title", result.getTitle());
        assertEquals("📚", result.getIcon());
        assertEquals("Updated Content", result.getContent());
        assertEquals("updated", result.getTags());
        verify(flashNoteMapper).updateById(existingNote);
    }

    @Test
    void updateNote_withWrongUser_throwsException() {
        // Given
        UserMapper userMapper = mock(UserMapper.class);
        User alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        User bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");
        when(userMapper.selectOne(any())).thenReturn(alice, bob);

        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        // Alice's note in DB
        FlashNote aliceNote = new FlashNote();
        aliceNote.setId(1L);
        aliceNote.setUserId(1L);
        aliceNote.setTitle("Alice's Note");
        aliceNote.setDeleted(false);

        // Bob tries to update Alice's note - query returns null because userId doesn't match
        when(flashNoteMapper.selectOne(any())).thenReturn(null);

        FlashNote updateData = new FlashNote();
        updateData.setTitle("Hacked");

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, 
            () -> service.updateNote("bob", 1L, updateData));
        
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        assertEquals("Note not found", exception.getMessage());
    }

    @Test
    void deleteNote_withValidId_deletesNote() {
        // Given
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        FlashNote existingNote = new FlashNote();
        existingNote.setId(1L);
        existingNote.setUserId(1L);
        existingNote.setTitle("To Delete");
        existingNote.setDeleted(false);

        when(flashNoteMapper.selectOne(any())).thenReturn(existingNote);

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        // When
        service.deleteNote("alice", 1L);

        // Then
        assertTrue(existingNote.getDeleted());
        verify(flashNoteMapper).updateById(existingNote);
    }

    @Test
    void deleteNote_withNonExistentId_throwsException() {
        // Given
        UserMapper userMapper = createMockUserMapper(1L, "alice");
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        when(flashNoteMapper.selectOne(any())).thenReturn(null);

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, 
            () -> service.deleteNote("alice", 999L));
        
        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        assertEquals("Note not found", exception.getMessage());
    }

    @Test
    void listNotes_withInvalidUser_throwsException() {
        // Given
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectOne(any())).thenReturn(null);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        FlashNoteServiceImpl service = new FlashNoteServiceImpl(userMapper, flashNoteMapper, messageMapper);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, 
            () -> service.listNotes("nonexistent"));
        
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), exception.getCode());
    }
}
