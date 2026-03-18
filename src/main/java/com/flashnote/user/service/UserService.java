package com.flashnote.user.service;

import com.flashnote.user.entity.UserProfile;
import com.flashnote.user.dto.ContactSearchUserDto;
import com.flashnote.user.dto.ContactUserDto;
import com.flashnote.user.dto.FriendRequestDto;

import java.util.List;

public interface UserService {
    UserProfile getProfile(String username);

    UserProfile updateProfile(String username, UserProfile profile);

    String updateAvatar(String username, String avatarUrl);

    List<ContactUserDto> listContacts(String username);

    List<FriendRequestDto> listPendingRequests(String username);

    Long countPendingRequests(String username);

    void sendFriendRequest(String username, Long targetUserId);

    void acceptFriendRequest(String username, Long requestId);

    void rejectFriendRequest(String username, Long requestId);

    void removeContact(String username, Long contactUserId);

    List<ContactSearchUserDto> searchUsers(String username, String keyword);
}
