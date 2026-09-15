package com.codewisdom.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codewisdom.resource.entity.FileNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * FileNode 数据访问接口。基础 CRUD 由 MyBatis-Plus 提供，复杂查询再补自定义方法。
 */
@Mapper
public interface FileNodeMapper extends BaseMapper<FileNode> {
}
