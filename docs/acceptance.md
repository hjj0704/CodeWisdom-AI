# 阶段验收标准（acceptance）

> 验收 = 可执行的命令 + 可观察的产物。禁止「看起来没问题」式验收。
> 全局门禁：`mvn clean verify` 必须通过，且**不得有被跳过（`@Disabled`）的测试**。

## 全局门禁（每阶段结束都必须满足）

| 编号 | 标准 | 验证命令 |
|---|---|---|
| G-1 | 全量构建通过 | `mvn clean verify` |
| G-2 | 无跳过的测试 | `mvn test` 输出中无 `Skipped: >0` |
| G-3 | 无版本冲突 | `mvn dependency:tree -Dverbose` 无 `omitted for conflict` 关键项 |
| G-4 | 接口有文档 | `curl -s localhost:8080/v3/api-docs` 返回 200 且非空 |
| G-5 | 不执行用户代码 | 代码审查确认无 `Runtime.exec` / `ProcessBuilder` 用于用户输入 |

---

## 阶段 0：技术验证与文档初始化 —— ✅ 已通过（2026-09-15）

- 目录结构 `docs/`、`docs/api/`、`docs/summaries/`、`docs/adr/` 存在。
- 7 份文档齐全且无「待补充」占位。
- **所有版本号有 Maven Central 实测依据**，未验证项明确标注 `需验证`。
- LangHarness 结论明确为「不存在 Java SDK，自研」。
- 四项冒烟 S1~S4 全部通过，证据落盘。
- 命令：`ls docs docs/api docs/summaries docs/adr && ls CLAUDE.md`
- 证据：`docs/summaries/s1-dependency-tree.txt`；冒烟测试类见 `**/spike/`。

## 阶段 1A：工程骨架（零中间件） —— ✅ 已通过（2026-09-15）

- 父 pom + `codewisdom-common` + gateway/project-resource/code-analysis/agent-orchestration/evaluation-export 七个 module 均编译通过。
- **`mvn clean verify` 在无任何中间件运行的情况下通过**（这是本阶段的核心约束）。
- 各服务 `local` profile 下的测试不依赖 Nacos/MySQL/Redis/MinIO/RabbitMQ。
- 网关 4 条静态路由注册成功，`GlobalExceptionHandler` 三态、`TraceIdFilter` 四项行为均有单测。
- 命令：`mvn clean verify`
- 实测结果：**7 模块 SUCCESS，26 个测试，0 失败 0 跳过**。
- **退出条件**：S1、S2、S3、S4 冒烟全部 ✅ —— 已满足。
- **本阶段是阶段 2 的启动条件** —— 已满足。

## 阶段 1B：中间件接入 🐳 挂起（等 Docker）

> 状态：**已挂起**。用户于 2026-09-15 决定暂不安装 Docker Desktop。恢复 Docker 后补做。

- `docker compose up -d` 后 Nacos、MySQL、Redis、MinIO、RabbitMQ 五容器 `healthy`。
- 网关路由可达：`curl -i localhost:8080/code-analysis/actuator/health` → 200。
- Nacos 控制台能看到 5 个服务实例；Sentinel 限流规则生效（超阈值返回 429）。
- 服务启动时自动建表（Flyway baseline 执行成功）。
- 命令：`docker compose up -d && docker compose ps && curl -i localhost:8080/code-analysis/actuator/health`
- **退出条件**：S5、S6、S7 冒烟全部 ✅。

## 阶段 2：项目多源导入

- 导入一个真实 GitHub 公开仓库（≥500 文件），任务状态 `PENDING→RUNNING→SUCCESS`。
- 文件树节点数与磁盘实际文件数一致；`.git`、`target`、`node_modules` 被过滤。
- 上传 ZIP 可解压；**含 `../` 路径穿越的恶意 ZIP 被拒绝并返回明确错误码**。
- 🐳 MinIO `cw-source` 桶中能查到原包与源码对象，大小一致 —— **挂起（等 Docker）**。
- 🐳 RabbitMQ 消费侧收到 `cw.parse` 消息 —— **挂起（等 Docker）**。
- 命令：`mvn -q -pl codewisdom-project-resource -am test`（🐳 相关测试在 Docker 就绪前以 `@Tag("docker")` 排除）
- **安全红线**：Zip Slip 防护测试必须存在且通过。

## 阶段 3：代码解析服务

- 对样例 Spring Boot 工程，解析出的类数、接口数、方法数与人工统计一致（允许 0 误差）。
- 每个方法有准确的文件路径 + 起始/结束行号。
- 跨文件调用关系可生成，无自环噪音边。
- 大工程（≥1000 文件）分片并行解析耗时低于单线程基线。
- 解析结果入 MySQL 且 Redis 缓存命中（二次解析不重复计算）。
- 命令：`mvn -q -pl codewisdom-code-analysis -am test`
- **前置条件**：S2 冒烟 ✅。

## 阶段 4：架构逆向

- 生成 Mermaid 架构图，**能被 Mermaid 官方解析器成功渲染**（不是只生成字符串）。
- 分层识别（controller/service/mapper/config/...）在样例集上准确率 ≥ 标注基线。
- 技术栈识别输出与标注一致。
- 构造的循环依赖样例能被检出，并给出完整环路径。
- 命令：`mvn -q -pl codewisdom-code-analysis -Dtest='*Layer*,*Mermaid*,*Tech*,*Cycle*' -Dsurefire.failIfNoSpecifiedTests=false test`
  （原写的是 `*Arch*`，与阶段 4 四张卡的实际测试类名都对不上，门禁会跑成空集；已按
  `LayerDetectTest` / `MermaidGenTest` / `TechStackTest` / `CycleDetectTest` 更正。）

