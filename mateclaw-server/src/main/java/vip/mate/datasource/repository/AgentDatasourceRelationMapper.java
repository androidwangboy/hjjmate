package vip.mate.datasource.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.datasource.model.AgentDatasourceRelationEntity;

/**
 * 业务聚合
 *
 * @version [V2.0]
 * @package vip.mate.datasource.repository;
 * @description:[专家与数据元绑定关系Mapper]
 * @author: [zhuben]
 * @date: [2026-11-16 13:44]
 * @copyright: 2026 echovin.com
 */
@Mapper
public interface AgentDatasourceRelationMapper extends BaseMapper<AgentDatasourceRelationEntity> {
}
