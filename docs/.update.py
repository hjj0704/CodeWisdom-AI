import io

# ---------- tasks.md ----------
p = 'docs/tasks.md'
s = io.open(p, encoding='utf-8').read()

old_row = '| T-505 | ⬜ | 风险分级（高/中/低）+ 问题模型入库 | 每条问题含文件、行号、描述、风险说明、触发场景 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=AuditPipelineTest -Dsurefire.failIfNoSpecifiedTests=false test` |'
new_row = '| T-505 | 🟡 | 风险分级（高/中/低）+ 问题模型入库 | 每条问题含文件、行号、描述、风险说明、触发场景 | `mvn -q -pl codewisdom-code-analysis -am -Dtest=AuditPipelineTest -Dsurefire.failIfNoSpecifiedTests=false test` |'
assert old_row in s
s = s.replace(old_row, new_row, 1)

anchor = '''> **阶段 5 门禁实测**：`-Dtest='*Audit*,*Conflict*,*Rule*,*Arch*'` → 47 个测试全绿
> （`RuleEngineTest` 12 + `PomConflictTest` 9 + `ReqConflictTest` 12 + `ArchRiskTest` 14），
> T-505 落地后 `AuditPipelineTest` 会自动纳入。'''
addition = '''> **阶段 5 门禁实测**：`-Dtest='*Audit*,*Conflict*,*Rule*,*Arch*'` → **57 个测试全绿**
> （`RuleEngineTest` 12 + `PomConflictTest` 9 + `ReqConflictTest` 12 + `ArchRiskTest` 14 +
> `AuditPipelineTest` 10）——T-505 的测试已自动纳入，门禁五个类全覆盖。

> **T-505 进度：风险分级与汇总已完成，入库未做（等一个范围决定）**
>
> **已完成**（`AuditReport` / `AuditPipeline` / `AuditPipelineTest`，10 个用例）：
> 1. **汇总**：四路产出（`AuditEngine` / `PomConflictAnalyzer` / `RequirementsConflictAnalyzer` /
>    `ArchRiskAnalyzer`）**已经都是 `List<AuditIssue>`**，管线只做「去重 → 分级 → 稳定排序」，
>    **不重新实现各分析器已经做过的字段校验与排序**，也不再跑一遍任何分析。
> 2. **去重**：完全相同的条目（规则 + 文件 + 行号 + 描述）合并。同一文件被喂给同一路分析器两次
>    会产出完全相同的条目，不合并会把风险计数灌水——而风险计数正是这份报告最直接被人看的东西。
>    注意与 T-501 的约定不冲突：那里说的「不去重」指**不同规则**命中同一行，那是两条问题。
> 3. **等级冲突取高不取低**：同一条发现被两路产出且等级不一致时保留更高的一档，
>    宁可报重也不漏报。去重键**刻意不含等级**——把等级放进键里，两条会同时留下当作两个问题，
>    反而把「同一处发现被判定成两个等级」这个异常掩盖过去。
> 4. **分级恒定三档**：`countByRiskLevel()` 永远返回 高危/中危/低危 三行，无该档时为 0。
>    只列出现过的档看着精简，但「高危 0」与「压根没统计高危」在报告里长得一样。
> 5. **空报告不给自己安等级**：`highestRiskLevel()` 返回 `Optional.empty()`——
>    「没有发现问题」与「问题都是低危」是两回事。
>
> **未做：问题模型入库**。原因不是做不了，是**范围需要确认**：
> `codewisdom-code-analysis` 目前**完全没有持久化层**（无 MyBatis-Plus / H2 / Flyway 依赖、
> 无数据源配置、无 entity/mapper 包），而「各服务接入 MySQL + MyBatis-Plus + Flyway 基线脚本」
> 正是**挂起中的 T-105** 的职责。在 T-505 里半接一个数据源会让 T-105 落地时更难对齐。
> 两个可选路径见 `docs/STATUS.md` 的「T-505 待决」。

> **T-505 已完成的验收证据**：10 个用例。
> ①分级计数、最高等级、含高危判定；②**三档恒定出现**（「高危 0」可区分于「没统计」）；
> ③空报告不给自己安等级；④**等级冲突取高不取低**（负向用例）；
> ⑤去重只在「规则/文件/行号/描述」四项全同时生效，任一不同都算两条；
> ⑥`null` 与空输入不炸；⑦排序与传入顺序无关；
> ⑧**端到端**：真实分析器（`AuditEngine` 四规则）的产出被直接汇总成报告，
> 实测摘要 `共 4 条审计问题：高危 1 / 中危 2 / 低危 1，涉及 1 个文件`，
> 并逐条断言六个字段齐全。'''

