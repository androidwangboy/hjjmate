# 专家 API 调用管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为专家提供可发布、可鉴权、可观测的 REST 与 OpenAI 兼容 API，并在专家管理页提供摘要和独立 API 控制台。

**Architecture:** 新增 `vip.mate.agent.api` 发布层，不修改现有 Web 对话契约。专家 API Key 单独存储并映射到唯一专家；公开控制器先完成 Key、发布状态、工作区和限流校验，再通过 `ChatOrigin` 调用现有 `AgentService`。管理页面在专家编辑摘要中提供入口，复杂能力由 `/agents/:id/api` 控制台承载。

**Tech Stack:** Spring Boot、Spring MVC、MyBatis-Plus、Flyway SQL、Vue 3、TypeScript、Element Plus、Vitest、JUnit 5。

---

## 文件结构

### 后端新增

- `mateclaw-server/src/main/java/vip/mate/agent/api/model/AgentApiPublicationEntity.java`：专家 API 发布配置实体。
- `mateclaw-server/src/main/java/vip/mate/agent/api/model/AgentApiKeyEntity.java`：专家级 API Key 实体。
- `mateclaw-server/src/main/java/vip/mate/agent/api/model/AgentApiRequestLogEntity.java`：调用元数据实体。
- `mateclaw-server/src/main/java/vip/mate/agent/api/model/AgentApiTaskEntity.java`：异步任务实体。
- `mateclaw-server/src/main/java/vip/mate/agent/api/repository/*Mapper.java`：四个 MyBatis-Plus mapper。
- `mateclaw-server/src/main/java/vip/mate/agent/api/dto/AgentApiDtos.java`：管理、公开调用和 OpenAI DTO。
- `mateclaw-server/src/main/java/vip/mate/agent/api/service/AgentApiKeyService.java`：Key 生成、哈希、认证、撤销和使用时间。
- `mateclaw-server/src/main/java/vip/mate/agent/api/service/AgentApiPublicationService.java`：发布配置、工作区校验和公开快照。
- `mateclaw-server/src/main/java/vip/mate/agent/api/service/AgentApiRateLimiter.java`：专家默认值、Key 覆盖、窗口计数和并发许可。
- `mateclaw-server/src/main/java/vip/mate/agent/api/service/AgentApiExecutionService.java`：统一同步、流式、异步执行适配。
- `mateclaw-server/src/main/java/vip/mate/agent/api/service/AgentApiTaskService.java`：任务持久化、后台执行、查询和取消。
- `mateclaw-server/src/main/java/vip/mate/agent/api/service/AgentApiLogService.java`：调用日志和统计聚合。
- `mateclaw-server/src/main/java/vip/mate/agent/api/controller/AgentApiManagementController.java`：登录用户管理接口。
- `mateclaw-server/src/main/java/vip/mate/agent/api/controller/AgentApiPublicController.java`：REST 公开接口。
- `mateclaw-server/src/main/java/vip/mate/agent/api/controller/OpenAiCompatibleController.java`：OpenAI 兼容接口。
- `mateclaw-server/src/main/java/vip/mate/agent/api/AgentApiExceptionHandler.java`：公开接口错误信封和 HTTP 状态映射。
- `mateclaw-server/src/main/resources/db/migration/h2/V192__agent_api_management.sql`：H2 schema。
- `mateclaw-server/src/main/resources/db/migration/mysql/V192__agent_api_management.sql`：MySQL schema。
- `mateclaw-server/src/main/resources/db/migration/kingbase/V192__agent_api_management.sql`：Kingbase/PostgreSQL schema。

### 后端修改

- `mateclaw-server/src/main/java/vip/mate/config/SecurityConfig.java`：放行公开 API 路径，保留控制器自定义 Key 鉴权。
- `mateclaw-server/src/main/java/vip/mate/agent/controller/AgentController.java`：删除专家时清理发布配置，或由服务监听清理。
- `mateclaw-server/src/main/java/vip/mate/agent/api/...`：只依赖已有 `AgentService`、`ConversationService` 和 `WorkspaceService`。

### 前端新增

