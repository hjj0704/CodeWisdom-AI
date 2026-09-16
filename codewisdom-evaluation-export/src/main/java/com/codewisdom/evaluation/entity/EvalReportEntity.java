package com.codewisdom.evaluation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 量化评测报告（表 {@code t_eval_report}）。 */
@Data
@TableName("t_eval_report")
public class EvalReportEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String datasetVersion;

    private String sampleId;

    /** 如 COMPLETED / FAILED。 */
    private String status;

    private String metricsJson;

    private String traceJson;

    private String reportMarkdown;

    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
