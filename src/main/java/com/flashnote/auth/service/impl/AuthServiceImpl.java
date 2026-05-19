package com.flashnote.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.dto.LoginRequest;
import com.flashnote.auth.dto.LoginResponse;
import com.flashnote.auth.dto.RegisterRequest;
import com.flashnote.auth.dto.ChangePasswordRequest;
import com.flashnote.auth.dto.GestureLockBackupRequest;
import com.flashnote.auth.dto.GestureLockBackupResponse;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh:";
    private static final String REFRESH_TOKEN_SESSION_KEY_PREFIX = "auth:refresh:session:";
    private static final String SESSION_START_KEY_PREFIX = "auth:session:start:";

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
        long sessionStartMillis = System.currentTimeMillis();
        long refreshTtlSeconds = Math.max(1L, jwtUtil.getRefreshExpirationSeconds());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());
        redisUtil.set(buildRefreshTokenKey(user.getId(), refreshToken), String.valueOf(user.getId()), refreshTtlSeconds);
        redisUtil.set(buildTokenSessionStartKey(user.getId(), refreshToken), String.valueOf(sessionStartMillis), jwtUtil.getMaxSessionDurationMillis() / 1000);

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

        String refreshTokenKey = buildRefreshTokenKey(userId, refreshToken);
        String cachedTokenUserId = redisUtil.get(refreshTokenKey);
        String legacyCachedToken = cachedTokenUserId == null ? redisUtil.get(buildLegacyRefreshTokenKey(userId)) : null;
        if (cachedTokenUserId == null && (legacyCachedToken == null || !legacyCachedToken.equals(refreshToken))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token has expired");
        }

        long sessionStartMillis = resolveSessionStartMillis(userId, refreshToken);
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - sessionStartMillis);
        long maxDuration = jwtUtil.getMaxSessionDurationMillis();
        if (elapsed >= maxDuration) {
            redisUtil.delete(refreshTokenKey);
            redisUtil.delete(buildTokenSessionStartKey(userId, refreshToken));
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Session expired, please login again");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User does not exist");
        }

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername());
        long remainingMillis = Math.max(1L, maxDuration - elapsed);
        long refreshTtlMillis = Math.max(1L, Math.min(jwtUtil.getRefreshExpirationMillis(), remainingMillis));
        long refreshTtlSeconds = Math.max(1L, refreshTtlMillis / 1000);
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername(), refreshTtlMillis);
        redisUtil.delete(refreshTokenKey);
        if (legacyCachedToken != null) {
            redisUtil.delete(buildLegacyRefreshTokenKey(userId));
        }
        redisUtil.delete(buildTokenSessionStartKey(userId, refreshToken));
        redisUtil.set(buildRefreshTokenKey(userId, newRefreshToken), String.valueOf(userId), refreshTtlSeconds);
        redisUtil.set(buildTokenSessionStartKey(userId, newRefreshToken), String.valueOf(sessionStartMillis), Math.max(1L, (maxDuration - elapsed) / 1000));

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
            redisUtil.deleteByPattern(REFRESH_TOKEN_KEY_PREFIX + userId + ":*");
            redisUtil.deleteByPattern(REFRESH_TOKEN_SESSION_KEY_PREFIX + userId + ":*");
            redisUtil.delete(buildLegacyRefreshTokenKey(userId));
            redisUtil.delete(buildSessionStartKey(userId));
        }
    }

    @Override
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = requireUser(username);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    public void saveGestureLockBackup(String username, GestureLockBackupRequest request) {
        User user = requireUser(username);
        user.setGestureLockCiphertext(request.getCiphertext());
        user.setGestureLockNonce(request.getNonce());
        user.setGestureLockKdfParams(request.getKdfParams());
        user.setGestureLockVersion(request.getVersion());
        user.setGestureLockUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public GestureLockBackupResponse getGestureLockBackup(String username) {
        User user = requireUser(username);
        boolean configured = user.getGestureLockCiphertext() != null && !user.getGestureLockCiphertext().isBlank();
        return new GestureLockBackupResponse(configured, user.getGestureLockVersion(), user.getGestureLockUpdatedAt());
    }

    @Override
    public void clearGestureLockBackup(String username) {
        User user = requireUser(username);
        user.setGestureLockCiphertext(null);
        user.setGestureLockNonce(null);
        user.setGestureLockKdfParams(null);
        user.setGestureLockVersion(null);
        user.setGestureLockUpdatedAt(null);
        userMapper.updateById(user);
    }

    private String buildRefreshTokenKey(Long userId, String refreshToken) {
        return REFRESH_TOKEN_KEY_PREFIX + userId + ":" + fingerprintToken(refreshToken);
    }

    private String buildLegacyRefreshTokenKey(Long userId) {
        return REFRESH_TOKEN_KEY_PREFIX + userId;
    }

    private String buildTokenSessionStartKey(Long userId, String refreshToken) {
        return REFRESH_TOKEN_SESSION_KEY_PREFIX + userId + ":" + fingerprintToken(refreshToken);
    }

    private String buildSessionStartKey(Long userId) {
        return SESSION_START_KEY_PREFIX + userId;
    }

    private User requireUser(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        return user;
    }

    private long resolveSessionStartMillis(Long userId, String refreshToken) {
        String tokenStart = redisUtil.get(buildTokenSessionStartKey(userId, refreshToken));
        if (tokenStart != null) {
            try {
                long parsed = Long.parseLong(tokenStart);
                if (parsed > 0L) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        String cachedStart = redisUtil.get(buildSessionStartKey(userId));
        if (cachedStart != null) {
            try {
                long parsed = Long.parseLong(cachedStart);
                if (parsed > 0L) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        long issuedAt = jwtUtil.getIssuedAtMillis(refreshToken);
        return issuedAt > 0L ? issuedAt : System.currentTimeMillis();
    }

    private String fingerprintToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
