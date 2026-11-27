package vip.mate.dashboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.cron.model.CronJobEntity;
import vip.mate.cron.repository.CronJobMapper;
import vip.mate.dashboard.model.CronJobRunEntity;
import vip.mate.dashboard.service.CronJobRunService;
import vip.mate.dashboard.service.DashboardService;
import vip.mate.exception.MateClawException;
import vip.mate.workspace.core.annotation.RequireWorkspaceRole;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 统计接口
 *
 * @author MateClaw Team
 */
@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CronJobRunService cronJobRunService;
    private final CronJobMapper cronJobMapper;

    @Operation(summary = "获取概览统计")
    @GetMapping("/overview")
    @RequireWorkspaceRole("member")
    public R<Map<String, Object>> overview(
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        return R.ok(dashboardService.getOverview(workspaceId));
    }

    @Operation(summary = "获取日用量趋势")
    @GetMapping("/trend")
    @RequireWorkspaceRole("member")
    public R<List<Map<String, Object>>> trend(
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @RequestParam(defaultValue = "30") int days) {
        return R.ok(dashboardService.getTrend(workspaceId, Math.min(days, 90)));
    }

    // 修改人:祝奔；时间：2026-11-23T08:23:34.674Z；修改原因/内容：新增用户维度使用趋势接口，按日期/用户/专家返回会话次数与 Token 统计；代码来源：AI生成
    // 修改人:祝奔；时间：2026-11-23T08:32:58.413Z；修改原因/内容：趋势指标由会话数改为消息数；代码来源：AI生成
    @Operation(summary = "获取用户维度使用趋势（按日期/用户/专家统计消息数与 Token）")
    @GetMapping("/user-trend")
    @RequireWorkspaceRole("member")
    public R<Map<String, Object>> userTrend(
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) Long agentId) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        return R.ok(dashboardService.getUserTrend(wsId, startDate, endDate, agentId));
    }

    @Operation(summary = "获取 CronJob 执行历史")
    @GetMapping("/cron-runs/{cronJobId}")
    @RequireWorkspaceRole("member")
    public R<List<CronJobRunEntity>> cronJobRuns(
            @PathVariable Long cronJobId,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @RequestParam(defaultValue = "20") int limit) {
        // Verify the cron job belongs to the caller's workspace. Checked
        // against the job's own workspace_id so agent-less system jobs
        // (e.g. wiki_process) verify the same way as agent-bound jobs.
        CronJobEntity job = cronJobMapper.selectById(cronJobId);
        if (job != null && job.getWorkspaceId() != null) {
            long wsId = workspaceId != null ? workspaceId : 1L;
            if (!job.getWorkspaceId().equals(wsId)) {
                throw new MateClawException("err.common.wrong_workspace", 403, "资源不属于当前工作区");
            }
        }
        return R.ok(cronJobRunService.listByJobId(cronJobId, Math.min(limit, 100)));
    }

    @Operation(summary = "获取最近执行记录（当前 workspace 关联的 CronJob）")
    @GetMapping("/cron-runs")
    @RequireWorkspaceRole("member")
    public R<List<CronJobRunEntity>> recentRuns(
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
            @RequestParam(defaultValue = "20") int limit) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        return R.ok(cronJobRunService.listRecentByWorkspace(wsId, Math.min(limit, 100)));
    }
}
