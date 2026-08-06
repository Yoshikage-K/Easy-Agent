"""Generate Python gRPC modules with package-relative imports."""
from pathlib import Path
import re

from grpc_tools import protoc


ROOT = Path(__file__).resolve().parents[1]
PROTO_DIR = ROOT / "proto"
OUTPUT_DIR = ROOT / "agent_service" / "generated"
PROTO_FILE = PROTO_DIR / "agent.proto"


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    result = protoc.main([
        "grpc_tools.protoc",
        f"-I{PROTO_DIR}",
        f"--python_out={OUTPUT_DIR}",
        f"--grpc_python_out={OUTPUT_DIR}",
        str(PROTO_FILE),
    ])
    if result != 0:
        raise SystemExit(result)

    grpc_file = OUTPUT_DIR / "agent_pb2_grpc.py"
    content = grpc_file.read_text()
    content = re.sub(
        r"^import agent_pb2 as agent__pb2$",
        "from . import agent_pb2 as agent__pb2",
        content,
        flags=re.MULTILINE,
    )
    grpc_file.write_text(content)


if __name__ == "__main__":
    main()
