package com.codewisdom.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 修复建议与 Diff 快照（表 {@code t_fix_record}）。 */
@Data
@TableName("t_fix_record")
public class FixRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String issueKey;

    private String ruleId;

    private String filePath;

    private Integer lineNo;

    /** 存 {@link com.codewisdom.agent.domain.fix.FixModels.RiskLevel#name()}。 */
    private String riskLevel;

    private String suggestion;

    private String rationale;

    private Boolean diffChanged;

    private String diffJson;

    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
