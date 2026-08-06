"""Runtime initialization for the CLI entrypoint."""
import threading

from .config import TASKS_DIR, WORKTREES_DIR, MAILBOX_DIR, MEMORY_DIR
from .cron import cron_scheduler_loop, load_durable_jobs
from .hooks import (
    register_hook,
    user_prompt_hook,
    permission_hook,
    log_hook,
    large_output_hook,
    stop_hook,
)
from .skills import scan_skills
from .state import HOOKS

_initialized = False


def initialize_runtime(start_scheduler: bool = True):
    global _initialized
    if _initialized:
        return

    for directory in (TASKS_DIR, WORKTREES_DIR, MAILBOX_DIR, MEMORY_DIR):
        directory.mkdir(parents=True, exist_ok=True)

    scan_skills()
    load_durable_jobs()

    for callbacks in HOOKS.values():
        callbacks.clear()
    register_hook("UserPromptSubmit", user_prompt_hook)
    register_hook("PreToolUse", permission_hook)
    register_hook("PreToolUse", log_hook)
    register_hook("PostToolUse", large_output_hook)
    register_hook("Stop", stop_hook)

    if start_scheduler:
        threading.Thread(target=cron_scheduler_loop, daemon=True).start()

    _initialized = True

