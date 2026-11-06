package vip.mate.wiki.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import vip.mate.wiki.model.WikiRecordTemplateEntity;

/**
 * 病历模板库数据访问层（方案 B：独立维护）
 */
@Mapper
public interface WikiRecordTemplateMapper extends BaseMapper<WikiRecordTemplateEntity> {
}
