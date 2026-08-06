""" 上下文管理 """
from .config import MEMORY_INDEX, CONTEXT_LIMIT
from .background import collect_background_results
from .compact import estimate_size, tool_result_budget, snip_compact, micro_compact, compact_history
from .state import mcp_clients, active_teammates


def update_context(context: dict, messages: list) -> dict:
    memories = ""
    if MEMORY_INDEX.exists():
        memories = MEMORY_INDEX.read_text()[:2000]
    return {
        "memories": memories,
        "connected_mcp": list(mcp_clients.keys()),
        "active_teammates": list(active_teammates.keys()),
    }


def prepare_context(messages: list) -> list:
    # 上下文管理：在每次 LLM 调用前，检查消息列表的长度，如果超过 CONTEXT_LIMIT，则进行压缩和裁剪，以确保不会超出模型的上下文限制。
    messages[:] = tool_result_budget(messages)
    messages[:] = snip_compact(messages)
    messages[:] = micro_compact(messages)
    if estimate_size(messages) > CONTEXT_LIMIT:
        messages[:] = compact_history(messages)
    return messages


def build_user_content(results: list[dict]) -> list[dict]:
    # 将工具结果和已完成的后台通知都作为用户端内容返回给模型，匹配 tool_result 的反馈循环。
    content = list(results)
    for note in collect_background_results():
        content.append({"type": "text", "text": note})
    return content


def inject_background_notifications(messages: list):
    # 将后台通知注入到消息列表中，作为用户端内容返回给模型，匹配 tool_result 的反馈循环。   
    notes = collect_background_results()
    if notes:
        messages.append({"role": "user", "content": [
            {"type": "text", "text": note} for note in notes]})
