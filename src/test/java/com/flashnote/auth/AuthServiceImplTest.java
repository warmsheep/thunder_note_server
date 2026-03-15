package com.flashnote.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.dto.LoginRequest;
import com.flashnote.auth.dto.LoginResponse;
import com.flashnote.auth.dto.RegisterRequest;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.auth.service.impl.AuthServiceImpl;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.utils.JwtUtil;
import com.flashnote.common.utils.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RedisUtil redisUtil;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper, passwordEncoder, jwtUtil, redisUtil);
    }

    @Test
    void login_withValidCredentials_returnsLoginResponse() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword("encodedPassword");
        user.setNickname("Alice");
        user.setAvatar(null);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        when(jwtUtil.generateAccessToken(1L, "alice")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(1L, "alice")).thenReturn("refresh-token");
        when(jwtUtil.getRefreshExpirationSeconds()).thenReturn(604800L);
        when(jwtUtil.getAccessExpirationSeconds()).thenReturn(3600L);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");
        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        verify(redisUtil).set(anyString(), eq("refresh-token"), anyLong());
    }

    @Test
    void login_withInvalidUsername_throwsException() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("password123");

        assertThrows(BusinessException.class, () -> authService.login(request));
    }

    @Test
    void login_withWrongPassword_throwsException() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("encodedPassword");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrongPassword");

        assertThrows(BusinessException.class, () -> authService.login(request));
    }

    @Test
    void register_withNewUser_createsUser() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("bob");
        request.setEmail("bob@example.com");
        request.setPassword("password123");

        assertDoesNotThrow(() -> authService.register(request));
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void register_withExistingUsername_throwsException() {
        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setUsername("bob");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("bob");
        request.setEmail("new@example.com");
        request.setPassword("password123");

        assertThrows(BusinessException.class, () -> authService.register(request));
    }

    @Test
    void refreshToken_withValidToken_returnsNewTokens() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setNickname("Alice");

        when(jwtUtil.validateToken("valid-refresh-token", "refresh")).thenReturn(true);
        when(jwtUtil.getUserId("valid-refresh-token")).thenReturn(1L);
        when(redisUtil.get(anyString())).thenReturn("valid-refresh-token");
        when(userMapper.selectById(1L)).thenReturn(user);
        when(jwtUtil.generateAccessToken(1L, "alice")).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken(1L, "alice")).thenReturn("new-refresh-token");
        when(jwtUtil.getRefreshExpirationSeconds()).thenReturn(604800L);
        when(jwtUtil.getAccessExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.refreshToken("valid-refresh-token");

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
    }

    @Test
    void refreshToken_withInvalidToken_throwsException() {
        when(jwtUtil.validateToken("invalid-token", "refresh")).thenReturn(false);

        assertThrows(BusinessException.class, () -> authService.refreshToken("invalid-token"));
    }

    @Test
    void logout_withValidToken_deletesRefreshToken() {
        when(jwtUtil.validateToken("valid-access-token", "access")).thenReturn(true);
        when(jwtUtil.getUserId("valid-access-token")).thenReturn(1L);

        authService.logout("Bearer valid-access-token");

        verify(redisUtil).delete(anyString());
    }

    @Test
    void logout_withNullToken_doesNothing() {
        assertDoesNotThrow(() -> authService.logout(null));
        verifyNoInteractions(redisUtil);
    }
}
