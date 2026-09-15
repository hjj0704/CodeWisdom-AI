package com.codewisdom.resource.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.codewisdom.resource.domain.enums.ProjectStatus;
import com.codewisdom.resource.domain.enums.SourceType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目。一个项目对应一次导入结果，可被多次重新导入（见 {@link ImportTask}）。
 */
@Data
@TableName("t_project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 导入来源。 */
    private SourceType sourceType;

    /** 仓库地址；{@link SourceType#ZIP} / {@link SourceType#FILES} 时为空。 */
    private String sourceUrl;

    /** 默认分支，仅 Git 导入有值。 */
    private String defaultBranch;

    private ProjectStatus status;

    /** 对象存储桶名。 */
    private String storageBucket;

    /** 对象存储路径前缀，形如 {@code projects/{id}/}。 */
    private String storagePrefix;

    /** 文件树中的文件总数（不含目录）。 */
    private Integer fileCount;

    /** 源码总字节数。 */
    private Long totalSize;

    /** 创建时间。只在插入时填充，更新时永不写回——审计字段必须不可变。 */
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
