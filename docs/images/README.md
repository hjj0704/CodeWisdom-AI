# 仓库展示图规范

Gitee / GitHub 首页 README 里的图 **不要用小于 1920px 宽的截图再放大**，在 Retina 屏上会发糊。

## 推荐截图参数

| 项 | 建议 |
|----|------|
| 浏览器宽度 | **1920px**（或 1440 最小） |
| 设备像素比 | 系统 100% 缩放；或 125% 时窗口仍保持 ≥1440 CSS 像素 |
| 格式 | **PNG**（界面有渐变时用 PNG，避免 JPEG 块） |
| 文件名 | `import-center@2x.png`（宽 **2048** 左右） |

## Windows 快速截图（Chrome）

1. 打开 [演示站](http://47.93.158.48/) 并登录到 **导入中心**
2. `F12` → `Ctrl+Shift+P` → 输入 **Capture screenshot** → 选 **Capture full size screenshot**（整页）  
   或只截视口：**Capture node screenshot** 选中主内容区
3. 用画图 / Snipaste 裁掉浏览器外框，保留约 **16:9 或 2:1** 比例
4. 覆盖本目录：
   - `import-center@2x.png`（主展示，≥1920 宽）
   - `import-center.png`（可选缩略，1024 宽）

## 当前文件

| 文件 | 用途 |
|------|------|
| `import-center@2x.png` | README 主图（2048×1070，由旧图 Lanczos 放大 + 锐化，**建议尽快用真 1920 截图替换**） |
| `import-center.png` | 缩略 / 兼容 |

替换后执行：

```bash
git add docs/images/
git commit -m "docs: 更新 README 展示图"
git push
```