- `mateclaw-ui/src/views/AgentApiConsole.vue`：API 控制台外壳和左侧导航。
- `mateclaw-ui/src/views/AgentApiConsole/components/ApiOverview.vue`：概览与快速开始。
- `mateclaw-ui/src/views/AgentApiConsole/components/ApiKeysPanel.vue`：Key 列表、创建和撤销。
- `mateclaw-ui/src/views/AgentApiConsole/components/ApiDocsPanel.vue`：REST/OpenAI 文档和示例复制。
- `mateclaw-ui/src/views/AgentApiConsole/components/ApiLogsPanel.vue`：调用日志列表。
- `mateclaw-ui/src/views/AgentApiConsole/components/ApiStatsPanel.vue`：调用统计。
- `mateclaw-ui/src/views/AgentApiConsole/components/ApiSettingsPanel.vue`：发布、限流、Webhook 配置。
- `mateclaw-ui/src/views/AgentApiConsole/components/ApiTestPanel.vue`：独立在线测试。
- `mateclaw-ui/src/views/AgentApiConsole/__tests__/apiConsoleLogic.test.ts`：控制台纯逻辑测试。

### 前端修改

- `mateclaw-ui/src/api/index.ts`：增加 `agentApiManagement` API 客户端。
- `mateclaw-ui/src/types/index.ts`：增加 API 发布、Key、日志、统计和任务类型。
- `mateclaw-ui/src/router/index.ts`：增加 `/agents/:id/api` 路由。
- `mateclaw-ui/src/views/Agents.vue`：增加 API 摘要入口和 API 标签页摘要。
- `mateclaw-ui/src/i18n/locales/zh-CN.ts`：增加中文文案。
- `mateclaw-ui/src/i18n/locales/en-US.ts`：增加英文文案，保持多语言键一致。

---

## Task 1: 固化数据库结构和实体

**Files:**
- Create: 三个 `V192__agent_api_management.sql` 文件。
- Create: 四个 `*Entity.java` 文件。
- Create: 四个 `*Mapper.java` 文件。
- Test: `mateclaw-server/src/test/java/vip/mate/agent/api/model/AgentApiEntityMappingTest.java`。

- [ ] **Step 1: 写实体映射失败测试**

测试使用 `TableInfoHelper` 验证四个实体的表名、Key 哈希字段和专家唯一约束对应字段存在。

```java
@Test
void publicationMapsToAgentApiPublicationTable() {
    TableInfo info = TableInfoHelper.getTableInfo(AgentApiPublicationEntity.class);
    assertEquals("mate_agent_api_publication", info.getTableName());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd mateclaw-server && mvn -Dtest=AgentApiEntityMappingTest test
```

Expected: FAIL because the new entities do not exist。

- [ ] **Step 3: 创建 V192 schema**

每个数据库创建四张表及以下索引：

```sql
CREATE TABLE IF NOT EXISTS mate_agent_api_publication (...);
CREATE TABLE IF NOT EXISTS mate_agent_api_key (...);
CREATE TABLE IF NOT EXISTS mate_agent_api_request_log (...);
CREATE TABLE IF NOT EXISTS mate_agent_api_task (...);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_publication_agent ON mate_agent_api_publication(agent_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_key_hash ON mate_agent_api_key(token_hash);
CREATE INDEX IF NOT EXISTS idx_agent_api_key_agent ON mate_agent_api_key(agent_id, deleted);
CREATE INDEX IF NOT EXISTS idx_agent_api_log_agent_time ON mate_agent_api_request_log(agent_id, create_time);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_task_task_id ON mate_agent_api_task(task_id);
```

字段使用项目约定：Snowflake `BIGINT` 主键、`create_time`、`update_time`、`deleted`；Key 哈希使用 `CHAR(64)`；日志和任务的可变文本使用 `TEXT` 或 MySQL `LONGTEXT`。

- [ ] **Step 4: 创建实体和 mapper**

实体必须使用 Lombok `@Data`、MyBatis-Plus `@TableName` 和 `@TableId(type = IdType.ASSIGN_ID)`。Key 的 `tokenHash` 使用 `@JsonIgnore`。敏感字段不进入管理接口返回 DTO。

- [ ] **Step 5: 运行实体测试**

Run:

```bash
cd mateclaw-server && mvn -Dtest=AgentApiEntityMappingTest test
```

Expected: PASS。

- [ ] **Step 6: 运行 Flyway/H2 启动检查**

Run:

```bash
cd mateclaw-server && mvn -DskipTests package
```

Expected: PASS and migration V192 is applied during the H2 test context。

---

## Task 2: 实现专家级 Key 和发布配置服务

