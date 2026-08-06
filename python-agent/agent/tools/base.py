""" 基础工具集合 """
import ast, subprocess, json
from pathlib import Path
from ..config import WORKDIR
from .. import state


def safe_path(p: str, cwd: Path = None) -> Path:
    # 校验并返回限制在工作区内的安全路径
    base = cwd or WORKDIR
    path = (base / p).resolve()
    if not path.is_relative_to(base):
        raise ValueError(f"Path escapes workspace: {p}")
    return path


def run_bash(command: str, cwd: Path = None,
             run_in_background: bool = False) -> str:
    # 在指定目录运行 shell 命令并返回输出
    try:
        r = subprocess.run(command, shell=True, cwd=cwd or WORKDIR,
                           capture_output=True, text=True, timeout=120)
        out = (r.stdout + r.stderr).strip()
        return out[:50000] if out else "(no output)"
    except subprocess.TimeoutExpired:
        return "Error: Timeout (120s)"


def run_read(path: str, limit: int | None = None,
             offset: int = 0, cwd: Path = None) -> str:
    # 读取文件内容（支持偏移和行数限制）
    try:
        lines = safe_path(path, cwd).read_text().splitlines()
        offset = max(int(offset or 0), 0)
        limit = int(limit) if limit is not None else None
        lines = lines[offset:]
        if limit is not None and limit < len(lines):
            lines = lines[:limit] + [f"... ({len(lines) - limit} more lines)"]
        return "\n".join(lines)
    except Exception as e:
        return f"Error: {e}"


def run_write(path: str, content: str, cwd: Path = None) -> str:
    # 将字符串内容写入文件（自动创建父目录）
    try:
        fp = safe_path(path, cwd)
        fp.parent.mkdir(parents=True, exist_ok=True)
        fp.write_text(content)
        return f"Wrote {len(content)} bytes to {path}"
    except Exception as e:
        return f"Error: {e}"


def run_edit(path: str, old_text: str, new_text: str,
             cwd: Path = None) -> str:
    # 在文件中查找并替换第一处匹配的文本
    try:
        fp = safe_path(path, cwd)
        text = fp.read_text()
        if old_text not in text:
            return f"Error: text not found in {path}"
        fp.write_text(text.replace(old_text, new_text, 1))
        return f"Edited {path}"
    except Exception as e:
        return f"Error: {e}"


def run_glob(pattern: str, cwd: Path = None) -> str:
    import glob as g
    # 在工作区中按 glob 模式查找匹配的文件路径
    try:
        base = cwd or WORKDIR
        results = []
        for match in g.glob(pattern, root_dir=base):
            if (base / match).resolve().is_relative_to(base):
                results.append(match)
        return "\n".join(results) if results else "(no matches)"
    except Exception as e:
        return f"Error: {e}"


def _normalize_todos(todos):
    # 规范化 todos 输入（接受列表或 JSON/字符串表示）
    if isinstance(todos, str):
        try:
            todos = json.loads(todos)
        except json.JSONDecodeError:
            try:
                todos = ast.literal_eval(todos)
            except (SyntaxError, ValueError):
                return None, "Error: todos must be a list or JSON array string"
    if not isinstance(todos, list):
        return None, "Error: todos must be a list"
    for i, todo in enumerate(todos):
        if not isinstance(todo, dict):
            return None, f"Error: todos[{i}] must be an object"
        if "content" not in todo or "status" not in todo:
            return None, f"Error: todos[{i}] missing 'content' or 'status'"
        if todo["status"] not in ("pending", "in_progress", "completed"):
            return None, f"Error: todos[{i}] has invalid status '{todo['status']}'"
    return todos, None


def run_todo_write(todos: list) -> str:
    # 更新当前的 TODO 列表并返回确认信息
    todos, error = _normalize_todos(todos)
    if error:
        return error
    state.CURRENT_TODOS = todos
    print(f"  \033[33m[todo] updated {len(state.CURRENT_TODOS)} item(s)\033[0m")
    return f"Updated {len(state.CURRENT_TODOS)} todos"
