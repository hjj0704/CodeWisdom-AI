# 进度跟踪（progress）

> 每完成一张任务卡，更新对应行 + 「变更日志」。**未验证的技术项一律标 `需验证`，不得标记为已完成。**

## 0. 恢复指引（2026-09-16 收工）

> **一页版清单见 [`docs/STATUS.md`](STATUS.md)** —— 完成度看板、逐卡状态、红线提醒。
> 本节是简版，两份内容一致，任选其一发给 Claude 即可。

**明日从这里开始**：

1. 读 `CLAUDE.md` → `docs/progress.md`（本节）→ `docs/tasks.md`（阶段 5）。
2. **下一张卡：T-601 —— LLM 客户端抽象层（可切换 Provider，含超时/重试/降级）**
   （阶段 5 已 5/5 收尾；另：阶段 8 的 T-801/T-802 不依赖中间件，可随时插入）
3. 起手前先跑一次基线：`mvn clean verify`
   预期：**7 模块 SUCCESS，417 个测试，0 失败 0 跳过**
   （若机器上没有 Node，`MermaidGenTest` 的 3 个官方解析器用例会**显式跳过**并打印启用办法，
   此时是 **414 通过 + 3 跳过**，不是失败。）
4. 工作模式：自主执行，每张卡完成后用中文详细提交信息 commit + `git push origin main`。
5. `github.com` 本机不可达（R-08），需要真实仓库验证时用 **Gitee**。

**最新提交**：`feat(code-analysis): 风险分级与审计汇总（T-505）`（与 `origin/main` 一致）

**已解锁**：**阶段 5 缺陷与依赖审计 5/5 全部完成**。四路审计（代码规则 / pom 冲突 /
requirements 冲突 / 架构隐患）统一产出 `AuditIssue`，汇总管线 `AuditPipeline` + `AuditReport`
完成分级与聚合。**`acceptance.md` 阶段 5 的六条验收标准没有一条涉及数据库，已全部满足。**

**入库的去向**：T-505 的「问题模型入库」按 2026-09-16 决策**移入 T-105**（挂起中）——
code-analysis 没有持久化层，半接数据源会让 T-105 统一接入时返工。
**在 T-105 完成前，不能说「审计结果已入库」。**

**Docker 已就绪（2026-09-16）**：Docker Desktop 29.8.0 + Compose v5.5.1 实测可用，
**此前挂起的 14 张卡全部解锁**。当前正在补 T-003：
`docker-compose.yml` 已写好，**minio / nacos / rabbitmq / redis 四个容器 healthy**，
mysql 待用户以管理员身份 `net stop mysql`（本机 MySQL 8.1 占着 3306）。

⚠️ **本机 Docker 的两个环境前提**（不在仓库里，已备份）：
① Docker Hub **直连不可达**，必须走镜像加速器（已配在 `~/.docker/daemon.json`）；
② Docker 会继承 Windows 系统代理，而系统代理指向未运行的 `127.0.0.1:7890`，
已把 Docker Desktop 改成直连。详见 `docs/tasks.md` T-003 小节。

---

## 1. 总体进度

| 阶段 | 名称 | 状态 | 完成卡 / 总卡 |
|---|---|---|---|
| 阶段 0 | 技术验证与文档初始化 | 🟡 进行中（Docker 已就绪，回来补卡） | 7 / 9（T-003 4/5、T-008 待做） |
| 阶段 1A | 工程骨架（零中间件） | ✅ **已完成** | 3 / 3 |
| 阶段 1B | 中间件接入 | ⏸️ 挂起（等 Docker） | 0 / 3 |
| 阶段 2 | 项目多源导入 | ✅ **已完成**（T-205、T-206 挂起） | 4 / 6 |
| 阶段 3 | 代码解析服务 | ✅ **已完成**（T-305 挂起） | 4 / 5 |
| 阶段 4 | 架构逆向 | ✅ **已完成** | 4 / 4 |
| 阶段 5 | 缺陷与依赖审计 | ✅ **已完成** | 5 / 5 |
| 阶段 6 | 文档注释生成 | ⬜ 未开始（下一张 T-601） | 0 / 4 |
| 阶段 7 | 修复建议 / Diff / HITL | ⬜ 未开始 | 0 / 4 |
| 阶段 8 | 运行判定与导出 | ⏸️ 部分挂起（等 Docker） | 0 / 4 |
| 阶段 9 | 量化评测 | ⬜ 未开始 | 0 / 4 |
| 阶段 10 | Vue3 前端 | ⬜ 未开始 | 0 / 7 |
| 阶段 11 | Docker 部署 | ⏸️ 挂起（等 Docker） | 0 / 4 |

