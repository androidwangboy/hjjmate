# 专家 API 调用管理设计

## 背景

当前项目已经具备专家运行时、同步对话、SSE 对话、PAT/JWT 鉴权和 A2A 接口。
但外部系统还不能在专家维度完成发布、凭证、调用治理和调用观测。

本功能为专家增加 API 发布能力，并在专家编辑界面提供轻量摘要。
复杂运营能力进入独立 API 控制台。

## 目标

1. 外部系统可以调用指定已发布专家。
2. 每个专家可以管理多把专家级 API Key。
3. 同时提供 MateClaw REST 和 OpenAI 兼容协议。
4. 支持同步、SSE 流式、异步任务和多轮会话。
5. 外部用户通过 `endUserId` 隔离会话和长期记忆。
6. 专家页能看到发布状态和核心调用入口。
7. API 控制台支持概览、凭证、文档、日志、统计和设置。
8. 支持专家级默认治理和 Key 级覆盖。
9. 异步任务支持轮询，并可选 Webhook 回调。

## 非目标

1. 不替换现有 Web UI 对话接口。
2. 不把 API Key 变成用户 PAT 的别名。
3. 不允许外部 Key 切换到其他专家。
4. 不在首期实现独立微服务网关。
5. 不重构现有 AgentService 和会话存储。
6. 不为 API 调用新增一套工具权限模型。

## 已确认的产品决策

- 专家编辑页采用“API 摘要 + API 控制台”入口。
- 控制台采用左侧导航型结构。
- 概览页包含“快速开始”卡片。
- 在线测试作为独立入口。
- Key 归属于专家，一个专家支持多把 Key。
- Key 支持名称和撤销，明文只显示一次。
- API Key 决定专家，公开请求不需要在 URL 传专家 ID。
- 支持 REST 和 OpenAI 兼容协议。
- OpenAI `model` 固定为专家别名 `expert`。
- `endUserId` 和 `conversationId` 必填。
- REST 和 OpenAI 都支持同步响应。
- 支持 SSE、异步任务、文件/多模态输入。
- 异步接口返回 taskId，支持轮询和可选 callbackUrl。
- 工具权限沿用专家现有配置。
- 专家提供限流默认值，Key 可覆盖。

## 整体架构

新增一个 API 发布层，位于 HTTP 控制器和现有 AgentService 之间。

```text
外部系统
  │
  ├─ MateClaw REST / OpenAI 兼容
  │
  ▼
Agent API Controller
  │  API Key 认证、专家绑定、workspace 校验
  ▼
Agent API Access Service
  │  发布状态、限流、并发、日志、任务、Webhook
  ▼
Agent API Execution Service
  │  把统一请求转换为现有 ChatOrigin / AgentService 调用
  ▼
AgentService / ConversationService / 工具运行时
```

API Key 不复用 PAT 表。
Key 明文只返回一次，数据库只保存 SHA-256 哈希。
认证成功后将 `agentId` 放入请求上下文，控制器不接受调用方切换专家。

## 数据模型

### `mate_agent_api_publication`

专家的 API 发布配置。

- `id`
- `agent_id`，唯一
- `workspace_id`
- `enabled`
- `model_alias`，默认 `expert`
- `requests_per_minute`
- `concurrent_limit`
- `daily_quota`
- `timeout_seconds`
- `webhook_enabled`
- `created_at`
- `updated_at`
- `deleted`

### `mate_agent_api_key`

专家级调用凭证。

- `id`
- `publication_id`
- `agent_id`
- `workspace_id`
- `name`
- `key_prefix`
- `token_hash`
- `enabled`
- `requests_per_minute_override`
- `concurrent_limit_override`
- `daily_quota_override`
- `created_at`
- `last_used_at`
- `revoked_at`
- `deleted`

Key 格式为 `mak_` 加高熵随机串。
接口列表只返回前缀、名称、状态和使用时间。

### `mate_agent_api_request_log`

记录调用元数据，不保存完整 prompt 和响应正文。

- `id`
- `request_id`
- `task_id`
- `publication_id`
- `api_key_id`
- `agent_id`
- `workspace_id`
- `protocol`：`rest` / `openai`
- `mode`：`sync` / `stream` / `async`
- `conversation_id`
- `end_user_id`
- `status`
- `http_status`
- `error_code`
- `latency_ms`
- `input_tokens`
- `output_tokens`
- `created_at`

### `mate_agent_api_task`

持久化异步任务，避免仅依赖内存状态。

- `id`
- `task_id`，公开唯一
- `request_id`
- `publication_id`
- `api_key_id`
- `agent_id`
- `workspace_id`
- `conversation_id`
- `end_user_id`
- `status`：`queued` / `running` / `completed` / `failed` / `canceled`
- `result_text`
- `error_message`
- `callback_url`
- `callback_status`
- `created_at`
- `started_at`
- `completed_at`
- `expires_at`

## 对外接口

### REST

认证头：

```http
Authorization: Bearer mak_xxx
```

统一入口不携带专家 ID：

```http
POST /api/v1/open/agent/chat
POST /api/v1/open/agent/stream
POST /api/v1/open/agent/tasks
GET  /api/v1/open/agent/tasks/{taskId}
POST /api/v1/open/agent/tasks/{taskId}/cancel
```

请求最低字段：

```json
{
  "conversationId": "crm-user-1001-session-1",
  "endUserId": "crm-user-1001",
  "message": "请总结本周随访记录"
}
```

流式请求允许 `contentParts`，并沿用现有消息内容片段结构。
异步请求额外接受 `callbackUrl`。

