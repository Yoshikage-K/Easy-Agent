# EA-Haraness Transfer 详细 PRD 与源码解读

> 文档类型：逆向分析型 PRD、代码阅读指南与技术设计说明
>
> 分析对象：`/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service`
>
> 当前命名：原来的 download/update 方向统一归入 `transfer`；当前实现中，`transfer` 下同时保留 `upload` 和 `download` 两个方向。

## 1. 先看结论：现在的 transfer 到底做什么

当前 Java 服务中的 `transfer` 不是单纯的“下载模块”，而是文件传输模块，包含两条不同的数据流：

```text
transfer.upload：远端 HTTP/HTTPS 文件 URL -> Java Range 下载 -> MinIO Multipart Upload

transfer.download：MinIO 对象 -> Java StreamingResponseBody -> 浏览器/调用方
```

完整关系可以理解为：

```text
                         上传到 MinIO
外部远端文件服务器 ─────────────────────────> MinIO
       ^                                      |
       |                                      | 下载/预签名 URL
       |                                      v
       |                              Java REST 客户端
       |
       └── Java 使用 HTTP HEAD 和 Range GET
```

这意味着：

- `upload` 才是大文件、128 MiB 分片、8 个线程、Redis 分片状态、DirectByteBuffer 和 MinIO Multipart 的核心链路。
- `download` 是从 MinIO 读取已经存在的对象，支持普通下载、单段 HTTP Range 下载和预签名 URL。
- 当前没有把完整文件读入 Java 堆内存，也没有把 TB 级文件一次性保存到 Java 本地磁盘。
- Java 内存中保存的是任务元数据、上传 ID、ETag 和运行控制对象；真正的文件内容在远端和 MinIO 之间以流的方式传输。

当前实现仍然是原型和本地联调版本：任务对象保存在 Java 内存，Redis 只记录分片状态，MinIO 保存 Multipart 上传内容。Java 服务重启后，内存中的 `UploadTask` 和运行时控制对象会消失，Redis 和 MinIO 中遗留的上传数据不会自动恢复成 Java 任务。

## 2. 业务需求映射

### 2.1 远端文件传入 MinIO

用户提供：

- 远端 HTTP/HTTPS 文件地址；
- 可选请求头，例如远端服务要求的鉴权头；
- MinIO bucket；
- MinIO objectName。

系统执行：

1. 对远端地址发送 HEAD，取得 `Content-Length`、`ETag`、`Content-Type`、`Accept-Ranges`。
2. 按固定的 128 MiB 理论分片计算每个分片的字节区间。
3. 调用 MinIO 创建 Multipart Upload，取得 `uploadId`。
4. 使用最多 8 个 worker，并行对远端发送 HTTP Range GET。
5. 每个远端分片不落本地磁盘，而是经过 direct buffer 流式交给 MinIO 的 `uploadPart`。
6. 每个分片成功后记录 ETag，并把 Redis 中的两 bit 状态设为 `COMPLETED`。
7. 所有分片完成后，按 part number 排序，调用 MinIO complete Multipart Upload。

### 2.2 传输控制

当前提供：

- 创建任务；
- 启动任务；
- 暂停任务；
- 恢复任务；
- 重试失败或暂停任务；
- 取消任务并终止 MinIO Multipart Upload；
- 查询任务和各分片状态。

暂停不是强行杀死线程。实现会先把任务设为 `PAUSING`，设置暂停标志，关闭正在读取的远端 InputStream，等待协调线程收集分片结果，最后将任务设为 `PAUSED`。

### 2.3 从 MinIO 下载给客户端

用户可以：

- 先调用接口获取 15 分钟有效的 MinIO 预签名 URL；
- 或通过 Java 接口让 Java 读取 MinIO 对象并流式写回客户端；
- 通过 HTTP `Range: bytes=start-end` 请求单段范围。

这条链路不使用远端文件的 `RangeFileSource`，也不使用 Redis 分片状态。它读取的是已经存在的 MinIO 对象。

## 3. 实际工程结构

