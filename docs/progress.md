# 进度跟踪（progress）

> 每完成一张任务卡，更新对应行 + 「变更日志」。**未验证的技术项一律标 `需验证`，不得标记为已完成。**

## 0. 恢复指引（2026-09-15 收工）

**明日从这里开始**：

1. 读 `CLAUDE.md` → `docs/progress.md`（本节）→ `docs/tasks.md`（阶段 3）。
2. **下一张卡：T-304 —— 提取 import / 跨文件调用关系**
   （阶段 3 最后一张非挂起卡，做完阶段 3 即收尾；T-305 依赖 Redis，挂起）
3. 起手前先跑一次基线：`mvn clean verify`
   预期：**7 模块 SUCCESS，283 个测试，0 失败 0 跳过**
4. 工作模式：自主执行，每张卡完成后用中文详细提交信息 commit + `git push origin main`。
5. `github.com` 本机不可达（R-08），需要真实仓库验证时用 **Gitee**。

**当前 HEAD**：`a9359b9`（已推送，与 `origin/main` 一致）

**已解锁**：阶段 2 完成；阶段 3 的 T-301~T-303 完成，阶段 4 可随时开工
（Mermaid 生成能力已由 LangGraph4j 验证可行）。

**仍挂起（等 Docker）**：T-003、T-008、T-103~T-105、T-205、T-206、T-305、阶段 8 部分、阶段 11。
若想解除，最轻的路径是 **WSL2 + 容器引擎**（不装 Docker Desktop），可一次性解锁 8 张卡。

---

## 1. 总体进度

| 阶段 | 名称 | 状态 | 完成卡 / 总卡 |
|---|---|---|---|
| 阶段 0 | 技术验证与文档初始化 | ✅ **已完成** | 6 / 9（T-003、T-008 挂起） |
| 阶段 1A | 工程骨架（零中间件） | ✅ **已完成** | 3 / 3 |
| 阶段 1B | 中间件接入 | ⏸️ 挂起（等 Docker） | 0 / 3 |
| 阶段 2 | 项目多源导入 | ✅ **已完成**（T-205、T-206 挂起） | 4 / 6 |
| 阶段 3 | 代码解析服务 | 🟡 进行中 | 3 / 5（T-305 挂起） |
| 阶段 4 | 架构逆向 | ⬜ 未开始 | 0 / 4 |
| 阶段 5 | 缺陷与依赖审计 | ⬜ 未开始 | 0 / 5 |
| 阶段 6 | 文档注释生成 | ⬜ 未开始 | 0 / 4 |
| 阶段 7 | 修复建议 / Diff / HITL | ⬜ 未开始 | 0 / 4 |
| 阶段 8 | 运行判定与导出 | ⏸️ 部分挂起（等 Docker） | 0 / 4 |
| 阶段 9 | 量化评测 | ⬜ 未开始 | 0 / 4 |
| 阶段 10 | Vue3 前端 | ⬜ 未开始 | 0 / 7 |
| 阶段 11 | Docker 部署 | ⏸️ 挂起（等 Docker） | 0 / 4 |

状态图例：⬜ 未开始 · 🟡 进行中 · ✅ 已完成 · 🔴 阻塞 · ⏸️ 挂起

## 2. 当前状态

- **当前任务卡**：T-304（提取 import / 跨文件调用关系）
- **已完成**：T-000 文档初始化、T-001 `git init`、T-002 工程骨架、T-004~T-007 四项冒烟、
  T-101/T-102/T-106（阶段 1A）、T-201~T-204（阶段 2 全部非挂起卡）、
  T-301 解析器封装、T-302 类型声明抽取、**T-303 方法签名抽取**
- **构建基线**：`mvn clean verify` → 7 模块全绿，**283 个测试**，0 失败 0 跳过
- **T-202 实测证据**：真实导入 Gitee RuoYi → **624 文件 / 9.1MB / master / head 7995a83e**
- **T-203 安全证据**：6 种 Zip Slip 写法、3 类压缩炸弹、畸形条目名、非 ZIP 输入全部拦截，
  且恶意包**不产生项目记录**、**目标目录外无逃逸文件**
- **T-204 验收证据**：文件树节点数与压缩包内容逐条对齐（12 节点 = 6 文件 + 6 目录），
  统计口径与 `t_project` 冗余计数对账一致
- **持久化验证方案**：无 Docker 环境下用 **H2（MODE=MySQL）+ Flyway** 真实执行建表脚本（R-13 仍待 1B 闭环）
- **阻塞项**：
  1. ~~Docker~~ → 已接受为长期约束（R-04），🐳 卡片挂起。
  2. **本机 `github.com` 不可达**（R-08）—— 真实链路验证改用 Gitee 完成；
     GitHub 导入按同一套 JGit 通用能力实现，但标注「本机未验证」。
- **剩余未闭环验证**：S5（MinIO）、S6（Nacos）、S7（Docker），均因缺 Docker 挂起。

## 3. 冒烟验证矩阵