### OpenAI 兼容

```http
POST /v1/chat/completions
```

请求：

```json
{
  "model": "expert",
  "stream": false,
  "user": "crm-user-1001",
  "conversation_id": "crm-user-1001-session-1",
  "messages": [
    {"role": "user", "content": "请总结本周随访记录"}
  ]
}
```

`model` 只能是 `expert`。
`user` 映射为 `endUserId`，`conversation_id` 必填。
多模态内容映射为现有 `MessageContentPart`。

同步响应遵循 OpenAI `choices`、`usage`、`id` 结构。
流式响应使用 `text/event-stream` 和 `data: [DONE]`。

## 管理接口

这些接口需要登录并通过工作区权限校验。

```text
GET    /api/v1/agents/{id}/api
PUT    /api/v1/agents/{id}/api
GET    /api/v1/agents/{id}/api/keys
POST   /api/v1/agents/{id}/api/keys
DELETE /api/v1/agents/{id}/api/keys/{keyId}
GET    /api/v1/agents/{id}/api/logs
GET    /api/v1/agents/{id}/api/stats
POST   /api/v1/agents/{id}/api/test
```

创建 Key 的响应包含一次性 `plaintext`。
测试接口使用当前登录用户身份，不消耗外部 Key。

## 调用流程

### 同步

1. API Key 过滤器解析 Bearer。
2. 根据哈希查找启用 Key。
3. 校验专家已发布且启用。
4. 应用专家默认治理和 Key 覆盖值。
5. 校验 `conversationId`、`endUserId`。
6. 构建 `ChatOrigin`，owner 使用 `api:<endUserId>`。
7. 调用现有 AgentService。
8. 记录日志并返回统一响应。

### 流式

认证、治理和身份步骤与同步一致。
请求进入现有结构化 SSE 流。
每个请求只记录最终状态和累计指标。
客户端断开时释放并发占用。

### 异步

1. 创建持久化任务并返回 `taskId`。
2. 后台执行专家请求。
3. 更新任务状态和结果。
4. 写入调用日志。
5. 如果存在 `callbackUrl`，发送一次完成通知。
6. 回调失败不影响任务完成状态，调用方可继续轮询。

首期 Webhook 采用可选回调，不引入复杂的重试调度。
回调请求包含 `taskId`、`status`、`result` 或 `error`。

## 限流与配额

专家配置提供默认值。
Key 的非空覆盖值优先。

治理顺序：

1. 发布开关
2. Key 状态和过期/撤销状态
3. 每日额度
4. 每分钟请求数
5. 并发数
6. 单请求超时

超限统一返回：

- HTTP `429`
- 错误码 `agent_api_rate_limited`
- 响应包含 `retryAfterSeconds`

首期使用应用内限流器。
限流器按 `publicationId` 和 `apiKeyId` 维度统计。

## 安全边界

- 公开调用只接受专家 API Key。
- API Key 不可访问管理接口。
- Key 永不返回完整明文。
- API 日志不保存 prompt 和答案正文。
- `endUserId` 参与会话和记忆 owner 计算。
- `conversationId + endUserId + agentId` 作为会话边界。
- 专家禁用或取消发布后立即拒绝新请求。
- 外部调用继承专家现有工具权限。
- callbackUrl 首期限制为 HTTPS 或本地开发 HTTP，并记录投递结果。

## 前端设计

### 专家编辑页

在现有 Basic、Skills、Tools、Providers、Wiki 后增加 API 摘要区域。
不把凭证和日志完整嵌入编辑弹窗。

摘要包含：

- 是否已发布
- REST 与 OpenAI 入口
- Key 数量
- 今日调用量
- 成功率
- “打开 API 控制台”
- “查看文档”
- “复制快速开始示例”

### API 控制台

新增专家 API 控制台路由。
采用左侧导航：

- 概览
- 凭证
- 文档
- 调用日志
- 统计
- 设置

概览页显示快速开始卡片。
在线测试从文档页或独立按钮进入。

在线测试：

- 使用当前登录用户身份
- 不展示真实外部 Key
- 默认使用同步请求
- 明确提示可能触发专家已有工具
- 不保存测试文本到外部调用日志

## 测试策略

### 后端单元测试

- Key 生成、哈希、一次性明文返回
- Key 撤销和跨专家访问拒绝
- 发布配置默认值和 Key 覆盖
- `endUserId` / `conversationId` 校验
- OpenAI 请求转换
- 限流和并发释放
- 异步任务状态转换
- Webhook 请求体生成

### 后端集成测试

- REST 同步调用
- REST SSE 调用
- OpenAI 同步和流式调用
- 未发布专家拒绝
- 无效 Key 返回 401
- 其他专家 Key 不可调用
- 任务轮询和取消
- 管理接口工作区隔离

### 前端测试

- API 摘要状态渲染
- Key 创建明文一次性弹窗
- Key 撤销确认
- 文档示例复制
- 控制台导航和分页
- 在线测试错误提示

## 分阶段交付

### Phase 1：发布、Key 和同步调用

完成数据表、管理接口、专家 Key、REST 同步、OpenAI 同步、文档摘要和基础控制台。

### Phase 2：流式、多模态和在线测试

接入现有结构化 SSE，支持内容片段转换和控制台测试。

### Phase 3：异步任务、日志和统计

增加持久化任务、轮询、任务取消、调用日志和统计卡片。

### Phase 4：限流覆盖和可选 Webhook

增加专家默认治理、Key 覆盖和 callbackUrl 投递。
