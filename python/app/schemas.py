from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    id: str
    role: str
    content: str


class SessionSummary(BaseModel):
    id: str
    title: str
    created_at: str
    updated_at: str


class SessionDetail(SessionSummary):
    messages: list[ChatMessage]


class CreateSessionResponse(BaseModel):
    session: SessionDetail


class SendMessageRequest(BaseModel):
    content: str = Field(min_length=1)


class SendMessageResponse(BaseModel):
    session: SessionDetail
    assistant_messages: list[ChatMessage]
