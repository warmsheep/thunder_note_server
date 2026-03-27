package com.flashnote.sync.service;

import java.util.Map;

public interface SyncService {
    Map<String, Object> pull(String username, String lastMessageCreatedAt);

    Map<String, Object> push(String username, Map<String, Object> payload);

    Map<String, Object> bootstrap(String username);
}
