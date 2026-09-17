package com.codewisdom.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codewisdom.agent.entity.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
}
