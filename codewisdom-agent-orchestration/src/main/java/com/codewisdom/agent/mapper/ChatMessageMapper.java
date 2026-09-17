package com.codewisdom.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codewisdom.agent.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
