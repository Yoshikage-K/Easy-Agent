# EA-Haraness：Java 调用 Python 的 gRPC SOP 与面试题

本文基于当前仓库 `/Users/yunhua/Work/Java/projects/EA-Haraness` 编写，目标是说明：

```text
Vue 3 -> Java Spring Boot REST -> Java gRPC Client -> Python gRPC Server -> Agent Core -> Model API
```

当前实现是本地联调版本：Java REST 监听 `127.0.0.1:8080`，Python gRPC 监听 `127.0.0.1:50051`，两者使用明文连接；会话和运行时状态都保存在内存中，服务重启后会丢失。

## 第一部分：gRPC 调用流程

### 1. 先理解 gRPC 在本项目中的位置

gRPC 不是一个需要单独启动的中心服务，也不是 Nacos。它是一套通信框架和协议：Python 进程启动 gRPC Server，Java 进程作为 gRPC Client 通过 TCP 连接到 Python 的监听地址。

本地时两个进程在同一台机器上；部署到不同服务器时，只需要把 Java 的 `agent.grpc.host` 改成 Python 服务可访问的地址，并配置网络、安全组和 TLS。

```mermaid
sequenceDiagram
    participant Browser as Vue 前端
    participant Java as Java REST 网关 :8080
    participant Stub as Java BlockingStub
    participant Py as Python gRPC Server :50051
    participant Runner as app.agent_runner
    participant Loop as agent.loop
    participant Model as 模型 API

    Browser->>Java: POST /api/sessions/{id}/messages
    Java->>Java: 校验 Java 内存会话并保存 user 消息
    Java->>Stub: 构造 ChatRequest
    Stub->>Py: HTTP/2 gRPC Chat RPC
    Py->>Runner: send_to_agent(session, message)
    Runner->>Loop: agent_loop(history, context)
    Loop->>Model: 调用模型并处理工具循环
    Model-->>Loop: assistant/tool 结果
    Loop-->>Runner: 更新 history/context
    Runner-->>Py: 提取 assistant 文本
    Py-->>Stub: ChatResponse
    Stub-->>Java: 生成的 protobuf response
    Java->>Java: 转成本地 DTO 并保存 assistant 消息
    Java-->>Browser: JSON ChatResponse
```

### 2. 协议文件：双方通信的合同

文件：

- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/src/main/proto/agent.proto`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/proto/agent.proto`

当前协议核心内容是：

```proto
service AgentService {
  rpc Chat(ChatRequest) returns (ChatResponse);
}
```

`ChatRequest` 有三个字段：`trace_id`、`session_id`、`message`；`ChatResponse` 有五个字段：`trace_id`、`session_id`、`success`、`content`、`error_message`。

字段后的数字是 protobuf 的 wire tag，不是 Java/Python 的数组下标。双方必须保持 service 名称、RPC 名称、字段编号和字段类型兼容。新增字段通常可以兼容，但不要复用已经发布过的字段编号。

Java proto 中的选项：

- `java_multiple_files = true`：生成多个 Java 类型，而不是全部嵌套在一个外层类中。
- `java_package = "com.eaharness.agent.v1"`：生成类的 Java 包名。
- `java_outer_classname = "AgentProto"`：外层 proto 类名，当前多文件模式下主要作为生成配置的一部分。

两份 proto 目前内容相同。它们不是运行时互相读取，而是在两个项目内分别生成各自语言的代码，所以修改协议后必须重新生成双方代码并重新编译。

### 3. Java 端如何生成 gRPC 代码

文件：

- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/pom.xml`
- 生成目录通常是 `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/target/generated-sources/`

`pom.xml` 使用 protobuf Maven 插件，配合 `protoc` 和 `protoc-gen-grpc-java` 生成：

- `com.eaharness.agent.v1.ChatRequest`
- `com.eaharness.agent.v1.ChatResponse`
- `com.eaharness.agent.v1.AgentServiceGrpc`

因此 `AgentServiceGrpc` 不是手写类。IDEA 中出现 `Cannot resolve symbol 'AgentServiceGrpc'` 时，优先执行：

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness/agent-service
mvn generate-sources
mvn compile
```

如果系统 `PATH` 中没有 `mvn`，使用 IntelliJ 自带 Maven 的绝对路径：

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" generate-sources
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" compile
```

生成目录属于构建产物，通常不应手工修改。协议源文件是 `src/main/proto/agent.proto`。

### 4. Python 端如何生成 gRPC 代码

文件：

- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/scripts/generate_grpc.py`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/agent_service/generated/agent_pb2.py`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/agent_service/generated/agent_pb2_grpc.py`

