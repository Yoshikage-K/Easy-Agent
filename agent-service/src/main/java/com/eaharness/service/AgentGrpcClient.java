package com.eaharness.service;

import com.eaharness.dto.ChatResponse;
import com.eaharness.agent.v1.AgentServiceGrpc;
import com.eaharness.agent.v1.ChatRequest;
import io.grpc.ManagedChannel;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AgentGrpcClient {
    private final AgentServiceGrpc.AgentServiceBlockingStub stub;

    public AgentGrpcClient(ManagedChannel channel) {
        this.stub = AgentServiceGrpc.newBlockingStub(channel);
    }

    public ChatResponse chat(String sessionId, String message) {
        String traceId = "trace_" + UUID.randomUUID().toString().replace("-", "");
        com.eaharness.agent.v1.ChatResponse response = stub.chat(ChatRequest.newBuilder()
                .setTraceId(traceId)
                .setSessionId(sessionId)
                .setMessage(message)
                .build());
        return new ChatResponse(
                response.getTraceId(),
                response.getSessionId(),
                response.getSuccess(),
                response.getContent(),
                response.getErrorMessage());
    }
}