**Files:**
- Create: `AgentApiDtos.java`。
- Create: `AgentApiKeyService.java`。
- Create: `AgentApiPublicationService.java`。
- Test: `AgentApiKeyServiceTest.java`。
- Test: `AgentApiPublicationServiceTest.java`。

- [ ] **Step 1: 写 Key 生命周期失败测试**

覆盖以下行为：

```java
@Test
void createReturnsPlaintextOnlyOnceAndStoresHash() { ... }

@Test
void authenticateReturnsBoundAgentAndRejectsRevokedKey() { ... }

@Test
void revokeRejectsKeyOwnedByAnotherAgent() { ... }
```

断言 Key 以 `mak_` 开头；明文不等于 `tokenHash`；列表 DTO 不包含明文或哈希。

- [ ] **Step 2: 运行测试确认失败**

```bash
cd mateclaw-server && mvn -Dtest=AgentApiKeyServiceTest test
```

Expected: FAIL because service and DTOs do not exist。

- [ ] **Step 3: 实现 Key 服务**

生成 32 字节随机值，使用 URL-safe Base64 无 padding，拼接 `mak_`；用 SHA-256 哈希查找。认证结果至少包含 `agentId`、`publicationId`、`keyId`、`workspaceId` 和 Key 配置覆盖值。

支持两种请求头：

```text
Authorization: Bearer mak_xxx
X-API-Key: mak_xxx
```

Bearer 优先。Key 撤销使用软删除并写入 `revokedAt`。

- [ ] **Step 4: 实现发布配置服务**

`getOrCreate(agentId, workspaceId)` 使用专家所属工作区；默认值如下：

```text
enabled = false
modelAlias = "expert"
requestsPerMinute = 60
concurrentLimit = 4
dailyQuota = 10000
timeoutSeconds = 120
webhookEnabled = false
```

更新时拒绝跨工作区、负数和零值治理参数；发布时校验专家存在且启用。

- [ ] **Step 5: 运行服务测试**

```bash
cd mateclaw-server && mvn -Dtest=AgentApiKeyServiceTest,AgentApiPublicationServiceTest test
```

Expected: PASS。

---

## Task 3: 增加管理 REST 接口

**Files:**
- Create: `AgentApiManagementController.java`。
- Modify: `SecurityConfig.java` only if controller path currently被公开规则遗漏。
- Test: `AgentApiManagementControllerTest.java`。

- [ ] **Step 1: 写 MockMvc 失败测试**

覆盖：

```java
GET    /api/v1/agents/{id}/api
PUT    /api/v1/agents/{id}/api
GET    /api/v1/agents/{id}/api/keys
POST   /api/v1/agents/{id}/api/keys
DELETE /api/v1/agents/{id}/api/keys/{keyId}
GET    /api/v1/agents/{id}/api/logs
GET    /api/v1/agents/{id}/api/stats
POST   /api/v1/agents/{id}/api/test
```

测试必须验证 workspace 不匹配返回 403；创建 Key 返回 `plaintext`；再次查询不返回 `plaintext`。

- [ ] **Step 2: 运行测试确认失败**

```bash
cd mateclaw-server && mvn -Dtest=AgentApiManagementControllerTest test
```

Expected: FAIL because controller endpoints do not exist。

- [ ] **Step 3: 实现 DTO 和 controller**

管理接口沿用项目 `R<T>` 信封和 `@RequireWorkspaceRole("member")` / `viewer`。管理响应禁止直接返回 Entity，使用 `PublicationView`、`ApiKeyView` 和 `CreatedApiKeyView`。

- [ ] **Step 4: 实现测试调用入口**

`POST /api/test` 使用登录用户、当前专家和 `ChatOrigin.web(...)`，调用同步执行服务；该调用不写外部 Key 日志，不扣外部 Key 配额。

- [ ] **Step 5: 运行 controller 测试**

```bash
cd mateclaw-server && mvn -Dtest=AgentApiManagementControllerTest test
```

Expected: PASS。

---

## Task 4: 实现 REST 同步和 OpenAI 同步调用

**Files:**
- Create: `AgentApiExecutionService.java`。
- Create: `AgentApiPublicController.java`。
- Create: `OpenAiCompatibleController.java`。
- Create: `AgentApiExceptionHandler.java`。
- Modify: `SecurityConfig.java` to permit `/api/v1/open/agent/**` and `/v1/chat/completions`.
- Test: `AgentApiPublicControllerTest.java`。
- Test: `OpenAiCompatibleControllerTest.java`。

- [ ] **Step 1: 写请求转换失败测试**

