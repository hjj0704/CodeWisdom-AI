package com.codewisdom.evaluation.domain;

/** 项目运行能力判定结果（T-801）。 */
public enum RunCapability {
    /** 无 Redis/MySQL/MQ 等中间件依赖，可沙箱预览（MVP 仅判定，不执行）。 */
    LIGHTWEIGHT,
    /** 含中间件或复杂环境，走本地部署指引。 */
    HEAVY
}
