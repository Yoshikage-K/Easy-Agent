<div align="center">

# 🤖 EA Harness

**一个用于本地运行和调试 EASY-AGENT 的全栈开发工作台**

<p>
  <img src="https://img.shields.io/badge/Python-3.10%2B-3776AB?logo=python&logoColor=white" alt="Python" />
  <img src="https://img.shields.io/badge/FastAPI-0.1%2B-009688?logo=fastapi&logoColor=white" alt="FastAPI" />
  <img src="https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white" alt="Vue 3" />
  <img src="https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white" alt="TypeScript" />
  <img src="https://img.shields.io/badge/Vite-7-646CFF?logo=vite&logoColor=white" alt="Vite" />
</p>

<p>
  <a href="#-快速开始">快速开始</a> ·
  <a href="#-架构概览">架构概览</a> ·
  <a href="#-api">API</a> ·
  <a href="#-测试">测试</a>
</p>

</div>

---

## 👋 About the Project

EA Harness 是 EASY-AGENT runtime 的本地开发与验证环境，提供一个浏览器聊天界面和一组后端会话 API。

它把 Agent 的核心执行过程集中在一个可观察、可调试的工作台中，方便验证：

- 多轮会话与消息历史管理
- Agent Loop、模型调用和工具调用
- 上下文压缩与历史整理
- MCP、Skills、Subagent 和后台任务
- 会话创建、切换、删除和运行状态展示

当前项目由 Vue 前端、FastAPI 接入层和 Python Agent runtime 组成，并保留了 gRPC/Protobuf 服务作为独立服务化入口。

## ✨ Features

| 模块 | 能力 |
| --- | --- |
| Chat Workspace | 会话列表、新建任务、消息输入和结果展示 |
| Session API | 创建、查询、删除会话，提交用户消息 |
| Agent Loop | 驱动模型、工具结果和下一轮推理 |
| Context | 管理上下文长度，必要时压缩历史消息 |
| Tools & MCP | 扩展外部工具、MCP 服务和 Agent Skills |
| Subagent | 支持子 Agent 和后台任务协作 |
| Service Layer | FastAPI HTTP 接口与 gRPC 服务入口 |

## 🧰 Tech Stack

### Languages

![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)

### Backend

![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white)
![Anthropic](https://img.shields.io/badge/Anthropic-191919?style=for-the-badge&logo=anthropic&logoColor=white)
![gRPC](https://img.shields.io/badge/gRPC-244C5A?style=for-the-badge&logo=grpc&logoColor=white)
![Protocol Buffers](https://img.shields.io/badge/Protobuf-4285F4?style=for-the-badge&logo=google&logoColor=white)

### Frontend

![Vue](https://img.shields.io/badge/Vue_3-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=vite&logoColor=white)

## 🏗️ 架构概览

```text
┌────────────────────┐
│ Vue 3 Frontend     │  会话列表 / 聊天窗口 / 消息输入
└─────────┬──────────┘
          │ HTTP JSON
┌─────────▼──────────┐
│ FastAPI API        │  /api/health
│                    │  /api/sessions
└─────────┬──────────┘
          │
┌─────────▼──────────┐
│ Session & Runtime  │  会话状态 / Agent 调度
└─────────┬──────────┘
          │
┌─────────▼──────────┐
│ Agent Loop         │  模型调用 ↔ 工具调用 ↔ 工具结果
└─────┬───────┬──────┘
      │       │
  Tools     MCP / Skills / Subagents / Background Tasks
```

一次消息的核心路径是：前端提交消息 → FastAPI 找到会话 → runtime 驱动 Agent Loop → Agent 根据需要调用工具 → 结果回到会话 → API 返回给前端。

## 📁 Project Structure

```text
EA-Harness/
├── backend/
│   ├── main.py                 # FastAPI 应用与 HTTP API
│   ├── agent/                  # Agent runtime 核心
│   │   ├── loop.py             # Agent 主循环
│   │   ├── context.py          # 上下文准备
│   │   ├── compact.py          # 历史消息压缩
│   │   ├── tools/              # 工具实现
│   │   ├── subagent/           # 子 Agent
│   │   ├── mcp.py              # MCP 集成
│   │   ├── skills.py           # Skills 加载
│   │   └── test/               # Agent 测试
│   ├── agent_service/          # gRPC 服务入口
│   ├── app/                    # 应用层模块
│   └── requirements.txt
├── frontend/
│   ├── src/                    # Vue 页面与组件
│   ├── package.json
│   └── vite.config.ts
├── proto/
│   └── agent.proto             # gRPC/Protobuf 定义
└── README.md
```

## 🚀 快速开始

### 1. 启动后端

```bash
cd /Users/yunhua/Work/Agent/EA-Harness/backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn main:app --reload --host 127.0.0.1 --port 8000
```

后端默认地址：<http://127.0.0.1:8000>

### 2. 启动前端

新开一个终端：

```bash
cd /Users/yunhua/Work/Agent/EA-Harness/frontend
npm install
npm run dev
```

打开 <http://127.0.0.1:5173> 即可进入工作台。

> 运行模型调用前，请根据 `backend/.env.example` 配置模型相关环境变量。复制的 Agent runtime 也支持 `backend/agent/.env`。不要把真实密钥提交到 Git。

## 🔌 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/health` | 检查服务状态 |
| `GET` | `/api/sessions` | 获取会话列表 |
| `POST` | `/api/sessions` | 创建新会话 |
| `GET` | `/api/sessions/{session_id}` | 获取会话详情 |
| `DELETE` | `/api/sessions/{session_id}` | 删除会话 |
| `POST` | `/api/sessions/{session_id}/messages` | 向会话发送消息 |

## 🧪 测试

后端测试：

```bash
cd /Users/yunhua/Work/Agent/EA-Harness/backend
python -m unittest discover -s agent/test -p 'test_*.py'
```

前端类型检查与生产构建：

```bash
cd /Users/yunhua/Work/Agent/EA-Harness/frontend
npm run build
```

## 🧭 开发约定

- 后端配置放在本地 `.env` 中，密钥和运行时状态不要提交。
- 新增 Agent 能力时，优先放入对应的 runtime 模块，并补充测试。
- 修改 API 时同步更新前端调用和本 README 的接口表。
- 对上下文、工具调用和子 Agent 的改动，重点验证异常路径与重复调用行为。

## 📌 Status

项目当前定位为 EASY-AGENT 的本地开发、联调和实验性验证环境。功能会随着 Agent runtime 的演进持续补充。

