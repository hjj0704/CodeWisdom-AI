package com.codewisdom.agent.domain.doc;

/** 文档产物类型（T-604）。 */
public enum DocArtifactType {
    /** 接口文档（OpenAPI 导出或 LLM 生成）。 */
    API,
    /** 模块说明。 */
    MODULE,
    /** 部署文档。 */
    DEPLOY,
    /** README（T-603 产物）。 */
    README
}
