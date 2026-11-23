package vip.mate.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
// 修改人:祝奔；时间：2026-11-23T08:23:34.674Z；修改原因/内容：引入 Agent 实体与 Mapper，用于用户趋势接口返回专家列表并按专家过滤；代码来源：AI生成
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.workspace.conversation.model.ConversationEntity;
import vip.mate.workspace.conversation.model.MessageEntity;
import vip.mate.workspace.conversation.repository.ConversationMapper;
import vip.mate.workspace.conversation.repository.MessageMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Dashboard 统计服务
 * <p>
 * 直接实时查询 mate_message / mate_conversation 表，不依赖预聚合。
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final ObjectMapper objectMapper;
    // 修改人:祝奔；时间：2026-11-23T08:23:34.674Z；修改原因/内容：注入 AgentMapper 以回显专家筛选列表；代码来源：AI生成
    private final AgentMapper agentMapper;

    /**
     * 获取概览统计（今日/本周/本月）— 实时查询
     */
    public Map<String, Object> getOverview(Long workspaceId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate monthStart = today.withDayOfMonth(1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", queryStats(workspaceId, today, today));
        result.put("thisWeek", queryStats(workspaceId, weekStart, today));
        result.put("thisMonth", queryStats(workspaceId, monthStart, today));
        return result;
    }

    /**
     * 获取日趋势数据（最近 N 天，按天聚合）
     */
    public List<Map<String, Object>> getTrend(Long workspaceId, int days) {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> dayStats = queryStats(workspaceId, date, date);
            dayStats.put("date", date.toString());
            trend.add(dayStats);
        }
        return trend;
    }

    // 修改人:祝奔；时间：2026-11-23T08:23:34.674Z；修改原因/内容：新增按日期/用户/专家统计会话次数与 Token 消耗的实时查询逻辑；代码来源：AI生成
    // 修改人:祝奔；时间：2026-11-23T08:32:58.413Z；修改原因/内容：趋势指标由会话数改为消息数，消息数与 Token 均按消息发生日统计；代码来源：AI生成
    /**
     * 用户维度使用趋势：消息数按消息发生日统计，Token 按 assistant 消息发生日统计。
     *
     * @param workspaceId 工作区 ID
     * @param startDate   起始日期（含），为空时取结束日期前 6 天
     * @param endDate     结束日期（含），为空时取今天
     * @param agentId     专家 ID，为空时统计全部专家
     */
    public Map<String, Object> getUserTrend(Long workspaceId, LocalDate startDate,
                                            LocalDate endDate, Long agentId) {
        LocalDate today = LocalDate.now();
        if (endDate == null) endDate = today;
        if (startDate == null) startDate = endDate.minusDays(6);
        if (startDate.isAfter(endDate)) {
            LocalDate tmp = startDate;
            startDate = endDate;
            endDate = tmp;
        }
        // 限制最大查询跨度，避免单次请求拉取过多数据
        LocalDate earliest = endDate.minusDays(89);
        if (startDate.isBefore(earliest)) startDate = earliest;

        List<String> dates = new ArrayList<>();
        Map<String, Integer> dateIndex = new HashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dateIndex.put(date.toString(), dates.size());
            dates.add(date.toString());
        }
        int dateCount = dates.size();
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        // 修改人:祝奔；时间：2026-11-23T08:32:58.413Z；修改原因/内容：按消息发生日统计每个用户每天的消息数与 Token，再按会话关联专家；代码来源：AI生成
        LambdaQueryWrapper<MessageEntity> msgWrapper = new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getDeleted, 0)
                .ge(MessageEntity::getCreateTime, startTime)
                .le(MessageEntity::getCreateTime, endTime)
                .select(MessageEntity::getConversationId, MessageEntity::getRole,
                        MessageEntity::getCreateTime, MessageEntity::getPromptTokens,
                        MessageEntity::getCompletionTokens);
        List<MessageEntity> messages = messageMapper.selectList(msgWrapper);

        Set<String> messageConversationIds = new HashSet<>();
        for (MessageEntity msg : messages) {
            if (msg.getConversationId() != null) {
                messageConversationIds.add(msg.getConversationId());
            }
        }

        Map<String, String> usernameByConversation = new HashMap<>();
        Map<String, long[]> messageCounts = new HashMap<>();
        Map<String, long[]> tokenTotals = new HashMap<>();
        Set<String> usernames = new LinkedHashSet<>();
        boolean hasUnassignedAgent = false;
        if (!messageConversationIds.isEmpty()) {
            LambdaQueryWrapper<ConversationEntity> convWrapper = new LambdaQueryWrapper<ConversationEntity>()
                    .in(ConversationEntity::getConversationId, messageConversationIds)
                    .select(ConversationEntity::getConversationId, ConversationEntity::getUsername,
                            ConversationEntity::getAgentId);
            if (workspaceId != null) {
                convWrapper.eq(ConversationEntity::getWorkspaceId, workspaceId);
            }
            applyAgentFilter(convWrapper, agentId);
            List<ConversationEntity> conversations = conversationMapper.selectList(convWrapper);
            for (ConversationEntity conv : conversations) {
                registerConversation(conv, usernameByConversation, usernames);
                if (conv.getAgentId() == null) {
                    hasUnassignedAgent = true;
                }
            }

            for (MessageEntity msg : messages) {
                String user = usernameByConversation.get(msg.getConversationId());
                Integer idx = msg.getCreateTime() == null
                        ? null : dateIndex.get(msg.getCreateTime().toLocalDate().toString());
                if (user == null || idx == null) {
                    continue;
                }
                messageCounts.computeIfAbsent(user, k -> new long[dateCount])[idx]++;
                if (!"assistant".equals(msg.getRole())) {
                    continue;
                }
                long tokens = (msg.getPromptTokens() == null ? 0 : msg.getPromptTokens())
                        + (msg.getCompletionTokens() == null ? 0 : msg.getCompletionTokens());
                tokenTotals.computeIfAbsent(user, k -> new long[dateCount])[idx] += tokens;
            }
        }

        List<Map<String, Object>> users = new ArrayList<>();
        for (String user : usernames) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", user);
            row.put("messages", toList(messageCounts.get(user), dateCount));
            row.put("tokens", toList(tokenTotals.get(user), dateCount));
            users.add(row);
        }
        users.sort(Comparator.comparing((Map<String, Object> u) -> String.valueOf(u.get("name"))));

        List<Map<String, Object>> agentOptions = new ArrayList<>();
        LambdaQueryWrapper<AgentEntity> agentWrapper = new LambdaQueryWrapper<AgentEntity>()
                .select(AgentEntity::getId, AgentEntity::getName);
        if (workspaceId != null) {
            agentWrapper.eq(AgentEntity::getWorkspaceId, workspaceId);
        }
        for (AgentEntity agent : agentMapper.selectList(agentWrapper)) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("id", agent.getId());
            option.put("name", agent.getName());
            agentOptions.add(option);
        }
        if (hasUnassignedAgent) {
            Map<String, Object> unassigned = new LinkedHashMap<>();
            unassigned.put("id", 0);
            unassigned.put("name", "未分配专家");
            agentOptions.add(unassigned);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("agents", agentOptions);
        result.put("users", users);
        return result;
    }

    private String normalizeUsername(String username) {
        return username == null || username.isBlank() ? "未知用户" : username;
    }

    // 修改人:祝奔；时间：2026-11-23T08:23:34.674Z；修改原因/内容：补充会话用户登记与专家过滤辅助方法，兼容“未分配专家”筛选；代码来源：AI生成
    private void registerConversation(ConversationEntity conv, Map<String, String> usernameByConversation,
                                      Set<String> usernames) {
        String user = normalizeUsername(conv.getUsername());
        usernameByConversation.put(conv.getConversationId(), user);
        usernames.add(user);
    }

    private void applyAgentFilter(LambdaQueryWrapper<ConversationEntity> wrapper, Long agentId) {
        if (agentId == null) {
            return;
        }
        if (agentId.longValue() == 0L) {
            wrapper.isNull(ConversationEntity::getAgentId);
        } else {
            wrapper.eq(ConversationEntity::getAgentId, agentId);
        }
    }

    private List<Long> toList(long[] values, int size) {
        List<Long> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(values == null ? 0L : values[i]);
        }
        return list;
    }

    private Map<String, Object> queryStats(Long workspaceId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(LocalTime.MAX);

        // 对话数
        LambdaQueryWrapper<ConversationEntity> convWrapper = new LambdaQueryWrapper<ConversationEntity>()
                .ge(ConversationEntity::getCreateTime, startTime)
                .le(ConversationEntity::getCreateTime, endTime);
        if (workspaceId != null) {
            convWrapper.eq(ConversationEntity::getWorkspaceId, workspaceId);
        }
        long conversations = conversationMapper.selectCount(convWrapper);

        // Workspace 级消息过滤：通过 conversation 关联 workspace
        // MessageEntity 没有 workspaceId 字段，需通过所属 conversation 间接过滤
        List<String> wsConversationIds = null;
        if (workspaceId != null) {
            List<ConversationEntity> wsConvs = conversationMapper.selectList(
                    new LambdaQueryWrapper<ConversationEntity>()
                            .eq(ConversationEntity::getWorkspaceId, workspaceId)
                            .select(ConversationEntity::getConversationId));
            wsConversationIds = wsConvs.stream()
                    .map(ConversationEntity::getConversationId).toList();
            if (wsConversationIds.isEmpty()) {
                // 该 workspace 无任何对话，直接返回零值
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("conversations", conversations);
                empty.put("messages", 0L);
                empty.put("totalTokens", 0L);
                empty.put("promptTokens", 0L);
                empty.put("completionTokens", 0L);
                empty.put("toolCalls", 0L);
                return empty;
            }
        }

        // 总消息数
        LambdaQueryWrapper<MessageEntity> msgWrapper = new LambdaQueryWrapper<MessageEntity>()
                .ge(MessageEntity::getCreateTime, startTime)
                .le(MessageEntity::getCreateTime, endTime)
                .eq(MessageEntity::getDeleted, 0);
        if (wsConversationIds != null) {
            msgWrapper.in(MessageEntity::getConversationId, wsConversationIds);
        }
        long messages = messageMapper.selectCount(msgWrapper);

        // Token 统计（assistant 消息）
        LambdaQueryWrapper<MessageEntity> tokenWrapper = new LambdaQueryWrapper<MessageEntity>()
                .eq(MessageEntity::getRole, "assistant")
                .ge(MessageEntity::getCreateTime, startTime)
                .le(MessageEntity::getCreateTime, endTime)
                .eq(MessageEntity::getDeleted, 0)
                .select(MessageEntity::getPromptTokens, MessageEntity::getCompletionTokens,
                        MessageEntity::getMetadata);
        if (wsConversationIds != null) {
            tokenWrapper.in(MessageEntity::getConversationId, wsConversationIds);
        }

        List<MessageEntity> assistantMessages = messageMapper.selectList(tokenWrapper);

        // Tool calls are not stored as standalone role="tool" rows; each agent
        // turn records them inside the assistant message's metadata JSON
        // (metadata.toolCalls, with metadata.segments[type=tool_call] as the
        // streaming-timeline fallback). Counting role="tool" therefore always
        // returned 0. We reuse the assistant messages already loaded for token
        // accounting and sum the tool-call entries from each row's metadata.
        long totalTokens = 0, promptTokens = 0, completionTokens = 0, toolCalls = 0;
        for (MessageEntity m : assistantMessages) {
            int pt = m.getPromptTokens() != null ? m.getPromptTokens() : 0;
            int ct = m.getCompletionTokens() != null ? m.getCompletionTokens() : 0;
            promptTokens += pt;
            completionTokens += ct;
            totalTokens += pt + ct;
            toolCalls += countToolCalls(m.getMetadata());
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("conversations", conversations);
        stats.put("messages", messages);
        stats.put("totalTokens", totalTokens);
        stats.put("promptTokens", promptTokens);
        stats.put("completionTokens", completionTokens);
        stats.put("toolCalls", toolCalls);
        return stats;
    }

    /**
     * Count tool invocations recorded on a single assistant message.
     * <p>
     * Tool calls live in the message's {@code metadata} JSON, not as separate
     * rows. The canonical list is {@code metadata.toolCalls}; older messages may
     * only carry the streaming timeline, so we fall back to counting
     * {@code metadata.segments} entries whose {@code type} is {@code tool_call}.
     * Parsing failures are treated as zero so a malformed row never breaks the
     * dashboard.
     */
    long countToolCalls(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank() || "{}".equals(metadataJson.trim())) {
            return 0;
        }
        try {
            // H2's JSON column hands the value back as a quoted JSON string
            // literal (double-encoded); MySQL returns the object directly. Mirror
            // MessageVO.parseMetadataToObject and unwrap one string layer first,
            // otherwise readTree yields a TextNode and toolCalls is never found.
            String json = metadataJson.trim();
            if (json.startsWith("\"") && json.endsWith("\"")) {
                json = objectMapper.readValue(json, String.class);
            }
            if (json.isBlank() || "{}".equals(json)) {
                return 0;
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode toolCalls = root.get("toolCalls");
            if (toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty()) {
                return toolCalls.size();
            }
            JsonNode segments = root.get("segments");
            if (segments != null && segments.isArray()) {
                long count = 0;
                for (JsonNode seg : segments) {
                    if ("tool_call".equals(seg.path("type").asText())) {
                        count++;
                    }
                }
                return count;
            }
        } catch (Exception e) {
            log.debug("Failed to parse message metadata for tool-call count: {}", e.getMessage());
        }
        return 0;
    }
}
