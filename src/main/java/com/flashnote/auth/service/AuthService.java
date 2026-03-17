package com.flashnote.auth.service;

import com.flashnote.auth.dto.LoginRequest;
import com.flashnote.auth.dto.LoginResponse;
import com.flashnote.auth.dto.RegisterRequest;
import com.flashnote.auth.dto.ChangePasswordRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);

    void register(RegisterRequest request);

    LoginResponse refreshToken(String refreshToken);

    void logout(String accessToken);

    void changePassword(String username, ChangePasswordRequest request);
}
