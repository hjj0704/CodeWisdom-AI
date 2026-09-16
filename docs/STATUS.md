# CodeWisdom AI — 进度清单（STATUS）

> **用途**：一页看清「哪里做完了、哪里没做完」。明天开工把这份文件发给我即可，无需回溯上下文。
> **更新于**：2026-09-16 收工
> **最新提交**：`feat(code-analysis): import 抽取与跨文件调用图（T-304）`，与 `origin/main` 一致
> **仓库**：https://gitee.com/han-jiajiemm/CodeWisdom.git
>
> （这里刻意不写 commit 短哈希：写哈希就得在同一提交里改，哈希随之变化，永远对不上。
> 核对版本用 `git log -1 --oneline`。）

---

## 一、一句话状态

**阶段 0 / 1A / 2 / 3 已完成（阶段 3 的 T-305 因 Redis 挂起）；阶段 4 完成 2/4。**
`mvn clean verify` → **7 模块全绿，335 个测试，0 失败 0 跳过**。

| 快速数字 | 值 |
|---|---|
| 完成任务卡 | **20 / 62**（另有 16 张因缺 Docker 挂起） |
| 测试 | **335 个，0 失败 0 跳过**（common 12 / gateway 1 / project-resource 210 / code-analysis 107 / agent-orchestration 4 / evaluation-export 1） |
| 主代码 | 62 个 Java 文件 / 5890 行 |
| 测试代码 | 31 个 Java 文件 / 5873 行（测试比主代码还多） |
| 提交 | 13 个，全部已推送 |
| 文档 | 8 份（`CLAUDE.md` + `docs/*.md`）+ 1 个测试工具（`tools/mermaid-verify/`） |

> ⚠️ **基线数字更正**：此前文档记的基线是 283，长期实测为 **284**（少算 1 个）。
> T-304 +24 → 308；T-401 +16 → 324；T-402 +11 → **335**。
>
> ⚠️ **无 Node 的机器**：`MermaidGenTest` 的 3 个官方解析器用例会**显式跳过**并打印启用命令，
> 此时是 **332 通过 + 3 跳过**，不是失败。启用：`cd tools/mermaid-verify && npm install`。

---

## 二、阶段看板

| 阶段 | 名称 | 状态 | 完成卡 |
|---|---|---|---|
| 0 | 技术验证与文档初始化 | ✅ 完成 | 6 / 9（2 张挂起） |
| 1A | 工程骨架（零中间件） | ✅ 完成 | 3 / 3 |
| 1B | 中间件接入 | ⏸️ 挂起 | 0 / 3 |
| 2 | 项目多源导入 | ✅ 完成 | 4 / 6（2 张挂起） |
| 3 | 代码解析服务 | ✅ 完成（T-305 挂起） | 4 / 5 |
| 4 | 架构逆向 | 🟡 **进行中** | 2 / 4 |
| 5 | 缺陷与依赖审计 | ⬜ 未开始 | 0 / 5 |
| 6 | 文档注释生成 | ⬜ 未开始 | 0 / 4 |
| 7 | 修复建议 / Diff / HITL | ⬜ 未开始 | 0 / 4 |
| 8 | 运行判定与导出 | 🟡 2 张可做 / 2 张挂起 | 0 / 4 |
| 9 | 量化评测 | ⬜ 未开始 | 0 / 4 |
| 10 | Vue3 前端 | ⬜ 未开始 | 0 / 7 |
| 11 | Docker 部署 | ⏸️ 挂起 | 0 / 4 |

图例：✅ 完成 ｜ 🟡 进行中 ｜ ⏸️ 挂起（等 Docker） ｜ ⬜ 未开始

---

## 三、任务卡逐条清单

### ✅ 已完成（20 张）