测试以下请求必须返回 400：缺少 Key、缺少 `conversationId`、缺少 `endUserId`、空消息、OpenAI `model` 非 `expert`。

```java
@Test
void openAiModelMustBeExpertAlias() { ... }

@Test
void externalIdentityFieldsAreRequired() { ... }
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd mateclaw-server && mvn -Dtest=AgentApiPublicControllerTest,OpenAiCompatibleControllerTest test
```

Expected: FAIL because public controllers do not exist。

- [ ] **Step 3: 实现公开认证上下文**

`AgentApiExecutionService.authenticate(request)`：

1. 提取 Bearer 或 `X-API-Key`。
2. 哈希查 Key。
3. 校验 Key enabled、publication enabled、Agent enabled。
4. 校验 Key 绑定的 workspace 和 Agent workspace。
5. 返回不可变 `AgentApiPrincipal`。

认证失败返回 HTTP 401，响应：

```json
{"error":{"code":"agent_api_unauthorized","message":"Invalid API key"}}
```

- [ ] **Step 4: 实现统一同步执行**

构造：

```java
ChatOrigin origin = ChatOrigin.web(
    request.conversationId(),
    request.endUserId(),
    principal.workspaceId(),
    null
).withAgent(principal.agentId())
 .withSender(null, "api", null);
```

调用 `AgentService.chatWithUsage(agentId, text, conversationId, origin)`。将结果转换为 REST 响应和 OpenAI `choices` 响应。

- [ ] **Step 5: 实现公开异常处理**

映射：

```text
invalid request -> 400 / agent_api_invalid_request
invalid key -> 401 / agent_api_unauthorized
unpublished or disabled -> 403 / agent_api_not_published
rate limited -> 429 / agent_api_rate_limited
runtime failure -> 502 / agent_api_upstream_error
```

- [ ] **Step 6: 运行同步接口测试**

```bash
cd mateclaw-server && mvn -Dtest=AgentApiPublicControllerTest,OpenAiCompatibleControllerTest test
```

Expected: PASS。

---

## Task 5: 接入流式、多模态和在线测试

**Files:**
- Modify: `AgentApiExecutionService.java`。
- Modify: 两个公开 controller。
- Modify: `AgentApiManagementController.java`。
- Test: `AgentApiStreamingControllerTest.java`。

- [ ] **Step 1: 写 SSE 失败测试**

Mock `AgentService.chatStructuredStream` 返回内容 delta、工具事件和 `_usage_final`，断言 REST SSE 至少输出 `message`、`usage` 和 `done` 事件；断开连接后释放并发许可。

- [ ] **Step 2: 实现 REST SSE**

增加：

```text
POST /api/v1/open/agent/stream
```

使用 `Utf8SseEmitter`，把 `StreamDelta.content` 转为 `delta`，把结构化事件转为 `event`，最终发送 `done`。

- [ ] **Step 3: 实现 OpenAI streaming**

`POST /v1/chat/completions` 接收 `stream=true`，每段输出：

```text
data: {"id":"...","object":"chat.completion.chunk","choices":[...]}

data: [DONE]
```

