package com.flashnote.message;

import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import com.flashnote.message.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageServiceImplTest {

    @Test
    void sendMessageRejectsForeignFlashNote() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userMapper.selectOne(any())).thenReturn(user);

        FlashNote flashNote = new FlashNote();
        flashNote.setId(9L);
        flashNote.setUserId(2L);
        when(flashNoteMapper.selectById(9L)).thenReturn(flashNote);

        MessageServiceImpl service = new MessageServiceImpl(messageMapper, userMapper, flashNoteMapper);
        Message message = new Message();
        message.setFlashNoteId(9L);
        message.setContent("hello");

        assertThrows(BusinessException.class, () -> service.sendMessage("alice", message));
    }
}