运行：

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness/agent-core
./.venv/bin/python scripts/generate_grpc.py
```

`grpcio` 是运行时依赖，`grpcio-tools` 提供 Python 代码生成工具。`agent_pb2.py` 保存 protobuf 消息类，`agent_pb2_grpc.py` 保存客户端 Stub、服务端 Servicer 基类以及注册函数。

`generate_grpc.py` 额外把生成文件中的：

```python
import agent_pb2 as agent__pb2
```

修正为包内相对导入：

```python
from . import agent_pb2 as agent__pb2
```

这是为了支持 `python -m agent_service.server` 的包启动方式。生成文件不要直接编辑，应该修改脚本或重新生成。

### 5. Python Server 的启动与注册

文件：

- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/agent_service/server.py`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/agent_service/service.py`

启动入口 `server.py` 做两件事：配置日志，然后调用 `service.py` 的 `serve()`。

`service.py` 的 `serve()` 流程：

1. 创建 `grpc.server(futures.ThreadPoolExecutor(max_workers=4))`。
2. 创建 `AgentService()`，它继承生成的 `AgentServiceServicer`。
3. 调用 `agent_pb2_grpc.add_AgentServiceServicer_to_server(...)` 注册服务实现。
4. 调用 `server.add_insecure_port("127.0.0.1:50051")` 绑定监听地址。
5. `server.start()` 启动服务。
6. `server.wait_for_termination()` 阻塞主线程，保持进程运行。

`max_workers=4` 是 Python gRPC Server 的业务执行线程数，和 Java 客户端的连接数不是同一个概念。当前使用 `add_insecure_port`，只适合本地开发；生产环境应该使用服务器证书、客户端凭证或服务网格提供的 TLS。

### 6. Python `Chat` RPC 做了什么

文件：

`/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/agent_service/service.py`

方法：`AgentService.Chat(self, request, context)`。

执行顺序：

1. 从 protobuf request 读取 `trace_id`、`session_id` 和 `message`。
2. 记录 trace 日志。
3. 校验 `session_id` 和非空 message；失败时返回 `success=false` 的 `ChatResponse`。
4. 使用 `store.get_or_create(request.session_id)` 获取 Python 侧 AgentSession。
5. 调用 `send_to_agent(session, request.message)`。
6. 将返回的 assistant 消息拼接成 `content`。
7. 构造 protobuf `ChatResponse` 返回给 Java。
8. 捕获异常并记录堆栈，返回 `success=false` 和 `error_message`。

这里没有直接返回 gRPC status exception，而是把业务失败编码到 response 的 `success/error_message` 字段中。传输层异常，例如 Python 进程退出或连接拒绝，Java 端仍然会收到 gRPC `StatusRuntimeException`。

### 7. Python gRPC 到 Agent Core 的调用

文件：

- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/app/agent_runner.py`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/app/session_store.py`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/agent/loop.py`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/agent/runtime.py`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/agent/context.py`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-core/agent/config.py`

`agent_runner.py` 是 gRPC 层和原有 Agent 系统之间的适配器：

1. `ensure_runtime()` 首次请求时调用 `initialize_runtime(start_scheduler=False)`，完成运行时目录、工具等初始化；通过 `_runtime_ready` 避免每轮重复初始化。
2. 把用户消息写入 Python 会话的 UI 消息和模型 `history`。
3. 调用 `agent_loop(session.history, session.context)`。
4. 调用 `update_context(...)` 更新上下文。
5. 从本轮新增的 assistant 消息中提取文本，作为 RPC response 的 `content`。

`agent/loop.py` 是真正执行模型循环的地方：它组装系统提示词、请求模型、处理工具调用，并在模型不再请求工具时结束本轮。`agent/config.py` 读取本地环境变量和模型配置，真实 API Key 应只保留在本地 `.env`，不能写入协议或提交到仓库。

`app/session_store.py` 的 `store` 是 Python 进程内的内存会话仓库。Java 侧的 `SessionService` 也有一份独立内存会话。两边通过同一个 `session_id` 关联，但不是共享同一个对象；当前 Java 删除会话不会自动删除 Python 会话，这是后续需要补充的跨服务会话删除协议或持久化设计。

### 8. Java Channel、Stub 与请求发送

文件：

- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/src/main/resources/application.yml`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/src/main/java/com/eaharness/config/AgentGrpcProperties.java`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/src/main/java/com/eaharness/config/GrpcConfig.java`
- `/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/src/main/java/com/eaharness/service/AgentGrpcClient.java`

`application.yml` 配置：

```yaml
agent:
  grpc:
    host: 127.0.0.1
    port: 50051
```

