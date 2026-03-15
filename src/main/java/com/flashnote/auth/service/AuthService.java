package com.flashnote.auth.service;

import com.flashnote.auth.dto.LoginRequest;
import com.flashnote.auth.dto.LoginResponse;
import com.flashnote.auth.dto.RegisterRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);

    void register(RegisterRequest request);

    LoginResponse refreshToken(String refreshToken);

    void logout(String accessToken);
}