| 编号 | 验证项 | 状态 | 结论 / 证据 |
|---|---|---|---|
| S1 | 全套技术栈依赖共存 | ✅ **通过** | 零 `omitted for conflict`；证据 `docs/summaries/s1-dependency-tree.txt` |
| S2 | Tree-Sitter 解析 Java | ✅ **通过** | ABI core=15 / min=13 / grammar=14，兼容；类/方法/行号提取正确 |
| S3 | LangGraph4j 建图执行 | ✅ **通过** | 条件分支 + HITL 循环边跑通；执行序列 `__START__→audit→fix→hitl→fix→hitl→__END__` |
| S4 | JGit 拉取公开仓库 | ✅ **通过** | 克隆 Gitee RuoYi：706 文件 / branch=master / head=7995a83e |
| S5 | MinIO 连通 | ⏸️ 挂起 | 等 Docker |
| S6 | Nacos 注册发现 | ⏸️ 挂起 | 等 Docker |
| S7 | docker compose 起中间件 | ⏸️ 挂起 | 用户决定暂不安装 Docker Desktop |

> **阶段 3 与阶段 4+ 的关键路径阻塞项（S2、S3）已全部解除。**

## 4. 变更日志

| 日期 | 任务卡 | 变更摘要 | 涉及文件 |
|---|---|---|---|
| 2026-09-15 | T-000 | 初始化文档体系；依赖版本实测检索；建立技术基线 | `CLAUDE.md`、`docs/*.md` |
| 2026-09-15 | T-000 | 决策：暂不安装 Docker Desktop；阶段 1 拆 1A/1B；执行顺序重排为零中间件优先 | `docs/tasks.md`、`docs/progress.md`、`docs/acceptance.md`、`docs/tech-spike.md` |
| 2026-09-15 | T-001 | `git init` + `.gitignore`，建立 `git diff` 审查基线 | `.gitignore` |
| 2026-09-15 | T-002 | 父 pom + `codewisdom-common` + 5 个服务骨架；6 模块 `mvn clean verify` 通过 | `pom.xml`、`codewisdom-*/pom.xml`、各服务启动类与 `application.yml` |
| 2026-09-15 | T-101 | `R<T>` 统一响应体、`ErrorCode` 分段错误码、`BizException`、`GlobalExceptionHandler`（三态单测） | `codewisdom-common/**` |
| 2026-09-15 | T-102 | `TraceIdHolder` + `TraceIdFilter`（上游透传 / 自动生成 / 回写响应头 / MDC 清理） | `codewisdom-common/**` |
| 2026-09-15 | T-004 | S1 通过：全套技术栈依赖共存，零冲突 | `docs/summaries/s1-dependency-tree.txt` |
| 2026-09-15 | T-005 | S2 通过：Tree-Sitter 可用于 Java/Python 解析；**发现并修正字节偏移陷阱** | `codewisdom-code-analysis/**` |
| 2026-09-15 | T-006 | S3 通过：LangGraph4j 条件分支 + 循环边 + Mermaid 生成 | `codewisdom-agent-orchestration/**` |
| 2026-09-15 | T-007 | S4 通过：JGit 克隆 Gitee 公开仓库；**确认 github.com 不可达** | `codewisdom-project-resource/**` |
| 2026-09-15 | T-201 | 领域模型 `Project`/`ImportTask`/`FileNode` + 枚举 + Mapper；`V1__init_schema.sql`；审计字段自动填充 | `codewisdom-project-resource/**` |
| 2026-09-15 | T-201 | **无 Docker 持久化验证方案落地**：H2(MODE=MySQL) + Flyway 真实跑建表脚本，9 个测试全通过 | `application-test.yml`、`SchemaMigrationTest`、`PersistenceCrudTest` |
| 2026-09-15 | T-202 | Git 仓库导入：SSRF 防护校验器、路径剪枝、语言/分类识别、JGit 拉取、文件树落库、REST 接口 | `codewisdom-project-resource/**` |
| 2026-09-15 | T-202 | **真实导入 Gitee RuoYi 成功**：624 文件 / 9.1MB / master / head 7995a83e | `GitNetworkImportTest` |
| 2026-09-15 | T-202 | **修复 Windows 只读 pack 文件导致的两个故障**：重复导入失败、`mvn clean` 删不掉 target | `GitRepoFetcher`、`TestWorkspaces`、`application-test.yml` |
| 2026-09-15 | T-203 | ZIP 安全解压：Zip Slip 六种写法 + 三类压缩炸弹 + 畸形条目名防护；条目名预检前置到建项目之前 | `ZipExtractor`、`ImportService`、`ImportController` |
| 2026-09-15 | T-203 | **修复 JDK 行为缺口**：非 ZIP 输入被 `ZipInputStream` 静默当成空包 → 加魔数校验 | `ZipExtractor` |
| 2026-09-15 | T-204 | 文件树查询（整树 / 子树 / depth 限制）与分类统计；一次查询内存组装，避免 N+1 | `FileTreeService`、`FileTreeController`、`FileTreeNode`、`FileTreeStats` |
| 2026-09-15 | T-204 | **修复顺序依赖的测试**：`SchemaMigrationTest` 断言"表为空"，但 H2 内存库跨测试类共享，改为断言"表存在" | `SchemaMigrationTest` |
| 2026-09-15 | T-301 | 解析器封装：`LanguageRegistry`（grammar 缓存且永不 close）、`SourceParser`（TSParser 每次新建）、`ParseHandle`（文本提取统一走 UTF-8 字节切片） | `codewisdom-code-analysis/**` |
| 2026-09-15 | T-301 | 实测发现：tree-sitter-java 中接口方法与类方法同为 `method_declaration`，需靠父节点类型/body 字段区分 | `SourceParserTest` |

