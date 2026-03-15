package com.flashnote.sync.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.sync.service.SyncService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/pull")
    public ApiResponse<Map<String, Object>> pull(Authentication authentication) {
        return ApiResponse.success(syncService.pull(authentication.getName()));
    }

    @PostMapping("/push")
    public ApiResponse<Map<String, Object>> push(Authentication authentication,
                                                 @RequestBody Map<String, Object> payload) {
        return ApiResponse.success(syncService.push(authentication.getName(), payload));
    }

    @PostMapping("/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrap(Authentication authentication) {
        return ApiResponse.success(syncService.bootstrap(authentication.getName()));
    }
}
