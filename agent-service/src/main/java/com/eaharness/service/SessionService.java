package com.eaharness.service;

import com.eaharness.dto.ChatMessage;
import com.eaharness.dto.SessionDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public String create() {
        String id = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        sessions.put(id, new SessionState(id));
        return id;
    }

    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public boolean delete(String sessionId) {
        return sessions.remove(sessionId) != null;
    }

    public List<Map<String, String>> list() {
        return sessions.values().stream()
                .map(session -> Map.of("id", session.id, "title", session.title))
                .toList();
    }

    public SessionDetail detail(String sessionId) {
        SessionState session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        return new SessionDetail(session.id, session.title, List.copyOf(session.messages));
    }

    public void addMessage(String sessionId, String role, String content) {
        SessionState session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        if ("user".equals(role) && "New task".equals(session.title)) {
            session.title = content.strip().lines().findFirst().orElse("New task").substring(
                    0, Math.min(48, content.strip().lines().findFirst().orElse("New task").length()));
        }
        session.messages.add(new ChatMessage(
                "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                role,
                content));
    }

    private static final class SessionState {
        private final String id;
        private volatile String title = "New task";
        private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();

        private SessionState(String id) {
            this.id = id;
        }
    }
}