`AgentGrpcProperties` 将配置绑定为 Java 对象。`GrpcConfig` 创建一个 Spring Bean：

```java
ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
```

`ManagedChannel` 是可复用的长生命周期连接抽象，不应该每次请求都创建和销毁。`@Bean(destroyMethod = "shutdownNow")` 保证 Spring 关闭时释放 Channel。

`AgentGrpcClient` 用生成的 `AgentServiceGrpc.newBlockingStub(channel)` 创建同步 Stub。`chat()` 中：

1. 生成 `trace_id`。
2. 使用生成的 `ChatRequest.newBuilder()` 构造请求。
3. 调用 `stub.chat(...)`，此调用会阻塞当前 Java 线程直到收到响应或发生异常。
4. 将生成的 `com.eaharness.agent.v1.ChatResponse` 转换成项目自己的 `com.eaharness.dto.ChatResponse`。

特别注意：代码中有两个 `ChatResponse`。`com.eaharness.agent.v1.ChatResponse` 是 protobuf 生成类，`com.eaharness.dto.ChatResponse` 是 Java REST 返回 DTO，二者不是同一个类型。

### 9. Java REST 入口与完整请求链

文件：

`/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/src/main/java/com/eaharness/controller/ChatController.java`

前端请求：

```http
POST /api/sessions/{sessionId}/messages
Content-Type: application/json

{"message":"请介绍一下这个项目"}
```

Controller 先校验 Java 内存会话存在，再保存 user 消息，调用 `AgentGrpcClient.chat()`，成功后保存 assistant 消息并返回 JSON。

Java 侧的本地 DTO 位于：

`/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/src/main/java/com/eaharness/dto/`

其中 `ChatRequest` 负责 REST 入参校验，`ChatResponse` 负责 REST 出参，`ChatMessage` 和 `SessionDetail` 负责前端会话数据。它们和 protobuf DTO 分层存在，避免把 RPC 协议类型直接暴露给浏览器。

### 10. 完整启动 SOP

#### 10.1 首次准备 Python 虚拟环境

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness/agent-core
python3 -m venv .venv
./.venv/bin/python -m pip install -r requirements.txt
cp .env.example .env
```

在 `.env` 中填写本地模型配置。不要把真实 API Key 提交到 Git。

#### 10.2 启动 Python gRPC Server

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness/agent-core
./.venv/bin/python scripts/generate_grpc.py
./.venv/bin/python -m agent_service.server
```

看到以下日志表示 Python 监听成功：

```text
Python Agent gRPC server listening on 127.0.0.1:50051
```

#### 10.3 启动 Java REST 网关

另开一个终端：

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness/agent-service
mvn generate-sources
mvn spring-boot:run
```

如果 `mvn` 不在 PATH：

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -f /Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/pom.xml generate-sources
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" -f /Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/pom.xml spring-boot:run
```

#### 10.4 只启动两个 RPC 相关进程

仓库已有脚本：

`/Users/yunhua/Work/Java/projects/EA-Haraness/scripts/start-rpc-services.sh`

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness
./scripts/start-rpc-services.sh
```

它只负责 Python gRPC Server 和 Java Agent Service，不启动 Vue，也不启动 Python FastAPI 主入口。Python 日志写到 `.runtime/python-grpc.log`。

#### 10.5 用 curl 验证接口

先验证 Java：

```bash
curl http://127.0.0.1:8080/api/health
```

创建 Java 会话：

```bash
curl -X POST http://127.0.0.1:8080/api/sessions
```

假设返回 `{"sessionId":"sess_xxx"}`，替换下面的 `sess_xxx`：

```bash
curl -X POST http://127.0.0.1:8080/api/sessions/sess_xxx/messages \
  -H 'Content-Type: application/json' \
  -d '{"message":"请用一句话介绍你自己"}'
