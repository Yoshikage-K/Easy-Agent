from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from app.agent_runner import send_to_agent
from app.schemas import CreateSessionResponse, SendMessageRequest, SendMessageResponse
from app.session_store import store

app = FastAPI(title="EA Harness API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/health")
def health():
    return {"ok": True}


@app.get("/api/sessions")
def list_sessions():
    return {"sessions": store.list()}


@app.post("/api/sessions", response_model=CreateSessionResponse)
def create_session():
    session = store.create()
    return {"session": store.detail(session)}


@app.get("/api/sessions/{session_id}")
def get_session(session_id: str):
    session = store.get(session_id)
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    return {"session": store.detail(session)}


@app.delete("/api/sessions/{session_id}")
def delete_session(session_id: str):
    if not store.delete(session_id):
        raise HTTPException(status_code=404, detail="Session not found")
    return {"ok": True}


@app.post("/api/sessions/{session_id}/messages", response_model=SendMessageResponse)
def send_message(session_id: str, payload: SendMessageRequest):
    session = store.get(session_id)
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    try:
        assistant_messages = send_to_agent(session, payload.content)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"{type(exc).__name__}: {exc}") from exc
    return {
        "session": store.detail(session),
        "assistant_messages": assistant_messages,
    }