```text
/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/
├── src/main/java/com/eaharness/
│   ├── EaHarnessApplication.java
│   ├── agent/                         Agent gRPC 与会话接口
│   ├── plugin/                        Mini 插件系统
│   └── transfer/                      文件传输模块
│       ├── config/                    transfer 配置、线程池、MinIO Client
│       ├── source/                    远端文件 HEAD/Range GET
│       ├── state/                     分片状态抽象与 Redis 实现
│       ├── storage/                   Multipart 存储抽象与 MinIO 实现
│       ├── util/                      direct buffer 流
│       ├── upload/                    远端文件 -> MinIO
│       │   ├── controller/
│       │   ├── domain/
│       │   ├── dto/
│       │   ├── exception/
│       │   └── service/
│       └── download/                  MinIO -> 客户端
│           ├── controller/
│           ├── dto/
│           ├── exception/
│           ├── service/
│           └── storage/
├── src/main/proto/agent.proto
├── src/main/resources/application.yml
└── pom.xml
```

### 3.1 命名变化说明

当前源码包名是：

```java
com.eaharness.transfer
```

而不是旧版本中可能出现的：

```java
com.eaharness.download
com.eaharness.download.update
```

因此阅读新代码时，以 `transfer` 为入口。`upload` 和 `download` 是传输方向，不要把 `upload` 理解成“用户上传本地文件”：当前 `upload` 的源是远端 URL，目标才是 MinIO。

另外，`RedisPartStateStore` 里的 key 前缀仍然是：

```text
eaharness:download:parts:{taskId}
```

这只是 Redis key 的历史命名，没有改变 Java 包已经统一为 `transfer` 的事实。如果未来需要彻底统一，可以把 key 前缀改为 `eaharness:transfer:upload:parts:`，但改动时要考虑已有状态数据迁移。

## 4. 配置与依赖

### 4.1 Maven 依赖

文件：

`/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/pom.xml`

传输模块直接依赖：

| 依赖 | 用途 |
|---|---|
| `spring-boot-starter-web` | 提供 REST Controller、响应流和 HTTP 接口 |
| `spring-boot-starter-validation` | 校验请求体中的 `@NotBlank` 等约束 |
| `spring-boot-starter-data-redis` | 提供 `StringRedisTemplate`，读写分片状态 |
| `io.minio:minio:8.5.17` | Java 调用 MinIO，创建、上传、完成、取消 Multipart，以及读对象和生成 URL |
| Java 17 | 使用 Record、`HttpClient`、现代并发与集合 API |

gRPC 依赖属于 Agent 链路，不是 transfer 的核心依赖，但和这个 Spring Boot 服务一起打包。

### 4.2 application.yml

文件：

`/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/src/main/resources/application.yml`

当前关键配置：

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379

