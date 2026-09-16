package com.codewisdom.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** HITL 审核轮次记录（表 {@code t_hitl_review}），每轮提交一行。 */
@Data
@TableName("t_hitl_review")
public class HitlReviewEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String sessionKey;

    private Long fixRecordId;

    private Integer round;

    /** 存 {@link com.codewisdom.agent.domain.fix.FixModels.HitlReviewStatus#name()}。 */
    private String decisionStatus;

    private Boolean approved;

    private Boolean isTerminated;

    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