assert anchor in s
s = s.replace(anchor, addition, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('tasks.md updated')

# ---------- progress.md ----------
p = 'docs/progress.md'
s = io.open(p, encoding='utf-8').read()

pr = [
    ('2. **下一张卡：T-505 —— 风险分级（高/中/低）+ 问题模型入库**（阶段 5 收尾卡）',
     '2. **T-505 进行中**：风险分级与汇总**已完成**；「问题模型入库」**待一个范围决定**\n   （见 `docs/STATUS.md` 的「T-505 待决」——是否把 T-105 的持久化接线提前做掉）'),
    ('   预期：**7 模块 SUCCESS，407 个测试，0 失败 0 跳过**',
     '   预期：**7 模块 SUCCESS，417 个测试，0 失败 0 跳过**'),
    ('   此时是 **404 通过 + 3 跳过**，不是失败。）',
     '   此时是 **414 通过 + 3 跳过**，不是失败。）'),

    ('**最新提交**：`feat(code-analysis): 架构隐患规则（T-504）`（与 `origin/main` 一致）',
     '**最新提交**：`feat(code-analysis): 风险分级与审计汇总（T-505 部分）`（与 `origin/main` 一致）'),

    ('''**已解锁**：阶段 5 已完成 4/5。四类审计都已就位并统一产出 `AuditIssue`：
代码规则（T-501）、pom 冲突（T-502）、requirements 冲突（T-503）、架构隐患（T-504）。
**T-505 是阶段 5 收尾卡**：把四路产出汇总成一份报告（风险分级计数、按文件/规则聚合），
再加问题模型入库。注意 T-505 的「入库」依赖 MySQL —— 无 Docker 环境下应当按既有约定
用 **H2(MODE=MySQL) + Flyway** 验证，真实 MySQL 仍挂 R-13。''',
     '''**已解锁**：阶段 5 已完成 4/5，第 5 张进行中。四路审计都已就位并统一产出 `AuditIssue`，
汇总管线（`AuditPipeline` + `AuditReport`）也已就位——**汇总与分级已完成**。
**唯一未决的是 T-505 的「入库」**：`codewisdom-code-analysis` 没有持久化层，
而这是挂起中的 T-105 的职责。两个可选路径写在 `docs/STATUS.md` 的「T-505 待决」，
需要用户拍板后继续。'''),

    ('| 阶段 5 | 缺陷与依赖审计 | 🟡 进行中 | 4 / 5 |',
     '| 阶段 5 | 缺陷与依赖审计 | 🟡 进行中 | 4 / 5（T-505 部分完成） |'),

    ('- **当前任务卡**：T-505（风险分级 + 问题模型入库）——阶段 5 收尾',
     '- **当前任务卡**：T-505（风险分级 ✅ 已完成 / 问题模型入库 ⏸️ 待定范围）'),

    ('''  T-502 pom 依赖冲突检测、T-503 requirements 依赖冲突检测、**T-504 架构隐患规则**
- **构建基线**：`mvn clean verify` → 7 模块全绿，**407 个测试**，0 失败 0 跳过
  （各模块：common 12 / gateway 1 / project-resource 210 / code-analysis 179 /
  agent-orchestration 4 / evaluation-export 1）
  ⚠️ 无 Node 的机器上 `MermaidGenTest` 的 3 个官方解析器用例会显式跳过 → 404 通过 + 3 跳过''',
     '''  T-502 pom 依赖冲突检测、T-503 requirements 依赖冲突检测、T-504 架构隐患规则、
  **T-505 风险分级与审计汇总（部分）**
- **构建基线**：`mvn clean verify` → 7 模块全绿，**417 个测试**，0 失败 0 跳过
  （各模块：common 12 / gateway 1 / project-resource 210 / code-analysis 189 /
  agent-orchestration 4 / evaluation-export 1）
  ⚠️ 无 Node 的机器上 `MermaidGenTest` 的 3 个官方解析器用例会显式跳过 → 414 通过 + 3 跳过
- **T-505 已完成部分**：`AuditReport`（恒定三档分级、空报告不给等级）+ `AuditPipeline`
  （去重 + 等级冲突取高不取低）+ 10 个用例；端到端实测摘要
  `共 4 条审计问题：高危 1 / 中危 2 / 低危 1，涉及 1 个文件`
- **T-505 未完成部分**：问题模型入库——需要先决定是否把 T-105 的持久化接线提前做掉（见 STATUS「T-505 待决」）'''),

    ('| 2026-09-16 | T-504 | **修复阶段 5 门禁漏卡**',
     '''| 2026-09-16 | T-505 | 审计报告模型 `AuditReport`：**恒定三档分级**（「高危 0」必须可区分于「没统计高危」）、空报告不给自己安等级、按规则/文件聚合、稳定排序 | `AuditReport` |
| 2026-09-16 | T-505 | 汇总管线 `AuditPipeline`：四路产出只做「去重 → 分级 → 排序」，**不重新实现各分析器已做过的校验与排序**；去重键刻意不含等级，等级冲突时**取高不取低** | `AuditPipeline`、`AuditPipelineTest` |
| 2026-09-16 | T-505 | **入库未做**：code-analysis 无持久化层，而这是挂起中 T-105 的职责。已把范围决定与两个可选路径写进 `docs/STATUS.md`，等用户拍板 | `docs/tasks.md`、`docs/STATUS.md` |
| 2026-09-16 | T-504 | **修复阶段 5 门禁漏卡**'''),
]

for old, new in pr:
    assert old in s, 'progress NOT FOUND: ' + old[:60]
    s = s.replace(old, new, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('progress.md updated')

# ---------- STATUS.md ----------
p = 'docs/STATUS.md'
s = io.open(p, encoding='utf-8').read()

st = [
    ('> **最新提交**：`feat(code-analysis): 架构隐患规则（T-504）`，与 `origin/main` 一致',
     '> **最新提交**：`feat(code-analysis): 风险分级与审计汇总（T-505 部分）`，与 `origin/main` 一致'),
    ('`mvn clean verify` → **7 模块全绿，407 个测试，0 失败 0 跳过**。',
     '`mvn clean verify` → **7 模块全绿，417 个测试，0 失败 0 跳过**。'),

    ('| 任务卡 | **26 / 62**（另有 14 张因缺 Docker 挂起） |',
     '| 任务卡 | **26 / 62 完成 + 1 进行中**（另有 14 张因缺 Docker 挂起） |'),
    ('| 完成任务卡 | **26 / 62**（另有 14 张因缺 Docker 挂起） |',
     '| 完成任务卡 | **26 / 62 完成 + T-505 进行中**（另有 14 张因缺 Docker 挂起） |'),
    ('| 测试 | **407 个，0 失败 0 跳过**（common 12 / gateway 1 / project-resource 210 / code-analysis 179 / agent-orchestration 4 / evaluation-export 1） |',
     '| 测试 | **417 个，0 失败 0 跳过**（common 12 / gateway 1 / project-resource 210 / code-analysis 189 / agent-orchestration 4 / evaluation-export 1） |'),
    ('| 主代码 | 83 个 Java 文件 / 8973 行 |', '| 主代码 | 85 个 Java 文件 / 9205 行 |'),
    ('| 测试代码 | 37 个 Java 文件 / 8227 行（测试与主代码量级相当） |',
     '| 测试代码 | 38 个 Java 文件 / 8493 行（测试与主代码量级相当） |'),
    ('| 提交 | 20 个，全部已推送 |', '| 提交 | 21 个，全部已推送 |'),

    ('；T-504 +14 → **407**。', '；T-504 +14 → 407；T-505 +10 → **417**。'),
    ('> 此时是 **404 通过 + 3 跳过**，不是失败。启用：`cd tools/mermaid-verify && npm install`。',
     '> 此时是 **414 通过 + 3 跳过**，不是失败。启用：`cd tools/mermaid-verify && npm install`。'),

    ('| 5 | 缺陷与依赖审计 | 🟡 **进行中** | 4 / 5 |',
     '| 5 | 缺陷与依赖审计 | 🟡 **进行中** | 4 / 5（T-505 部分完成） |'),

    ('''| **T-505** | **风险分级（高/中/低）+ 问题模型入库** ← **下一张**（阶段 5 收尾） | 阶段 5 |''',
     '''| **T-505 剩余** | **问题模型入库** ← **待你拍板范围**，见下方「T-505 待决」 | 阶段 5 |'''),

    ('# 1. 跑基线，确认起点正确（预期：7 模块 SUCCESS，407 测试，0 失败 0 跳过）\n#    没有 Node 的机器上是 404 通过 + 3 跳过（MermaidGenTest 的官方解析器用例显式跳过）',
     '# 1. 跑基线，确认起点正确（预期：7 模块 SUCCESS，417 测试，0 失败 0 跳过）\n#    没有 Node 的机器上是 414 通过 + 3 跳过（MermaidGenTest 的官方解析器用例显式跳过）'),

    ('然后对我说：**「先读 CLAUDE.md、docs/progress.md、docs/tasks.md。当前任务：T-505」** 即可。',
     '''**⚠️ 先处理「T-505 待决」，再往下走。**

---

## 四之二、T-505 待决 —— 问题模型入库要做到哪一步

T-505 的「风险分级」已完成并测试通过；**「问题模型入库」卡在一个范围问题上**：

`codewisdom-code-analysis` **目前完全没有持久化层**：pom 里没有 MyBatis-Plus / H2 / Flyway，
没有数据源配置，没有 `entity` / `mapper` 包，`application.yml` 里也没有 datasource。
而「各服务接入 MySQL + MyBatis-Plus + Flyway 基线脚本」**正是挂起中的 T-105 的职责**。

两条路，需要拍板：

| 方案 | 做什么 | 代价 |
|---|---|---|
| **A. 在 T-505 里把持久化接线一并做掉** | 给 code-analysis 加 MyBatis-Plus + Flyway + H2 依赖、数据源配置、`t_audit_issue` 建表脚本、entity、mapper，按 T-201 的既有约定用 **H2(MODE=MySQL) 真实跑 Flyway 脚本**验证 | 约 8~10 个文件；**等于把 T-105 在这一个服务上提前做掉一半**，T-105 落地时可能要对齐甚至返工；真实 MySQL 仍无法验证（R-13） |
| **B. 入库留给 T-105（推荐）** | T-505 只交付「内存中的报告」，把入库留到 T-105 与其余四个服务一起统一接线 | T-505 的卡片名里有「入库」而实际未做，需要在文档里写明；但避免了现在半接一个数据源 |

我的判断是 **B**：现在半接数据源，等 T-105 要用统一方式接入时，这里已经有一套别的写法，
要么对齐要么返工；而 T-105 的解封方式（WSL2 + 容器引擎或 Docker Desktop）在 README 里已经有了。
**但这是你的决定——说一句「A」我就接着做。**'''),

    ('| **架构隐患阈值** |',
     '''| **审计问题入库** | 🟡 **未做**。`AuditIssue` 模型与报告都已就绪，但落库需要 code-analysis 先有持久化层（T-105 的职责，挂起中）。**不能说「审计结果已入库」**。 |
| **架构隐患阈值** |'''),
]

for old, new in st:
    assert old in s, 'STATUS NOT FOUND: ' + old[:60]
    s = s.replace(old, new, 1)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('STATUS.md updated')
