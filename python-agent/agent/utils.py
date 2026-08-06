import threading
from . import config

try:
    import readline
except ImportError:
    readline = None


def terminal_print(text: str):
    if threading.current_thread() is threading.main_thread() or not config.CLI_ACTIVE:
        print(text)
        return
    line = ""
    if config.READLINE_AVAILABLE and readline is not None:
        try:
            line = readline.get_line_buffer()
        except Exception:
            line = ""
    print(f"\r\033[K{text}")
    print(config.PROMPT + line, end="", flush=True)


def extract_text(content) -> str:
    # 提取文本块内容，忽略工具调用和工具结果块
    if not isinstance(content, list):
        return str(content)
    return "\n".join(
        getattr(block, "text", "")
        for block in content
        if getattr(block, "type", None) == "text").strip()


def print_turn_assistants(messages: list, turn_start: int):
    for msg in messages[turn_start:]:
        if msg.get("role") != "assistant":
            continue
        for block in msg.get("content", []):
            block_type = block.get("type") if isinstance(block, dict) else getattr(block, "type", None)
            if block_type == "text":
                terminal_print(block["text"] if isinstance(block, dict) else block.text)
