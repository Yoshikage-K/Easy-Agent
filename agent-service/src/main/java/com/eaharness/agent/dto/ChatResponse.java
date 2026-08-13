package com.eaharness.agent.dto;

public record ChatResponse(
        String traceId,
        String sessionId,
        boolean success,
        String content,
        String errorMessage) {
}
