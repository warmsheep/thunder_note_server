package com.flashnote.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.common.service.CurrentUserService;
import com.flashnote.message.entity.Message;
import com.flashnote.message.mapper.MessageMapper;
import com.flashnote.user.dto.ContactSearchUserDto;
import com.flashnote.user.entity.FriendRelation;
import com.flashnote.user.entity.UserProfile;
import com.flashnote.user.mapper.FriendRelationMapper;
import com.flashnote.user.mapper.UserProfileMapper;
import com.flashnote.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Test
    void getProfile_whenProfileMissing_createsAndReturnsDerivedFields() {
        UserMapper userMapper = mock(UserMapper.class);
        UserProfileMapper userProfileMapper = mock(UserProfileMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setAvatar("avatar.png");
        user.setNickname("Alice");
        when(currentUserService.getRequiredUser("alice")).thenReturn(user);
        when(userProfileMapper.selectOne(any())).thenReturn(null);

        UserServiceImpl service = new UserServiceImpl(userMapper, userProfileMapper, mock(FriendRelationMapper.class), mock(MessageMapper.class), currentUserService);

        UserProfile profile = service.getProfile("alice");

        assertNotNull(profile);
        assertEquals(1L, profile.getUserId());
        assertEquals("avatar.png", profile.getAvatar());
        assertEquals("Alice", profile.getNickname());
        verify(userProfileMapper).insert(any(UserProfile.class));
    }

    @Test
    void updateProfile_updatesProfileAndUserProjectionFields() {
        UserMapper userMapper = mock(UserMapper.class);
        UserProfileMapper userProfileMapper = mock(UserProfileMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setAvatar("old.png");
        user.setNickname("Old");
        when(currentUserService.getRequiredUser("alice")).thenReturn(user);

        UserProfile existing = new UserProfile();
        existing.setId(10L);
        existing.setUserId(1L);
        when(userProfileMapper.selectOne(any())).thenReturn(existing);

        UserProfile incoming = new UserProfile();
        incoming.setBio("new bio");
        incoming.setPreferencesJson("{}");
        incoming.setAvatar("new.png");
        incoming.setNickname("New");

        UserServiceImpl service = new UserServiceImpl(userMapper, userProfileMapper, mock(FriendRelationMapper.class), mock(MessageMapper.class), currentUserService);

        UserProfile result = service.updateProfile("alice", incoming);

        assertEquals("new bio", result.getBio());
        assertEquals("{}", result.getPreferencesJson());
        assertEquals("new.png", result.getAvatar());
        assertEquals("New", result.getNickname());
        verify(userProfileMapper).updateById(existing);
        verify(userMapper).updateById(user);
    }

    @Test
    void searchUsers_returnsMappedSearchResults() {
        UserMapper userMapper = mock(UserMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        FriendRelationMapper friendRelationMapper = mock(FriendRelationMapper.class);

        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("alice");
        when(currentUserService.getRequiredUser("alice")).thenReturn(currentUser);

        User found = new User();
        found.setId(2L);
        found.setUsername("bob");
        found.setNickname("Bob");
        found.setAvatar("bob.png");
        found.setStatus(1);

        when(userMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<User> page = invocation.getArgument(0);
            page.setRecords(List.of(found));
            page.setTotal(1);
            return page;
        });
        when(friendRelationMapper.selectOne(any())).thenReturn(null);

        UserServiceImpl service = new UserServiceImpl(userMapper, mock(UserProfileMapper.class), friendRelationMapper, mock(MessageMapper.class), currentUserService);

        List<ContactSearchUserDto> result = service.searchUsers("alice", "bo");

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getUserId());
        assertEquals("bob", result.get(0).getUsername());
        assertEquals("NONE", result.get(0).getRelationStatus());
    }

    @Test
    void listContacts_resolvesLatestMessageAndRelationStatus() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        UserMapper userMapper = mock(UserMapper.class);
        FriendRelationMapper friendRelationMapper = mock(FriendRelationMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);

        User current = new User();
        current.setId(1L);
        current.setUsername("alice");
        when(currentUserService.getRequiredUser("alice")).thenReturn(current);

        FriendRelation relation = new FriendRelation();
        relation.setId(9L);
        relation.setRequesterId(1L);
        relation.setAddresseeId(2L);
        relation.setStatus("ACCEPTED");
        when(friendRelationMapper.selectList(any())).thenReturn(List.of(relation));

        User friend = new User();
        friend.setId(2L);
        friend.setUsername("bob");
        friend.setNickname("Bob");
        friend.setAvatar("bob.png");
        friend.setStatus(1);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(friend));

        Message latest = new Message();
        latest.setId(100L);
        latest.setSenderId(1L);
        latest.setReceiverId(2L);
        latest.setContent("hi");
        latest.setMediaType("TEXT");
        when(messageMapper.selectOne(any())).thenReturn(latest);

        UserServiceImpl service = new UserServiceImpl(userMapper, mock(UserProfileMapper.class), friendRelationMapper, messageMapper, currentUserService);

        var contacts = service.listContacts("alice");

        assertEquals(1, contacts.size());
        assertEquals("FRIEND", contacts.get(0).getRelationStatus());
        assertEquals("hi", contacts.get(0).getLatestMessage());
    }

    @Test
    void sendFriendRequest_rejectsSelf() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        User current = new User();
        current.setId(1L);
        current.setUsername("alice");
        when(currentUserService.getRequiredUser("alice")).thenReturn(current);

        UserServiceImpl service = new UserServiceImpl(mock(UserMapper.class), mock(UserProfileMapper.class), mock(FriendRelationMapper.class), mock(MessageMapper.class), currentUserService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendFriendRequest("alice", 1L));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("Cannot add yourself", ex.getMessage());
    }

    @Test
    void countPendingRequests_returnsCount() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        User current = new User();
        current.setId(1L);
        when(currentUserService.getRequiredUser("alice")).thenReturn(current);

        FriendRelationMapper friendRelationMapper = mock(FriendRelationMapper.class);
        when(friendRelationMapper.selectCount(any())).thenReturn(3L);

        UserServiceImpl service = new UserServiceImpl(mock(UserMapper.class), mock(UserProfileMapper.class), friendRelationMapper, mock(MessageMapper.class), currentUserService);

        Long count = service.countPendingRequests("alice");

        assertEquals(3L, count);
    }

    @Test
    void cancelFriendRequest_withInvalidRequestId_throwsBadRequest() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        User current = new User();
        current.setId(1L);
        when(currentUserService.getRequiredUser("alice")).thenReturn(current);

        FriendRelationMapper friendRelationMapper = mock(FriendRelationMapper.class);
        UserServiceImpl service = new UserServiceImpl(mock(UserMapper.class), mock(UserProfileMapper.class), friendRelationMapper, mock(MessageMapper.class), currentUserService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelFriendRequest("alice", 0L));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(friendRelationMapper, never()).selectById(any());
    }
}
