import logging
from concurrent import futures

import grpc

from app.agent_runner import send_to_agent
from app.session_store import store

from .generated import agent_pb2, agent_pb2_grpc


LOGGER = logging.getLogger(__name__)


class AgentService(agent_pb2_grpc.AgentServiceServicer):
    """Translate gRPC requests into calls to the existing Agent runner."""

    def Chat(self, request, context):
        trace_id = request.trace_id or "no-trace-id"
        LOGGER.info("[%s] gRPC chat received session=%s", trace_id, request.session_id)
        if not request.session_id:
            return agent_pb2.ChatResponse(
                trace_id=trace_id,
                success=False,
                error_message="session_id is required",
            )
        if not request.message.strip():
            return agent_pb2.ChatResponse(
                trace_id=trace_id,
                session_id=request.session_id,
                success=False,
                error_message="message is required",
            )

        session = store.get_or_create(request.session_id)

        try:
            messages = send_to_agent(session, request.message)
            content = "\n\n".join(message.content for message in messages)
            LOGGER.info("[%s] gRPC chat completed session=%s", trace_id, request.session_id)
            return agent_pb2.ChatResponse(
                trace_id=trace_id,
                session_id=request.session_id,
                success=True,
                content=content,
            )
        except Exception as exc:
            LOGGER.exception("[%s] gRPC chat failed", trace_id)
            return agent_pb2.ChatResponse(
                trace_id=trace_id,
                session_id=request.session_id,
                success=False,
                error_message=f"{type(exc).__name__}: {exc}",
            )


def serve(host: str = "127.0.0.1", port: int = 50051):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    agent_pb2_grpc.add_AgentServiceServicer_to_server(AgentService(), server)
    server.add_insecure_port(f"{host}:{port}")
    server.start()
    LOGGER.info("Python Agent gRPC server listening on %s:%s", host, port)
    server.wait_for_termination()
