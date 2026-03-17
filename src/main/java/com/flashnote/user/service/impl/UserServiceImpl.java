package com.flashnote.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.user.entity.UserProfile;
import com.flashnote.user.mapper.UserProfileMapper;
import com.flashnote.user.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;

    public UserServiceImpl(UserMapper userMapper, UserProfileMapper userProfileMapper) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
    }

    @Override
    public UserProfile getProfile(String username) {
        User user = getRequiredUser(username);
        UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, user.getId()));

        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(user.getId());
            profileMapperInsert(profile);
        }
        return profile;
    }

    @Override
    public UserProfile updateProfile(String username, UserProfile incoming) {
        UserProfile current = getProfile(username);
        current.setBio(incoming.getBio());
        current.setPreferencesJson(incoming.getPreferencesJson());
        userProfileMapper.updateById(current);
        return current;
    }

    @Override
    public String updateAvatar(String username, String avatarUrl) {
        User user = getRequiredUser(username);
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
        return avatarUrl;
    }

    private User getRequiredUser(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        return user;
    }

    private void profileMapperInsert(UserProfile profile) {
        userProfileMapper.insert(profile);
    }
}
