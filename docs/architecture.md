# 架构设计（architecture）

> 依赖版本依据见 `docs/tech-spike.md`。本文档只描述架构，不含实现代码。

## 1. 架构总览

```
                         ┌──────────────────────┐
   Vue3 + Element Plus ──▶│  gateway (8080)      │ 路由 / 跨域 / 限流 / OpenAPI 聚合
                         └──────────┬───────────┘
                                    │  Nacos 服务发现 + Sentinel 熔断
        ┌───────────────┬───────────┼───────────┬────────────────┐
        ▼               ▼           ▼           ▼                ▼
 project-resource  code-analysis  agent-orchestration   evaluation-export
    (8081)           (8082)          (8083)                (8084)
        │               │              │                     │
        │               │              │  LangGraph4j        │
        │               │              │  状态图编排          │
        └───────────────┴──────────────┴─────────────────────┘
                                    │
        MySQL 8.0 · Redis 7.x · MinIO · RabbitMQ · Nacos
```

## 2. MVP 微服务拆分（5 个，不做过度拆分）

| 服务 | 端口 | 职责 | 对应原始 10 服务 |
|---|---|---|---|
| **gateway** | 8080 | 统一入口、路由、跨域、限流、鉴权拦截、OpenAPI 聚合 | 网关服务 |
| **project-resource** | 8081 | 仓库拉取(JGit)、ZIP 解压、文件上传、MinIO、文件树构建 | 项目资源服务 |
| **code-analysis** | 8082 | Tree-Sitter 解析、架构逆向、缺陷审计、依赖冲突 | 代码解析 + 智能审计 |
| **agent-orchestration** | 8083 | LangGraph4j 编排：业务规则、文档注释、修复建议、Diff、HITL | 业务逆向 + 文档 + 修复 |
| **evaluation-export** | 8084 | 运行能力判定、沙箱运行、打包导出、量化评测、持久化 | 运行导出 + 智能评测 + 数据 |

**拆分原则**：按「变更频率」和「资源画像」拆，不按名词拆。
- `project-resource` IO 密集（网络 + 磁盘）→ 独立，可横向扩展。
- `code-analysis` CPU 密集（解析）→ 独立，可配大内存 + MQ 削峰。
- `agent-orchestration` IO 等待密集（LLM 调用）→ 独立，超时/重试策略与其他服务完全不同。
- `evaluation-export` 批处理 + 存储 → 独立，避免拖慢在线链路。

**后续可拆方向**（流程稳定后再做）：把 `code-analysis` 拆成 `code-parser` + `code-audit`；把 `evaluation-export` 拆出 `evaluation` 独立服务。

## 3. 模块内部结构（每个服务统一）

```
<service>/
├── pom.xml
└── src/main/java/com/codewisdom/<service>/
    ├── <Service>Application.java
    ├── controller/     # REST 入口，只做参数校验 + 委派
    ├── service/        # 业务编排
    ├── domain/         # 领域模型 / 枚举 / 值对象
    ├── mapper/         # MyBatis-Plus Mapper
    ├── entity/         # 数据库实体
    ├── dto/            # 请求/响应 DTO
    ├── convert/        # MapStruct 转换器
    ├── config/         # 配置类
    └── client/         # 调用其他服务的 Feign Client
```

公共模块：`codewisdom-common`（统一响应体、异常体系、常量、工具、全局异常处理、TraceId 透传）。

## 4. MVP 范围

### 4.1 MVP 内（必须交付）

