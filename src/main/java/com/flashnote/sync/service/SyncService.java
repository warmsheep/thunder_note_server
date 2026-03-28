package com.flashnote.sync.service;

import com.flashnote.sync.dto.SyncPushRequest;

import java.util.Map;

public interface SyncService {
    Map<String, Object> pull(String username, String lastMessageCreatedAt);

    Map<String, Object> push(String username, SyncPushRequest payload);

    Map<String, Object> bootstrap(String username);
}