- [ ] **Step 4: 复用 MessageContentPart`**

OpenAI `messages[].content` 支持字符串或内容数组；图片和文件内容转换为现有 `MessageContentPart`，禁止把未知 part 类型静默当作文本。

- [ ] **Step 5: 实现在线测试接口和页面调用**

管理端 `POST /api/v1/agents/{id}/api/test` 使用同步执行；页面显示 assistant 文本、耗时、错误和 token 摘要。

- [ ] **Step 6: 运行流式和多模态测试**

```bash
cd mateclaw-server && mvn -Dtest=AgentApiStreamingControllerTest test
```

Expected: PASS。

---

## Task 6: 实现异步任务和可选 Webhook

**Files:**
- Create: `AgentApiTaskService.java`。
- Modify: `AgentApiExecutionService.java`。
- Modify: `AgentApiPublicController.java`。
- Modify: `AgentApiTaskEntity.java` and mapper。
- Test: `AgentApiTaskServiceTest.java`。
- Test: `AgentApiTaskControllerTest.java`。

- [ ] **Step 1: 写任务状态失败测试**

覆盖 queued → running → completed、queued → canceled、运行异常 → failed；重复取消终态任务保持原状态。

- [ ] **Step 2: 实现异步端点**

增加：

```text
POST /api/v1/open/agent/tasks
GET  /api/v1/open/agent/tasks/{taskId}
POST /api/v1/open/agent/tasks/{taskId}/cancel
```

创建任务时保存 `agentId`、`apiKeyId`、`conversationId`、`endUserId` 和 callbackUrl。响应只返回 taskId、状态和创建时间。

- [ ] **Step 3: 实现后台执行**

使用项目虚拟线程模式提交任务；后台执行复用同步 execution service，完成后写 `resultText` 或 `errorMessage`。每个任务检查终态，避免取消后覆盖为 completed。

- [ ] **Step 4: 实现可选 callbackUrl**

任务终态后发送 JSON：

```json
{
  "taskId":"task_xxx",
  "status":"completed",
  "result":"...",
  "error":null
}
```

callbackUrl 为空时不发送；回调失败只记录 `callback_status=failed`，GET 任务仍返回完整结果。

- [ ] **Step 5: 运行任务测试**

```bash
cd mateclaw-server && mvn -Dtest=AgentApiTaskServiceTest,AgentApiTaskControllerTest test
```

Expected: PASS。

---

## Task 7: 增加日志、统计和治理

**Files:**
- Create: `AgentApiRateLimiter.java`。
- Create: `AgentApiLogService.java`。
- Modify: `AgentApiExecutionService.java`。
- Modify: `AgentApiManagementController.java`。
- Test: `AgentApiRateLimiterTest.java`。
- Test: `AgentApiLogServiceTest.java`。

- [ ] **Step 1: 写限流失败测试**

测试专家默认值、Key 覆盖值、每分钟超限、并发许可释放和每日额度超限。

- [ ] **Step 2: 实现限流器**

使用并发安全的内存窗口计数器：

```text
windowKey = publicationId + ":" + apiKeyId + ":" + epochMinute
```

并发使用 `Semaphore`，请求终止通过 `finally` 释放；超限抛出包含 retryAfterSeconds 的领域异常。

- [ ] **Step 3: 接入执行服务**

认证成功后依次申请每日额度、每分钟额度和并发许可；认证失败不计入成功调用；所有已开始执行的请求写一条日志。

- [ ] **Step 4: 实现日志与统计接口**

`GET /logs` 支持 page、size、status、protocol、apiKeyId；`GET /stats` 返回今日调用、成功率、平均耗时、token 总量和按协议计数。

- [ ] **Step 5: 运行治理测试**

```bash
cd mateclaw-server && mvn -Dtest=AgentApiRateLimiterTest,AgentApiLogServiceTest test
```

Expected: PASS。

---

## Task 8: 增加前端 API 客户端、类型和路由

**Files:**
- Modify: `mateclaw-ui/src/api/index.ts`。
- Modify: `mateclaw-ui/src/types/index.ts`。
- Modify: `mateclaw-ui/src/router/index.ts`。
- Modify: `mateclaw-ui/src/i18n/locales/zh-CN.ts`。
- Modify: `mateclaw-ui/src/i18n/locales/en-US.ts`。
- Test: `mateclaw-ui/src/api/__tests__/agentApiManagement.test.ts`。

- [ ] **Step 1: 写 API 客户端失败测试**

Mock axios，验证 URL 和 payload：

```ts
agentApiManagement.getPublication(42)
agentApiManagement.createKey(42, { name: 'CRM' })
agentApiManagement.revokeKey(42, 'key-1')
agentApiManagement.getLogs(42, { page: 1, size: 20 })
```

- [ ] **Step 2: 增加类型和 API 方法**

增加 `AgentApiPublication`、`AgentApiKey`、`CreatedAgentApiKey`、`AgentApiLog`、`AgentApiStats`、`AgentApiTask` 类型，并实现对应 HTTP 方法。

- [ ] **Step 3: 增加控制台路由**

在 `router/index.ts` 的 Agents 路由附近增加：

```ts
{
  path: 'agents/:id/api',
  name: 'AgentApiConsole',
  component: () => import('@/views/AgentApiConsole.vue'),
  meta: { title: 'Agent API', requiredCapability: 'manage:agents' },
}
```

- [ ] **Step 4: 增加 i18n 键**

中英文必须包含相同的 `agentApi` 树：`overview`、`keys`、`docs`、`logs`、`stats`、`settings`、`test`、`quickStart`、`createKey`、`revokeKey`、错误和空状态。

- [ ] **Step 5: 运行前端测试**

```bash
cd mateclaw-ui && pnpm vitest run src/api/__tests__/agentApiManagement.test.ts
```

Expected: PASS。

---

## Task 9: 实现 API 控制台和专家摘要

**Files:**
- Create: `AgentApiConsole.vue` 和七个子组件。
- Modify: `Agents.vue`。
- Test: `AgentApiConsole/__tests__/apiConsoleLogic.test.ts`。

- [ ] **Step 1: 写控制台纯逻辑失败测试**

覆盖：

```ts
expect(formatEndpoint('rest')).toContain('/api/v1/open/agent')
expect(formatEndpoint('openai')).toContain('/v1/chat/completions')
expect(copyQuickStart('rest', publication, key)).toContain('Authorization')
expect(maskKey('mak_abcdef')).toBe('mak_...cdef')
```

- [ ] **Step 2: 实现控制台壳层**

使用左侧导航和 `router.push`，导航项为概览、凭证、文档、调用日志、统计、设置；`agentId` 从 route params 读取，进入页面后并行加载发布配置和统计。

- [ ] **Step 3: 实现概览和快速开始**

显示发布状态、Key 数量、今日调用、成功率、平均耗时；快速开始卡片提供 REST curl 和 OpenAI curl，复制按钮使用现有 clipboard 工具。

- [ ] **Step 4: 实现 Key 面板**

支持创建名称、显示一次明文、复制、确认撤销；列表只显示前缀、名称、状态、创建时间和最后使用时间。

- [ ] **Step 5: 实现文档、日志、统计和设置**

文档页提供协议切换和参数说明；日志页分页；统计页展示指标；设置页编辑发布开关、默认限流、Key 覆盖和 Webhook 开关。

- [ ] **Step 6: 实现独立在线测试**

测试页输入消息、`endUserId`、`conversationId`，调用管理测试接口，展示状态、结果和耗时，并提示会沿用专家工具权限。

- [ ] **Step 7: 在 Agents 编辑弹窗增加摘要入口**

在现有 `modalTab` 后增加 API 摘要内容，不增加复杂 Key 表格；提供“打开 API 控制台”按钮并跳转 `/agents/${id}/api`。新建 Agent 时显示“保存后可配置 API”。

- [ ] **Step 8: 运行前端测试和构建**

```bash
cd mateclaw-ui && pnpm vitest run src/api/__tests__/agentApiManagement.test.ts src/views/AgentApiConsole/__tests__/apiConsoleLogic.test.ts
cd mateclaw-ui && pnpm build
```

Expected: PASS and production bundle succeeds。

---

## Task 10: 端到端验证和文档

**Files:**
- Modify: `README_zh.md`：增加专家 API 快速开始链接。
- Create: `docs/agent-api.md`：完整调用示例、Key 生命周期和错误码。
- Test: 现有后端全量测试和前端全量测试。

- [ ] **Step 1: 增加文档**

文档必须包含：

1. 控制台入口。
2. 创建 Key 示例。
3. REST 同步、SSE、异步示例。
4. OpenAI curl、Python 和 JavaScript 示例。
5. `endUserId` 与 `conversationId` 约束。
6. Webhook 请求体。
7. 401、403、429 和 502 错误处理。
8. Key 只显示一次的安全说明。

- [ ] **Step 2: 运行后端全量测试**

```bash
cd mateclaw-server && mvn test
```

Expected: PASS。

- [ ] **Step 3: 运行前端全量测试**

```bash
cd mateclaw-ui && pnpm test -- --run
```

Expected: PASS。

- [ ] **Step 4: 构建后端和前端**

```bash
cd mateclaw-server && mvn -DskipTests package
cd mateclaw-ui && pnpm build
```

Expected: PASS。

- [ ] **Step 5: 检查接口契约**

启动后端后验证：

```bash
curl -i http://localhost:8080/v3/api-docs
curl -i -X POST http://localhost:8080/api/v1/open/agent/chat
curl -i -X POST http://localhost:8080/v1/chat/completions
```

未携带 Key 的两个公开调用都必须返回 `agent_api_unauthorized`，不能落入 JWT 登录错误。

- [ ] **Step 6: 提交功能变更**

```bash
git add docs/superpowers/specs/2026-09-18-agent-api-management-design.md docs/superpowers/plans/2026-09-18-agent-api-management.md docs/agent-api.md README_zh.md mateclaw-server mateclaw-ui
git commit -m "feat: add expert api management"
```