eaharness:
  transfer:
    worker-count: 8
    part-size-bytes: 134217728
    buffer-size-bytes: 8388608
    max-part-count: 10000
    max-retry-attempts: 3
    retry-backoff-millis: 1000
    request-timeout-seconds: 120
    minio:
      endpoint: ${MINIO_ENDPOINT:http://127.0.0.1:9000}
      access-key: ${MINIO_ACCESS_KEY:minioadmin}
      secret-key: ${MINIO_SECRET_KEY:minioadmin}
```

配置含义：

- `worker-count=8`：同时执行分片任务的 worker 数量。
- `part-size-bytes=134217728`：每个理论分片 128 MiB。
- `buffer-size-bytes=8388608`：每个分片传输时使用 8 MiB direct buffer，不是文件大小限制。
- `max-part-count=10000`：防止文件被拆成过多分片；当前最大支持的文件理论大小约为 `10000 * 128 MiB`，实际还受 MinIO 和远端服务约束。
- `max-retry-attempts=3`：单个分片失败后的最大尝试次数。
- `retry-backoff-millis=1000`：重试前等待时间。
- `request-timeout-seconds=120`：HEAD 和 Range GET 的 HTTP 超时。
- `minio.endpoint`：MinIO API 地址，不是浏览器访问的管理控制台页面地址。

### 4.3 TransferProperties

文件：

`/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/src/main/java/com/eaharness/transfer/config/TransferProperties.java`

`@ConfigurationProperties(prefix = "eaharness.transfer")` 将 YAML 绑定为 Java 配置对象。类中的默认常量包括 worker 数、128 MiB 分片大小、最大分片数和最大重试次数；YAML 可以覆盖这些默认值。

`requestTimeout()` 将秒数转换成 Java `Duration`，供 JDK `HttpClient` 使用。

## 5. 推荐的代码阅读顺序

不要一开始从 `MinioMultipartStorage` 或 `DirectBufferInputStream` 读。建议按一条真实请求从外到内阅读。

### 第一步：先看总配置

1. `src/main/resources/application.yml`
2. `transfer/config/TransferProperties.java`
3. `transfer/config/TransferConfig.java`
4. `transfer/config/MinioConfig.java`

先回答三个问题：线程池有几个、分片多大、MinIO 和 Redis 连接到哪里。

### 第二步：看入口 Controller

先看：

`transfer/upload/controller/UploadController.java`

再看：

`transfer/download/controller/DownloadController.java`

上传方向的接口：

| 方法 | 路径 | 作用 |
|---|---|---|
| POST | `/api/uploads` | HEAD 远端文件、创建上传任务和 MinIO uploadId |
| POST | `/api/uploads/{taskId}/start` | 启动或恢复执行 |
| POST | `/api/uploads/{taskId}/pause` | 请求暂停 |
| POST | `/api/uploads/{taskId}/resume` | 从暂停/失败状态恢复 |
| POST | `/api/uploads/{taskId}/retry` | 重试暂停或失败任务 |
| POST | `/api/uploads/{taskId}/cancel` | 取消任务并 abort MinIO Multipart |
| GET | `/api/uploads/{taskId}` | 查询任务和全部分片状态 |

下载方向的接口：

| 方法 | 路径 | 作用 |
|---|---|---|
| POST | `/api/downloads/url` | 查询对象信息并生成 15 分钟预签名 URL |
| GET | `/api/downloads?bucket=...&objectName=...` | Java 流式读取 MinIO 对象 |
| GET | `/api/downloads/{bucket}/{objectName}` | 按路径形式读取 MinIO 对象 |

### 第三步：看 Service 接口和实现

上传：

- `transfer/upload/service/UploadService.java`
- `transfer/upload/service/DefaultUploadService.java`

下载：

- `transfer/download/service/DownloadService.java`
- `transfer/download/service/DefaultDownloadService.java`

Service 是整条业务链的核心。阅读 `DefaultUploadService` 时重点追踪：

```text
create -> start -> runTask -> uploadPart -> complete
                   ├── pause / resume
                   ├── retry
                   └── cancel / abort
```

### 第四步：看上传领域模型

目录：

`transfer/upload/domain/`

阅读顺序：

1. `UploadPart.java`
2. `UploadPartStatus.java`
3. `UploadTaskStatus.java`
4. `UploadTask.java`

这一步解决“任务是什么、分片是什么、状态是什么”三个问题。

### 第五步：看远端文件源

目录：

`transfer/source/`

阅读顺序：

1. `RangeFileSource.java`
2. `RemoteFileMetadata.java`
3. `HttpRangeFileSource.java`

这里才是远端文件的读取逻辑：HEAD 取文件元数据，Range GET 取指定区间。它不负责 MinIO，也不负责任务状态。

### 第六步：看状态存储

目录：

`transfer/state/`

阅读顺序：

1. `PartStateStore.java`
2. `RedisPartStateStore.java`

先理解接口，再看 Redis 实现。Service 只知道“获取/设置分片状态”，不知道 Redis 命令细节，这就是状态存储抽象。

### 第七步：看存储适配器

目录：

`transfer/storage/`

阅读顺序：

1. `MultipartStorage.java`
2. `MinioMultipartStorage.java`

这里负责把通用的 `initiate/uploadPart/complete/abort` 映射成 MinIO SDK 调用。Service 不直接依赖具体 MinIO 方法，因此未来可以替换成 S3 或测试内存实现。

### 第八步：最后看内存流

文件：

`transfer/util/DirectBufferInputStream.java`

它解决的是传输过程中的内存使用方式：每个分片通过一个 direct `ByteBuffer` 读取和转发，而不是将 128 MiB 分片完整读进 Java heap。

## 6. 上传链路逐行理解

### 6.1 创建任务：`DefaultUploadService.create`

文件：

`/Users/yunhua/Work/Java/projects/EA-Haraness/agent-service/src/main/java/com/eaharness/transfer/upload/service/DefaultUploadService.java`

执行逻辑：

1. `source.head(...)` 请求远端文件元数据。
2. 从 `contentLength` 和配置中的 `partSizeBytes` 计算 `partCount`。
3. `storage.initiate(bucket, objectName)` 调用 MinIO 创建 Multipart Upload，拿到 `uploadId`。
4. 创建 `UploadTask`，记录源 URL、请求头、目标 bucket/object、文件大小、分片信息和 uploadId。
5. 放进 Java 内存中的 `tasks` Map。
6. 返回 taskId 和任务摘要。

这里的“创建任务”不会下载文件，也不会立即提交所有分片。它只完成元数据确认和 Multipart 会话初始化。

### 6.2 分片计算：`UploadPart`

```java
long start = (long) (partNumber - 1) * task.getPartSize();
long end = Math.min(task.getFileSize() - 1,
        start + task.getPartSize() - 1);
```

分片使用闭区间 `[start, end]`，所以长度是：

```java
end - start + 1
```

例如文件 300 MiB，分片 128 MiB：

| partNumber | start | end | length |
|---:|---:|---:|---:|
| 1 | 0 | 134217727 | 134217728 |
| 2 | 134217728 | 268435455 | 134217728 |
| 3 | 268435456 | 314572799 | 46137344 |

最后一个分片可以小于 128 MiB。128 MiB 是分片上限/理论分片大小，不表示每个分片都必须正好 128 MiB。

### 6.3 启动与协调：`start` 和 `runTask`

`start` 为每个任务创建一个 `UploadRuntime`，使用 `running.compareAndSet(false, true)` 防止同一个任务被重复启动，然后把任务提交给 coordinator 线程池。

`runTask` 使用 `ExecutorCompletionService`：

1. 从 1 到 `partCount` 遍历分片。
2. 已经是 `COMPLETED` 的分片跳过。
3. 将未完成分片提交到 `partExecutor`。
4. 用 `completion.take()` 按完成顺序收集 Future，而不是按分片编号等待。
5. 如果有任务失败，标记整个任务 `FAILED`。
6. 如果暂停标志被设置，标记为 `PAUSED`。
7. 如果全部成功，按 part number 排序并调用 `storage.complete(...)`。

这里的并行数由 `TransferProperties.workerCount` 控制，当前默认是 8，代码没有把 8 直接散落在业务逻辑里。

### 6.4 单个分片：`uploadPart`

单个分片的一次尝试顺序：

1. 检查暂停或取消标志。
2. 将 Redis 状态设为 `DOWNLOADING`。
3. `source.openRange(...)` 对远端发起 `Range: bytes=start-end` 请求。
4. 校验远端返回 HTTP 206；如果远端返回 200，说明没有遵守 Range 请求，当前实现直接失败，避免把完整文件误当作一个分片上传。
5. 将远端 response body 注册到 `activeStreams`。
6. 用 `DirectBufferInputStream` 包装远端流。
7. 调用 `storage.uploadPart(...)` 上传到 MinIO。
8. 保存返回的 ETag 到 `UploadTask.uploadedParts`。
9. 将 Redis 状态设为 `COMPLETED`。
10. 在 finally 中移除 active stream。

失败时将状态设为 `PAUSED_OR_INTERRUPTED`。如果不是暂停/取消且还有重试次数，就等待 `retryBackoffMillis` 后重新请求同一个 Range；超过次数后抛出 `UploadException`。

### 6.5 暂停、恢复、重试、取消

`UploadRuntime` 保存的是运行控制状态，不是持久化任务：

```text
running
pauseRequested
cancelRequested
activeStreams
```

暂停：

1. `task.status = PAUSING`。
2. `pauseRequested = true`。
3. 关闭所有 active remote streams。
4. 分片线程收到 IOException 或下一次检查标志后退出。
5. coordinator 收集完 Future，将任务设为 `PAUSED`。

恢复和重试本质上都重新调用 `start`，但只会跳过 Redis 中已经 `COMPLETED` 的分片。暂停或失败的分片会再次从远端 Range 起点读取。

取消：

1. 设置 `cancelRequested` 和 `pauseRequested`。
2. 关闭 active streams。
3. 任务标记为 `CANCELLED`。
4. 调用 MinIO `abortMultipartUpload` 释放未完成的 Multipart。

### 6.6 完成 Multipart

所有分片成功后，Service 将 `uploadedParts` 中的 `(partNumber, etag)` 转成 MinIO 的 `Part[]`，按分片编号排序，再调用 `completeMultipartUpload`。

MinIO 最终对象的顺序不是线程完成顺序，而是 complete 时传入的 part number 顺序。因此“并发上传”和“最终文件顺序”由两件不同的事情负责：线程负责提高上传速度，part number/complete 负责恢复文件顺序。

## 7. Redis 分片状态：代码中的设计原理

### 7.1 四种状态

文件：

`transfer/upload/domain/UploadPartStatus.java`

```text
UN_DOWNLOADED       0 -> 00
DOWNLOADING         1 -> 01
COMPLETED           2 -> 10
PAUSED_OR_INTERRUPTED 3 -> 11
```

虽然名称仍叫 `UploadPartStatus`，状态语义描述的是“远端分片向 MinIO 传输过程”。`UN_DOWNLOADED` 是历史命名，不代表客户端从 MinIO 下载。

### 7.2 为什么一个分片使用两个 bit

一个 bit 只能表示 0/1，无法表示四种状态。两个 bit 可以表示四种组合：`00`、`01`、`10`、`11`。

当前约定：

- 低位 offset：`code % 2`；
- 高位 offset+1：`code / 2` 的整数部分。

第 N 个分片的起始 bit offset 是：

```java
(long) partNumber * 2L
```

注意这里的 part number 从 1 开始，所以 part 1 占 Redis bit 2 和 3，bit 0 和 1 没有使用。这不会影响功能，但属于当前实现的布局细节。

### 7.3 为什么写状态使用 Lua 脚本

`RedisPartStateStore.set` 通过 `StringRedisTemplate.execute` 执行 Lua，一次完成两个 `SETBIT`：

```lua
local offset = tonumber(ARGV[1])
local code = tonumber(ARGV[2])
local low = code % 2
local high = math.floor(code / 2)
redis.call('SETBIT', KEYS[1], offset, low)
redis.call('SETBIT', KEYS[1], offset + 1, high)
return code
```

如果拆成两个独立 Redis 命令，中间可能被其他请求观察到半更新状态。Lua 让这两个 bit 在 Redis 内以一次脚本执行完成，降低读取到不完整状态的可能性。

`get` 则读取两个 bit，拼成整数，再交给 `UploadPartStatus.fromCode` 转成枚举。未知 code 直接报错，避免把损坏状态默认为正常状态。

## 8. MinIO 工具说明

下面只说明当前代码中 MinIO 工具“负责什么”，不展开 MinIO 内部原理。

### 8.1 `MinioAsyncClient`

文件：

`transfer/config/MinioConfig.java`

这是 MinIO Java SDK 的异步客户端。当前上传模块通过它调用：

- `createMultipartUploadAsync`：创建一个 Multipart 上传会话，返回 `uploadId`。
- `uploadPartAsync`：把一个分片流上传到指定 uploadId 和 part number，返回 ETag。
- `completeMultipartUploadAsync`：提交所有 part number 和 ETag，让 MinIO 生成最终对象。
- `abortMultipartUploadAsync`：取消未完成 Multipart，清理上传会话。

代码虽然使用 Async API，但在每次调用后使用 `.join()` 等待结果。因此当前 Service 的分片线程仍然会等待 MinIO 返回；异步客户端主要提供 SDK 接口和请求执行方式，并没有把整个业务链改成非阻塞 Reactor 流程。

### 8.2 `MinioClient`

同样在 `MinioConfig.java` 中创建，供下载模块使用。它负责：

- `statObject`：查询对象大小、Content-Type、ETag。
- `getObject`：打开对象输入流，可指定 offset 和 length。
- `getPresignedObjectUrl`：生成一个在指定时间内有效的 GET 下载 URL。

### 8.3 `CreateMultipartUploadResponse`

这是 MinIO 创建 Multipart 后的响应对象。当前代码从 `response.result().uploadId()` 取出 uploadId，并写入 `UploadTask`。

### 8.4 `UploadPartResponse`

这是 MinIO 上传单个 part 后的响应对象。当前代码主要取 `response.etag()`。这个 ETag 后续必须和 part number 一起提交给 complete 接口。

### 8.5 `io.minio.messages.Part`

这是 MinIO complete Multipart 时需要的分片描述对象。当前代码把自己的 `MultipartStorage.UploadedPart` 转成 `new Part(partNumber, etag)`，并排序后提交。

### 8.6 `GetObjectArgs`

这是 MinIO 读取对象时的请求参数对象。当前下载代码设置 bucket、object、offset，并在有长度时设置 length。

### 8.7 `StatObjectArgs`

这是查询对象元数据时的参数对象。当前代码使用 bucket 和 object。

### 8.8 `GetPresignedObjectUrlArgs` 与 `Method.GET`

这两个工具一起生成一个 GET 预签名 URL。当前有效期由 `DefaultDownloadService` 固定为 15 分钟，返回给客户端后，客户端可以直接访问 MinIO，不必让 Java 中转全部文件内容。

### 8.9 `ObjectStorageReader` 与 `MultipartStorage`

这两个是项目自己定义的接口，不是 MinIO 类：

- `ObjectStorageReader`：面向“读取对象、查询对象、生成下载 URL”。
- `MultipartStorage`：面向“创建、上传分片、完成、取消 Multipart”。

`MinioObjectStorageReader` 和 `MinioMultipartStorage` 是具体 MinIO 实现。这样做的价值是业务 Service 不被 MinIO SDK 类型绑死，测试时可以替换成 fake 实现，未来也可以接入其他 S3 兼容存储。

## 9. MinIO 下载方向的代码解读

### 9.1 生成预签名 URL

`DefaultDownloadService.createDownloadUrl` 执行：

1. `storage.stat(bucket, objectName)` 查询对象元数据。
2. 从 objectName 最后一个 `/` 后提取文件名。
3. 没有 Content-Type 时使用 `application/octet-stream`。
4. 生成 15 分钟有效的 presigned GET URL。
5. 返回 bucket、objectName、文件名、大小、类型、URL 和过期秒数。

### 9.2 Java 流式下载

`DefaultDownloadService.download` 执行：

1. 先查询对象元数据。
2. 解析可选的 `Range` 请求头。
3. 调用 `storage.get(bucket, objectName, start, length)` 打开 MinIO 输入流。
4. 用 `StreamingResponseBody` 将输入流传给 HTTP 输出流。
5. 设置 Content-Length、Accept-Ranges、Content-Disposition 和 Content-Range。
6. 有 Range 时返回 `206 PARTIAL_CONTENT`，否则返回 `200 OK`。

当前 `DownloadRange` 只支持单个 byte range，不支持逗号分隔的多段 Range，也不支持 suffix range，例如 `bytes=-500`。这是当前代码的明确边界。

## 10. 主要文件清单与职责

### 10.1 配置

| 文件 | 作用 |
|---|---|
| `transfer/config/TransferProperties.java` | 绑定 transfer 配置和默认参数 |
| `transfer/config/TransferConfig.java` | 创建分片 worker 线程池和协调线程池 |
| `transfer/config/MinioConfig.java` | 创建 MinioAsyncClient 与 MinioClient |

### 10.2 远端来源

| 文件 | 作用 |
|---|---|
| `source/RangeFileSource.java` | 抽象 HEAD 和打开远端分片流 |
| `source/HttpRangeFileSource.java` | 用 JDK HttpClient 实现 HTTP/HTTPS HEAD 与 Range GET |
| `source/RemoteFileMetadata.java` | 保存 Content-Length、ETag、Content-Type、Accept-Ranges |

### 10.3 Redis 状态

| 文件 | 作用 |
|---|---|
| `state/PartStateStore.java` | 分片状态存储接口 |
| `state/RedisPartStateStore.java` | 用 Redis String 的 bit 操作保存两个 bit 的状态 |

### 10.4 Multipart 存储

| 文件 | 作用 |
|---|---|
| `storage/MultipartStorage.java` | Multipart 生命周期抽象 |
| `storage/MinioMultipartStorage.java` | MinIO Multipart SDK 适配器 |

### 10.5 上传

| 文件 | 作用 |
|---|---|
| `upload/controller/UploadController.java` | 暴露创建、开始、暂停、恢复、重试、取消、状态接口 |
| `upload/service/UploadService.java` | 上传业务接口 |
| `upload/service/DefaultUploadService.java` | 上传编排、线程调度、重试和任务控制 |
| `upload/domain/UploadPart.java` | 分片编号和字节区间 |
| `upload/domain/UploadPartStatus.java` | 分片四状态及数字编码 |
| `upload/domain/UploadTask.java` | 任务元数据、uploadId、ETag、任务状态 |
| `upload/domain/UploadTaskStatus.java` | 任务级状态 |
| `upload/dto/CreateUploadRequest.java` | 创建任务入参 |
| `upload/dto/UploadResponse.java` | 任务摘要响应 |
| `upload/dto/UploadStatusResponse.java` | 任务摘要加分片列表 |
| `upload/dto/UploadPartStatusResponse.java` | 单个分片区间和状态 |
| `upload/exception/UploadException.java` | 上传领域运行时异常 |

### 10.6 下载

| 文件 | 作用 |
|---|---|
| `download/controller/DownloadController.java` | 暴露预签名 URL 和 Java 流式下载接口 |
| `download/service/DownloadService.java` | 下载业务接口 |
| `download/service/DefaultDownloadService.java` | 元数据、Range、响应头和流式返回编排 |
| `download/storage/ObjectStorageReader.java` | 对象读取抽象 |
| `download/storage/MinioObjectStorageReader.java` | MinIO stat/get/presigned URL 适配器 |
| `download/dto/DownloadRequest.java` | bucket/objectName 入参 |
| `download/dto/DownloadUrlResponse.java` | 预签名 URL 和对象信息响应 |
| `download/exception/DownloadException.java` | 下载领域运行时异常 |

### 10.7 内存流

| 文件 | 作用 |
|---|---|
| `transfer/util/DirectBufferInputStream.java` | 使用 direct ByteBuffer 将远端 InputStream 转成 MinIO 可读取的 InputStream |

## 11. 当前实现的边界和需要注意的问题

### 11.1 任务不持久化

`DefaultUploadService` 使用：

```java
private final Map<String, UploadTask> tasks = new ConcurrentHashMap<>();
private final Map<String, UploadRuntime> runtimes = new ConcurrentHashMap<>();
```

服务重启后，Java 不知道原来的 taskId、uploadId、sourceUrl 和 ETag。Redis 中的 bit 状态不会自动让 Java 恢复任务。生产化需要把任务元数据、源文件校验信息、uploadId、ETag 和任务状态放到数据库或持久化任务存储中。

### 11.2 源文件变化风险

创建任务时读取了远端 ETag 和 Content-Length，但当前 `UploadTask` 没有保存 `RemoteFileMetadata` 的 ETag，也没有在每次 Range 请求时验证远端对象版本。如果远端文件在任务过程中发生变化，分片可能来自不同版本。生产化应保存并校验 ETag、Last-Modified 或供应商版本标识。

### 11.3 Range 支持是硬前提

`HttpRangeFileSource.openRange` 要求远端返回 HTTP 206。如果远端只返回 200，当前实现会失败。这是正确的保护行为，但产品层需要提前探测并向用户解释“远端不支持 Range，无法进行并行分片传输”。

### 11.4 暂停不是即时状态切换

暂停通过关闭流触发线程退出，网络请求和 MinIO SDK 调用的退出时间取决于底层实现。因此接口返回 `PAUSING` 是合理的，最终状态要以查询接口为准。

### 11.5 重试的幂等性

失败分片会重新从同一个 Range 获取数据并再次上传同一个 part number。MinIO Multipart 对同一个 uploadId 和 part number 的后一次上传会替换前一次 part，但必须确保任务仍使用同一个 uploadId，且最终 complete 使用最新 ETag。

### 11.6 MinIO 失败后的清理

创建任务后，如果后续所有分片失败，当前代码会将任务标记为 FAILED，但不会在所有失败路径都自动 abort Multipart。长期运行时可能产生未完成 Multipart，需要补充清理策略和定时扫描。

### 11.7 URL、请求头和安全

当前只禁止 `Host` 和 `Content-Length` 两类用户请求头，并限制源地址为 HTTP/HTTPS。生产化还要考虑 SSRF 防护、内网地址禁止访问、重定向后的地址校验、请求头白名单、源站鉴权信息脱敏和下载权限控制。

### 11.8 线程池资源

`TransferConfig` 为线程池 Bean 设置了 `shutdownNow`，Spring 关闭时会释放线程池。实际部署仍需设置合理的并发上限，否则多个任务同时运行时，8 个 worker 会被所有任务共享，任务之间可能互相影响吞吐。

## 12. 本地阅读和验证 SOP

### 12.1 先启动依赖

当前代码需要：

- Redis：`127.0.0.1:6379`；
- MinIO API：`127.0.0.1:9000`；
- Java：`127.0.0.1:8080`。

Redis 和 MinIO 不属于 Java 代码本身，但 `RedisPartStateStore` 和 MinIO Client Bean 启动时会参与 Spring 上下文创建，配置错误可能导致调用失败。

### 12.2 编译 Java

```bash
cd /Users/yunhua/Work/Java/projects/EA-Haraness/agent-service
mvn clean compile
mvn spring-boot:run
```

如果 `mvn` 不在 PATH，使用 IntelliJ 自带 Maven 的绝对路径。当前 transfer 代码的编译重点是 Redis、MinIO 和 Spring Boot 依赖已经进入 classpath。

### 12.3 创建远端文件传输任务

```bash
curl -X POST http://127.0.0.1:8080/api/uploads \
  -H 'Content-Type: application/json' \
  -d '{
    "sourceUrl": "https://example.com/large-file.bin",
    "bucket": "ea-files",
    "objectName": "large-file.bin",
    "headers": {}
  }'
