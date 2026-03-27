package com.flashnote.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.flashnote.auth.dto.GestureLockBackupRequest;
import com.flashnote.auth.dto.GestureLockBackupResponse;
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
import org.springframework.security.authentication.TestingAuthenticationToken;

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

    @Test
    void saveGestureLockBackup_forwardsAuthenticatedUsernameAndRequest() {
        GestureLockBackupRequest request = new GestureLockBackupRequest();
        request.setCiphertext("ciphertext");
        request.setNonce("nonce");
        request.setKdfParams("params");
        request.setVersion("v1");

        ApiResponse<Void> apiResponse = authController.saveGestureLockBackup(
                new TestingAuthenticationToken("alice", null),
                request
        );

        assertEquals("alice", authService.lastGestureUsername);
        assertEquals("ciphertext", authService.lastGestureRequest.getCiphertext());
        assertEquals(0, apiResponse.getCode());
    }

    @Test
    void getGestureLockBackup_returnsServiceResponse() {
        authService.gestureResponse = new GestureLockBackupResponse(true, "v1", null);

        ApiResponse<GestureLockBackupResponse> response = authController.getGestureLockBackup(
                new TestingAuthenticationToken("alice", null)
        );

        assertEquals("alice", authService.lastGestureUsername);
        assertEquals("v1", response.getData().getVersion());
    }

    @Test
    void clearGestureLockBackup_forwardsAuthenticatedUsername() {
        ApiResponse<Void> response = authController.clearGestureLockBackup(
                new TestingAuthenticationToken("alice", null)
        );

        assertEquals("alice", authService.lastGestureUsername);
        assertFalse(authService.gestureConfigured);
        assertNull(authService.gestureResponse);
        assertEquals(0, response.getCode());
    }

    private static final class RecordingAuthService implements AuthService {
        private String lastRefreshToken;
        private LoginResponse response;
        private String lastGestureUsername;
        private GestureLockBackupRequest lastGestureRequest;
        private GestureLockBackupResponse gestureResponse;
        private boolean gestureConfigured;

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

        @Override
        public void saveGestureLockBackup(String username, GestureLockBackupRequest request) {
            lastGestureUsername = username;
            lastGestureRequest = request;
            gestureConfigured = true;
        }

        @Override
        public GestureLockBackupResponse getGestureLockBackup(String username) {
            lastGestureUsername = username;
            return gestureResponse;
        }

        @Override
        public void clearGestureLockBackup(String username) {
            lastGestureUsername = username;
            gestureConfigured = false;
            gestureResponse = null;
        }
    }
}
