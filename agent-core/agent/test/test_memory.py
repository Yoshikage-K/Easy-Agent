"""真实模型跨会话记忆测试。

测试目标：

1. 第一次会话告诉 Agent 一个长期项目代号；
2. Agent 结束后通过 extract_memories() 保存记忆；
3. 第二次使用全新的 messages，排除会话历史影响；
4. Agent 通过 load_memories() 找回记忆并回答项目代号。

本测试不 Mock 模型，也不 Mock记忆提取/检索。唯一的 Patch 是把记忆目录
重定向到当前测试目录下的 .memory_test，避免污染正式的 .memory。

执行方式：

    cd /Users/yunhua/Work/Java/projects/EA-Haraness/agent-core
    python -m agent.test.test_memory
"""

import shutil
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


AGENT_CORE_DIR = Path(__file__).resolve().parents[2]
if str(AGENT_CORE_DIR) not in sys.path:
    sys.path.insert(0, str(AGENT_CORE_DIR))

from agent import context as context_module
from agent import loop as loop_module
from agent import memory as memory_module
from agent.utils import extract_text


TEST_DIR = Path(__file__).resolve().parent
TEST_MEMORY_DIR = TEST_DIR / ".memory_test"
TEST_MEMORY_INDEX = TEST_MEMORY_DIR / "MEMORY.md"


def latest_assistant_text(messages: list[dict]) -> str:
    """读取一次 Agent 执行产生的最后一条 Assistant 文本。"""

    for message in reversed(messages):
        if message.get("role") == "assistant":
            return extract_text(message.get("content", []))
    return ""


class TestMemoryRecall(unittest.TestCase):
    """验证 Agent 能在两个独立会话之间保存并召回长期记忆。"""

    PROJECT_CODE = "NEBULA-7319"

    def setUp(self) -> None:
        """每次运行前清空测试记忆，保证结果不受旧数据影响。"""

        if TEST_MEMORY_DIR.exists():
            shutil.rmtree(TEST_MEMORY_DIR)
        TEST_MEMORY_DIR.mkdir(parents=True)

    def test_second_session_recalls_first_session_memory(self) -> None:
        """第二个全新会话应根据持久化记忆回答第一个会话的信息。"""

        # 只替换记忆存储位置。模型调用、记忆选择、记忆提取和 Agent Loop
        # 全部执行真实代码，不使用 Fake Model。
        with (
            patch.object(memory_module, "MEMORY_DIR", TEST_MEMORY_DIR),
            patch.object(memory_module, "MEMORY_INDEX", TEST_MEMORY_INDEX),
            patch.object(context_module, "MEMORY_INDEX", TEST_MEMORY_INDEX),
        ):
            # --------------------------------------------------------------
            # 第一次会话：向 Agent 提供一条应被持久化的项目事实。
            # --------------------------------------------------------------
            first_messages = [{
                "role": "user",
                "content": (
                    "请记住这条长期项目事实：我的项目代号是 "
                    f"{self.PROJECT_CODE}。只回复你已经记住。"
                ),
            }]

            loop_module.agent_loop(first_messages, {})
            first_answer = latest_assistant_text(first_messages)

            print(f"\n第一次回答：{first_answer}")
            print(f"记忆索引位置：{TEST_MEMORY_INDEX}")

            # 验证第一轮结束后确实产生了索引和独立记忆文件。
            self.assertTrue(
                TEST_MEMORY_INDEX.exists(),
                "第一次任务结束后没有生成 MEMORY.md",
            )

            memory_files = [
                path
                for path in TEST_MEMORY_DIR.glob("*.md")
                if path.name != "MEMORY.md"
            ]
            self.assertTrue(
                memory_files,
                "第一次任务结束后没有生成独立记忆文件",
            )

            print("记忆索引内容：")
            print(TEST_MEMORY_INDEX.read_text())

            # --------------------------------------------------------------
            # 第二次会话：使用全新的 messages，不携带第一次会话 history。
            # 如果回答正确，只能来自 load_memories() 加载的持久化记忆。
            # --------------------------------------------------------------
            second_messages = [{
                "role": "user",
                "content": (
                    "这是一个全新的会话。请根据长期记忆回答："
                    "我的项目代号是什么？只输出项目代号。"
                ),
            }]

            loop_module.agent_loop(second_messages, {})
            second_answer = latest_assistant_text(second_messages)

            print(f"第二次回答：{second_answer}")

            self.assertIn(
                self.PROJECT_CODE,
                second_answer,
                "第二次会话没有从持久化记忆中召回项目代号",
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
