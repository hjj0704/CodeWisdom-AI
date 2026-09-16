package com.codewisdom.agent.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class DocMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class,
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 文档产物记录不可变
    }
}
