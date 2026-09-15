# CLAUDE.md — CodeWisdom AI 项目工作规约

> 本文件是本仓库的最高优先级约定。任何任务开始前必须先读本文件。

## 0. 项目一句话

CodeWisdom AI：基于 Java 微服务 + 多智能体编排的全链路代码智能治理与多维度迭代平台。

## 1. 工作原则（硬性）

1. 一次只执行一个任务卡，禁止一次性生成整个项目。
2. 每次工作前，先读取：`CLAUDE.md`、`docs/spec.md`、`docs/architecture.md`、`docs/tasks.md`、`docs/progress.md`、`docs/acceptance.md`、`docs/tech-spike.md`。文件不存在则先创建。
3. 先输出计划，不超过 10 行。**非首次任务必须等用户回复「执行」后再写代码。**
4. 单次回复新增/修改文件不超过 5 个（用户任务卡显式列举的除外）。
5. 禁止编造依赖、版本、SDK、API。新增依赖必须给出：Maven 坐标 / 推荐版本 / 用途 / 替代方案，且版本必须经 Maven Central 实际检索确认。
6. 不读无关文件。用 `rg`、`find`、`git diff` 定位，禁止无脑读取整个仓库。
7. 所有代码必须可编译、可测试。完成后运行任务卡指定的测试命令。
8. 只改任务指定文件，禁止重构无关代码。
9. 不确定先问，不要猜。
10. 默认不执行用户代码，沙箱必须隔离，安全优先。

## 2. 输出格式（每次回复必须遵守）

1. **任务理解**：不超过 5 行。
2. **执行计划**：不超过 10 行。
3. **修改文件列表**。
4. **关键 diff**：不超过 100 行。
5. **测试命令与结果**。
6. **风险与下一步**。

## 3. 节省 Token 规则

- 不复述已知需求。
- 不贴完整文件，除非是新建文件。
- 优先输出 diff、摘要、命令。
- 长文件先摘要到 `docs/summaries/`。
- 不输出与当前任务无关的解释。

## 4. 禁止事项

- 禁止一次性生成整个项目。
- 禁止编造 Maven 坐标、API、SDK。
- 禁止修改任务外文件。
- 禁止重构无关代码。
- 禁止默认执行用户代码。
- 禁止跳过测试。
- 禁止输出大段无关解释。
- 禁止把「辅助分析」说成「完全自动保证准确」。
- 禁止在未验证前承诺 LangGraph Java / LangHarness Java 一定可用。

## 5. 技术基线（已检索确认，详见 docs/tech-spike.md）

- JDK 17、Spring Boot 3.5.x、Spring Cloud 2025.0.x、Spring Cloud Alibaba 2025.0.0.0。
- 智能体编排：首选 **LangGraph4j 1.8.x（LTS）**，全 Java；备选方案 A（Java + Python LangGraph 服务）。
- 评测：**LangHarness 无 Java SDK，按不存在处理**，采用自研轻量评测模块。
- 解析：**io.github.bonede:tree-sitter**（含 x86_64-windows 原生库，无需外部安装）。

## 6. 任务卡模板（用户按此下发）

```text
先读 CLAUDE.md、docs/progress.md、docs/tasks.md。
当前任务：T-xxx 任务名。

任务卡：
目标：
涉及模块：
只允许修改：
输入：
输出：
验收：
测试命令：
禁止：

先给计划，不要写代码。
```

## 7. 文档索引

| 文件 | 用途 |
|---|---|
| `docs/spec.md` | 规范版需求（已有，第 9 节为技术验证结论） |
| `docs/architecture.md` | 微服务拆分、模块职责、MVP 范围 |
| `docs/tasks.md` | 分阶段任务清单（每卡 ≤2h，含验收与测试命令） |
| `docs/progress.md` | 进度跟踪 |
| `docs/acceptance.md` | 各阶段验收标准 |
| `docs/tech-spike.md` | 技术验证清单、方法、结论、风险、替代方案 |
| `docs/api/` | OpenAPI / 接口文档 |
| `docs/adr/` | 架构决策记录 |
| `docs/summaries/` | 长文件摘要 |
