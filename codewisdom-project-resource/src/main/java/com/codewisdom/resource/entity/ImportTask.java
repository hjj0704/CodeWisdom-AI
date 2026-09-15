package com.codewisdom.resource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.codewisdom.resource.domain.enums.ImportTaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入任务。一个项目可有多条（重试、重新导入），保留完整历史便于排查。
 */
@Data
@TableName("t_import_task")
public class ImportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private ImportTaskStatus status;

    /** 进度百分比 0~100。 */
    private Integer progress;

    /** 可读的进度或失败说明。 */
    private String message;

    /** 失败时的错误码，取值见 {@code ErrorCode}。 */
    private String errorCode;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** 创建时间。只在插入时填充，更新时永不写回——审计字段必须不可变。 */
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