| 卡号 | 任务 | 关键证据 |
|---|---|---|
| T-000 | 文档初始化 | 7 份文档 + `docs/{api,summaries,adr}/` |
| T-001 | `git init` + `.gitignore` | 仓库基线 |
| T-002 | 父 pom + common + 5 服务骨架 | 7 模块编译通过 |
| T-101 | 统一响应体 / 分段错误码 / 全局异常处理 | 三态单测（成功 / 业务异常 / 未知异常） |
| T-102 | TraceId 透传 | 上游复用 / 自动生成 / 回写响应头 / MDC 清理 四项单测 |
| T-106 | 各服务配置 + 网关 4 条静态路由 | 无中间件即可启动 |
| T-004 | **S1** 全套技术栈依赖共存 | 零 `omitted for conflict`，证据 `docs/summaries/s1-dependency-tree.txt` |
| T-005 | **S2** Tree-Sitter 解析 Java | ABI 兼容（core 15 / min 13 / grammar 14） |
| T-006 | **S3** LangGraph4j 条件分支 + 循环边 | 执行序列 `__START__→audit→fix→hitl→fix→hitl→__END__` |
| T-007 | **S4** JGit 拉取公开仓库 | 克隆 Gitee 仓库成功 |
| T-201 | 领域模型 + 建表脚本 + Mapper | H2 真实跑 Flyway 脚本，9 测试 |
| T-202 | Git 仓库导入 + SSRF 防护 | **真实导入 Gitee RuoYi：624 文件 / 9.1MB / master / head 7995a83e** |
| T-203 | ZIP 安全解压 | 6 种 Zip Slip + 3 类压缩炸弹 + 畸形名全拦截，19 个安全测试 |
| T-204 | 文件树接口 + 分类统计 | 节点数与磁盘逐条对齐（12 节点 = 6 文件 + 6 目录） |
| T-301 | 解析器封装 + 语言注册表 | grammar 缓存 + parser 生命周期互不干扰 |
| T-302 | 类型声明抽取 | 五种类型、多层嵌套限定名、修饰符、注解、继承 |
| T-303 | 方法签名抽取 | 构造器 / 泛型 / 可变参数 / 抽象方法 / throws / 参数注解 |
| T-304 | **import 与跨文件调用图** | 7 文件样例工程断言 12 条内部边 + 2 条外部边；递归 / `this(...)` / 同类互调三类自环噪音分两级滤掉；歧义按需导入判未解析；继承方法挂到真正声明的父类型 |
| T-401 | **包结构与分层识别** | 自建样例集 32 个类型 **100%（32/32）**；四信号分离（路径/注解/包名/类型名），冲突可查；`model` 等歧义段不猜，认不出显式列 UNKNOWN |
| T-402 | **Mermaid 架构图生成** | 分层图 + 包拓扑图；**用 `mermaid@11.6.0` 官方解析器真校验**（含断言「坏图必须被拒」的负向用例）。⚠️ 只验到解析，**未验渲染** |

### ⏸️ 挂起 —— 全部因为**本机没有 Docker**（16 张）

| 卡号 | 任务 | 依赖的中间件 |
|---|---|---|
| T-003 | docker-compose 一键起环境 | 全部 |
| T-008 | S5 MinIO 连通冒烟 | MinIO |
| T-103 | 网关 Nacos 注册 + Sentinel 限流 | Nacos |
| T-104 | 网关聚合 OpenAPI | 需要服务都起得来 |
| T-105 | 各服务接入 MySQL + Flyway | MySQL |
| T-205 | MinIO 归档源码包 | MinIO |
| T-206 | RabbitMQ 异步投递解析任务 | RabbitMQ |
| T-305 | 解析结果入库 + Redis 缓存 + 分片并行 | Redis |
| T-803 | 导出 ZIP → MinIO | MinIO |
| T-804 | 沙箱运行时安全边界 | 容器运行时 |
| T-1101 ~ T-1104 | 阶段 11 容器化部署（4 张） | 全部 |

> **⚠️ 注意**：阶段 8 的 **T-801（运行能力判定）与 T-802（部署指引生成）不依赖中间件，可以立即做**
> —— 它们只基于文件树与依赖文件做分析，不碰 MinIO。
>
> **解锁方式**：装 Docker Desktop，或 **WSL2 + 容器引擎**（更轻，不必装 Docker Desktop）。
> 一次性解锁这 16 张卡。

