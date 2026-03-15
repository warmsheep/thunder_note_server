package com.flashnote.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.dto.LoginRequest;
import com.flashnote.auth.dto.LoginResponse;
import com.flashnote.auth.dto.RegisterRequest;
import com.flashnote.auth.dto.UserInfo;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.auth.service.AuthService;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.common.utils.JwtUtil;
import com.flashnote.common.utils.RedisUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    public AuthServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           RedisUtil redisUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Username or password is incorrect");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());
        redisUtil.set(buildRefreshTokenKey(user.getId()), refreshToken, jwtUtil.getRefreshExpirationSeconds());

        UserInfo userInfo = new UserInfo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatar()
        );

        return new LoginResponse(accessToken, refreshToken, "Bearer", jwtUtil.getAccessExpirationSeconds(), userInfo);
    }

    @Override
    public void register(RegisterRequest request) {
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .or()
                .eq(User::getEmail, request.getEmail()));

        if (existingUser != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username or email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getUsername());
        user.setStatus(1);
        userMapper.insert(user);
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken, "refresh")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }

        String cachedToken = redisUtil.get(buildRefreshTokenKey(userId));
        if (cachedToken == null || !cachedToken.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token has expired");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User does not exist");
        }

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());
        redisUtil.set(buildRefreshTokenKey(userId), newRefreshToken, jwtUtil.getRefreshExpirationSeconds());

        UserInfo userInfo = new UserInfo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatar()
        );

        return new LoginResponse(newAccessToken, newRefreshToken, "Bearer", jwtUtil.getAccessExpirationSeconds(), userInfo);
    }

    @Override
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }

        String token = accessToken.startsWith("Bearer ") ? accessToken.substring(7) : accessToken;
        if (!jwtUtil.validateToken(token, "access")) {
            return;
        }

        Long userId = jwtUtil.getUserId(token);
        if (userId != null) {
            redisUtil.delete(buildRefreshTokenKey(userId));
        }
    }

    private String buildRefreshTokenKey(Long userId) {
        return "auth:refresh:" + userId;
    }
}