状态图例：⬜ 未开始 · 🟡 进行中 · ✅ 已完成 · 🔴 阻塞 · ⏸️ 挂起

## 2. 当前状态

- **当前任务卡**：T-601（LLM 客户端抽象层）——阶段 6 首卡
- **已完成**：T-000 文档初始化、T-001 `git init`、T-002 工程骨架、T-004~T-007 四项冒烟、
  T-101/T-102/T-106（阶段 1A）、T-201~T-204（阶段 2 全部非挂起卡）、
  T-301 解析器封装、T-302 类型声明抽取、T-303 方法签名抽取、T-304 import 与跨文件调用关系、
  T-401 包结构与分层识别、T-402 Mermaid 架构图生成、T-403 技术栈识别、
  T-404 模块循环依赖检测、T-501 审计规则引擎与规则注册机制、
  T-502 pom 依赖冲突检测、T-503 requirements 依赖冲突检测、T-504 架构隐患规则、
  **T-505 风险分级与审计汇总**
- **构建基线**：`mvn clean verify` → 7 模块全绿，**417 个测试**，0 失败 0 跳过
  （各模块：common 12 / gateway 1 / project-resource 210 / code-analysis 189 /
  agent-orchestration 4 / evaluation-export 1）
  ⚠️ 无 Node 的机器上 `MermaidGenTest` 的 3 个官方解析器用例会显式跳过 → 414 通过 + 3 跳过
- **T-505 已完成部分**：`AuditReport`（恒定三档分级、空报告不给等级）+ `AuditPipeline`
  （去重 + 等级冲突取高不取低）+ 10 个用例；端到端实测摘要
  `共 4 条审计问题：高危 1 / 中危 2 / 低危 1，涉及 1 个文件`
- **T-505 入库的去向**：按 2026-09-16 决策**移入 T-105**（挂起中）。原因：code-analysis 没有
  持久化层，半接数据源会让 T-105 统一接入时返工。**T-105 完成前不能说「审计结果已入库」**
- **阶段 5 完成**：`acceptance.md` 阶段 5 六条验收标准**没有一条涉及数据库**，已全部满足
- **T-504 验收证据**：14 个用例。样例一次埋三类隐患，共检出 6 条；
  **打通断言**：`CW-ARCH-001` 的环路径必须与 `CycleDetector` 直接算出的**一致**（不是另算一个环）；
  所有问题都锚在真实文件与行号上；上帝类检查排除 DTO/实体层（负向用例锁定）
- **T-504 口径**：全部基于**静态结构**；阈值（20 个类型、15 个依赖方）是**经验值**，
  措辞是「建议关注」不是「违反规范」
- **T-503 验收证据**：12 个用例。①同文件钉两个版本**高危**；②跨文件不一致**中危**且断言描述
  不含「冲突」二字；③同约束重复低危；④`django=4.2` 单等号判无效；⑤**三条负向断言**：
  带环境标记的不参与判定、区间约束不参与、选项与注释行跳过
- **T-503 口径**：只比对 `==` 钉死的精确版本（区间约束之间能否同时满足需要区间求解）；
  不跟随 `-r`/`-c` 包含的文件；不解析 `constraints.txt`
