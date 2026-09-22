package vip.mate.agent.api.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.agent.api.model.AgentApiPublicationEntity;

@Mapper
public interface AgentApiPublicationMapper extends BaseMapper<AgentApiPublicationEntity> {
}
