import os, re
from pathlib import Path

from anthropic import Anthropic
from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent / ".env", override=True)
load_dotenv(override=True)
if os.getenv("ANTHROPIC_BASE_URL"):
    os.environ.pop("ANTHROPIC_AUTH_TOKEN", None)

# 基础信息
WORKDIR = Path.cwd()

MODEL = os.environ["MODEL_ID"]
client = Anthropic(base_url=os.getenv("ANTHROPIC_BASE_URL"))
CONTINUATION_PROMPT = "Continue from the previous response. Do not repeat completed work."
PROMPT = "\033[36ms20 >> \033[0m"
CLI_ACTIVE = False

try:
    import readline
    readline.parse_and_bind('set bind-tty-special-chars off')
    READLINE_AVAILABLE = True
except ImportError:
    READLINE_AVAILABLE = False

# 任务
TASKS_DIR = WORKDIR / ".tasks"

# 工作区
WORKTREES_DIR = WORKDIR / ".worktrees"
VALID_WT_NAME = re.compile(r'^[A-Za-z0-9._-]{1,64}$')

# MCP
_DISALLOWED_CHARS = re.compile(r'[^a-zA-Z0-9_-]')

# skills
SKILLS_DIR = WORKDIR / "skills"

# 代理间通信
MAILBOX_DIR = WORKDIR / ".mailboxes"

# 代理自动化
IDLE_POLL_INTERVAL = 5
IDLE_TIMEOUT = 60

# 工具调用权限
DENY_LIST = ["rm -rf /", "sudo", "shutdown", "reboot", "mkfs", "dd if="]
DESTRUCTIVE = ["rm ", "> /etc/", "chmod 777"]

# 压缩
PERSIST_THRESHOLD = 30000
TOOL_RESULTS_DIR = WORKDIR / ".task_outputs" / "tool-results"
KEEP_RECENT_TOOL_RESULTS = 3
TRANSCRIPT_DIR = WORKDIR / ".transcripts"

# 错误处理
PRIMARY_MODEL = MODEL
FALLBACK_MODEL = os.getenv("FALLBACK_MODEL_ID")
DEFAULT_MAX_TOKENS = 8000
MAX_RETRIES = 3
MAX_CONSECUTIVE_529 = 2
MAX_RECOVERY_RETRIES = 2
BASE_DELAY_MS = 500
CONTEXT_LIMIT = 50000
ESCALATED_MAX_TOKENS = 16000

# 定时任务
DURABLE_PATH = WORKDIR / ".scheduled_tasks.json"

# 上下文管理
MEMORY_DIR = WORKDIR / ".memory"
MEMORY_INDEX = MEMORY_DIR / "MEMORY.md"