| 能力 | 归属服务 |
|---|---|
| 网关路由、跨域、限流 | gateway |
| GitHub/Gitee 公开仓库导入 | project-resource |
| ZIP 上传与解压 | project-resource |
| 项目文件树生成 | project-resource |
| Tree-Sitter 解析类/方法/依赖 | code-analysis |
| Mermaid 架构图生成 | code-analysis |
| 风险分级审计（高危/中危/低危） | code-analysis |
| 依赖冲突检测（pom.xml / requirements.txt） | code-analysis |
| README / 注释生成 | agent-orchestration |
| 修复建议 + Diff 展示 | agent-orchestration |
| HITL 人工审核（确认/修改/驳回/多轮） | agent-orchestration |
| 运行能力判定（轻量/重型） | evaluation-export |
| ZIP 打包导出 | evaluation-export |
| 量化评测报告 | evaluation-export |
| Vue3 全流程前端 | 前端 |
| OpenAPI 文档 | 全部后端 |
| `docker compose up -d` 一键起中间件 | 基础设施 |

### 4.2 MVP 外（明确不做，避免范围蔓延）

- 私有仓库认证导入（SSH Key / Token 托管）。
- 真实沙箱执行用户代码 —— **MVP 仅做「运行能力判定 + 本地部署指引」**，不启动容器执行用户代码。沙箱执行列入 2.x。
- 业务规则逆向的**高准确率承诺** —— 定位为探索性辅助功能，输出需人工确认。
- 多租户、RBAC 权限体系 —— MVP 用单用户/简单 Token。
- 分布式事务（Seata 虽在 BOM 内，MVP 不启用）。
- CI/CD 流水线。

### 4.3 沙箱安全红线（贯穿所有阶段）

1. 默认**不执行**任何用户代码。
2. 引入沙箱后必须：独立网络命名空间、只读挂载、CPU/内存/磁盘配额、执行超时、禁止特权模式。
3. 上传文件必须校验扩展名 + MIME + 大小，解压必须防 Zip Slip（路径穿越）。
4. 仓库地址必须白名单校验域名，禁止内网地址（SSRF 防护）。

## 5. 数据存储设计

| 存储 | 用途 | 关键 Key / 表 |
|---|---|---|
| MySQL 8.0 | 项目、任务、审计、文档、评测的持久化 | `t_project`、`t_import_task`、`t_file_node`、`t_analysis_result`、`t_audit_issue`、`t_doc_record`、`t_fix_record`、`t_hitl_review`、`t_eval_report` |
| Redis 7.x | 解析结果缓存、Agent 中间态、幂等锁、任务进度 | `cw:analysis:{projectId}`、`cw:agent:state:{taskId}`、`cw:lock:{bizKey}` |
| MinIO | 原始包、源码、生成文档、导出包 | bucket：`cw-source`、`cw-artifact`、`cw-export` |
| RabbitMQ | 大项目异步解析、批量文档生成、评测任务 | exchange：`cw.topic`；queue：`cw.parse`、`cw.docgen`、`cw.eval` |

持久化统一由 `evaluation-export` 的 `repository` 子模块承担（MVP 阶段各服务直连同一 MySQL，不引入额外数据服务）。

## 6. 核心数据流

```
导入 → [project-resource] 拉取/解压 → MinIO(cw-source) + 文件树入库
     → MQ(cw.parse) →
       [code-analysis] Tree-Sitter 解析 → 架构逆向 → 审计 → 结果入库 + Redis 缓存
     → MQ(cw.agent) →
       [agent-orchestration] LangGraph4j 状态图：
            业务规则 → 文档注释(开关) → 修复建议 → Diff → HITL 循环
     → [evaluation-export] 运行判定 → 导出 ZIP(MinIO: cw-export)
     → [evaluation-export] 评测任务 → 指标计算 → 报告
```

## 7. Agent 编排设计（LangGraph4j）

**状态载体**：单一 `CodeWisdomState`（不可变优先，节点返回增量），字段覆盖 `docs/spec.md` 4.3 节列出的全部状态项。

**图节点**：`resourceParse → staticParse → archReverse → audit → bizRule → docGen(条件) → fixSuggest → hitlReview → runJudge → export`

