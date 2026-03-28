package com.flashnote.favorite;

import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.service.CurrentUserService;
import com.flashnote.favorite.dto.FavoriteMessageItem;
import com.flashnote.favorite.entity.FavoriteMessage;
import com.flashnote.favorite.mapper.FavoriteMessageMapper;
import com.flashnote.favorite.service.impl.FavoriteServiceImpl;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FavoriteServiceImplTest {

    private CurrentUserService mockCurrentUserService() {
        CurrentUserService cs = mock(CurrentUserService.class);
        when(cs.getRequiredUserId("alice")).thenReturn(1L);
        return cs;
    }

    @Test
    void addFavoriteIsIdempotentWhenUniqueConstraintRaces() {
        UserMapper userMapper = mock(UserMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        FavoriteMessageMapper favoriteMessageMapper = mock(FavoriteMessageMapper.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userMapper.selectOne(any())).thenReturn(user);

        Message message = new Message();
        message.setId(5L);
        message.setSenderId(1L);
        message.setReceiverId(1L);
        message.setContent("hello");
        when(messageMapper.selectById(5L)).thenReturn(message);

        when(favoriteMessageMapper.selectOne(any())).thenReturn(null)
                .thenReturn(existingFavorite(8L, 1L, 5L));
        doThrow(new DuplicateKeyException("duplicate")).when(favoriteMessageMapper).insert(any(FavoriteMessage.class));

        FavoriteServiceImpl service = new FavoriteServiceImpl(userMapper, messageMapper, flashNoteMapper, favoriteMessageMapper, mockCurrentUserService());

        FavoriteMessageItem item = service.addFavorite("alice", 5L);

        assertEquals(5L, item.getMessageId());
    }

    @Test
    void listFavoritesDoesNotExposeForeignFlashNoteTitle() {
        UserMapper userMapper = mock(UserMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        FavoriteMessageMapper favoriteMessageMapper = mock(FavoriteMessageMapper.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(favoriteMessageMapper.selectList(any())).thenReturn(List.of(existingFavorite(9L, 1L, 7L)));

        Message message = new Message();
        message.setId(7L);
        message.setSenderId(1L);
        message.setReceiverId(1L);
        message.setContent("saved");
        message.setFlashNoteId(11L);
        when(messageMapper.selectById(7L)).thenReturn(message);

        FlashNote foreignNote = new FlashNote();
        foreignNote.setId(11L);
        foreignNote.setUserId(2L);
        foreignNote.setTitle("foreign");
        when(flashNoteMapper.selectById(11L)).thenReturn(foreignNote);

        FavoriteServiceImpl service = new FavoriteServiceImpl(userMapper, messageMapper, flashNoteMapper, favoriteMessageMapper, mockCurrentUserService());

        List<FavoriteMessageItem> items = service.listFavorites("alice");

        assertEquals(1, items.size());
        assertNull(items.get(0).getFlashNoteTitle());
    }

    private FavoriteMessage existingFavorite(Long id, Long userId, Long messageId) {
        FavoriteMessage favorite = new FavoriteMessage();
        favorite.setId(id);
        favorite.setUserId(userId);
        favorite.setMessageId(messageId);
        return favorite;
    }
}
