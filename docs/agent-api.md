# 专家 API 调用

专家 API 允许 CRM、门户、客服系统等外部系统调用指定数字专家。

## 控制台入口

登录后进入：

```text
专家 → 编辑 → API 调用 → 打开 API 控制台
```

也可以从专家卡片的 `API` 操作进入。

## 创建 API Key

在 API 控制台的「凭证」页面创建 Key。

Key 明文只显示一次。

请为每个外部系统单独创建一把 Key。

请求头支持：

```http
Authorization: Bearer mak_xxx
```

或：

```http
X-API-Key: mak_xxx
```

Key 只绑定一位专家。

撤销某把 Key 不影响其他 Key。

## REST 同步调用

```bash
curl -X POST "https://mateclaw.example.com/api/v1/open/agent/chat" \
  -H "Authorization: Bearer mak_xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "crm-user-1001-session-1",
    "endUserId": "crm-user-1001",
    "message": "请总结本周随访记录"
  }'
```

响应示例：

```json
{
  "requestId": "req_xxx",
  "conversationId": "crm-user-1001-session-1",
  "endUserId": "crm-user-1001",
  "content": "本周共有 3 次随访……",
  "status": "completed",
  "usage": {
    "promptTokens": 1200,
    "completionTokens": 180,
    "totalTokens": 1380
  }
}
```

## REST SSE 流式调用

```bash
curl -N -X POST "https://mateclaw.example.com/api/v1/open/agent/stream" \
  -H "Authorization: Bearer mak_xxx" \
  -H "Accept: text/event-stream" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "crm-user-1001-session-1",
    "endUserId": "crm-user-1001",
    "message": "请总结本周随访记录"
  }'
```

事件包括：

- `message`：回答内容增量
- `thinking`：思考增量
- `event`：工具或运行时事件
- `done`：本轮完成
- `error`：本轮失败

## OpenAI 兼容调用

`model` 固定为 `expert`。

API Key 已经决定具体专家。

```bash
curl -X POST "https://mateclaw.example.com/v1/chat/completions" \
  -H "Authorization: Bearer mak_xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "expert",
    "stream": false,
    "user": "crm-user-1001",
    "conversation_id": "crm-user-1001-session-1",
    "messages": [
      {"role": "user", "content": "请总结本周随访记录"}
    ]
  }'
```

也可使用 OpenAI SDK，将 `base_url` 指向 MateClaw 服务地址。

```python
from openai import OpenAI

client = OpenAI(
    api_key="mak_xxx",
    base_url="https://mateclaw.example.com/v1",
)

response = client.chat.completions.create(
    model="expert",
    user="crm-user-1001",
    extra_body={"conversation_id": "crm-user-1001-session-1"},
    messages=[{"role": "user", "content": "请总结本周随访记录"}],
)
print(response.choices[0].message.content)
```

## 身份和记忆隔离

`endUserId` 必填。

`conversationId` 必填。

同一把 Key 可以代理多个外部用户。

不同 `endUserId` 的记忆不会混用。

服务端使用以下组合隔离会话：

```text
agentId + endUserId + conversationId
```

## 异步任务

提交任务：

```bash
curl -X POST "https://mateclaw.example.com/api/v1/open/agent/tasks" \
  -H "Authorization: Bearer mak_xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "crm-user-1001-session-1",
    "endUserId": "crm-user-1001",
    "message": "生成本月随访分析报告",
    "callbackUrl": "https://crm.example.com/hooks/mateclaw"
  }'
```

查询任务：

```bash
curl "https://mateclaw.example.com/api/v1/open/agent/tasks/task_xxx" \
  -H "Authorization: Bearer mak_xxx"
```

取消任务：

```bash
curl -X POST "https://mateclaw.example.com/api/v1/open/agent/tasks/task_xxx/cancel" \
  -H "Authorization: Bearer mak_xxx"
```

回调示例：

```json
{
  "taskId": "task_xxx",
  "status": "completed",
  "result": "报告内容……",
  "error": null
}
```

回调失败时仍可轮询任务。

## 多模态输入

REST 可以使用 `contentParts`。

```json
{
  "conversationId": "crm-user-1001-session-1",
  "endUserId": "crm-user-1001",
  "message": "请分析这张检查单",
  "contentParts": [
    {
      "type": "image",
      "fileUrl": "https://cdn.example.com/report.png",
      "fileName": "report.png",
      "contentType": "image/png"
    }
  ]
}
```

OpenAI 兼容接口支持 `text` 和 `image_url` 内容片段。

## 限流和错误

专家设置提供默认限流。

Key 可以覆盖专家默认值。

超限返回 HTTP `429`：

```json
{
  "error": {
    "code": "agent_api_rate_limited",
    "message": "requests per minute limit reached",
    "details": {
      "retryAfterSeconds": 60
    }
  }
}
```

常见错误码：

- `agent_api_invalid_request`：请求字段不完整
- `agent_api_unauthorized`：Key 无效或已撤销
- `agent_api_not_published`：专家未发布或已禁用
- `agent_api_rate_limited`：超过限流或配额
- `agent_api_upstream_error`：专家运行时失败

调用日志只保存元数据。

不会保存完整 prompt 和答案正文。