**关键控制流**：
- `docGen` 由用户配置开关决定是否跳过（条件边）。
- `hitlReview` 为**循环边**：`rejected` → 回到 `fixSuggest`；`approved` → 前进 `runJudge`；设置最大轮次上限（MVP 建议 5 轮）防死循环。
- 每个节点失败进入 `errorHandler` 节点，记录节点名 + 异常，供评测模块错误溯源使用。
- 检查点：MVP 用 Redis 存 state（`langgraph4j-postgres-saver` 列为后续选项）。

**Agent 与微服务的映射**：Agent 不是独立微服务，而是 `agent-orchestration` 内部的图节点。`docs/spec.md` 4.4 节的 10 个 Agent 均落在此服务的图节点中，其中仓库解析、静态解析节点通过 Feign/MQ 委派给对应服务。

## 8. 技术基线（详见 tech-spike.md）

| 层 | 选型 | 版本 |
|---|---|---|
| JDK | OpenJDK | 17 |
| 框架 | Spring Boot | 3.5.15 |
| 微服务 | Spring Cloud / Spring Cloud Alibaba | 2025.0.3 / 2025.0.0.0 |
| 注册配置 | Nacos Client | 3.0.3 |
| 熔断限流 | Sentinel | 1.8.9 |
| 网关 | spring-cloud-starter-gateway | 4.3.5 |
| ORM | MyBatis-Plus (boot3 starter) | 3.5.17 |
| 对象映射 | MapStruct | 1.6.3 |
| 代码解析 | io.github.bonede:tree-sitter + tree-sitter-java | 0.26.6 / 0.23.5 |
| Git | JGit | 7.8.0.202609011348-r |
| 对象存储 | MinIO Java SDK | 8.6.0（9.0.3 需验证后升级） |
| Agent 编排 | LangGraph4j | 1.8.27（LTS） |
| API 文档 | springdoc-openapi | 2.9.1 |
| 前端 | Vue3 + Element Plus + Vite | 待定（阶段 10） |

## 9. 环境约束与实现约定（实测得出，必须遵守）

### 9.1 网络约束

本机实测（2026-09-15）：`github.com` **不可达**（连接超时），`gitee.com`、`codeload.github.com`、
`raw.githubusercontent.com`、`repo1.maven.org` 可达。

**实现约定**：
1. 仓库导入**不得硬编码**任一托管源，按 JGit 通用能力实现，URL 由用户输入。
2. GitHub 导入按协议实现，但**文档与测试中必须标注「本机未验证」**，不得声称已验证。
3. 网络依赖的测试一律打 `@Tag("network")`，并在不可达时用 `Assumptions` 优雅跳过，
   保证 `mvn clean verify` 在离线环境也是绿的。
4. 大仓库导入（实测 18MB 仓库浅克隆约 10 秒）**必须异步 + MQ**，禁止放在同步请求链路。

### 9.2 解析层实现约定（Tree-Sitter）

Tree-Sitter 绑定返回的是 **UTF-8 字节偏移**，不是字符偏移。解析层必须：

```java
byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
String text = new String(bytes, node.getStartByte(), node.getEndByte() - node.getStartByte(),
                          StandardCharsets.UTF_8);
```

**禁止** `source.substring(node.getStartByte(), node.getEndByte())` —— 源码含中文时会切出乱码。
该约定适用于所有节点文本提取（类名、方法名、注释、字面量）。

### 9.3 编排层实现约定（LangGraph4j）

1. `stream()` 返回 `AsyncGenerator` 而非 `Stream`，需 `.map(...).stream()` 才能 `toList()`。
2. 流式节点序列含 `__START__` / `__END__` 合成节点，断言执行顺序时必须计入。
3. HITL 循环边**必须设最大轮次上限**（防死循环）；MVP 建议 5 轮，冒烟用 2 轮。
4. 架构图（Mermaid）优先复用 `CompiledGraph.getGraph(Type.MERMAID, title, false)`，
   不要手工拼接字符串。
