package com.flashnote.user.service;

import com.flashnote.user.entity.UserProfile;
import com.flashnote.user.dto.ContactUserDto;

import java.util.List;

public interface UserService {
    UserProfile getProfile(String username);

    UserProfile updateProfile(String username, UserProfile profile);

    String updateAvatar(String username, String avatarUrl);

    List<ContactUserDto> listContacts(String username);
}
