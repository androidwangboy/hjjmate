package vip.mate.agent.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.agent.api.model.AgentApiKeyEntity;
import vip.mate.agent.api.repository.AgentApiKeyMapper;
import vip.mate.exception.MateClawException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentApiKeyServiceTest {

    @Mock
    private AgentApiKeyMapper mapper;

    @InjectMocks
    private AgentApiKeyService service;

    @Test
    void createReturnsMakPlaintextAndStoresOnlyHash() {
        when(mapper.insert(any(AgentApiKeyEntity.class))).thenReturn(1);

        AgentApiKeyService.CreatedKey result = service.create(
                11L, 42L, 7L, "CRM", null, null, null);

        assertTrue(result.plaintext().startsWith("mak_"));
        assertEquals(47, result.plaintext().length());
        assertNotNull(result.entity().getTokenHash());
        assertFalse(result.plaintext().equals(result.entity().getTokenHash()));
        assertEquals("mak_" + result.plaintext().substring(4, 12), result.entity().getKeyPrefix());
    }

    @Test
    void authenticateReturnsBoundPrincipalForActiveKey() {
        String plaintext = "mak_test-secret";
        AgentApiKeyEntity key = activeKey();
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        Optional<AgentApiKeyService.AgentApiPrincipal> principal = service.authenticate(plaintext);

        assertTrue(principal.isPresent());
        assertEquals(42L, principal.get().agentId());
        assertEquals(11L, principal.get().publicationId());
        assertEquals(7L, principal.get().workspaceId());
    }

    @Test
    void blankOrWrongPrefixDoesNotHitDatabase() {
        assertTrue(service.authenticate(null).isEmpty());
        assertTrue(service.authenticate("").isEmpty());
        assertTrue(service.authenticate("mc_not_an_agent_key").isEmpty());
        verify(mapper, never()).selectOne(any());
    }

    @Test
    void revokeRequiresAgentOwnership() {
        AgentApiKeyEntity key = activeKey();
        when(mapper.selectById(9L)).thenReturn(key);

        assertThrows(MateClawException.class, () -> service.revoke(99L, 9L));
        verify(mapper, never()).updateById(any(AgentApiKeyEntity.class));
    }

    private AgentApiKeyEntity activeKey() {
        AgentApiKeyEntity key = new AgentApiKeyEntity();
        key.setId(9L);
        key.setPublicationId(11L);
        key.setAgentId(42L);
        key.setWorkspaceId(7L);
        key.setTokenHash(AgentApiKeyService.sha256Hex("mak_test-secret"));
        key.setEnabled(true);
        key.setDeleted(0);
        return key;
    }
}
