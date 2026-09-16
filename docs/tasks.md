# 任务清单（tasks）

> 每张卡 ≤ 2 小时。**每次只执行一张卡，先出计划，等用户回复「执行」后再写代码。**
> 测试命令约定：Maven 用 `mvn -q -pl <module> -am test`；单类用 `-Dtest=<Class> test`。

## 阶段 0：技术验证与文档初始化 —— ✅ 已完成（T-003/T-008 挂起）

| 卡号 | 状态 | 任务 | 验收 | 测试命令 |
|---|---|---|---|---|
| T-000 | ✅ | 文档初始化（CLAUDE.md + docs/*） | 7 份文档存在且内容完整 | `ls docs` |
| T-001 | ✅ | 环境补齐：`git init` + `.gitignore`。~~安装 Docker Desktop~~ **（用户决定跳过，2026-09-15）** | `git status` 可执行 | `git status` |
| T-002 | ✅ | 父 pom + common + 5 个空服务骨架，**不接入任何中间件** | 7 个 module 全部编译成功 | `mvn clean verify` |
| T-003 | ⏸️ 🐳 | **挂起** docker-compose：Nacos/MySQL/Redis/MinIO/RabbitMQ | 5 个容器 healthy | `docker compose up -d && docker compose ps` |
| T-004 | ✅ | **S1 冒烟**：全套技术栈依赖共存 | 零 `omitted for conflict` | 见下方「S1 验证方法」 |
| T-005 | ✅ | **S2 冒烟**：Tree-Sitter 解析 Java ⭐关键路径 | 取到 `class_declaration`/`method_declaration` 节点名+行号，且 ABI 兼容 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=TreeSitterSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-006 | ✅ | **S3 冒烟**：LangGraph4j 条件分支 + 循环边 ⭐关键路径 | 图执行完，驳回循环生效，Mermaid 可生成 | `mvn -q -pl codewisdom-agent-orchestration -am -Dtest=LangGraphSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-007 | ✅ | **S4 冒烟**：JGit 拉取公开仓库 | 克隆成功且能遍历文件树 | `mvn -q -pl codewisdom-project-resource -am -Dtest=JGitSmokeTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-008 | ⏸️ 🐳 | **挂起** **S5 冒烟**：MinIO 建桶/传/下/删 | 四步全通 | `mvn -q -pl codewisdom-project-resource -Dtest=MinioSmokeTest test` |

> 🐳 = 依赖 Docker/中间件，本次已延后。
> **S1~S4 全部通过，阶段 3 与阶段 4+ 的关键路径已解锁。**

**S1 验证方法**（一次性探针，不污染正式模块）：

```bash
# 用临时 pom 拉起全套技术栈，检查解析结果与冲突
mvn -B -f .spike-tmp/pom.xml dependency:tree > docs/summaries/s1-dependency-tree.txt
grep -c "omitted for conflict" docs/summaries/s1-dependency-tree.txt   # 必须为 0
```

### 阶段 0 执行顺序（已完成）

```
T-001(git init) → T-002(骨架) → T-004(S1) → T-005(S2)★ → T-006(S3)★ → T-007(S4)  全部 ✅
T-003 / T-008  ⏸️ 挂起，等 Docker 就绪
```

## 阶段 1：工程骨架

### 1A —— 零中间件 —— ✅ 已完成

| 卡号 | 状态 | 任务 | 验收 | 测试命令 |
|---|---|---|---|---|
| T-101 | ✅ | `codewisdom-common`：统一响应体 `R<T>`、分段错误码 `ErrorCode`、`BizException`、全局异常处理 | 单测覆盖成功/业务异常/未知异常三分支 | `mvn -q -pl codewisdom-common test` |
| T-102 | ✅ | `codewisdom-common`：TraceId 透传（上游复用 / 自动生成 / 回写响应头 / MDC 清理） | 4 项行为均有单测 | `mvn -q -pl codewisdom-common -Dtest=TraceIdFilterTest test` |
| T-106 | ✅ | 各服务 `application.yml` + 端口/service-name；网关 4 条静态路由（Nacos 就绪前可直连验证） | 本地 profile 不依赖任何中间件即可启动，`/ping` 返回统一响应体 | `mvn clean verify` |

### 1B —— 🐳 需要中间件（Docker 就绪后执行）

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-103 | 🐳 `gateway`：Nacos 注册 + 路由 + CORS + Sentinel 限流 | 经网关能打到下游服务 `/actuator/health` | `curl -i http://localhost:8080/code-analysis/actuator/health` |
| T-104 | 🐳 `gateway`：springdoc 聚合下游 OpenAPI | `/v3/api-docs` 返回合并后的 JSON（含各服务 tag） | `curl -s http://localhost:8080/v3/api-docs \| head -c 200` |
| T-105 | 🐳 各服务接入 MySQL + MyBatis-Plus + Flyway 基线脚本 | 启动自动建表，`SELECT 1` 通 | `mvn -q -pl evaluation-export test` |

## 阶段 2：项目多源导入

| 卡号 | 状态 | 任务 | 验收 | 测试命令 |
|---|---|---|---|---|
| T-201 | ✅ | 领域模型：`Project`、`ImportTask`、`FileNode` + 枚举 + Mapper + `V1__init_schema.sql` | 建表成功，Mapper CRUD 单测通过 | `mvn -q -pl codewisdom-project-resource -am -Dtest='SchemaMigrationTest,PersistenceCrudTest' -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-202 | ✅ | GitHub/Gitee 公开仓库导入（JGit）+ SSRF 防护 + 路径剪枝 | 导入后 `t_file_node` 有数据，`.git/target/node_modules` 被过滤 | 见下方「T-202 验证命令」 |
| T-203 | ✅ | ZIP 上传与解压 + **Zip Slip / Zip Bomb 防护** | 正常 ZIP 解压成功；含 `../` 的恶意 ZIP 被拒绝 | 见下方「T-203 验证命令」 |
| T-204 | ✅ | 文件树查询接口 + 分类统计（树形 JSON / 子树 / depth / stats） | 返回树形 JSON，节点数与磁盘一致 | 见下方「T-204 验证命令」 |
| T-205 | ⏸️ 🐳 | **挂起** MinIO 归档：原包/源码入 `cw-source` | 对象存在且大小一致 | `mvn -q -pl codewisdom-project-resource -Dtest=MinioArchiveTest test` |
| T-206 | ⏸️ 🐳 | **挂起** RabbitMQ 异步投递 `cw.parse` 任务 | 消息可被消费，任务状态流转 `PENDING→RUNNING` | `mvn -q -pl codewisdom-project-resource -Dtest=ImportTaskMqTest test` |

> **T-201 持久化验证说明**：当前无 Docker，建表脚本与 Mapper 映射通过
> **H2（MODE=MySQL）+ Flyway** 真实执行验证（9 个测试）。H2 是近似非等价，
> 真实 MySQL 8 的验证列入 1B（见 R-13）。
> 测试档：`src/test/resources/application-test.yml`，测试类需标 `@ActiveProfiles("test")`。

**T-202 验证命令**：

```bash
# 全量（含真实 Gitee 导入，需外网）
mvn -B -pl codewisdom-project-resource -am test

# 只跑离线部分（不依赖外网）
mvn -B -pl codewisdom-project-resource -am -Dtest='RepoUrlValidatorTest,ImportRulesTest,FileTreeScannerTest,GitRepoFetcherTest,GitImportIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

**T-202 实测证据**：导入 `https://gitee.com/y_project/RuoYi.git` →
624 文件 / 9.1MB / `master` / head `7995a83e`，`.git`、`target`、二进制全部被剪掉。

> **注意**：SSRF 防护的校验顺序是「协议 → 凭据 → 端口 → 敏感主机名 → 域名白名单 → 解析后 IP」。
> 仅做域名白名单不足以防 SSRF（DNS 可被劫持到内网），**必须校验解析后的真实 IP**，
> 并注意后缀匹配要用 `.域名` 边界，否则 `evilgithub.com` 会被误放行。
> 回归测试：`RepoUrlValidatorTest`。

**T-203 验证命令**：

```bash
mvn -B -pl codewisdom-project-resource -am -Dtest='ZipExtractorTest,ZipImportIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

**T-203 安全验收清单**（`ZipExtractorTest` 逐条覆盖）：

| 攻击手法 | 防御 | 关键断言 |
|---|---|---|
| `../escaped.txt` 相对穿越 | 规范化后必须仍在目标目录内 | 抛异常 **且目标目录外不生成文件** |
| `a/b/../../../escaped.txt` 深层穿越 | 同上（`contains("..")` 挡不住这类写法） | 同上 |
| `/etc/passwd` 绝对路径 | 显式拒绝前导 `/` | 同上 |
| `C:/escaped.txt` 盘符 | 显式拒绝 `^[A-Za-z]:` | 同上 |
| `..\escaped.txt` 反斜杠 | 统一转 `/` 后再规范化 | 同上 |
| `sub/..` 指向根 | 拒绝解析结果等于目标目录的条目 | 抛异常 |
| NUL / `<>:"\|?*` 畸形名 | 字符白名单 | 抛异常 |
| Zip Bomb（总量） | 边写边统计实际字节，超限熔断 | 抛异常 |
| Zip Bomb（压缩比） | `min(配置总量, 包体×压缩比上限)` | 小包大解被拦 |
| 条目数爆炸 | 条目数上限 | 抛异常 |
| 非 ZIP / 损坏文件 | 魔数校验（`PK\x03\x04` 等） | 抛异常 |
| 恶意包污染业务数据 | 条目名预检在建项目**之前**执行 | 路径穿越包**不产生项目记录** |

> **设计要点**：条目名合法性可以廉价预检，因此放在创建项目记录之前；
> 压缩炸弹无法预判（声明的 size 会撒谎），只能边写边熔断，那类输入会留下一条
> FAILED 记录——这是期望行为，便于审计。

**T-204 验证命令**：

```bash
mvn -B -pl codewisdom-project-resource -am -Dtest='FileTreeEndpointTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

**T-204 接口**：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/projects/{id}/tree` | 整树；返回**虚拟根**（id 为 null，代表项目本身），子节点为一级条目 |
| GET | `/projects/{id}/tree?depth=1` | 限制展开层级；1 = 只返回一级条目 |
| GET | `/projects/{id}/tree?path=src/main/java` | 只返回该路径的子树 |
| GET | `/projects/{id}/stats` | 分类统计：节点数、体积、按分类、按语言、最大层级 |

**实现要点**：
1. **一次查询、内存组装**——不做逐层递归查子节点（典型 N+1）。单项目节点数有硬上限
   （`codewisdom.import.max-files`，默认 20000），全量加载是有界的。
2. 也因此**没有为 `path` 建索引**：MySQL 8 下 `VARCHAR(1024)` 全列索引超出键长上限，
   要建就得用前缀索引，而 H2 不支持该语法，会破坏「同一份建表脚本两边都能跑」的约定。
3. 排序固定为「目录在前，同类按名称升序」，保证输出稳定可比对。
4. 统计口径与 `t_project` 的冗余计数字段一致，测试中有对账断言。

## 阶段 3：代码解析服务

| 卡号 | 状态 | 任务 | 验收 | 测试命令 |
|---|---|---|---|---|
| T-301 | ✅ | Tree-Sitter 解析器封装 + 语言注册表 | Java/Python 语法包加载成功 | `mvn -q -pl codewisdom-code-analysis -am -Dtest='LanguageRegistryTest,SourceParserTest' -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-302 | ✅ | 提取类/接口/枚举/注解/记录定义 | 对样例工程，类数量与包结构断言一致 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=JavaStructureExtractorTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-303 | ✅ | 提取方法签名、参数、返回值、行号 | 方法列表与预期快照一致 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=JavaMethodExtractorTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-304 | ✅ | 提取 import / 跨文件调用关系 | 生成调用边，无自环噪音 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=CallGraphTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-305 | ⏸️ 🐳 | **挂起** 解析结果入库 + Redis 缓存 + 分片并行 | 大工程分片解析耗时 < 单线程基线 | `mvn -q -pl codewisdom-code-analysis -Dtest=ParsePipelineTest test` |

> **T-301 实现约定**（落在 `LanguageRegistry` / `SourceParser` / `ParseHandle`）：
> 1. **grammar 缓存且永不 close**——`TSLanguage` 是只读结构可跨线程共享，
>    但它背后的原生指针一旦释放，缓存对象再被引用会崩 JVM。
> 2. **`TSParser` 每次新建**——它持有解析状态，非线程安全；共享会并发串数据。
> 3. **文本提取只走 `ParseHandle.text(node)`**——Tree-Sitter 给的是 UTF-8 字节偏移，
>    直接 `substring` 处理含中文的源码会切出乱码（见 `tech-spike.md` F-1）。
> 4. **行号对外统一 1-based**（Tree-Sitter 原生 0-based），便于直接展示。
> 5. **容错解析**：语法错误的文件仍返回句柄，由 `hasError()` 标识——
>    真实项目常混有无法编译的文件，直接放弃会漏掉大量有效结构。

> **实测发现（T-301 暴露，T-302 已处理）**：
> tree-sitter-java 中**接口方法同样是 `method_declaration`**，与类方法节点类型相同。
> 区分方式：看父节点类型（`class_body` vs `interface_body`），或看有无 `body` 字段
> （接口方法为抽象方法，无 body）。已由
> `SourceParserTest.distinguishesInterfaceMethodsFromClassMethods` 锁定。

> **T-302 实测发现**（均在 `JavaStructureExtractor` 中处理并有测试锁定）：
> 1. **接口方法/类方法同型**（同上），T-303 抽取方法时必须处理。
> 2. **`superclass` 节点文本含 `extends` 关键字**——实测为 `"extends BaseService"`，
>    必须剥掉前缀，否则父类名没法直接比对。
> 3. **带注解的类型，起始行指向注解行而非声明行**——grammar 把 `@Deprecated`
>    放进 `modifiers` 节点，而 `modifiers` 属于 `class_declaration` 的一部分。
>    这是 grammar 语义不是 bug，已写进测试注释避免后来者误判。
> 4. **嵌套类是扁平并列的 `class_declaration`**，靠父子关系区分；
>    必须用栈维护外层限定名才能算出 `demo.Outer.Inner.Deepest`。
> 5. **测试中不写死行号常量**——改为从样例源码反查（`lineOf`），
>    避免手数行号随样例增删漂移，也避免"抽取器错了"与"我数错了"混淆。

> **T-303 实测发现**（均在 `JavaStructureExtractor` 中处理并有测试锁定）：
> 1. **`getChildByFieldName` 对不存在的字段返回「空节点」而非 Java `null`**——
>    必须用 `isNull()` 判断。只看 `!= null` 会把抽象方法与接口方法误判成有方法体。
> 2. **`throws` 子句节点没有字段名**（`getFieldNameForChild` 返回 null），
>    只能按节点类型 `throws` 定位；其子节点还混有 `throws` 关键字与逗号，需按节点类型过滤。
> 3. **`spread_parameter`（可变参数）的 `type`/`name` 字段都是空的**——
>    只能从节点文本按 `...` 拆。实测文本形如 `int... nums`。
> 4. **`formal_parameters` 的子节点包含括号与逗号标点**，
>    直接遍历会把 `(` `,` `)` 当成参数，必须按节点类型挑 `formal_parameter` / `spread_parameter`。
> 5. **参数注解在 `modifiers` 节点里**（与类型声明一致），不能只看参数的直接子节点。
> 6. **构造器与同名方法靠节点类型区分**：`constructor_declaration` vs `method_declaration`，
>    不能靠「有没有返回类型」推断。

> **T-304 实测发现**（均在 `JavaDependencyExtractor` / `CallGraph` 中处理并有测试锁定）：
> 1. **构造器体是 `constructor_body` 不是 `block`**——只按 `block` 找方法体会静默漏掉
>    全部构造器内的调用，不报错、不抛异常，没有测试就永远发现不了。
> 2. **`method_invocation` 的 `object` 在无接收者时缺失**（`requireNonNull(t)` 只有 `name` 字段），
>    必须用 `isNull()` 判空节点，否则全被误当成 `this.xxx()`。
> 3. **`type_arguments` 是 `name` 之前的独立字段**（`this.<String>foo()`），
>    按子节点下标取方法名会取到泛型参数，只能按字段名取。
> 4. **`import_declaration` 与 `method_reference` 没有字段名**：前者子节点是
>    `import`/`static`?/`scoped_identifier`/`asterisk`?/`;`；**通配符的 `scoped_identifier`
>    文本不含 `.*`**，`asterisk` 是兄弟节点。后者子节点是 限定符/`::`/成员名，
>    其中 `X::new` 的第二段是 `new` **关键字节点**，只按 `identifier` 筛会漏。
> 5. **静态导入 `import static a.b.C.member;` 的最后一段是成员名不是类型名**——
>    直接建「简单名 → 类型」映射会把 `requireNonNull` 当成类，因此
>    `ImportDeclaration.isDirectTypeImport()` 必须前置过滤。
> 6. **构造器在调用图里统一记成 `<init>`**（与字节码一致）：用类名做标识会让
>    `this(...)` 委派与自身不同标识，自环漏网，且同名重载会散成多个节点。
> 7. **隐式构造器**：类/枚举/记录即使没写构造器也能 `new`，接口与注解不行，
>    否则 `new Helper()` 会因「找不到 `<init>`」被误断边。
> 8. **继承链上没找到方法就断边**，不把父类方法挂到子类头上——假边比缺边危害大，
>    详见 `tech-spike.md` F-16。

> **T-304 验收证据**：7 文件样例工程（`CallGraphTest`，24 个用例）——
> 精确断言 12 条内部边 + 2 条外部边；递归自调用 / `this(...)` 委派 / 同类内部互调
> 三类噪音分别在方法级与类型级滤掉，且方法级的同类互调<b>保留</b>；
> 按需导入歧义（两个包下同名 `Dup`）判为未解析、不产边；
> 继承来的方法挂到真正声明的父类型；静态导入指向宿主类型而非调用方自己。

## 阶段 4：架构逆向

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-401 | ✅ | 包结构/分层识别（controller/service/mapper/...） | 分层归类正确率在样例集上达标 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=LayerDetectTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-402 | ✅ | Mermaid 架构图生成（模块图/依赖拓扑图） | 输出可被 Mermaid 解析，前端可渲染 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=MermaidGenTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-403 | ⬜ | 技术栈识别（Spring/MyBatis/Vue/...） | 对样例工程识别结果与标注一致 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=TechStackTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-404 | ⬜ | 模块循环依赖检测 | 对构造的循环依赖样例能检出并给出环路径 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=CycleDetectTest -Dsurefire.failIfNoSpecifiedTests=false test` |

> **T-401 实现约定**（落在 `LayerKind` / `LayerAssignment` / `LayerReport` / `LayerDetector`）：
> 1. **四个信号分开保留，不合并成一个结论**——路径 / 注解 / 包名 / 类型名各存一份。
>    「类放在哪」与「类是什么」不一致本身就是结论，合并掉这个信息就永久丢了。
> 2. **优先级：路径 → 注解 → 包名 → 类型名**，理由写在 `LayerAssignment#layer()`：
>    路径是编译期事实（先隔离测试代码），注解是框架契约，包是物理边界，名字只是标签。
> 3. **包名从最内层往外找**：`..service.impl` 里 `impl` 不表态，继续往外命中 `service`；
>    `..domain.vo` 直接命中 `vo`（DTO）而非 `domain`（实体）。
> 4. **`TEST` 不是角色信号**，不论来自路径还是 `*Test` 后缀，都不参与冲突判定——
>    否则「`service` 包下的 `OrderServiceTest`」会被误报成分层混乱。
> 5. **`model` / `pojo` / `bean` / `core` / `common` 刻意不建映射**：这些包里实体与 DTO 混放是常态，
>    凭段名判断等于猜。认不出来就给 `UNKNOWN`，由 `unclassified()` 显式暴露。

> **T-401 实测踩坑**：
> - **路径标记写成 `/src/test/`（带前导斜杠）永远匹配不上**——工程内路径是相对路径
>   `src/test/java/...`。而且匹配不上时**不报错**，只是静默把测试类混进分层统计。
>   改用 `src/test/` 后顺带兼容 `module-a/src/test/java/...` 多模块路径。
>   这条靠「测试类应判 TEST」的断言才抓到，说明**负向断言比正向断言更容易漏**。

> **T-401 验收证据**：自建样例集 **32 个类型**（仿 RuoYi + Spring Boot 真实包结构手工标注）
> **准确率 100%（32/32）**，测试里打印实测值。⚠️ **口径说明：样例集是本卡自己造的，
> 只能说明规则内部自洽，不能外推成「真实工程识别准确」**；真实工程准确率需独立标注的工程另测。
> 另有 15 个用例覆盖优先级、冲突判定、混合包检测、嵌套类型取所在文件的包、输出顺序稳定性，
> 以及与 T-302 解析链路打通的端到端用例。

> **T-402 实现约定**（落在 `MermaidGenerator` + `tools/mermaid-verify/`）：
> 1. **两张图都从现成产物推导**：节点取自 `LayerReport`，边取自 `CallGraph.typeEdges()`（已去自环）。
> 2. **同层调用不画**：分层图上的 `SERVICE → SERVICE` 一律丢掉——两个 service 互相调用画成自环毫无信息量。
>    **包拓扑图同理**，同包内的调用不画。但要小心：`service` 与 `service.impl` 是**同层不同包**，
>    分层图上要滤掉、包图上必须保留，这两条相反的断言都要有。
> 3. **节点 id 与显示名分开**：id 只用 `[A-Za-z0-9_]`，中文与包路径只出现在引号标签里。
>    归一化会让 `com.a.b` 与 `com_a_b` 撞 id → 按出现顺序加数字后缀去重，否则两个包会被画成一个节点。
> 4. **转义只有两条，但是实测出来的**：`"` → `#quot;`（会破坏引号标签边界）、`\` → `/`
>    （标签里的转义符，实测 `a/b\c` 渲染成 `a/bc`，属**静默内容丢失**）。其余字符包括中文原样可用。
> 5. **图里带免责声明**：`%% CodeWisdom 架构图：基于静态分析的辅助结果，需人工确认`——
>    图会被贴进文档脱离上下文，声明写在图里比写在接口文档里可靠。

> **T-402 验收证据**：11 个用例。
> ① 快照逐字符锁定两张图的输出；② 同层边滤掉 / 跨包边保留的**相反断言**成对存在；
> ③ 节点 id 撞车去重、引号与反斜杠转义各有用例。
> **④ 关键：真拿 Mermaid 官方解析器验，不是字符串自查**——`tools/mermaid-verify/verify.mjs`
> 里跑的是 `mermaid@11.6.0` 自己的 jison 语法（与前端渲染同一份解析器），
> 且**专有一条负向用例**断言校验器会拒绝坏图；没有它，「校验通过」可能只是校验器永远返回通过。
> ⚠️ **口径**：本机只能验到**解析**，验不到**渲染出 SVG**（jsdom 无布局引擎，`getBBox` 不存在）。
> 所以只能说「图能被官方解析器解析」，**不能说「图能渲染」**，详见 `tech-spike.md` F-17。

## 阶段 5：缺陷与依赖审计

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-501 | 审计规则引擎 + 规则注册机制 | 空指针/未捕获异常/硬编码/死代码规则可插拔 | `mvn -q -pl code-analysis -Dtest=RuleEngineTest test` |
| T-502 | `pom.xml` 依赖冲突检测（版本冲突/重复/无效） | 构造样例能检出冲突并给出路径 | `mvn -q -pl code-analysis -Dtest=PomConflictTest test` |
| T-503 | `requirements.txt` 依赖解析与冲突检测 | 同上（Python 侧） | `mvn -q -pl code-analysis -Dtest=ReqConflictTest test` |
| T-504 | 架构隐患规则（循环依赖/分层混乱/职责不单一） | 与 T-404 结果打通，输出统一问题模型 | `mvn -q -pl code-analysis -Dtest=ArchRiskTest test` |
| T-505 | 风险分级（高/中/低）+ 问题模型入库 | 每条问题含文件、行号、描述、风险说明、触发场景 | `mvn -q -pl code-analysis -Dtest=AuditPipelineTest test` |

## 阶段 6：文档注释生成

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-601 | LLM 客户端抽象层（可切换 Provider，含超时/重试/降级） | Mock Provider 下单测通过，超时触发降级 | `mvn -q -pl agent-orchestration -Dtest=LlmClientTest test` |
| T-602 | 类/方法/参数/返回值注释生成（**开关可控**） | 关闭开关时不产生任何 LLM 调用 | `mvn -q -pl agent-orchestration -Dtest=DocGenToggleTest test` |
| T-603 | README 生成（介绍/技术栈/目录结构/启动方式） | 对样例工程生成结构完整的 Markdown | `mvn -q -pl agent-orchestration -Dtest=ReadmeGenTest test` |
| T-604 | 接口文档 / 模块说明 / 部署文档生成 | 产物落 MinIO，DB 有记录 | `mvn -q -pl agent-orchestration -Dtest=DocArtifactTest test` |

## 阶段 7：修复建议、Diff、HITL

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-701 | 修复建议生成（关联 T-505 的问题 ID） | 每条高危问题有对应建议，含修改理由 | `mvn -q -pl agent-orchestration -Dtest=FixSuggestTest test` |
| T-702 | 统一 Diff 生成与结构化输出（前后内容 + hunks） | 前端可直接渲染；空改动不产生噪音 diff | `mvn -q -pl agent-orchestration -Dtest=DiffGenTest test` |
| T-703 | HITL 审核状态机（确认/修改/驳回/多轮） | 四态流转正确，超最大轮次自动终止 | `mvn -q -pl agent-orchestration -Dtest=HitlStateTest test` |
| T-704 | LangGraph4j 全图串联（修复 ↔ HITL 循环边） | 全图跑通，含 1 次驳回重生成 | `mvn -q -pl agent-orchestration -Dtest=FullGraphTest test` |

## 阶段 8：运行判定与导出

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-801 | 运行能力判定（是否含 Redis/MySQL/MQ/中间件依赖） | 轻量/重型样例判定正确 | `mvn -q -pl evaluation-export -Dtest=RunJudgeTest test` |
| T-802 | 本地部署指引生成（重型项目出口） | 输出含依赖清单与启动步骤的 Markdown | `mvn -q -pl evaluation-export -Dtest=DeployGuideTest test` |
| T-803 | 项目打包导出 ZIP → MinIO `cw-export` | 导出包可下载，内容与源一致（含修复后变更） | `mvn -q -pl evaluation-export -Dtest=ExportTest test` |
| T-804 | 沙箱运行时**安全边界**实现（网络隔离/资源限额/超时） | 危险样例被拒绝执行 | `mvn -q -pl evaluation-export -Dtest=SandboxGuardTest test` |

## 阶段 9：量化评测

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-901 | 测试数据集 Schema + 标注数据（常规/缺陷/冲突/无文档四类） | Schema 校验通过，每类 ≥3 个样例 | `mvn -q -pl evaluation-export -Dtest=DatasetSchemaTest test` |
| T-902 | 指标计算：召回率、误报率、架构识别准确率、规则匹配率、文档评分 | 对已知输入，指标数值可手算复现 | `mvn -q -pl evaluation-export -Dtest=MetricsTest test` |
| T-903 | 错误溯源：按流水线节点归因 | 能定位到具体节点与失败原因 | `mvn -q -pl evaluation-export -Dtest=ErrorTraceTest test` |
| T-904 | 评测报告生成（Markdown + JSON，可入库比对） | 报告含全部指标 + 溯源明细 | `mvn -q -pl evaluation-export -Dtest=EvalReportTest test` |

## 阶段 10：Vue3 前端

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-1001 | 工程初始化（Vue3 + Vite + Element Plus + Pinia + Axios） | `npm run build` 成功 | `npm run build` |
| T-1002 | 项目导入页（Git 地址 / ZIP 上传） | 可完成一次真实导入 | `npm run dev` 后手动走查 |
| T-1003 | 文件树 + 代码高亮查看 | 点击文件可高亮展示源码 | 同上 |
| T-1004 | Mermaid 架构图渲染 | 图可渲染并可缩放 | 同上 |
| T-1005 | 审计结果 + 风险分级列表 | 可按等级筛选，点击定位到源码行 | 同上 |
| T-1006 | Diff 对比视图 + HITL 审核操作 | 可确认/修改/驳回并触发下一轮 | 同上 |
| T-1007 | 导出下载 + 评测报告页 | 可下载 ZIP，报告可视化 | 同上 |

## 阶段 11：Docker 部署

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-1101 | 各服务 Dockerfile（分层构建 + JRE 17 基础镜像） | 5 个镜像构建成功 | `docker build -t cw-gateway ./codewisdom-gateway` |
| T-1102 | `docker-compose.yml` 全栈编排（中间件 + 应用 + 前端） | 一条命令起全栈 | `docker compose up -d && docker compose ps` |
| T-1103 | 健康检查 + 启动顺序依赖 + 日志规范 | 全部服务 healthy | `docker compose ps --format json` |
| T-1104 | 前端 Nginx 静态托管 + 反向代理到网关 | 浏览器访问前端可完整跑通全流程 | 手动走查 |

## 关键路径与依赖关系

```
T-001(git init) ─▶ T-002(骨架) ─▶ T-004..T-007 (阶段0 冒烟) ─▶ 阶段1A ─▶ 阶段2(部分)
                                                                          │
阶段4 ◀─ 阶段3 ◀──────────────────────────────────────────────────────────┘
  │
  └─▶ 阶段5 ─▶ 阶段6 ─▶ 阶段7 ─▶ 阶段8 ─▶ 阶段9
                                              │
                                       阶段10 ─┴─▶ 阶段11
```

**阻塞点**：S2(T-005)、S3(T-006) 未通过前，阶段 3 与阶段 4+ 不得开工。

## 🐳 Docker 延后影响清单（2026-09-15）

以下卡片因缺少中间件而挂起，**不影响**其余卡片的执行：

| 卡号 | 依赖的中间件 | 挂起后果 |
|---|---|---|
| T-003 | 全部 | 无法一键起环境 |
| T-008 / S5 | MinIO | 对象存储链路未验证 |
| T-103 / T-104 / T-105 | Nacos / MySQL | 网关路由与持久化未验证，**阶段 1 无法正式收尾** |
| T-205 | MinIO | 归档不可用（T-204 文件树仍可做） |
| T-206 | RabbitMQ | 异步投递不可用（T-202/T-203/T-204 同步链路仍可做） |
| T-305 | Redis | 缓存层不可用（T-301~T-304 纯解析仍可做） |
| T-801~T-804 部分 | MySQL / 容器运行时 | 导出与沙箱受限 |
| T-1101 ~ T-1104 | 全部 | 阶段 11 整体挂起 |

**可在无 Docker 情况下完整完成的阶段**：阶段 0（除 T-003/T-008）、阶段 1A、阶段 3（除 T-305）、阶段 4、阶段 5 的规则引擎与冲突检测、阶段 6 的 LLM 抽象层、阶段 7 的 Diff/HITL 状态机、阶段 9 的指标计算。

**降级建议**：若希望保留中间件能力，可考虑 **WSL2 + 容器引擎**（不装 Docker Desktop），或本地原生安装 MySQL/Nacos/RabbitMQ（Redis 在 Windows 上较麻烦，建议 Memurai 或用 WSL）。若都不做，则按上表挂起，先推进纯本地能力。
