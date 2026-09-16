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

## 阶段 4：架构逆向 —— ✅ 已完成（4 / 4）

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-401 | ✅ | 包结构/分层识别（controller/service/mapper/...） | 分层归类正确率在样例集上达标 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=LayerDetectTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-402 | ✅ | Mermaid 架构图生成（模块图/依赖拓扑图） | 输出可被 Mermaid 解析，前端可渲染 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=MermaidGenTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-403 | ✅ | 技术栈识别（Spring/MyBatis/Vue/...） | 对样例工程识别结果与标注一致 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=TechStackTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-404 | ✅ | 模块循环依赖检测 | 对构造的循环依赖样例能检出并给出环路径 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=CycleDetectTest -Dsurefire.failIfNoSpecifiedTests=false test` |

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

> **T-403 实现约定**（落在 `TechStack` / `TechStackItem` / `TechStackReport` / `TechStackDetector`）：
> 1. **技术目录是有边界的枚举**，不是「什么都能识别」——为的是结果可枚举、可比对。
>    代价是目录外的技术认不出，因此 `unrecognized()` 把**匹配不上任何规则的依赖坐标原样列出**，
>    「没认出来」必须可见，不能悄悄消失。
> 2. **三路证据缺一不可**：import 前缀（没有 pom 也能认框架）、注解名（Spring MVC 的**唯一可靠证据**，
>    因为 `spring-boot-starter-web` 也会被非 MVC 项目当 HTTP 客户端引）、依赖文件坐标（**版本的唯一来源**，
>    也是 Python 与前端的唯一证据）。
> 3. **证据是一等公民**：每条结果都必须能回答「凭什么说是它」，空证据视为缺陷，有测试断言。
> 4. **`@Mapper` 不进注解规则表**——MyBatis 与 MapStruct 同名，光看注解简单名分不出来。
>    只认 import 与依赖坐标，分不出来就不认，不掷硬币。
> 5. **版本解析不出就是 `null`**：Maven 的 `${property}` 会回查 `<properties>`，
>    回查不到留空；**绝不把 `${...}` 原文当版本号输出**——那比没有版本更糟，
>    它会以「看起来像个版本」的样子混进架构说明书。npm 的 `^`/`~` 与 requirements 的 `>=`
>    只剥前缀不做区间求解，且**只有 `==` 才认为拿到了确切版本**。

> **T-403 实测发现**：
> - **MyBatis 的核心包是 `org.apache.ibatis` 不是 `org.mybatis`**（`@Mapper`、`SqlSession` 都在前者，
>   `org.mybatis` 只是 mybatis-spring 这类集成包）。只写一个前缀会**漏掉大半个真实项目**，
>   而且是静默漏——现象是「MyBatis 没被识别」，看不出是规则写漏了。已两个都收。
> - **`package.json` 必须在 `dependencies` 块内取值**：整文件抓键值对会把 `name`/`version`/`scripts`
>   这些元数据当成「未识别依赖」，把未识别清单变成噪音。有负向断言锁住。
> - **Python 包名要按 PEP 503 归一化**（小写 + 下划线转连字符），否则 `SQLAlchemy` 与 `sqlalchemy`
>   会当成两个包。

> **T-403 验收证据**：14 个用例。样例工程 = Spring Boot + MyBatis-Plus 的 Java 后端
> + Vue 3 前端 + Python 依赖清单，**命中 21 项 / 未识别 3 项**，测试里打印实测值。
> 断言是**双向**的（`containsExactlyInAnyOrderElementsOf`）：多认一项和少认一项都要红。
> ⚠️ **口径**：样例是本卡自建的，只说明规则与标注一致，**不等于真实工程识别准确**；
> 且识别的是「工程里**声明或引用**了这项技术」，**不是**「这项技术在运行」。

> **T-404 实现约定**（落在 `DependencyCycle` / `CycleReport` / `CycleDetector`）：
> 1. **用 Tarjan 求强连通分量，不逐个节点找环**。「从每个节点出发 DFS 看能否回到自己」
>    在稠密图上会退化成指数级，而且会把同一个环按不同起点重复报好几遍。
>    Tarjan 一次遍历求出全部 SCC：大小为 1 的跳过，≥2 的整块缠在一起。
> 2. **但只有「块」不够**——验收要的是**完整环路径**。每个分量内再从字典序最小的节点做一次
>    **BFS**，取回到起点的**最短**环作为代表路径。用 BFS 不用 DFS：DFS 找到的那条可能绕一大圈，
>    把「拆一条边就能断」的环描述成「要拆五条」。
> 3. **两个粒度都要报**：同一份依赖可能**同时**是包环与分层环（`demo.web ↔ demo.svc` 既是包环，
>    也是 controller ↔ service 的反向依赖）。包环告诉你哪两个包要拆，层环告诉你架构分层倒了，
>    只报一个会漏掉另一半信息。
> 4. **模块内调用不是环**：同包内 A 调 B、B 调 A 在包级图上就是一条自环，毫无信息量，建图时丢掉。
>    反过来，同层不同包（`..service` 与 `..service.impl`）互调在**层图上**是自环、
>    在**包图上**是真环——这条对照有专门用例锁定。
> 5. **环路径首尾同一个模块**（`[a, b, c, a]`）。不重复收尾的话，「这是环」只能靠读者自己
>    把首尾接起来，而架构文档的读者往往正是没意识到这是环的人。构造时校验，非法路径直接抛异常。

> **T-404 验收证据**：11 个用例。
> ① 三包环检出且路径逐字符断言（`demo.a -> demo.b -> demo.c -> demo.a`）；② 两包环、多环按长度排序；
> ③ **负向断言**：链式依赖 `a → b → c` 必须不报环；④ 同包互调不报环，**并断言该样例确实有 2 条调用边**
> ——否则「没检出」可能只是因为样例压根没有依赖；⑤ 层环与包环同时报出；⑥ 空工程、非法路径构造。
>
> **阶段 4 门禁实测**：`mvn -pl codewisdom-code-analysis -am -Dtest='*Layer*,*Mermaid*,*Tech*,*Cycle*' test`
> → **52 个测试全绿**（`LayerDetectTest` 16 + `MermaidGenTest` 11 + `TechStackTest` 14 + `CycleDetectTest` 11），
> 说明上一轮修正过的门禁命令确实能跑通，不再空集。

## 阶段 5：缺陷与依赖审计

| 卡号 | 任务 | 验收 | 测试命令 |
|---|---|---|---|
| T-501 | ✅ | 审计规则引擎 + 规则注册机制 | 空指针/未捕获异常/硬编码/死代码规则可插拔 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=RuleEngineTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-502 | ✅ | `pom.xml` 依赖冲突检测（版本冲突/重复/无效） | 构造样例能检出冲突并给出路径 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=PomConflictTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-503 | ✅ | `requirements.txt` 依赖解析与冲突检测 | 同上（Python 侧） | `mvn -q -pl codewisdom-code-analysis -am -Dtest=ReqConflictTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-504 | ✅ | 架构隐患规则（循环依赖/分层混乱/职责不单一） | 与 T-404 结果打通，输出统一问题模型 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=ArchRiskTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| T-505 | ⬜ | 风险分级（高/中/低）+ 问题模型入库 | 每条问题含文件、行号、描述、风险说明、触发场景 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=AuditPipelineTest -Dsurefire.failIfNoSpecifiedTests=false test` |

> **T-501 实现约定**（落在 `AuditIssue` / `AuditRule` / `AuditEngine` / `arch/rules/*`）：
> 1. **统一问题模型只有一个**：代码缺陷、依赖冲突、架构隐患全部收敛成 `AuditIssue`
>    （`acceptance.md` 明确要求「使用统一问题模型」）。统一到同一个 DTO 而不是
>    「几个长得很像的 DTO」，前端渲染与入库才只需要一套。
> 2. **六个字段是构造函数的硬约束**：文件路径、行号、问题描述、风险说明、触发场景、风险等级
>    —— 缺一个就造不出对象，而不是等前端渲染时才发现是空的。把约束放进构造函数，
>    是让编译器与单测替人记住它。
> 3. **可插拔的机制就是 Spring**：实现 `AuditRule` + `@Component`，`AuditEngine` 通过
>    `List<AuditRule>` 构造注入拿到全部实现，引擎自身不认识任何具体规则。
>    加规则不改引擎、不改注册表——而不是「在 switch 里多加一个分支」。
> 4. **`riskLevel()` 是基准等级，不是最终等级**：同一条规则内部可按命中形态给不同等级
>    （硬编码密码高危、硬编码连接串中危）。这个方法的用途是让调用方<b>不执行规则</b>
>    也知道它会不会产出高危问题。
> 5. **引擎不吞规则异常**：规则抛异常就让它抛。静默跳过会让人以为「这条规则没发现问题」，
>    而真相是「这条规则根本没跑完」——审计工具里这种混淆比崩溃危险得多。
> 6. **引擎不做去重合并**：同一处代码被两条规则命中是两条问题，指向不同的风险。合并会丢依据。
> 7. **输出按「文件 → 行号 → 规则 ID」排序**：结果要入库、要做两轮比对（「这次比上次好了没有」），
>    顺序一漂移，所有比对都变成噪声。

> **T-501 实测踩坑（两条都是「不报错、只是永远不命中」）**：
> 1. **tree-sitter 的 `block` 子节点里包含 `{` 与 `}` 两个匿名 token**——`{}` 的
>    `getChildCount()` 是 **2 不是 0**。判 `childCount == 0` 让「空 catch 块」规则
>    **永远不命中且不报错**。必须显式排除花括号与注释。
>    （与 T-303 踩到的 `formal_parameters` 混入括号是同一类陷阱，这是第二次。）
> 2. **JDBC 连接串有子协议**：真实写法是 `jdbc:mysql://host:3306/db`，子协议夹在
>    `jdbc` 与 `://` 之间。正则写成 `(jdbc|redis|...)://` 会让 `jdbc:*` 这一支
>    **永远匹配不上**，连接串从不被检出。已改成 `jdbc(:[a-z0-9]+)?://`。

> **T-501 验收证据**：12 个用例。样例同时埋四类缺陷，逐处断言被检出且落在正确行上。
> **`acceptance.md` 的三条硬要求逐条对应**：①每条问题六个字段齐全；②风险等级只在三值内
> （枚举保证 + 断言取值）；③**标注样例集上高危漏报率 0.0%**（2 处高危、0 漏报，测试里打印实测值）。
> 另有可插拔验证（自定义规则无需改引擎即生效、引擎不认识任何具体规则）、
> 不误报验证（被调用的私有方法、`@PostConstruct` 回调、常量字段都不算死代码）、
> 无调用图时死代码规则安静退出。
> **阶段 5 门禁实测**：`-Dtest='*Audit*,*Conflict*,*Rule*,*Arch*'` → 47 个测试全绿
> （`RuleEngineTest` 12 + `PomConflictTest` 9 + `ReqConflictTest` 12 + `ArchRiskTest` 14），
> T-505 落地后 `AuditPipelineTest` 会自动纳入。
>
> ⚠️ **本卡发现并修正了门禁自身的缺陷**：原命令 `*Audit*,*Conflict*,*Rule*` **不匹配
> `ArchRiskTest`**——也就是「架构隐患」这张卡不在阶段 5 的门禁里。与阶段 4 曾出现的
> `*Arch*` 匹配空集是同一类缺陷（模式与测试类名对不上），已补上 `*Arch*`。

> **T-504 实现约定**（落在 `ArchRiskAnalyzer`）：
> 1. **这一卡做的是「翻译」，不是重新分析**。阶段 4 的三份产物已经是结构化数据
>    （`LayerReport` / `CycleReport` / `TechStackReport`），本类不重新解析源码、不重新跑图算法——
>    环检测仍然调用 T-404 的 `CycleDetector`，那是复用。**别又从头写一遍分析。**
> 2. **三类隐患，每条两个粒度，共六条规则**：
>    `CW-ARCH-001` 循环依赖（分层环**高危** / 包环中危）；
>    `CW-ARCH-002` 分层混乱（类级信号冲突**中危** / 包级混杂低危）；
>    `CW-ARCH-003` 职责不单一（包内类型数过多**中危** / 疑似上帝类低危）。
> 3. **架构隐患也必须锚在真实文件与行号上**。`DependencyCycle` 里只有模块名（`demo.a` / `CONTROLLER`），
>    没有位置。解法是让分析器接收「文件 → 类型声明」这份数据（调用方本来就为 `LayerDetector` 准备了），
>    据此建索引把问题锚到**真正参与的那个类**。不这么做就只能填 `NO_LINE`——架构隐患虽然横跨多个文件，
>    但「哪个类的声明行」仍比「没有行号」有用得多。
> 4. **锚点行号会落在注解行而不是 `public class` 行**：这是 T-302 记录的 grammar 行为
>    （`@RestController` 在 `modifiers` 里，而 `modifiers` 属于 `class_declaration`）。
>    不是 bug，测试里写明了这一点。

> **T-504 两处防误报**：
> - **上帝类检查排除 DTO / 实体 / 常量 / 工具 / 异常层**——这些类型本来就该被广泛引用，
>  把它们报成上帝类全是误报。有专门的负向用例（一个被 16 个类型依赖的 DTO 必须不报）。
> - **包级混杂定低危且措辞是「需人工判断」**：真实工程里 `common` 包混放 config 与 util 是常态。
>  阈值（20 个类型、15 个依赖方）是经验值，措辞一律是「建议关注」而不是「违反规范」。

> **T-504 验收证据**：14 个用例。样例工程一次埋三类隐患（互相依赖的 web↔svc、放错包的
> `@RestController`、21 个类型同包、被 16 个类型依赖的 Hub），共检出 6 条。
> ①**打通断言**：同一份输入下 `CW-ARCH-001` 的环路径必须与 `CycleDetector` 直接算出来的**一致**
> ——不是「另外算了一个环」；②分层环高危 / 包环中危、类级冲突中危 / 包级混杂低危，等级逐个断言；
> ③所有问题都锚在样例里真实存在的文件上且行号为正；④干净工程零产出；⑤输出顺序稳定。

> **T-503 实现约定**（落在 `PythonRequirement` / `RequirementsReader` / `RequirementsConflictAnalyzer`）：
> 1. **与 Maven 侧是两套语义，不是同一套规则换个文件**。pip 有三处不同的地方，每一处都有专门用例：
>    <b>环境标记</b>（Maven 没有对应物）、<b>单等号 `=` 会被 pip 拒绝安装</b>、
>    <b>PEP 503 包名归一化</b>（`zope.interface` 与 `zope-interface` 是同一个包，只把下划线换连字符会漏）。
> 2. **带环境标记的声明一律不参与冲突判定**：同一条依赖在不同标记下写不同版本是 pip 的<b>标准写法</b>
>    （`foo==2.7.1 ; python_version<"3.9"` 与 `foo==2.9.0 ; python_version>="3.9"`）。
>    两个标记是否互斥需要求解标记表达式，本工具不做——<b>不判就不会误报</b>，有负向断言锁定。
> 3. **只比对 `==` 钉死的精确版本**：区间约束之间能否同时满足是区间求解问题。取舍依据是
>    requirements.txt 的主流形态就是全钉版本（pip-tools 编译产物、导出的锁文件），
>    <b>钉版本之间的冲突正是最常见也最该报的那种</b>。
>    `==1.0rc1` 这类带预发布后缀的也<b>不算</b>精确版本——版本比较规则需要完整实现，本模块不做。
> 4. **不跟随 `-r` / `-c` 包含的文件**：每个文件独立分析，包含关系由调用方把文件给全。
>    读取器自作主张去读磁盘上的另一个文件，会让「同一份输入得到同一份输出」这条约定失效。
> 5. **重复声明与版本冲突不重叠**：这里把「重复」定义为<b>同文件内同包同约束</b>，
>    所以与钉版本冲突天然互斥，不需要像 T-502 那样额外做抑制。
> 6. **pom 与 requirements 的解析都抽成了公共静态读取器**，技术栈识别与冲突检测各共用一份。
>    两轮重构（T-502 的 `PomDependencyReader`、T-503 的 `RequirementsReader`）之后，
>    T-403 的 14 个用例始终全绿，行为无变化。

> **T-503 验收证据**：12 个用例。两份 requirements 文件（主 + dev）：
> ①同一文件内同包钉两个版本判**高危**且锚在第二处；②跨文件版本不一致判**中危**，
> 测试里**断言描述不含「冲突」二字**且风险说明含「是合法的」「本工具判不出」；
> ③同文件同约束重复判低危；④`django=4.2` 单等号判无效操作符；
> ⑤**三条负向断言**：带标记的两个版本不报冲突、区间约束不参与判定、选项行与注释行完全跳过；
> ⑥冲突路径含每一处声明的 `文件:行号`；⑦extras 与行内注释不影响解析；⑧输出与入参顺序无关。

> **T-502 实现约定**（落在 `DependencyCoordinate` / `PomDependencyReader` / `PomConflictAnalyzer`）：
> 1. **检测的是「声明级」冲突，不是运行时冲突**——这条口径必须说死，否则报告会被当成依赖仲裁结果：
>    <b>不解析传递依赖</b>（A 依赖 B、B 依赖 C-1.0 而工程直接声明 C-2.0，这种最常见的 Maven 冲突
>    **检不出来**，需要真正的依赖解析器）；<b>不看 `dependencyManagement`</b>（它决定实际生效版本）；
>    <b>不看 `exclusions` 与 `profiles`</b>。结论一律表述为「<b>声明</b>层面存在冲突」。
> 2. **同一 pom 内多版本 = 高危**：Maven 按「最近优先」裁决后另一个版本<b>静默失效</b>，
>    源码里写着 2.0、实际加载 1.0，排查时极具误导性。
> 3. **跨模块版本不一致 = 中危，且措辞不许说成「冲突」**：两个独立模块各用各的版本<b>是合法的</b>，
>    只有共享同一运行时 classpath 时才真正冲突——而本工具<b>判不出是否共享</b>，所以报为
>    「声明不一致」，风险说明里明写「请先确认，再由父 pom 的 dependencyManagement 统一」。
> 4. **行号锚在 `<dependency>` 块首行**，不是 `<version>` 行：用户拿行号要跳到的是「这条声明」。
>    因此读取器用「块的起始字符下标 + 数换行符」算行号——拿 `<artifactId>` 的下标当行号，
>    在多行写法下会差好几行。
> 5. **版本回查不到时保留 `${...}` 原文**（与 T-403 的口径相反）：冲突检测要报「引用了不存在的属性」
>    这个缺陷，置空就看不见了；技术栈识别只要版本号，所以在那边映射成 null。
>    同一个读取器服务两种口径，由调用方决定怎么用。
> 6. **pom 解析抽成公共读取器**（`PomDependencyReader`），技术栈识别与冲突检测共用一份——
>    「两份必须同步的解析代码」一旦漂移，两边会给出互相矛盾的结论。它是<b>无状态静态工具</b>，
>    不做成 Spring Bean，这样 T-403 的构造签名与测试都不用动。
> 7. **重复声明在已有版本冲突时不再单独报**：同坐标多版本的高危条目已经说了「声明了多个版本」，
>    再补一条低危的「重复声明」只是同一处代码刷两条，稀释重点。只有<b>同版本</b>的纯冗余才落到那条规则。

> **T-502 实测发现**：
> - **`<project>` 自身的 groupId/artifactId 不会被误收**：读取器只在 `<parent>` 与 `<dependency>`
>   块内取坐标，因此 `pom.xml` 顶层的坐标与 `<modules>` 都不会进依赖清单（有用例锁定）。

> **T-502 验收证据**：9 个用例。5 模块样例工程（parent + 3 个有问题的模块 + 1 个干净模块）：
> ①同一 pom 多版本判**高危**且锚在第二处声明；②跨模块版本不一致判**中危**且描述里不含「冲突」二字
> （有用例断言措辞）；③同 pom 重复声明判低危；④缺 groupId 与版本属性不存在各报一条；
> ⑤**冲突路径**：触发场景里含每一处声明的 `文件:行号` 与两个版本号；
> ⑥**负向断言**：干净模块与 parent pom 零产出、跨模块同版本不算冲突、能解析的属性引用不报无效；
> ⑦输出与入参顺序无关（倒序传入结果一致）。


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
