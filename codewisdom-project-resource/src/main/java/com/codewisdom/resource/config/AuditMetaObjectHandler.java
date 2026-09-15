package com.codewisdom.resource.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 审计字段自动填充。
 *
 * <p>建表脚本中 {@code created_at} / {@code updated_at} 为 {@code NOT NULL} 且无默认值，
 * 刻意不做数据库默认，强制由应用层填充，避免「谁写的」不可追溯。
 *
 * <p><b>精度对齐</b>：Java 的 {@link LocalDateTime#now()} 是纳秒精度，而列类型为
 * {@code DATETIME(3)}（毫秒）。若不在应用层截断，写入库再读回的值会与内存中的值不等，
 * 造成「刚插进去的对象和查出来的对象字段不一致」这类隐蔽 bug。此处统一截断到毫秒。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    /**
     * 更新时<b>无条件</b>覆盖 {@code updatedAt}。
     *
     * <p>这里刻意不用 {@code strictUpdateFill}：strict 系列只在字段为 null 时填充，
     * 而实体通常是从库里查出来再改的，{@code updatedAt} 早已有值，会导致
     * 「改了数据但 updatedAt 没变」——审计字段失去意义。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updatedAt", now(), metaObject);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
