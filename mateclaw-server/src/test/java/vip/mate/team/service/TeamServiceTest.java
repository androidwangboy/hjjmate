package vip.mate.team.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.team.model.AgentTeamEntity;
import vip.mate.team.model.AgentTeamMemberEntity;
import vip.mate.team.repository.AgentTeamMapper;
import vip.mate.team.repository.AgentTeamMemberMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pins team creation guards — most importantly that a plan-execute agent can
 * never become a lead: its own serial delegation pipeline bypasses the team
 * board, so the collaboration would silently degrade to solo delegation.
 */
class TeamServiceTest {

    private static final Long LEAD_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    @BeforeAll
    static void initTableInfo() {
        // Lambda wrappers resolve columns from the static TableInfo cache;
        // plain Mockito tests must seed it manually.
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentTeamEntity.class);
        TableInfoHelper.initTableInfo(assistant, AgentTeamMemberEntity.class);
    }

    private AgentTeamMapper teamMapper;
    private AgentTeamMemberMapper memberMapper;
    private AgentMapper agentMapper;
    private TeamService service;

    @BeforeEach
    void setUp() {
        teamMapper = mock(AgentTeamMapper.class);
        memberMapper = mock(AgentTeamMemberMapper.class);
        agentMapper = mock(AgentMapper.class);
        service = new TeamService(teamMapper, memberMapper, agentMapper,
                mock(ApplicationEventPublisher.class));
    }

    private AgentEntity agent(Long id, String agentType) {
        AgentEntity a = new AgentEntity();
        a.setId(id);
        a.setName("agent-" + id);
        a.setAgentType(agentType);
        return a;
    }

    @Test
    @DisplayName("a plan-execute agent is rejected as lead with an actionable message")
    void planExecuteLeadRejected() {
        when(agentMapper.selectById(LEAD_ID)).thenReturn(agent(LEAD_ID, "plan_execute"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.createTeam("组", null, LEAD_ID, List.of(MEMBER_ID), "admin"));

        assertTrue(e.getMessage().contains("ReAct"));
        verify(teamMapper, never()).insert(any(AgentTeamEntity.class));
    }

    @Test
    @DisplayName("members may be plan-execute — only the lead role is restricted")
    void planExecuteMemberAllowedByTypeGuard() {
        when(agentMapper.selectById(LEAD_ID)).thenReturn(agent(LEAD_ID, "react"));
        when(agentMapper.selectById(MEMBER_ID)).thenReturn(agent(MEMBER_ID, "plan_execute"));
        when(memberMapper.selectCount(any())).thenReturn(0L);

        assertDoesNotThrow(() ->
                service.createTeam("组", null, LEAD_ID, List.of(MEMBER_ID), "admin"));
        verify(teamMapper).insert(any(AgentTeamEntity.class));
    }
}
