package com.codewisdom.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 文档产物元数据（表 {@code t_doc_record}）。内容存 MinIO，这里只存索引。 */
@Data
@TableName("t_doc_record")
public class DocRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** {@link com.codewisdom.agent.domain.doc.DocArtifactType#name()} */
    private String docType;

    private String bucket;

    private String objectKey;

    private String fileName;

    private Long contentSize;

    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