```

这一步才会触发 Java -> Python 的 gRPC 调用。只调用 `/api/health` 不会访问 Python。

### 11. 断点调试顺序

Java 断点建议放在：

1. `ChatController.chat()`
2. `AgentGrpcClient.chat()` 构造 request 的位置
3. `GrpcConfig.agentChannel()` 查看 host/port 和 Channel 创建

Python 断点建议放在：

1. `agent_service/service.py::AgentService.Chat()`
2. `app/agent_runner.py::send_to_agent()`
3. `agent/loop.py::agent_loop()`

调用 curl 或前端发送消息后，预期顺序是 Java Controller -> Java Stub -> Python `Chat` -> `send_to_agent` -> `agent_loop` -> Python response -> Java DTO -> HTTP response。

常见故障：

| 现象 | 优先检查 |
|---|---|
| Java `UNAVAILABLE` / connection refused | Python 是否监听 `50051`，Java `application.yml` 的 host/port 是否一致 |
| `AgentServiceGrpc` 爆红 | 执行 `generate-sources` 和 `compile`，确认 Maven 项目已重新加载 |
| Python `No module named agent_pb2` | 使用包模块方式启动，并重新运行 `scripts/generate_grpc.py` |
| 请求卡住 | Python 模型 API、网络、工具循环或超时；当前 Java 使用 blocking stub |
| Java 返回 404 Session not found | 先调用 Java `/api/sessions` 创建会话，不能直接凭空使用 session ID |
| 服务重启后会话消失 | 当前 Java/Python SessionStore 都是内存实现 |

## 第二部分：面试问题与参考答案

### A. gRPC 基础

#### 1. gRPC 和 REST 有什么区别？

REST 通常以 HTTP 资源和 JSON 为中心，浏览器调用方便；gRPC 以服务方法和 protobuf 为中心，强类型、代码生成、二进制传输和 HTTP/2 通信能力更强。本项目对浏览器暴露 REST，对 Java 到 Python 的内部调用使用 gRPC，是分层后的适配选择。

#### 2. gRPC 为什么依赖 `.proto`？

`.proto` 是接口合同，定义 service、RPC 方法、请求和响应消息。不同语言根据同一份合同生成客户端和服务端代码，避免手写序列化、反序列化和网络协议细节。

#### 3. protobuf 字段编号有什么作用？

字段编号用于 wire format 的编码和解码。新增字段可以使用新的编号；已经使用过的编号不能随便改含义或重新分配，否则旧客户端和新服务端可能产生数据错误。

#### 4. gRPC 的四种调用类型是什么？

Unary 是一次请求一次响应；server streaming 是一次请求多个响应；client streaming 是多个请求一次响应；bidirectional streaming 是双方都可以连续发送。本项目的 `Chat` 是 unary RPC，后续要做流式输出可以改成 server streaming 或双向 streaming。

#### 5. Stub 是什么？

Stub 是客户端代理。调用 `stub.chat(request)` 看起来像调用本地方法，实际会把 protobuf 请求编码后通过 HTTP/2 发到远端服务，再把响应解码成 protobuf 对象。

#### 6. Channel 和 Stub 的区别？

Channel 负责连接、地址、传输和生命周期；Stub 基于 Channel 提供具体服务方法。Channel 应该复用，Stub 可以基于同一个 Channel 创建不同调用形式，例如 blocking、future 或 async stub。

#### 7. `usePlaintext()` 有什么风险？

它表示不启用 TLS，链路上的内容可能被窃听或篡改，只适合本地开发或受控内网。跨服务器部署应配置 TLS、证书校验、认证和网络访问控制。

#### 8. gRPC 异常和业务失败有什么区别？

连接拒绝、超时、服务不可用通常是 gRPC transport/status 异常；模型拒绝、参数不合法等可以作为业务 response 返回。本项目当前用 `success` 和 `error_message` 表达 Python 业务失败，同时让传输层异常以 gRPC 异常传播。

### B. 针对当前项目的追问

#### 1. Java 是如何找到 Python 服务的？

`application.yml` 中的 `agent.grpc.host` 和 `port` 被 `AgentGrpcProperties` 绑定，`GrpcConfig` 用它们创建 `ManagedChannel`。Java 不需要知道 Python 类名，只需要知道 proto 生成的服务合同和网络地址。

#### 2. Java 调用的 `AgentServiceGrpc` 是从哪里来的？

它由 `agent-service/pom.xml` 的 protobuf Maven 插件根据 `src/main/proto/agent.proto` 生成，位于 Maven 的 generated sources 中。它不是业务代码，也不应该手写或直接改 generated 文件。

#### 3. Python 为什么必须用 `python -m agent_service.server`？

因为 `server.py` 使用包内相对导入 `from .service import serve`，模块方式启动会保留 `agent_service` 包上下文。直接执行 `python agent_service/server.py` 会让 Python 缺少父包信息，容易触发 `attempted relative import with no known parent package`。

#### 4. Java 的 blocking stub 会不会阻塞 Tomcat 线程？

会。当前 `AgentGrpcClient.chat()` 在 REST 请求线程中同步等待 Python 返回。模型调用时间较长时会占用 Tomcat 工作线程。生产化可以考虑异步 stub、任务队列、超时、并发隔离或改成提交任务后轮询/订阅结果。

#### 5. 为什么 Java 和 Python 都有 SessionStore？

当前 Java SessionService 面向前端会话管理，Python SessionStore 面向 Agent history/context；它们属于两个进程，不能直接共享内存。现在用同一个 `session_id` 关联。生产环境需要明确单一会话数据源，或者用 Redis/数据库持久化，并设计创建、删除、过期和并发更新协议。

#### 6. 当前会话删除是否完整？

不完整。Java 删除只会删除 Java 侧内存会话，Python 侧的 `store` 仍可能保留相同 session。若要完整删除，需要增加 `DeleteSession` RPC，或由 Java 调用统一的会话服务，并处理删除失败、重试和幂等。

#### 7. 如果 Python 服务重启，Java Channel 会怎样？

Channel 本身可以继续存在并尝试重新连接，但正在执行的 RPC 可能失败，Java 会收到 `UNAVAILABLE` 等状态。客户端应设置 deadline、重试策略和错误分类；重试前还要确认 RPC 是否幂等，避免模型请求被重复执行。

#### 8. trace_id 的作用是什么？

Java 在 `AgentGrpcClient` 中生成 trace ID，通过 protobuf 传给 Python，Python 日志使用同一个 ID。这样可以把一次浏览器请求、Java 日志、Python gRPC 日志和模型执行日志串起来。更完整的方案还应把 HTTP 请求已有的 trace ID 透传下来，而不是每层重新生成。

#### 9. 如何给当前 gRPC 增加超时？

可以对 Stub 设置 deadline，例如 `stub.withDeadlineAfter(timeout, TimeUnit.SECONDS).chat(request)`。超时值应配置化，并区分连接超时、模型执行超时和客户端整体请求超时。当前代码还没有设置 deadline，这是一个生产化改进点。

#### 10. 如何增加流式输出？

把 proto 改成 server streaming，例如 `rpc Chat(ChatRequest) returns (stream ChatChunk);`，Python 使用 response iterator 持续返回 token/chunk，Java 使用 blocking streaming stub 或 async stub 消费流，Java REST 再通过 SSE/WebSocket 转给前端。不能只在现有 unary response 外面套一层“假流式”。

#### 11. 当前 Python gRPC Server 的并发模型是什么？

`grpc.server(ThreadPoolExecutor(max_workers=4))` 为 RPC handler 提供线程池。每个请求可能在一个 worker 中执行，而模型和工具调用可能很慢。需要结合模型 SDK 是否线程安全、session 是否允许并行轮次、最大并发、限流和资源占用来调整，而不是只增大线程数。

#### 12. 如何做安全和可观测性？

跨服务器时启用 TLS 和认证，限制监听地址和防火墙范围，不把 API Key 放进日志；为每个 RPC 设置 deadline、状态码和指标，记录 trace_id、session_id、耗时、结果类别和重试次数；对用户消息和模型输出做脱敏，避免日志泄露敏感数据。

#### 13. 如何测试这条链路？

可以分层测试：Java Controller 使用 mock 的 `AgentGrpcClient` 测 REST 行为；Java gRPC client 使用 Python 测试 server 或真实本地 server 测协议兼容；Python `AgentService.Chat` 使用 fake `send_to_agent` 测参数校验和错误映射；最后用真实模型做一条端到端测试。测试不应依赖真实 API Key 才能覆盖协议和错误路径。

#### 14. 如果两个服务的 proto 版本不一致怎么办？

建立 proto 版本管理和兼容性规则。服务端优先向后兼容，新增字段使用新编号并提供默认值；必要时引入 `ea.agent.v2` 包或新 RPC。构建阶段可以把 proto 作为共享契约包，避免 Java 和 Python 各自维护后发生漂移。

## 最后复述：一次调用如何完整走完

执行一次真正的对话调用时，按下面顺序检查：

1. Python 虚拟环境和本地 `.env` 正常。
2. Python 生成代码与 proto 一致。
3. Python `agent_service.server` 监听 `127.0.0.1:50051`。
4. Java Maven 已生成 `AgentServiceGrpc` 并启动 REST 网关 `127.0.0.1:8080`。
5. 先用 Java REST 创建 session。
6. 调用 `/api/sessions/{sessionId}/messages`。
7. Java Controller 调用 blocking Stub。
8. Python `AgentService.Chat` 接收并校验请求。
9. Python runner 调用 `agent_loop`，由 Agent Core 调用模型。
10. Python 返回 protobuf response，Java 转成本地 DTO，最后返回 JSON。

这条链路中，真正跨进程的边界只有 Java gRPC Client 和 Python gRPC Server 之间；Vue 和 Java 之间是 HTTP REST，Python Agent Core 和 gRPC Server 之间是同一 Python 进程内的函数调用。