## 阶段 5：缺陷与依赖审计

- 每条问题必须包含：文件路径、行号、问题描述、风险说明、触发场景、风险等级。
- 风险等级仅允许 高危/中危/低危 三值。
- 构造的缺陷样例集：**高危漏报率为 0**。
- `pom.xml` 版本冲突样例能检出并给出冲突路径；`requirements.txt` 同理。
- 架构隐患与阶段 4 结果打通，使用统一问题模型（同一 DTO）。
- 命令：`mvn -q -pl codewisdom-code-analysis -Dtest='*Audit*,*Conflict*,*Rule*' test`

## 阶段 6：文档注释生成

- **关闭开关时，一次 LLM 调用都不发生**（用 Mock Provider 计数断言为 0）。
- 生成的 README 包含：项目介绍、技术栈、目录结构、启动方式、注意事项五节。
- 类/方法/参数/返回值注释齐全，且为合法 Javadoc 语法。
- LLM 超时/失败时触发降级，不阻塞主流程。
- 生成产物落 MinIO，DB 有可追溯记录。
- 命令：`mvn -q -pl codewisdom-agent-orchestration -am test`

## 阶段 7：修复建议、Diff、HITL

- 每条高危问题均有对应修复建议，含修改理由（不是只给代码）。
- Diff 结构化输出（前后内容 + hunks），前端可直接渲染；无改动时不产生噪音 diff。
- HITL 四态（确认/修改/驳回/多轮）流转正确；超过最大轮次（5）自动终止且不抛异常。
- LangGraph4j 全图可跑通含 1 次驳回重生成，最终 state 正确。
- **默认不写入用户原始仓库**（断言：原目录文件未被修改）。
- 命令：`mvn -q -pl codewisdom-agent-orchestration -Dtest='*Fix*,*Diff*,*Hitl*,*Graph*' test`
- **前置条件**：S3 冒烟 ✅。

## 阶段 8：运行判定与导出

- 轻量/重型项目判定正确（含 Redis/MySQL/MQ 依赖者判为重型）。
- 重型项目输出本地部署指引，不进入沙箱。
- 导出 ZIP 可下载，解压后结构与源一致，且包含修复后的变更。
- **沙箱安全**：危险样例（如 `rm -rf /`、反弹 shell）被拒绝执行；沙箱具备网络隔离、资源限额、执行超时三项能力。
- 命令：`mvn -q -pl codewisdom-evaluation-export -am test`

## 阶段 9：量化评测

- 数据集 Schema 校验通过，四类样例（常规/缺陷/冲突/无文档）每类 ≥3 个。
- 指标可手算复现：召回率、误报率、架构识别准确率、规则匹配率、文档评分。
- 错误溯源能定位到具体流水线节点（仓库解析/静态解析/审计/修复/文档）。
- 报告输出 Markdown + JSON 双格式，可入库并与历史比对。
- 命令：`mvn -q -pl codewisdom-evaluation-export -Dtest='*Metric*,*Eval*,*Trace*' test`

## 阶段 10：Vue3 前端

- `npm run build` 成功，无 TS/ESLint 错误。
- 全流程可操作：导入 → 文件树 → 架构图 → 审计结果 → Diff → HITL → 导出 → 评测报告。
- 代码高亮正确；Mermaid 图可渲染可缩放。
- 审计结果点击可定位到源码对应行。
- HITL 操作可真实触发下一轮，不是纯前端演示。
- 命令：`npm run build && npm run dev`（手动走查全流程）

## 阶段 11：Docker 部署 ⏸️ 挂起

> 状态：**整体挂起** —— 用户于 2026-09-15 决定暂不安装 Docker Desktop。以下为恢复后的验收标准，保留备查。

- 5 个应用镜像 + 前端镜像构建成功。
- `docker compose up -d` 一条命令起全栈，全部服务 `healthy`。
- 前端经 Nginx 反代到网关，浏览器可完整跑通全流程。
- 日志格式统一，含 TraceId。
- 命令：`docker compose up -d && docker compose ps && docker compose logs --tail=50`

---

## 最终验收（项目阶段性完成）

对照 `CLAUDE.md` 第 11 节 / `docs/spec.md` 第 8 节，以下每条都需可演示：

- [ ] `mvn clean verify` 通过
- [ ] ⏸️ `docker compose up -d` 起 Nacos、MySQL、Redis、MinIO、RabbitMQ —— **挂起（等 Docker）**
- [ ] ⏸️ 网关路由正常 —— **挂起（等 Docker）**
- [ ] 导入 GitHub/Gitee 公开仓库
- [ ] ZIP 解压
- [ ] 生成项目文件树
- [ ] Tree-Sitter 解析类、方法、依赖
- [ ] 生成 Mermaid 架构图
- [ ] 输出风险分级审计结果
- [ ] 生成 README 和注释
- [ ] 展示 Diff
- [ ] ⏸️ 导出 ZIP —— **挂起（等 Docker，依赖 MinIO）**
- [ ] 生成评测报告
- [ ] 前端可操作全流程
- [ ] ⏸️ API 有 OpenAPI 文档 —— **1A 可先做单服务 `/v3/api-docs`，网关聚合挂起**

> 表述红线：所有 AI 分析结论对外一律表述为「**辅助分析结果，需人工确认**」，禁止表述为「完全自动保证准确」。
