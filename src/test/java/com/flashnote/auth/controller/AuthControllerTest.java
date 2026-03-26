package com.flashnote.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flashnote.auth.dto.LoginResponse;
import com.flashnote.auth.dto.RefreshTokenRequest;
import com.flashnote.auth.dto.ChangePasswordRequest;
import com.flashnote.auth.dto.LoginRequest;
import com.flashnote.auth.dto.RegisterRequest;
import com.flashnote.auth.dto.UserInfo;
import com.flashnote.auth.service.AuthService;
import com.flashnote.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthControllerTest {

    private AuthController authController;
    private RecordingAuthService authService;

    @BeforeEach
    void setUp() {
        authService = new RecordingAuthService();
        authController = new AuthController(authService);
    }

    @Test
    void refreshToken_readsRefreshTokenFromRequestBody() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");
        authService.response = new LoginResponse("access", "refresh-new", "Bearer", 3600L,
                new UserInfo(1L, "alice", "alice@example.com", "Alice", null));

        ApiResponse<LoginResponse> apiResponse = authController.refreshToken(request);

        assertEquals("refresh-token", authService.lastRefreshToken);
        assertEquals(0, apiResponse.getCode());
        assertEquals("access", apiResponse.getData().getAccessToken());
    }

    private static final class RecordingAuthService implements AuthService {
        private String lastRefreshToken;
        private LoginResponse response;

        @Override
        public LoginResponse login(LoginRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void register(RegisterRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoginResponse refreshToken(String refreshToken) {
            lastRefreshToken = refreshToken;
            return response;
        }

        @Override
        public void logout(String accessToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void changePassword(String username, ChangePasswordRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
