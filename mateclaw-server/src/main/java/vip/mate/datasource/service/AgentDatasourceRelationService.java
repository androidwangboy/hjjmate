package vip.mate.datasource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// 修改人:祝奔；时间：2026-11-16T14:09:29.021Z；修改原因/内容：清理未使用的 QueryWrapper/IService/LambdaUpdateWrapper 导入，改用 LambdaQueryWrapper 删除旧关系；代码来源：AI生成
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.datasource.model.AgentDatasourceRelationEntity;
import vip.mate.datasource.model.DatasourceEntity;
import vip.mate.datasource.repository.AgentDatasourceRelationMapper;
import vip.mate.datasource.repository.DatasourceMapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务聚合
 *
 * @version [V2.0]
 * @package vip.mate.datasource.service;
 * @description:[专家与数据源关系实现]
 * @author: [zhuben]
 * @date: [2026-11-16 13:45]
 * @copyright: 2026 echovin.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentDatasourceRelationService {

    private final AgentDatasourceRelationMapper agentDatasourceRelationMapper;
    private final DatasourceMapper datasourceMapper;

    public List<AgentDatasourceRelationEntity> selectByAgentId(Long agentId) {
        return agentDatasourceRelationMapper.selectList(new LambdaQueryWrapper<AgentDatasourceRelationEntity>()
                .eq(AgentDatasourceRelationEntity::getAgentId, agentId)
                .eq(AgentDatasourceRelationEntity::getDeleted, 0));
    }

    /**
     * 修改人:祝奔；时间：2026-11-16T14:09:29.021Z；修改原因/内容：整批覆盖 Agent 的数据源绑定关系：先按 agentId 物理删除旧关系再逐条插入新关系；
     * 支持空列表（清空全部绑定），并显式置空主键以走 ASSIGN_ID 自增；代码来源：AI生成
     */
    public boolean updateAgentDatasourceRelation(Long agentId, List<AgentDatasourceRelationEntity> agentDatasourceRelations) {
        if (agentId == null) {
            return false;
        }
        // 先删除该 Agent 的全部旧绑定，再按最新勾选重建
        agentDatasourceRelationMapper.delete(new LambdaQueryWrapper<AgentDatasourceRelationEntity>()
                .eq(AgentDatasourceRelationEntity::getAgentId, agentId));
        if (agentDatasourceRelations == null || agentDatasourceRelations.isEmpty()) {
            // 空列表代表"清空绑定"，删除已完成即成功
            return true;
        }
        for (AgentDatasourceRelationEntity relation : agentDatasourceRelations) {
            if (relation == null || relation.getDatasourceId() == null) {
                continue;
            }
            relation.setId(null);
            relation.setAgentId(agentId);
            agentDatasourceRelationMapper.insert(relation);
        }
        return true;
    }

    public String buildDatasourceContext(Long agentId) {
        List<AgentDatasourceRelationEntity> agentDatasourceRelationEntities = selectByAgentId(agentId);
        if (agentDatasourceRelationEntities == null || agentDatasourceRelationEntities.isEmpty()) {
            return "";
        }
        Set<Long> datasourceIds = agentDatasourceRelationEntities.stream().filter(s -> s != null && s.getDatasourceId() != null).map(s -> s.getDatasourceId()).collect(Collectors.toSet());
        if (datasourceIds.isEmpty()) {
            return "";
        }
        List<DatasourceEntity> datasourceEntities = datasourceMapper.selectList(new LambdaQueryWrapper<DatasourceEntity>()
                .in(DatasourceEntity::getId, datasourceIds)
                .eq(DatasourceEntity::getEnabled, 1)
                .eq(DatasourceEntity::getDeleted, 0));
        if (datasourceEntities == null || datasourceEntities.isEmpty()) {
            return "";
        }


        StringBuilder datasourceContext = new StringBuilder("## 绑定数据源：\r\n");
        datasourceContext.append(" 数据查询时仅允许从已发现的数据源列表中查询，使用query_datasource工具时，参数action='list_datasources'时,datasourceId参数必传，为绑定数据源ID \n");
        datasourceContext.append(" | 数据源ID | 数据源名称 | \n");
        for(DatasourceEntity datasourceEntity : datasourceEntities) {
            Long id = datasourceEntity.getId();
            String name = datasourceEntity.getName();
            datasourceContext.append(" | ").append(id).append(" | ").append(name).append("| \n");
        }
        datasourceContext.append("\r\n");

        return datasourceContext.toString();
    }
}
