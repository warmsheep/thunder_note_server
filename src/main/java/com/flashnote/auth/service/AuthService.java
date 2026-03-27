package com.flashnote.auth.service;

import com.flashnote.auth.dto.LoginRequest;
import com.flashnote.auth.dto.LoginResponse;
import com.flashnote.auth.dto.RegisterRequest;
import com.flashnote.auth.dto.ChangePasswordRequest;
import com.flashnote.auth.dto.GestureLockBackupRequest;
import com.flashnote.auth.dto.GestureLockBackupResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);

    void register(RegisterRequest request);

    LoginResponse refreshToken(String refreshToken);

    void logout(String accessToken);

    void changePassword(String username, ChangePasswordRequest request);

    void saveGestureLockBackup(String username, GestureLockBackupRequest request);

    GestureLockBackupResponse getGestureLockBackup(String username);

    void clearGestureLockBackup(String username);
}
