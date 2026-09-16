package com.codewisdom.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codewisdom.resource.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
