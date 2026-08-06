"""Shared runtime state for the split agent modules."""
import threading


CURRENT_TODOS: list[dict] = []

mcp_clients: dict[str, object] = {}

active_teammates: dict[str, bool] = {}
pending_requests: dict[str, object] = {}

HOOKS = {
    "UserPromptSubmit": [],
    "PreToolUse": [],
    "PostToolUse": [],
    "Stop": [],
}

SKILL_REGISTRY: dict[str, dict] = {}

_bg_counter = 0
background_tasks: dict[str, dict] = {}
background_results: dict[str, str] = {}
background_lock = threading.Lock()

scheduled_jobs: dict[str, object] = {}
cron_queue: list[object] = []
cron_lock = threading.Lock()
_last_fired: dict[str, str] = {}