- **T-502 验收证据**：9 个用例。5 模块样例（parent + 3 个问题模块 + 1 个干净模块）：
  同一 pom 多版本判**高危**、跨模块版本不一致判**中危且措辞不含「冲突」**、同 pom 重复声明判低危、
  坐标无效报两条；冲突路径给出每一处声明的 `文件:行号`；负向断言干净模块零产出
- **T-502 口径**：检测的是「**声明级**」冲突，**不解析传递依赖**（最常见的 Maven 冲突检不出来，
  需要真正的依赖解析器），也不看 `dependencyManagement`/`exclusions`/`profiles`
- **T-501 验收证据**：12 个用例。样例同时埋四类缺陷逐处断言检出；
  **标注样例集高危漏报率 0.0%**（2 处高危、0 漏报）；可插拔验证（自定义规则无需改引擎即生效）；
  不误报验证（被调用的私有方法、`@PostConstruct` 回调、常量字段都不算死代码）
- **阶段 5 门禁实测**：`-Dtest='*Audit*,*Conflict*,*Rule*,*Arch*'` → **57 个测试全绿**
  （`RuleEngineTest` 12 + `PomConflictTest` 9 + `ReqConflictTest` 12 + `ArchRiskTest` 14 +
  `AuditPipelineTest` 10）——阶段 5 五个测试类全覆盖
- **门禁漏卡修复**：原命令 `*Audit*,*Conflict*,*Rule*` **不匹配 `ArchRiskTest`**——
  「架构隐患」这张卡不在自己的阶段门禁里（与阶段 4 的 `*Arch*` 空集同类），已补 `*Arch*`
- **阶段 4 门禁实测**：`-Dtest='*Layer*,*Mermaid*,*Tech*,*Cycle*'` → **52 个测试全绿**
  （16 + 11 + 14 + 11），上一轮修正过的门禁命令确实能跑通，不再空集
- **T-404 验收证据**：11 个用例；三包环路径逐字符断言、包环与层环同时报出、
  **负向断言**链式依赖不报环、同包互调不报环（并断言该样例确实有 2 条调用边）
- **T-403 验收证据**：14 个用例；Java + Vue + Python 三栈样例工程
  **命中 21 项 / 未识别 3 项**，双向断言（多认少认都要红）。
  ⚠️ **口径**：样例自建，不等于真实工程准确；且识别的是「**声明或引用**了这项技术」，
  **不是**「这项技术在运行」
- **T-402 验收证据**：11 个用例；**用 `mermaid@11.6.0` 官方解析器真校验**（不是字符串自查），
  且带负向用例断言校验器会拒绝坏图。⚠️ **口径**：只验到**解析**，验不到**渲染出 SVG**
  （jsdom 无布局引擎）。只能说「图能被官方解析器解析」，不能说「图能渲染」
- **T-401 验收证据**：自建样例集 32 个类型准确率 **100%（32/32）**。
  ⚠️ **口径**：样例集是本卡自建的，只证明规则内部自洽，**不构成「真实工程识别准确」的结论**。
- **T-304 验收证据**：7 文件样例工程（`CallGraphTest`，24 个用例）精确断言
  12 条内部边 + 2 条外部边；递归自调用、`this(...)` 委派、同类内部互调
  三类自环噪音在方法级与类型级分别滤掉；按需导入歧义判未解析；继承来的方法挂到真正声明的父类型
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
| S7 | docker compose 起中间件 | 🟡 进行中 | Docker 已装（29.8.0）；**4/5 容器 healthy**（minio/nacos/rabbitmq/redis），mysql 待停本机服务 |

