# EA-Haraness

EA Agent 的三层本地联调项目：Vue3 前端、Java Spring Boot 网关和 Python Agent gRPC 服务。

```text
frontend (5173) -> java (8080) -> python gRPC (50051) -> model API
```

## 目录

```text
EA-Haraness/
├── java/       Java Spring Boot REST 网关
├── python/     Python Agent 和 gRPC 服务
└── frontend/   Vue3 + Vite 前端
```

## Python gRPC 服务

先准备配置文件。不要把真实 API Key 提交到 Git：

```bash
cd /Users/yunhua/Work/Java/project/EA-Haraness/python
python3 -m venv .venv
./.venv/bin/python -m pip install -r requirements.txt
cp .env.example .env
```

启动 gRPC 服务：

```bash
./.venv/bin/python -m agent_service.server
```

默认监听 `127.0.0.1:50051`。

如果协议文件发生变化，重新生成 Python gRPC 代码：

```bash
./.venv/bin/python -m grpc_tools.protoc \
  -I ./proto \
  --python_out=./agent_service/generated \
  --grpc_python_out=./agent_service/generated \
  ./proto/agent.proto
```

## Java REST 网关

```bash
cd /Users/yunhua/Work/Java/project/EA-Haraness/java
mvn generate-sources
mvn spring-boot:run
```

默认监听 `127.0.0.1:8080`，Python gRPC 地址配置在 `java/src/main/resources/application.yml`。

## Vue3 前端

```bash
cd /Users/yunhua/Work/Java/project/EA-Haraness/frontend
npm install
npm run dev
```

打开 `http://127.0.0.1:5173`。
