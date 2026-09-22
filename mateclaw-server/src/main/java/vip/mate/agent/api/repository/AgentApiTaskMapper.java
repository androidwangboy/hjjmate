package vip.mate.agent.api.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.agent.api.model.AgentApiTaskEntity;

@Mapper
public interface AgentApiTaskMapper extends BaseMapper<AgentApiTaskEntity> {
}