> **阶段 3 与阶段 4+ 的关键路径阻塞项（S2、S3）已全部解除。**
> **S7 于 2026-09-16 解冻**（Docker 就绪），正在补 T-003。

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
| 2026-09-15 | T-302 | 类型声明抽取：五种类型、多层嵌套限定名、修饰符、注解、继承关系 | `JavaStructureExtractor`、`JavaStructureExtractorTest` |
| 2026-09-15 | T-303 | 方法签名抽取：构造器 / 泛型 / 可变参数 / 抽象方法 / `throws` / 参数注解 | `JavaStructureExtractor`、`JavaMethodExtractorTest` |
| 2026-09-16 | T-304 | import 抽取（普通 / 静态 / 通配符）+ 调用点抽取（方法调用 / 构造 / 构造器委派 / 方法引用），接收者类型近似推断 | `ImportDeclaration`、`CallSite`、`JavaDependencyExtractor` |
| 2026-09-16 | T-304 | 跨文件调用图：按 JLS §6.5.5.1 解析简单名，按 JLS §6.5.6.1 处理无接收者调用，继承链上定位方法真正声明处 | `CallGraph` |
| 2026-09-16 | T-304 | **自环过滤**：方法级丢弃 `fromId == toId`（递归、`this(...)` 委派），类型级再投影丢弃同类互调 | `CallGraph`、`CallGraphTest` |
| 2026-09-16 | T-304 | 定策「找不到就断边，绝不猜」：歧义按需导入、链式接收者、工程内不存在的方法一律不产边 | `CallGraph`、`docs/tech-spike.md` F-16 |
| 2026-09-16 | T-304 | **修正文档基线数字**：实测基线为 284 个测试，此前文档记的 283 少算 1 个 | `docs/progress.md`、`docs/STATUS.md` |
| 2026-09-16 | T-401 | 分层识别：`LayerKind` 三张映射表（包名段 / 注解 / 类型名后缀）+ `LayerAssignment` 四信号分离 + `LayerReport` 包结构与分层视图 | `LayerKind`、`LayerAssignment`、`LayerReport`、`LayerDetector` |
| 2026-09-16 | T-401 | 定策优先级 **路径 → 注解 → 包名 → 类型名**；包名从最内层往外找；`TEST` 不算角色信号，不参与冲突判定 | `LayerAssignment` |
| 2026-09-16 | T-401 | **修复路径标记写死在 `/src/test/`**：相对路径永远匹配不上且**静默**失败，测试类被混进分层统计 | `LayerDetector`、`LayerDetectTest` |
| 2026-09-16 | T-401 | **更正 `docs/acceptance.md` 阶段 4 门禁命令**：原 `*Arch*` 与四张卡的实际测试类名都对不上，会跑成空集 | `docs/acceptance.md`、`docs/tasks.md` |
| 2026-09-16 | T-402 | Mermaid 生成器：分层架构图 + 包依赖拓扑图，从 `LayerReport` 与 `CallGraph.typeEdges()` 推导；同层/同包调用不画 | `MermaidGenerator` |
| 2026-09-16 | T-402 | **官方解析器真校验接入构建**：`tools/mermaid-verify/`（mermaid 11.6.0 + jsdom），stdin 进图、退出码出结果，仅测试工具不参与 Java 构建 | `tools/mermaid-verify/package.json`、`verify.mjs` |
| 2026-09-16 | T-402 | 实测标签转义只有两条：`"` 破坏引号标签边界、`\` 是转义符会吞字符（`a/b\c` → `a/bc`，**静默内容丢失**）；其余字符含中文原样可用 | `MermaidGenerator`、`docs/tech-spike.md` F-17 |
| 2026-09-16 | T-402 | 定界：**官方解析器能离线验、真实渲染不能**（`mermaid.render` 需 `getBBox`，jsdom 无布局引擎）。表述统一收敛为「能被解析」，禁止说「能渲染」 | `docs/tech-spike.md` F-17、`docs/tasks.md` |
| 2026-09-16 | 文档更正 | **核对看板数字**：挂起卡实为 **14 张**（原写 16；表里逐条只有 10 行 + `T-1101~1104` 四张）；阶段 0 实为 **7/9**（T-002 按 `tasks.md` 属阶段 0，原被算进 1A）；STATUS 头部「最新提交」停在 T-304 未随卡更新 | `docs/STATUS.md`、`docs/progress.md` |
| 2026-09-16 | T-403 | 技术栈目录（枚举 51 项、8 个分类）+ 识别结果模型（**证据是一等公民**）+ 报告（按分类分组、`unrecognized()` 显式暴露没认出的坐标） | `TechStack`、`TechStackItem`、`TechStackReport` |
| 2026-09-16 | T-403 | 识别器：三路证据（import 前缀 / 注解名 / 依赖文件坐标）；pom 的 `${属性}` 版本回查 `<properties>`，回查不到留空 | `TechStackDetector` |
| 2026-09-16 | T-403 | **修复 MyBatis 前缀写漏**：核心包是 `org.apache.ibatis` 不是 `org.mybatis`，只写后者会静默漏掉大半个真实项目 | `TechStackDetector`、`TechStackTest` |
| 2026-09-16 | T-403 | 定策「不猜」：`@Mapper` 在 MyBatis 与 MapStruct 里同名，**不进注解规则表**，只认 import 与依赖坐标；`package.json` 只在 dependencies 块内取值，避免元数据键灌进未识别清单 | `TechStackDetector` |
| 2026-09-16 | T-404 | 循环依赖检测：**Tarjan 强连通分量**一次求出全部环（不走「逐节点 DFS」的指数路线），再在分量内 BFS 取**最短环路径**；支持包级与分层级两个粒度 | `DependencyCycle`、`CycleReport`、`CycleDetector` |
| 2026-09-16 | T-404 | 环路径首尾同一模块并校验（非法路径构造直接抛异常）；模块内调用不是环，同层不同包互调在层图是自环、包图是真环，两条对照都有用例 | `DependencyCycle`、`CycleDetectTest` |
| 2026-09-16 | T-501 | **统一问题模型** `AuditIssue`：代码缺陷/依赖冲突/架构隐患收敛成同一个 DTO；六个必填字段（文件、行号、描述、风险说明、触发场景、风险等级）由构造函数强制校验，缺一个就造不出对象 | `AuditIssue` |
| 2026-09-16 | T-501 | **可插拔规则引擎**：`AuditRule` 接口 + `AuditEngine` 通过 `List<AuditRule>` 构造注入全部实现，引擎不认识任何具体规则；加规则不改引擎不改注册表 | `AuditRule`、`AuditEngine` |
| 2026-09-16 | T-501 | 四条内置规则：空指针（字面量应在 equals 左侧）、空 catch 吞异常、硬编码凭据/连接串、私有方法无调用点 | `arch/rules/*` |
| 2026-09-16 | T-501 | **修复 `block` 子节点含花括号**：`{}` 的 `getChildCount()` 是 2 不是 0，判 0 会让空 catch 规则**永远不命中且不报错**（与 T-303 的 `formal_parameters` 同类，第二次） | `ExceptionHandlingRule`、`RuleEngineTest` |
| 2026-09-16 | T-501 | **修复 JDBC 子协议**：`jdbc:mysql://host/db` 的子协议夹在 `jdbc` 与 `://` 之间，正则写成 `(jdbc\|...)://` 会让 `jdbc:*` 分支永远匹配不上 | `HardcodedValueRule`、`RuleEngineTest` |
| 2026-09-16 | T-502 | 依赖声明模型 `DependencyCoordinate`（带**文件:行号**）+ pom 解析抽成公共静态读取器 `PomDependencyReader`，**技术栈识别与冲突检测共用一份**（两份必须同步的解析代码会漂移出互相矛盾的结论） | `DependencyCoordinate`、`PomDependencyReader` |
| 2026-09-16 | T-502 | 冲突检测器 `PomConflictAnalyzer`：同一 pom 多版本**高危** / 跨模块版本不一致**中危**（措辞与等级都和真冲突分开）/ 同 pom 重复声明低危 / 坐标无效中危；产出统一走 `AuditIssue` | `PomConflictAnalyzer` |
| 2026-09-16 | T-503 | Python 依赖声明模型 `PythonRequirement`（PEP 503 归一化 + **环境标记** + 文件:行号）+ requirements 解析抽成公共静态读取器 | `PythonRequirement`、`RequirementsReader` |
| 2026-09-16 | T-503 | 冲突检测器 `RequirementsConflictAnalyzer`：同文件钉两版本**高危** / 跨文件不一致**中危** / 同约束重复低危 / **单等号 `=` 判无效**（pip 会拒绝安装） | `RequirementsConflictAnalyzer` |
| 2026-09-16 | T-503 | 定策：**带环境标记的声明一律不参与冲突判定**——不同标记下写不同版本是 pip 标准写法，标记是否互斥需表达式求解，不判就不会误报；只比对 `==` 钉死版本 | `RequirementsConflictAnalyzer`、`ReqConflictTest` |
| 2026-09-16 | T-504 | 架构隐患分析器 `ArchRiskAnalyzer`：**把阶段 4 的三份报告翻译成 `AuditIssue`**，不重新解析源码；环检测复用 T-404 的 `CycleDetector` | `ArchRiskAnalyzer` |
| 2026-09-16 | T-504 | 三类隐患共六条规则：`CW-ARCH-001` 循环依赖（分层环高危/包环中危）、`CW-ARCH-002` 分层混乱（类级中危/包级低危）、`CW-ARCH-003` 职责不单一（包过重中危/疑似上帝类低危） | `ArchRiskAnalyzer` |
| 2026-09-16 | T-504 | **解决「架构隐患没有行号」**：让分析器接收「文件 → 类型声明」建索引，把模块级结论锚回具体类，而不是敷衍填 `NO_LINE` | `ArchRiskAnalyzer` |
| 2026-09-16 | T-505 | 审计报告模型 `AuditReport`：**恒定三档分级**（「高危 0」必须可区分于「没统计高危」）、空报告不给自己安等级、按规则/文件聚合、稳定排序 | `AuditReport` |
| 2026-09-16 | T-505 | 汇总管线 `AuditPipeline`：四路产出只做「去重 → 分级 → 排序」，**不重新实现各分析器已做过的校验与排序**；去重键刻意不含等级，等级冲突时**取高不取低** | `AuditPipeline`、`AuditPipelineTest` |
| 2026-09-16 | T-505 | **决策：入库移入 T-105**。code-analysis 无持久化层（无 MyBatis-Plus / H2 / Flyway、无数据源、无 entity/mapper），而「各服务接入 MySQL」正是 T-105 的职责；半接数据源会让 T-105 统一接入时返工 | `docs/tasks.md`、`docs/STATUS.md`、`docs/progress.md` |
| 2026-09-16 | T-003 | **Docker 就绪**：Docker Desktop 29.8.0 + Compose v5.5.1 实测可用；配镜像加速器（Docker Hub 直连不通）、改 Docker 为直连（系统代理指向未运行的 7890）；`docker-compose.yml` 写好并通过 config 校验 | `docker-compose.yml`、`~/.docker/daemon.json` |
| 2026-09-16 | T-003 | **4/5 容器 healthy**（minio/nacos/rabbitmq/redis）；踩到 Nacos 3.x 三个坑：强制鉴权令牌、就绪探针 v1 端点返回 410、控制台移到容器内 8080（避开网关的 8080） | `docker-compose.yml` |
| 2026-09-16 | T-505 | **阶段 5 收尾**：`acceptance.md` 阶段 5 六条验收标准**无一条涉及数据库**，已全部满足；门禁 57 个测试全绿 | `docs/tasks.md` |
| 2026-09-16 | T-504 | **修复阶段 5 门禁漏卡**：原命令 `*Audit*,*Conflict*,*Rule*` 不匹配 `ArchRiskTest`，「架构隐患」这张卡不在自己的阶段门禁里。与阶段 4 的 `*Arch*` 空集同类，已补 `*Arch*` | `docs/acceptance.md` |
| 2026-09-16 | T-503 | **重构 `TechStackDetector` 复用 requirements 读取器**（与 T-502 的 pom 侧同理），T-403 的 14 个用例保持绿 | `TechStackDetector` |
| 2026-09-16 | T-502 | **重构 `TechStackDetector` 复用读取器**：删除重复的 pom 解析（含属性回查与块定位），T-403 的 14 个用例全部保持绿 | `TechStackDetector` |
| 2026-09-16 | T-502 | 定策：重复声明在已有版本冲突时**不再单独报**——高危条目已说明「声明了多个版本」，再补低危只是同一处刷两条 | `PomConflictAnalyzer` |
| 2026-09-16 | T-404 | **阶段 4 收尾**：门禁命令 `-Dtest='*Layer*,*Mermaid*,*Tech*,*Cycle*'` 实测 **52 个测试全绿**，确认上一轮修正过的门禁不再跑空集 | `docs/tasks.md`、`docs/acceptance.md` |

## 5. 已知风险台账

| ID | 风险 | 等级 | 缓解措施 | 状态 |
|---|---|---|---|---|
| R-01 | Tree-Sitter core ↔ grammar ABI 不兼容 | 高 | **S2 已证伪**：ABI 14 ∈ [13,15]，解析正确 | ✅ 已解除 |
| R-02 | LangGraph4j 与 Boot 3.5.x 依赖冲突 | 中 | **S3 已证伪**：依赖树零冲突，图正常执行 | ✅ 已解除 |
| R-03 | SCA 2025.0.0.0 兼容性缺官方声明 | 中 | **S1 已验证依赖共存**；运行时（Nacos 注册）待 S6 | 🟡 部分解除 |
| R-04 | ~~无 Docker，所有中间件链路无法验证~~ | ~~中~~ | **已解除**：2026-09-16 Docker Desktop 29.8.0 安装并实测可用；14 张挂起卡解锁，正在补 T-003。**但注意**：Docker Hub 本机直连不可达，必须走镜像加速器（已配）；且本机 MySQL 占用 3306 需先停 | ✅ 已解除 |
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
| R-18 | **调用图的解析是近似**：方法节点不带参数（同名重载合并为一）、变量类型表不区分作用域、`super.xxx()` 未解析、泛型与反射调用不可见 | 中 | 一律**宁缺勿假**：解析不到就不产边并记入 `unresolvedCallSites()` 备查；输出场景统一标注「辅助分析结果，需人工确认」 | 🟡 已定策 |
| R-19 | **分层识别是启发式，不是语义分析**：命名自由的项目里必然有认不出的类；「准确率 100%」只是 T-401 自建样例集上的结果，**不可外推** | 中 | ①注解 / 包名 / 类型名三个信号全部保留，冲突可查（`LayerReport.conflicts()`）；②`model`/`pojo`/`common` 等歧义段不建映射，认不出就给 `UNKNOWN` 并由 `unclassified()` 显式列出；③对外表述统一为「辅助分析结果，需人工确认」，禁止说「自动保证准确」 | 🟡 已定策 |
| R-20 | **Mermaid 只验到解析、验不到渲染**；且验证依赖 Node + `tools/mermaid-verify` 的 npm 依赖，未安装时用例**显式跳过**（不是静默通过，也不是失败） | 中 | ①表述统一为「图能被官方解析器解析」，**禁止说「图能渲染」**；②跳过时打印启用命令；③工作区已装好，本机实测通过；④真实渲染留到阶段 10 用真实浏览器验 | 🟡 已定策 |
| R-21 | **技术栈识别是「声明级」不是「运行级」**：依赖引了不用、import 了没跑都会被识别出来；目录外的技术一律认不出 | 中 | ①表述统一为「工程里**声明或引用**了这项技术」，**禁止说「项目在用 X」**；②`unrecognized()` 把没认出的依赖坐标原样列出，不静默丢弃；③每条结果强制带证据，空证据有测试断言；④输出统一标注「辅助分析结果，需人工确认」 | 🟡 已定策 |
| R-22 | **循环依赖检出的是「静态调用关系构成的环」不是运行时依赖环**：反射、事件总线、配置注入产生的运行期环检不出；只在死代码里存在的调用反而会被算成环 | 中 | ①表述统一为「静态调用关系构成的环」；②层级与包级两个粒度都报，且说明各自含义（包环=哪两个包要拆，层环=架构分层倒了）；③只列**最短**代表路径，不声称穷举了该分量内的所有环 | 🟡 已定策 |
| R-27 | **审计结果未入库**：`AuditIssue` 模型与报告已就绪，但落库需要 code-analysis 先有持久化层，而这是 T-105（挂起中）的职责 | 中 | ①**表述纪律：T-105 完成前不能说「审计结果已入库」**，只能说「审计模型与报告已就绪，落库待 T-105」；②所需的表结构、entity、mapper 已写进 T-105 的卡片说明，不会丢 | 🟡 已定策 |
| R-26 | **架构隐患的阈值是经验值**：包内 20 个类型、单类 15 个依赖方，都没有理论依据；包级混杂在真实工程里（`common` 包混放 config 与 util）会偏噪 | 中 | ①措辞统一为「建议关注/建议拆分」，**不说「违反规范」**；②上帝类检查**排除 DTO/实体/常量/工具/异常层**（本应被广泛引用），并有负向用例锁定；③阈值写成具名常量并注明「经验值、提示阈值」；④输出统一标注「辅助分析结果，需人工确认」 | 🟡 已定策 |
| R-25 | **Python 依赖冲突的判定面比 Maven 更窄**：只比对 `==` 钉死的精确版本，区间约束（`>=`/`~=`）之间是否相容**不判**；带环境标记的声明整体排除 | 中 | ①取舍依据写进类注释：requirements.txt 的主流形态就是全钉版本，钉版本之间的冲突正是最常见也最该报的；②`==1.0rc1` 这类预发布后缀也不算精确版本（版本比较规则未实现）；③三条范围外事项（不跟随 `-r`/`-c`、不解析 `constraints.txt`、不解析 extras 的传递依赖）在类注释里逐条列明 | 🟡 已定策 |
| R-24 | **依赖冲突只检「声明级」，不检传递依赖**：A→B→C-1.0 而工程声明 C-2.0 这种最常见的 Maven 冲突**检不出来**；也不看 `dependencyManagement`/`exclusions`/`profiles` | 中 | ①表述统一为「**声明**层面存在冲突」，**禁止说「运行时一定会冲突」**；②跨模块版本不一致只报「声明不一致」并在风险说明里明写「本工具判不出是否共享 classpath」；③`PomConflictAnalyzer` 的类注释完整列出四条范围外事项 | 🟡 已定策 |
| R-23 | **审计规则是启发式的，必然有误报漏报**：不排除规则本身写错（T-501 就两条规则「永远不命中且不报错」）；死代码规则会误报反射调用与仅在测试中调用的私有方法；`variable.equals("字面量")` 在变量确定非空时是假阳性 | 中 | ①每条问题**必须带触发场景与风险说明**（构造校验强制），让人能自己判断；②定向断言「**标注的那几处**必须被检出」，而不是「检出了问题」——后者一个永远返回空的实现也能过；③「漏报率」只在标注样例集上算，不外推；④死代码规则显式排除框架回调并写明误报范围；⑤输出统一标注「辅助分析结果，需人工确认」 | 🟡 已定策 |
