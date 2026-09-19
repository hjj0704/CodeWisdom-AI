# Gitee 曝光与推荐 —— CodeWisdom 清单

> 目标：让仓库在搜索、推荐位、开源活动中更容易被看到。**内容质量 > 刷 Star**。

## 一、仓库页必做（10 分钟）

1. **基本信息**
   - 名称：`CodeWisdom AI` 或 `CodeWisdom`
   - 介绍（≤140 字）：`Java 微服务 + Vue3 全链路代码治理：导入、Tree-Sitter 审计、架构图、LLM 修复/HITL、可控 Agent。在线 Demo：47.93.158.48`
   - 主页：演示站 URL
   - 语言：Java（主）+ Vue（次）

2. **标签**（多选，便于搜索）

   `java` `spring-cloud` `spring-boot` `vue3` `element-plus` `code-analysis` `static-analysis` `tree-sitter` `llm` `agent` `devtools` `microservices`

3. **README**
   - 首行一句话 + 徽章 + **高清截图**（见 `docs/images/README.md`）
   - 在线体验步骤 ≤ 4 步

4. **开源许可证**
   - 根目录添加 `LICENSE`（推荐 Apache-2.0 或 MIT）
   - Gitee 设置里勾选对应协议

5. **Release**
   - 创建 `v0.1.0`，说明：MVP 功能列表 + Demo 链接 + 已知限制

## 二、内容与活跃度

| 动作 | 频率 | 作用 |
|------|------|------|
| 功能/fix commit | 每周 ≥1 | 活跃度、推荐算法 |
| 更新 README / STATUS | 大版本后 | 访客信任 |
| 回复 Issue | 48h 内 | 社区健康度 |
| 发 Release Note | 里程碑 | 首页动态 |

## 三、站外引流（合规）

1. **掘金 / 知乎**：1 篇「架构 + 截图 + Demo 链接」，文末附 Gitee 地址  
2. **V2EX 创意 / 分享**：标题带「开源」「Java 微服务」「代码审计」  
3. **毕业设计 / 课程**：摘要里写仓库链接（学校允许的前提下）  
4. **不要**：买 Star、互刷、标题党与 Demo 不符

## 四、Gitee 官方渠道

- [Gitee 推荐项目 / GVP](https://gitee.com/gvp) — 按条件申报  
- 高校开源、毕设专题（关注 Gitee 活动页）  
- 组织/学校开源联盟转发

## 五、截图仍模糊时

根因几乎都是 **源图分辨率不够**。务必：

- 浏览器宽度 ≥ **1920**
- README 里展示宽度 **1024**，源图宽度 **≥2048**（2x）
- 不要用微信压缩过的 JPG

替换流程：`docs/images/README.md`

## 六、可选增强（有时间再做）

- [ ] 第二张图：工作台（审计 + AI 侧栏）
- [ ] 30 秒 GIF（导入 → 检查代码）
- [ ] Gitee Pages 静态页（若不用 ECS 演示）
- [ ] CI 徽章：`构建通过` 显示在 README
