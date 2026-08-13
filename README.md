# EA-Harness

EA-Harness 是一个面向本地智能 Agent 的三层应用系统。项目通过 Vue 3 提供交互界面，使用 Java Spring Boot 构建统一的 REST 网关，并通过 gRPC 调用 Python Agent Runtime，形成清晰的前后端与 Agent 解耦架构。

项目重点关注 Agent 的运行时编排能力，包括会话管理、上下文维护、模型调用、工具执行、任务恢复以及跨语言服务通信。

## Architecture

```text
┌──────────────────────┐
│  Vue 3 Web Frontend  │ :5173
└──────────┬───────────┘
           │ HTTP / JSON
           v
┌──────────────────────────────────────┐
│       Java Spring Boot Gateway       │ :8080
│                                      │
│  Session API · Chat API · RPC Client │
└──────────────────┬───────────────────┘
                   │ gRPC / Protobuf
                   v
┌──────────────────────────────────────┐
│          Python Agent Runtime        │ :50051
│                                      │
│  Agent Loop · Context · Tools · Task │
└──────────────────┬───────────────────┘
                   │
                   v
              Model API
```

### Request Flow

一次对话请求的处理流程如下：

```text
用户输入消息
    ↓
Vue 前端发送 REST 请求
    ↓
Java 网关校验会话并记录用户消息
    ↓
Java 通过 gRPC 调用 Python Agent
    ↓
Python Agent 执行模型调用和工具编排
    ↓
Python 返回 Agent 结果
    ↓
Java 保存助手消息并返回前端
```

Java 网关负责协议转换、会话 API 和服务边界；Python 服务负责 Agent 的核心推理与执行流程。

## Core Capabilities

### Agent Runtime

Python Agent Runtime 是系统的核心执行引擎，主要负责：

- 维护多轮对话历史；
- 组装系统提示词和运行时上下文；
- 调用大语言模型；
- 根据模型输出选择并执行工具；
- 处理多轮工具调用；
- 处理上下文过长、模型重试和恢复；
- 支持后台任务、定时任务以及 Agent 间协作基础能力。

核心代码位于：

```text
agent-core/agent/
├── loop.py          Agent 主循环
├── context.py       上下文构建和消息处理
├── prompt.py        系统提示词组装
├── tools/           工具定义、注册和执行
├── task.py          任务状态和任务管理
├── background.py    后台任务调度
├── cron.py          定时任务处理
├── subagent/        Agent 协作能力
└── runtime.py       运行时初始化
```

### Java Gateway

Java 服务作为前端和 Python Agent 之间的应用网关，负责：

- 提供会话创建、查询和删除接口；
- 接收用户消息并校验会话有效性；
- 生成 traceId 和构造 gRPC 请求；
- 调用 Python Agent Service；
- 将 Protobuf 响应转换成 REST DTO；
- 保存会话消息并向前端返回统一响应。

核心代码位于：

```text
agent-service/src/main/java/com/eaharness/agent/
├── config/       gRPC、Web 和 Agent 配置
├── controller/   会话和消息 REST 接口
├── dto/          请求、响应和会话数据模型
└── service/      gRPC 客户端和会话服务
```

### Cross-language RPC

项目通过 Protocol Buffers 定义 Java 与 Python 之间的通信协议：

```protobuf
service AgentService {
  rpc Chat(ChatRequest) returns (ChatResponse);
}
```

请求包含：

- `trace_id`：请求链路标识；
- `session_id`：会话标识；
- `message`：用户消息。

响应包含：

- `success`：调用是否成功；
- `content`：Agent 返回内容；
- `error_message`：失败时的错误信息。

协议文件位于：

```text
agent-service/src/main/proto/agent.proto
agent-core/proto/agent.proto
```

### Web Frontend

Vue 3 前端提供轻量级 Agent 工作台，支持：

- 创建新会话；
- 切换历史会话；
- 删除会话；
- 发送多轮消息；
- 展示 Agent 回复和错误状态；
- 在请求执行期间显示运行状态。

核心代码位于：

```text
web-frontend/src/
├── App.vue       页面和交互逻辑
├── api.ts        REST API 封装
├── main.ts       Vue 应用入口
└── styles.css    页面样式
```

## Technology Stack

| Layer | Technology |
|---|---|
| Frontend | Vue 3、TypeScript、Vite |
| Java Gateway | Java 17、Spring Boot 3.4、Spring MVC |
| Agent Runtime | Python、Anthropic SDK |
| RPC | gRPC、Protocol Buffers |
| Java Build | Maven |
| Python Dependencies | pip、requirements.txt |

## Project Structure

```text
EA-Haraness/
├── agent-core/       Python Agent Runtime 和 gRPC 服务
├── agent-service/    Java Spring Boot REST 网关
├── web-frontend/     Vue 3 前端
├── scripts/          本地联调脚本
└── PRD/              项目设计和技术文档
```

Java 服务中的 Agent 代码按功能域组织：

```text
com.eaharness.agent
├── config/
├── controller/
├── dto/
└── service/
```

这种结构将 Agent 相关的配置、接口、数据模型和业务服务集中在同一功能域中，避免技术分层目录随着功能增长而相互混杂。

## Local Development

### Configure the Python Runtime

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness/agent-core
python3 -m venv .venv
./.venv/bin/python -m pip install -r requirements.txt
cp .env.example .env
```

在 `.env` 中配置模型和 API 相关环境变量。真实密钥不要提交到 Git。

### Start the Python gRPC Service

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness/agent-core
./.venv/bin/python -m agent_service.server
```

默认监听：

```text
127.0.0.1:50051
```

### Start the Java Gateway

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness/agent-service
mvn generate-sources
mvn spring-boot:run
```

默认监听：

```text
127.0.0.1:8080
```

Java 服务通过 `agent-service/src/main/resources/application.yml` 配置 Python gRPC 地址。

### Start the Frontend

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness/web-frontend
npm install
npm run dev
```

访问：

```text
http://127.0.0.1:5173
```

### Start Java and Python Together

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness
./scripts/start-rpc-services.sh
```

该脚本启动 Python gRPC Server 和 Java Gateway，不启动前端开发服务器。

## API Overview

```text
GET    /api/health
POST   /api/sessions
GET    /api/sessions
GET    /api/sessions/{sessionId}
DELETE /api/sessions/{sessionId}
POST   /api/sessions/{sessionId}/messages
```

## Design Focus

- 通过 Java 网关隔离前端协议与 Agent Runtime 实现；
- 通过 gRPC 和 Protobuf 明确 Java 与 Python 的跨语言接口契约；
- 通过会话上下文维护多轮 Agent 交互；
- 通过工具注册和统一调用入口扩展 Agent 执行能力；
- 通过重试、上下文压缩和恢复逻辑提升长任务稳定性；
- 通过清晰的功能域边界为后续流式输出、评估体系和持久化能力扩展保留空间。

## Current Scope

EA-Harness 当前定位为本地 Agent 工作台和架构验证项目。会话数据和部分运行时状态仍采用内存实现；生产化部署还需要补充身份认证、权限控制、持久化存储、流式响应、系统化评估、任务取消和可观测性能力。
