# IconFontViewer 插件升级 PRD

## 元信息
- 访谈 ID: iconfontviewer-upgrade-2026-04-17
- 访谈轮数: 7
- 最终歧义度: 17.8%
- 项目类型: 存量项目（brownfield）
- 生成日期: 2026-04-17
- 歧义阈值: 20%
- 状态: 通过

## 清晰度评分

| 维度 | 分数 | 权重 | 加权分 |
|------|------|------|--------|
| 目标清晰度 | 0.90 | 35% | 0.315 |
| 约束清晰度 | 0.75 | 25% | 0.188 |
| 验收标准清晰度 | 0.80 | 25% | 0.200 |
| 上下文清晰度 | 0.80 | 15% | 0.120 |
| **总清晰度** | | | **0.823** |
| **歧义度** | | | **17.8%** |

## 一、项目目标

升级 IconFontViewer IntelliJ/Android Studio 插件，使其成为一个功能完整、体验流畅、无已知 bug 的 iconfont 预览与替换工具。核心目标是**补齐现有功能缺陷、修复已知 bug、打磨 UI 体验**，让插件从"能用"升级为"好用"。

## 二、约束条件

- **平台限制**：仅面向 Android 项目（依赖 `org.jetbrains.android` 插件 API）
- **UI 风格**：以 IntelliJ 原生组件（JBPanel、JBList、JBUI.Borders 等）为主，仅在开关（SwitchButton）、来源标签等少数地方加自定义设计亮点，整体融入 IDE 体验
- **TTF 检测策略**：智能检测 — 文件名包含 "icon"、"iconfont"、"symbol" 等关键词的 TTF 默认启用，其他默认关闭（避免误启用 Roboto 等普通字体）
- **刷新时机**：仅在 AS 启动时自动扫描 TTF 并缓存，运行期间不自动刷新（用户可通过面板手动触发重新扫描）
- **持久化**：用户的启用/关闭选择需跨会话持久保存（现有的 `.idea/iconFontViewer.xml` 机制）
- **弹窗范围**：点击 gutter icon 弹窗只展示**当前代码行引用的那个字体**的图标，不展示所有字体的图标

## 三、不做的事情（Non-Goals）

- 不支持 SVG 图标格式（本次升级范围外）
- 不支持网络字体 URL 加载（本次升级范围外）
- 不做跨字体图标替换功能
- 不对非 Android 项目提供支持

## 四、验收标准

### 4.1 核心功能
- [ ] AS 启动并 sync 完成后，代码中引用了 iconfont string resource 的行，gutter 处显示对应的预览图标
- [ ] 如果 string resource 对应的 Unicode 字符在所有启用的字体中都找不到 glyph，则不显示任何 gutter icon（静默处理）
- [ ] 点击 gutter icon 弹出弹窗，弹窗内展示**当前字体**的所有可用图标，支持搜索过滤
- [ ] 选中弹窗中的图标后，代码中的引用被自动替换（支持 Kotlin、Java、XML 三种语言）

### 4.2 管理面板
- [ ] Tools 菜单打开 IconFontViewer 面板，显示所有扫描到的 TTF 文件列表
- [ ] 每个 TTF 显示：文件名、完整路径、来源标签（PROJECT/AAR/USER）、启用/禁用开关
- [ ] 首次扫描时，文件名含 "icon"/"iconfont"/"symbol" 的 TTF 默认启用，其他默认关闭
- [ ] 用户手动切换的启用/关闭状态跨 AS 重启持久保存
- [ ] 面板底部有"添加 TTF 文件"按钮，可手动添加本地 TTF 文件（手动添加的默认启用）
- [ ] 面板底部有"重新扫描"按钮，可手动触发 TTF 重新扫描

### 4.3 Bug 修复
- [ ] 修复 iconPopupList 无限增长问题 — 弹窗数据应在每次打开时按需构建，不累积
- [ ] 修复 fontCache 线程安全问题 — 使用 ConcurrentHashMap 或同步机制
- [ ] 修复 IconFontLineMarkerNavHandler 中 PsiElement 引用泄漏 — 使用 SmartPsiElementPointer
- [ ] UI 整体使用 IntelliJ 原生组件，保持 IDE 一致性，SwitchButton 等自定义组件保留动画亮点

