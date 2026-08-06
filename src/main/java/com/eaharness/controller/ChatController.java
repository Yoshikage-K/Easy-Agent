package com.eaharness.controller;

import com.eaharness.dto.ChatRequest;
import com.eaharness.dto.ChatResponse;
import com.eaharness.service.AgentGrpcClient;
import com.eaharness.service.SessionService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final AgentGrpcClient agentGrpcClient;
    private final SessionService sessionService;

    public ChatController(AgentGrpcClient agentGrpcClient, SessionService sessionService) {
        this.agentGrpcClient = agentGrpcClient;
        this.sessionService = sessionService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "service", "java-api");
    }

    @PostMapping("/sessions")
    public Map<String, String> createSession() {
        String sessionId = sessionService.create();
        return Map.of("sessionId", sessionId);
    }

    @GetMapping("/sessions")
    public Map<String, Object> listSessions() {
        return Map.of("sessions", sessionService.list());
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable String sessionId) {
        if (!sessionService.delete(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ChatResponse chat(@PathVariable String sessionId, @Valid @RequestBody ChatRequest request) {
        if (!sessionService.exists(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return agentGrpcClient.chat(sessionId, request.message());
    }
}
