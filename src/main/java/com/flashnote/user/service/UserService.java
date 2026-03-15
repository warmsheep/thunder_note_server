package com.flashnote.user.service;

import com.flashnote.user.entity.UserProfile;

public interface UserService {
    UserProfile getProfile(String username);

    UserProfile updateProfile(String username, UserProfile profile);
}
