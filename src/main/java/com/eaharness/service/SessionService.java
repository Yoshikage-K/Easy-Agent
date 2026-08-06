package com.eaharness.service;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public String create() {
        String id = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        sessions.put(id, "New task");
        return id;
    }

    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public boolean delete(String sessionId) {
        return sessions.remove(sessionId) != null;
    }

    public List<Map<String, String>> list() {
        return sessions.entrySet().stream()
                .map(entry -> Map.of("id", entry.getKey(), "title", entry.getValue()))
                .toList();
    }
}