## 5. 已知风险台账

| ID | 风险 | 等级 | 缓解措施 | 状态 |
|---|---|---|---|---|
| R-01 | Tree-Sitter core ↔ grammar ABI 不兼容 | 高 | **S2 已证伪**：ABI 14 ∈ [13,15]，解析正确 | ✅ 已解除 |
| R-02 | LangGraph4j 与 Boot 3.5.x 依赖冲突 | 中 | **S3 已证伪**：依赖树零冲突，图正常执行 | ✅ 已解除 |
| R-03 | SCA 2025.0.0.0 兼容性缺官方声明 | 中 | **S1 已验证依赖共存**；运行时（Nacos 注册）待 S6 | 🟡 部分解除 |
| R-04 | 无 Docker，所有中间件链路无法验证 | 中 | 用户已接受暂缓；阶段 1B/11 挂起 | 已接受 |
| R-05 | 评测体系无现成 Java 框架，需自研 | 中 | 已定为自研轻量模块 | 已定策 |
| R-06 | 业务规则逆向准确率不可控 | 中 | 定位为辅助功能，输出必带「需人工确认」 | 已定策 |
| R-07 | 阶段 1 因缺中间件无法正式收尾 | 中 | 以「1A 完成」作为阶段 2 启动条件 | ✅ 已按此执行 |
| R-08 | **本机 `github.com` 不可达**，GitHub 导入链路无法验证 | 高 | S4 改用 Gitee 验证；实现时仓库源不硬编码；GitHub 支持按协议实现但标注「本机未验证」 | 🔴 开放 |
| R-09 | **Tree-Sitter 返回 UTF-8 字节偏移**，非字符偏移 | 高 | S2 已暴露；解析层统一以 `byte[]` 切片并显式 UTF-8 解码，禁止 `String.substring` | 🟡 已定策 |
| R-10 | 网络依赖测试在离线环境会红 | 低 | 网络测试打 `@Tag("network")` + `Assumptions` 优雅跳过；离线链路另有确定性测试覆盖 | ✅ 已定策 |
| R-11 | **MyBatis-Plus 审计字段静默不生效**：`strictUpdateFill` 只在字段为 null 时填充，实体从库里查出再改时 `updatedAt` 不变 | 中 | 改用 `setFieldValByName` 无条件覆盖；`createdAt` 加 `updateStrategy = FieldStrategy.NEVER`；已有测试断言锁定 | ✅ 已定策 |
| R-12 | **`LocalDateTime` 纳秒 vs 列 `DATETIME(3)` 毫秒**，导致内存值与库值不等 | 中 | 应用层统一 `truncatedTo(ChronoUnit.MILLIS)`；测试基准一律取库值而非内存值 | ✅ 已定策 |
| R-13 | **H2 只是 MySQL 的近似**，不能保证建表脚本在真实 MySQL 8 上同样通过 | 中 | 建表脚本避开 MySQL 专有语法（索引用独立 `CREATE INDEX`）；1B 阶段必须在真实 MySQL 上重跑同一脚本 | 🟡 待 1B 闭环 |
| R-14 | **Windows 上 Git pack 文件带只读属性**，导致 (a) 重复导入同一项目必失败、(b) `mvn clean` 删不掉 target | 高 | 删除前先清只读位（`setWritable(true)`）；测试工作区移出 `target/`，改用系统临时目录 + `@AfterAll` 清理 | ✅ 已定策并有回归测试 |
| R-15 | **Git 拉取放在同步 HTTP 请求里**——实测 18MB 仓库约 10 秒，大仓库会拖垮请求线程 | 中 | MVP 暂可接受；T-206（RabbitMQ）改为异步后接口改为返回任务 id | 🟡 待 T-206 |
| R-16 | **不可信压缩包**：Zip Slip 可覆盖服务器任意文件，Zip Bomb 可写满磁盘 | 高 | 六层校验 + 三重熔断 + 魔数校验；条目名预检前置，恶意包不产生业务记录；19 个安全测试锁定 | ✅ 已定策 |
| R-17 | **JDK `ZipInputStream` 对非 ZIP 输入不报错**，静默返回空条目流，导致"导入成功但 0 文件" | 中 | 解压前校验 `PK\x03\x04` 魔数 | ✅ 已定策并有回归测试 |