## 五、假设验证记录

| 原始假设 | 挑战 | 最终决策 |
|----------|------|----------|
| "首次启动默认都是开的" | 会导致 Roboto 等普通字体也被启用，产生噪音 | 改为智能检测：仅文件名含 icon 关键词的默认启用 |
| "弹窗展示所有可用 iconfont" | 多字体 2000+ 图标会导致性能问题和用户迷失 | 弹窗只展示当前代码行引用的那个字体的图标 |
| "需要重新设计 UI" | 全自定义组件开发成本高且可能不融入 IDE | 以 IntelliJ 原生风格为主 + 少量自定义亮点 |
| "ttf_ui_design.html 是设计基准" | 用户明确说不以此为准 | 重新设计，但保留 SwitchButton 等有价值的自定义组件 |

## 六、典型使用流程

```
用户打开一个使用了 iconfont 的 Android 项目
        ↓
AS 启动 → Gradle Sync 完成 → 后台自动扫描 TTF 文件
        ↓
打开代码文件（Kotlin/Java/XML）
        ↓
引用了 R.string.icon_xxx 或 @string/icon_xxx 的行
在 gutter 处显示对应的字体图标预览
        ↓
点击 gutter icon → 弹出弹窗
弹窗展示当前字体的所有可用图标 + 搜索框
        ↓
选中一个图标 → 代码中的引用自动替换
        ↓
[可选] Tools 菜单 → 打开 IconFontViewer 面板
查看/管理所有 TTF 文件，启用/禁用/手动添加
```

## 七、领域实体

| 实体 | 类型 | 关键字段 | 关系 |
|------|------|----------|------|
| TTF 字体文件 | 核心领域 | path, fileName, source(PROJECT/AAR/USER), enabled | 被管理面板管理，被 Gutter 图标引用 |
| Gutter 图标 | 核心领域 | icon(渲染结果), element(PSI 元素), fontRef | 由 LineMarkerProvider 创建，点击触发弹窗 |
| 图标选择弹窗 | 辅助 | iconList, searchFilter, selectedItem | 展示单个字体的所有图标，选中后替换代码 |
| 管理面板 | 辅助 | fontList, addButton, rescanButton | 管理所有字体的启用/禁用状态 |
| String 资源 | 外部系统 | name, value(unicode 字符) | Android 资源系统，被 Gutter 图标解析 |
| 用户偏好 | 辅助 | fontPath, enabled, persistedState | 持久化用户的字体启用/关闭选择 |

## 八、访谈记录

<details>
<summary>完整问答（7 轮）</summary>

### 第 1 轮
**问：** 现有代码已覆盖你描述的大部分功能。这次"升级"的核心目标到底是什么？
**答：** 功能需要补齐加优化，然后 UI 需要打磨
**歧义度：** 61.5%

### 第 2 轮
**问：** 假设升级完成的插件给你用，你会怎么验证它是"做好了"的？请描述理想使用流程。
**答：** 详细描述了完整使用流程：启动→sync→gutter 预览→点击弹窗→面板管理→开关持久化
**歧义度：** 37.0%

### 第 3 轮
**问：** TTF 检测策略：全部默认启用 vs 智能检测 vs 全部默认关闭？
**答：** 智能检测，只启用疑似 iconfont 的
**歧义度：** 33.2%

### 第 4 轮（反向挑战模式）
**问：** 弹窗真的需要展示所有字体的图标吗？还是只展示当前字体的就够了？
**答：** 只显示当前字体的图标
**歧义度：** 27.8%

### 第 5 轮
**问：** ttf_ui_design.html 作为 UI 设计基准，还是重新设计？
**答：** 重新设计
**歧义度：** 26.5%

### 第 6 轮（简化挑战模式）
**问：** UI 风格：IntelliJ 原生 vs 完全自定义 vs 原生+亮点？
**答：** IntelliJ 原生风格 + 小设计亮点
**歧义度：** 21.5%

### 第 7 轮
**问：** 边缘场景：找不到匹配字体时 gutter 怎么表现？
**答：** 不显示任何 gutter icon
**歧义度：** 17.8%

</details>
