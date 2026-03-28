package com.flashnote.collection;

import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.collection.dto.CollectionUpdateRequest;
import com.flashnote.collection.entity.Collection;
import com.flashnote.collection.mapper.CollectionMapper;
import com.flashnote.collection.service.impl.CollectionServiceImpl;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.common.service.CurrentUserService;
import com.flashnote.flashnote.entity.FlashNote;
import com.flashnote.flashnote.mapper.FlashNoteMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollectionServiceImplTest {

    private CurrentUserService mockCurrentUserService() {
        CurrentUserService cs = mock(CurrentUserService.class);
        when(cs.getRequiredUserId("alice")).thenReturn(1L);
        when(cs.getRequiredUserId("bob")).thenReturn(2L);
        return cs;
    }

    @Test
    void updateCollection_renamesFlashNoteTags() {
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);

        Collection existing = new Collection();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setName("工作");
        when(collectionMapper.selectOne(any())).thenReturn(existing);
        when(collectionMapper.updateById(any())).thenReturn(1);

        FlashNote note = new FlashNote();
        note.setId(100L);
        note.setUserId(1L);
        note.setTitle("周会");
        note.setTags("工作");
        note.setDeleted(false);

        CollectionUpdateRequest incoming = new CollectionUpdateRequest();
        incoming.setName("项目");
        incoming.setDescription("desc");

        CollectionServiceImpl service = new CollectionServiceImpl(null, collectionMapper, flashNoteMapper, mockCurrentUserService());

        Collection result = service.updateCollection("alice", 10L, incoming);

        assertEquals("项目", result.getName());
        verify(flashNoteMapper).update(eq(null), any(UpdateWrapper.class));
    }

    @Test
    void deleteCollection_clearsFlashNoteTags() {
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);

        Collection existing = new Collection();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setName("工作");
        when(collectionMapper.selectOne(any())).thenReturn(existing);
        when(collectionMapper.delete(any())).thenReturn(1);

        FlashNote note = new FlashNote();
        note.setId(100L);
        note.setUserId(1L);
        note.setTitle("周会");
        note.setTags("工作");
        note.setDeleted(false);

        CollectionServiceImpl service = new CollectionServiceImpl(null, collectionMapper, flashNoteMapper, mockCurrentUserService());

        service.deleteCollection("alice", 10L);

        verify(flashNoteMapper).update(eq(null), any(UpdateWrapper.class));
    }

    @Test
    void deleteCollection_missingCollectionThrows() {
        CollectionMapper collectionMapper = mock(CollectionMapper.class);
        FlashNoteMapper flashNoteMapper = mock(FlashNoteMapper.class);
        when(collectionMapper.selectOne(any())).thenReturn(null);

        CollectionServiceImpl service = new CollectionServiceImpl(null, collectionMapper, flashNoteMapper, mockCurrentUserService());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteCollection("alice", 10L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(flashNoteMapper, never()).selectList(any());
    }
}