### ⬜ 可立即开工，未开始

| 卡号 | 任务 | 所属阶段 |
|---|---|---|
| **T-403** | **技术栈识别（Spring/MyBatis/Vue/...）** ← **下一张** | 阶段 4 |
| T-404 | 循环依赖检测（可直接吃 `CallGraph.typeEdges()`） | 阶段 4 |
| T-501~T-505 | 审计规则引擎 / 依赖冲突 / 架构隐患 / 风险分级 | 阶段 5 |
| T-601~T-604 | LLM 抽象层 / 注释生成 / README / 文档产物 | 阶段 6 |
| T-701~T-704 | 修复建议 / Diff / HITL 状态机 / 全图串联 | 阶段 7 |
| T-901~T-904 | 数据集 / 指标 / 错误溯源 / 评测报告 | 阶段 9 |
| T-1001~T-1007 | Vue3 前端全部 | 阶段 10 |

---

## 四、明天怎么开始

```bash
# 1. 跑基线，确认起点正确（预期：7 模块 SUCCESS，335 测试，0 失败 0 跳过）
#    没有 Node 的机器上是 332 通过 + 3 跳过（MermaidGenTest 的官方解析器用例显式跳过）
cd D:/CodeWisdom
mvn clean verify

# 2. 下一张卡
#    T-403 —— 技术栈识别（Spring/MyBatis/Vue/...）
#    涉及模块：codewisdom-code-analysis
#    现成输入：JavaDependencyExtractor.extractImports()（判 Spring/MyBatis 关键字）
#             LayerReport（分层证据）、FileTreeStats 的文件清单（pom.xml / requirements.txt / package.json）
#    验收：对样例工程识别结果与标注一致
#    测试：mvn -q -pl codewisdom-code-analysis -am -Dtest=TechStackTest -Dsurefire.failIfNoSpecifiedTests=false test
```

然后对我说：**「先读 CLAUDE.md、docs/progress.md、docs/tasks.md。当前任务：T-403」** 即可。

---

## 五、红线 —— 明天不要让我说错话

**未验证、不得声称可用的东西：**

| 项 | 真实状态 |
|---|---|
| **GitHub 导入** | 🔴 **本机 `github.com` 不可达，从未验证过**。代码按同一套 JGit 通用能力实现，但**不能说"支持 GitHub 导入"**，只能说"按通用能力实现，本机未验证"。Gitee 已完整验证。 |
| **真实 MySQL 8** | 🟡 建表脚本只在 **H2(MODE=MySQL)** 上跑过。H2 是近似非等价，**真实 MySQL 必须等 Docker 起来补验**。 |
| **Nacos / Sentinel / MinIO / RabbitMQ / Redis** | 🟡 **一次都没跑过**，只有依赖解析验证过。 |
| **LangHarness** | ✅ 已确认**不存在 Java SDK**，走自研评测模块 —— 这条是结论，不是"待验证"。 |
| **Spring AI** | 🟡 `spring-ai-bom 2.0.1` 是否要求 Boot 4.x **未验证**；引入前必须先验。 |
| **Mermaid 图** | 🟢/🟡 **拆开说**：图能被 Mermaid **官方解析器解析**已实测（`mermaid@11.6.0`，与前端同一份语法，接入测试并有负向用例）；但**渲染出 SVG 本机验不了**（jsdom 无布局引擎，`mermaid.render` 报 `getBBox is not a function`）。**只能说「能被解析」，不能说「能渲染」**，渲染留到阶段 10 用真实浏览器验。 |
| **分层识别准确率** | 🟡 **100% 只在 T-401 自建样例集（32 个类型）上成立**，样例是自己标的，**不能外推成「真实工程识别准确」**。真实工程准确率需独立标注的工程另测。 |

**表述纪律**：所有 AI 分析结论一律说「辅助分析结果，需人工确认」，禁止说「完全自动保证准确」。

---

## 六、本轮踩到并修复的真实缺陷（有复查价值）

