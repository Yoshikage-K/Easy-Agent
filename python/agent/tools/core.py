def has_tool_use(content) -> bool:
    return any(getattr(block, "type", None) == "tool_use"
               for block in content)


def call_tool_handler(handler, args: dict, name: str) -> str:
    if not handler:
        return f"Unknown: {name}"
    try:
        return handler(**(args or {}))
    except TypeError as e:
        return f"Error: {e}"

