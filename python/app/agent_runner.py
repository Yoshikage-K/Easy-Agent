from agent.context import update_context
from agent.loop import agent_loop
from agent.runtime import initialize_runtime
from agent.utils import extract_text

from .session_store import AgentSession, store

_runtime_ready = False


def ensure_runtime():
    global _runtime_ready
    if _runtime_ready:
        return
    initialize_runtime(start_scheduler=False)
    _runtime_ready = True


def _assistant_texts(history: list[dict], start: int) -> list[str]:
    texts = []
    for message in history[start:]:
        if message.get("role") != "assistant":
            continue
        text = extract_text(message.get("content", []))
        if text:
            texts.append(text)
    return texts


def send_to_agent(session: AgentSession, content: str):
    ensure_runtime()
    store.add_ui_message(session, "user", content)
    turn_start = len(session.history)
    session.history.append({"role": "user", "content": content})
    agent_loop(session.history, session.context)
    session.context = update_context(session.context, session.history)

    assistant_messages = []
    for text in _assistant_texts(session.history, turn_start):
        assistant_messages.append(store.add_ui_message(session, "assistant", text))
    if not assistant_messages:
        assistant_messages.append(
            store.add_ui_message(session, "assistant", "(no assistant text returned)")
        )
    return assistant_messages