```

返回 `taskId` 后启动：

```bash
curl -X POST http://127.0.0.1:8080/api/uploads/{taskId}/start
```

查询：

```bash
curl http://127.0.0.1:8080/api/uploads/{taskId}
```

暂停、恢复、重试和取消：

```bash
curl -X POST http://127.0.0.1:8080/api/uploads/{taskId}/pause
curl -X POST http://127.0.0.1:8080/api/uploads/{taskId}/resume
curl -X POST http://127.0.0.1:8080/api/uploads/{taskId}/retry
curl -X POST http://127.0.0.1:8080/api/uploads/{taskId}/cancel
```

### 12.4 读取 MinIO 对象

先获取预签名 URL：

```bash
curl -X POST http://127.0.0.1:8080/api/downloads/url \
  -H 'Content-Type: application/json' \
  -d '{"bucket":"ea-files","objectName":"large-file.bin"}'
```

或者由 Java 流式返回：

```bash
curl -L -o large-file.bin \
  'http://127.0.0.1:8080/api/downloads?bucket=ea-files&objectName=large-file.bin'
```

读取指定范围：

```bash
curl -H 'Range: bytes=0-1023' \
  -o first-1k.bin \
  'http://127.0.0.1:8080/api/downloads?bucket=ea-files&objectName=large-file.bin'
```

## 13. 一句话记忆整个模块

```text
UploadController 接收任务
  -> DefaultUploadService 编排任务
  -> HttpRangeFileSource 从远端取指定区间
  -> DirectBufferInputStream 控制内存读取
  -> MinioMultipartStorage 上传 part
  -> RedisPartStateStore 记录四态进度
  -> complete Multipart 生成最终对象

DownloadController 接收下载请求
  -> DefaultDownloadService 解析 Range/生成 URL
  -> MinioObjectStorageReader 读取或签名
  -> StreamingResponseBody 返回客户端
```

阅读时最重要的区分是：`upload` 是“把远端文件传入 MinIO”，`download` 是“把 MinIO 文件传给客户端”，两者共同属于新的 `transfer` 模块，但共享的配置、MinIO Client 和命名空间不代表它们是同一条业务链。
