package vip.mate.agent.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.agent.api.dto.AgentApiDtos.PublicationUpdateRequest;
import vip.mate.agent.api.model.AgentApiPublicationEntity;
import vip.mate.agent.api.repository.AgentApiPublicationMapper;
import vip.mate.exception.MateClawException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApiPublicationServiceTest {

    @Mock
    private AgentApiPublicationMapper mapper;

    @InjectMocks
    private AgentApiPublicationService service;

    @Test
    void createDefaultUsesSafePublicationSettings() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(mapper.insert(any(AgentApiPublicationEntity.class))).thenReturn(1);

        AgentApiPublicationEntity publication = service.getOrCreate(42L, 7L);

        assertEquals(42L, publication.getAgentId());
        assertEquals(7L, publication.getWorkspaceId());
        assertEquals(false, publication.getEnabled());
        assertEquals("expert", publication.getModelAlias());
        assertEquals(60, publication.getRequestsPerMinute());
        assertEquals(4, publication.getConcurrentLimit());
        assertEquals(10000, publication.getDailyQuota());
        assertEquals(120, publication.getTimeoutSeconds());
        verify(mapper).insert(any(AgentApiPublicationEntity.class));
    }

    @Test
    void updateRejectsNonPositiveGovernanceValues() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing());

        assertThrows(MateClawException.class, () -> service.update(
                42L, 7L,
                new PublicationUpdateRequest(true, "expert", 0, 4, 1000, 120, false)));
    }

    @Test
    void updatePreservesExpertAliasWhenBlank() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing());
        when(mapper.updateById(any(AgentApiPublicationEntity.class))).thenReturn(1);

        AgentApiPublicationEntity updated = service.update(
                42L, 7L,
                new PublicationUpdateRequest(true, " ", 120, null, null, null, true));

        assertEquals("expert", updated.getModelAlias());
        assertEquals(120, updated.getRequestsPerMinute());
        assertEquals(4, updated.getConcurrentLimit());
        assertEquals(true, updated.getWebhookEnabled());
    }

    private AgentApiPublicationEntity existing() {
        AgentApiPublicationEntity entity = new AgentApiPublicationEntity();
        entity.setId(1L);
        entity.setAgentId(42L);
        entity.setWorkspaceId(7L);
        entity.setEnabled(false);
        entity.setModelAlias("expert");
        entity.setRequestsPerMinute(60);
        entity.setConcurrentLimit(4);
        entity.setDailyQuota(10000);
        entity.setTimeoutSeconds(120);
        entity.setWebhookEnabled(false);
        entity.setDeleted(0);
        return entity;
    }
}
