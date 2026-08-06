from dataclasses import dataclass, field
from datetime import datetime
from uuid import uuid4

from .schemas import ChatMessage, SessionDetail, SessionSummary


def now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


@dataclass
class AgentSession:
    id: str
    title: str
    created_at: str
    updated_at: str
    history: list[dict] = field(default_factory=list)
    context: dict = field(default_factory=dict)
    ui_messages: list[ChatMessage] = field(default_factory=list)


class SessionStore:
    def __init__(self):
        self._sessions: dict[str, AgentSession] = {}

    def list(self) -> list[SessionSummary]:
        sessions = sorted(
            self._sessions.values(),
            key=lambda item: item.updated_at,
            reverse=True,
        )
        return [self._summary(session) for session in sessions]

    def create(self) -> AgentSession:
        ts = now_iso()
        session = AgentSession(
            id=f"sess_{uuid4().hex[:12]}",
            title="New task",
            created_at=ts,
            updated_at=ts,
        )
        self._sessions[session.id] = session
        return session

    def get(self, session_id: str) -> AgentSession | None:
        return self._sessions.get(session_id)

    def get_or_create(self, session_id: str) -> AgentSession:
        session = self.get(session_id)
        if session is not None:
            return session
        session = AgentSession(
            id=session_id,
            title="New task",
            created_at="grpc",
            updated_at="grpc",
        )
        self._sessions[session_id] = session
        return session

    def delete(self, session_id: str) -> bool:
        return self._sessions.pop(session_id, None) is not None

    def touch(self, session: AgentSession):
        session.updated_at = now_iso()

    def add_ui_message(self, session: AgentSession, role: str, content: str) -> ChatMessage:
        message = ChatMessage(id=f"msg_{uuid4().hex[:12]}", role=role, content=content)
        session.ui_messages.append(message)
        if role == "user" and session.title == "New task":
            session.title = content.strip().splitlines()[0][:48] or "New task"
        self.touch(session)
        return message

    def detail(self, session: AgentSession) -> SessionDetail:
        return SessionDetail(
            id=session.id,
            title=session.title,
            created_at=session.created_at,
            updated_at=session.updated_at,
            messages=session.ui_messages,
        )

    def _summary(self, session: AgentSession) -> SessionSummary:
        return SessionSummary(
            id=session.id,
            title=session.title,
            created_at=session.created_at,
            updated_at=session.updated_at,
        )


store = SessionStore()
