package com.codewisdom.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codewisdom.analysis.entity.AuditIssueEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计问题数据访问接口。基础 CRUD 由 MyBatis-Plus 提供，复杂查询再补自定义方法。
 */
@Mapper
public interface AuditIssueMapper extends BaseMapper<AuditIssueEntity> {
}
