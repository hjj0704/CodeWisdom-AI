package com.codewisdom.evaluation.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class EvalMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class,
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 评测报告记录不可变
    }
}
