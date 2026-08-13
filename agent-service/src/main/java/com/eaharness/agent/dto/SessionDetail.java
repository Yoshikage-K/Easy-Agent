package com.eaharness.agent.dto;

import java.util.List;

public record SessionDetail(String id, String title, List<ChatMessage> messages) {
}
