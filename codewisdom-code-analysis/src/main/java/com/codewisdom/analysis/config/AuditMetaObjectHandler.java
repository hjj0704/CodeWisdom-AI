package com.codewisdom.analysis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 审计字段自动填充。
 *
 * <p>建表脚本里 {@code created_at} 是 {@code NOT NULL} 且**没有数据库默认值**——
 * 刻意如此，强制由应用层填充，避免「谁写的」不可追溯。所以这个处理器不是可选项：
 * 少了它，插入会直接撞 NOT NULL 约束。
 *
 * <p><b>精度对齐</b>：{@link LocalDateTime#now()} 是纳秒精度，而列类型是 {@code DATETIME(3)}
 * （毫秒）。不在应用层截断的话，写进库再读回的值会与内存中的值不等，
 * 造成「刚插进去的对象和查出来的对象字段不一致」这类隐蔽 bug（见 R-12）。
 *
 * <p>与 {@code codewisdom-project-resource} 的同名处理器形状一致。
 * 两份代码各自独立是有意的：审计字段的填充规则<b>可以</b>按服务不同，
 * 硬塞进 common 会逼着所有服务接受同一套填充策略。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 审计问题表没有 updatedAt——问题一旦记录就不该被改写
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class,
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    }

    /**
     * 更新时<b>什么都不填</b>。
     *
     * <p>{@code MetaObjectHandler} 的 {@code updateFill} 是<b>抽象方法</b>，必须实现
     * ——不实现会编译失败（第一版就栽在这里）。
     *
     * <p>本表刻意<b>没有</b> {@code updated_at} 列：审计问题是「某个时间点观察到的事实」，
     * 记录完就不该再被改写，改写等于抹掉历史。真要标记「已修复」，
     * 那是后续修复记录的职责（{@code t_fix_record}），不是这张表。
     * 所以这里是空实现，<b>不是漏写</b>。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 有意为空，见方法注释
    }
}