这些都会静默出错，只有测试能抓到：

| # | 缺陷 | 不修的后果 |
|---|---|---|
| 1 | Windows 上 Git pack 文件**只读** | 重复导入同一项目必失败（第一次永远正常）；`mvn clean` 删不掉 target、构建直接中断 |
| 2 | JDK `ZipInputStream` 对非 ZIP **不报错**，静默返回空条目流 | 用户传 jpg 却提示"导入成功、0 个文件" |
| 3 | `strictUpdateFill` 只在字段为 null 时填充 | 改了数据但 `updatedAt` 不变，审计字段失效 |
| 4 | `LocalDateTime` 纳秒 vs 列 `DATETIME(3)` 毫秒 | 写入再读回的值与内存值不等 |
| 5 | `FileTreeScanner` 只计数未累加体积 | `totalSize` 恒为 0 |
| 6 | 分类顺序：配置名排在扩展名之后 | `requirements.txt` 被误判为文档，依赖冲突检测漏掉它 |
| 7 | **`getChildByFieldName` 返回空节点而非 Java `null`** | 用 `!= null` 判断会把抽象方法、接口方法误判成有方法体 |
| 8 | `SchemaMigrationTest` 断言"表为空" | H2 内存库跨测试类共享 → 测试顺序依赖，单跑绿、全量红 |
| 9 | **构造器体是 `constructor_body` 不是 `block`** | 按 `block` 找方法体会**静默漏掉全部构造器内的调用**——不报错不抛异常，只是少一批调用边 |
| 10 | **通配符 import 的 `scoped_identifier` 文本不含 `.*`** | 在文本里找 `*` 永远找不到，通配符被当成普通导入，解析结果错得无声无息 |
| 11 | **静态导入的最后一段是成员名不是类型名** | 建「简单名 → 类型」映射会把 `requireNonNull` 当成类；无接收者调用则被挂到调用方自己头上，产出**指向自身的假边** |
| 12 | **路径标记写成 `/src/test/`（带前导斜杠）** | 工程内路径是相对路径 `src/test/java/...`，永远匹配不上；**而且匹配不上时不报错**，只是静默把测试类混进分层统计，架构图跟着失真 |

> 另有若干 Tree-Sitter grammar 的实测细节（`throws` 子句无字段名、`spread_parameter` 字段为空、
> 参数注解在 `modifiers` 里、带注解的类型起始行指向注解行、`import_declaration` 与
> `method_reference` 无字段名、`method_invocation.object` 缺失时的空节点判定、`type_arguments`
> 在 `name` 之前……）已记录在 `docs/tasks.md` 阶段 3 小节与 `docs/tech-spike.md`（F-14 ~ F-16）。
>
> **共同点**：第 9~12 条全都是「不报错、只是结果少一点或错一点」的类型。写新解析逻辑时，
> 光有正向断言不够，**必须补负向断言**（「这个不该出现」）——第 12 条就是靠
> 「测试类应判 TEST」的负向断言才抓到的，前面所有正向断言都是绿的。

---

## 七、配套文档索引

| 文件 | 什么时候看 |
|---|---|
| `CLAUDE.md` | 每次开工必读（工作原则、输出格式、禁止事项） |
| **`docs/STATUS.md`** | **本文件**，一页看完成度 |
| `docs/tasks.md` | 找下一张卡的具体要求与验收标准 |
| `docs/progress.md` | 变更日志、风险台账、冒烟矩阵 |
| `docs/tech-spike.md` | 技术验证结论与实测发现（F-1 ~ F-13） |
| `docs/architecture.md` | 微服务拆分、实现约定（§9 全部是踩坑总结） |
| `docs/acceptance.md` | 各阶段验收标准 |
| `docs/spec.md` | 规范版需求 |
| `docs/summaries/` | 依赖树等证据归档 |
| `tools/mermaid-verify/` | Mermaid 官方解析器校验器（T-402 起）。**不参与 Java 构建**，`mvn` 产出与它无关；新机器要跑该用例先 `cd tools/mermaid-verify && npm install` |
