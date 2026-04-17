# IconFontViewer 插件升级 — 技术设计文档

## 一、概述

本文档基于 PRD（`deep-interview-iconfontviewer-plugin-upgrade.md`）产出，描述 IconFontViewer 插件升级的技术实现方案。涵盖架构设计、模块改动、Bug 修复方案和 UI 重构计划。

## 二、现有架构

```
plugin.xml（扩展点注册）
├── postStartupActivity → SearchStartupActivity（启动扫描 TTF）
├── lineMarkerProvider  → IconFontLineMarkerProvider（Gutter 图标）
│                          └── IconFontLineMarkerNavHandler（点击弹窗）
└── action              → MainAction（Tools 菜单入口）
                           └── TTFInputDialog → TTFMainPanel（管理面板）

IconFontSettings（项目级持久化服务）
├── IconFontState（FontInfo 列表持久化）
├── fontCache（HashMap<String, Font>，内存缓存）
└── iconPopupList（MutableList，弹窗数据）

Util.kt（核心工具方法）
├── getIconForElement()  — 根据 PSI 元素获取字体图标
├── loadFont()           — 加载 TTF 字体
└── getStringResourceValue() — 解析 Android string 资源值

UI 组件
├── IconFromIconFontCharacter（字体字符 → Swing Icon）
├── ScaledIcon（图标缩放）
└── SwitchButton（自定义动画开关）
```

## 三、改动计划

### 3.1 Bug 修复（优先级最高）

#### Bug 1：iconPopupList 无限增长

**现状：** `IconFontSettings.iconPopupList` 是一个全局 `MutableList`，`Util.getIconForElement()` 每次被调用时都往里 append，从不清理。导致弹窗数据越来越多，出现重复项。

**修复方案：**
- 移除 `IconFontSettings` 中的全局 `iconPopupList`
- 在 `IconFontLineMarkerNavHandler` 点击时，按需构建当前字体的图标列表
- 新增 `Util.buildIconListForFont(font: Font, project: Project): List<IconFontPopupModel>` 方法
- 该方法遍历字体的所有 string resource，过滤出当前字体能渲染的字符，返回列表

**改动文件：**
- `IconFontSettings.kt` — 移除 `iconPopupList` 字段
- `Util.kt` — 新增 `buildIconListForFont()`，移除 `getIconForElement()` 中的 append 逻辑
- `IconFontLineMarkerNavHandler.kt` — 调用新方法构建列表

#### Bug 2：fontCache 线程不安全

**现状：** `IconFontSettings.fontCache` 使用 `HashMap<String, Font>`，后台扫描线程（`SearchStartupActivity`）和 EDT 线程（`LineMarkerProvider`）并发读写。

**修复方案：**
- 将 `HashMap` 替换为 `ConcurrentHashMap`
- 字体加载方法使用 `computeIfAbsent()` 确保原子性

**改动文件：**
- `IconFontSettings.kt` — `fontCache` 类型改为 `ConcurrentHashMap`
- `Util.kt` — 字体加载改用 `fontCache.computeIfAbsent(path) { loadFont(path) }`

#### Bug 3：PsiElement 引用泄漏

**现状：** `IconFontLineMarkerNavHandler` 的构造函数接收 `PsiElement` 并持有引用，PsiElement 可能在文件编辑后失效，导致内存泄漏和潜在异常。

**修复方案：**
- 使用 `SmartPsiElementPointer` 替代直接持有 `PsiElement`
- 在使用时通过 `pointer.element` 获取当前有效的 PSI 元素，为 null 则跳过操作

**改动文件：**
- `IconFontLineMarkerNavHandler.kt` — 构造函数改为接收 `SmartPsiElementPointer`
- `IconFontLineMarkerProvider.kt` — 创建 NavHandler 时传入 `SmartPointerManager.createSmartPsiElementPointer(element)`

#### Bug 4：中文字符串硬编码

**现状：** `TTFMainPanel.kt` 中存在大量硬编码中文字符串（"IconFont TTF 文件管理"、"确定要从列表中移除"等）。

**修复方案：**
- 将所有 UI 文案迁移到 `strings.properties`
- 使用 `ResourceBundle` 或 IntelliJ 的 `message()` 方法加载

**改动文件：**
- `strings.properties` — 新增缺失的 key
- `TTFMainPanel.kt` — 所有硬编码字符串改为从 properties 读取

### 3.2 功能补齐

#### 功能 1：智能 TTF 检测策略

**现状：** `SearchStartupActivity.kt` 使用 `filename.contains("icon", ignoreCase = true)` 判断是否自动启用。

**改进方案：**
- 扩展关键词列表为：`["icon", "iconfont", "symbol", "glyph", "fontawesome"]`
- 提取为 `Constants.kt` 中的常量 `ICONFONT_KEYWORDS`
- 检测逻辑：`ICONFONT_KEYWORDS.any { fileName.contains(it, ignoreCase = true) }`
- 对已有用户偏好的字体（`IconFontState` 中已记录的），保留用户选择，不覆盖

**改动文件：**
- `Constants.kt` — 新增 `ICONFONT_KEYWORDS` 列表
- `SearchStartupActivity.kt` — 修改启用判断逻辑，增加"尊重用户已有选择"的逻辑

#### 功能 2：弹窗只展示当前字体的图标

**现状：** 弹窗使用全局 `iconPopupList`，包含所有字体的图标混合在一起。

**改进方案：**
- `IconFontLineMarkerProvider` 在创建 `LineMarkerInfo` 时，记录匹配到的字体信息
- `IconFontLineMarkerNavHandler` 在弹窗时，只构建**匹配字体**的图标列表
- 数据流：`PSI Element → 匹配字体 → 构建该字体的图标列表 → 弹窗展示`

**改动文件：**
- `IconFontLineMarkerProvider.kt` — 将匹配的 Font 信息传递给 NavHandler
- `IconFontLineMarkerNavHandler.kt` — 接收 Font 引用，弹窗时按需构建图标列表
- `Util.kt` — `getIconForElement()` 返回值改为包含 Font 信息的结构

#### 功能 3：持久化用户偏好（增强）

**现状：** `IconFontSettings` 已有持久化机制，但扫描时对已存在的 FontInfo 处理不够细致。

**改进方案：**
- 扫描时检查 `IconFontState.fontInfos` 是否已有该路径的记录
  - 已有 → 保留用户的 `enabled` 设置
  - 新发现 → 按智能检测规则设置默认值
- 用户手动添加的字体（source = USER）在重新扫描时不被覆盖或删除

**改动文件：**
- `SearchStartupActivity.kt` — 扫描合并逻辑优化
- `IconFontSettings.kt` — 确保 state 序列化/反序列化正确

### 3.3 UI 重构

#### 管理面板（TTFMainPanel）重构

**设计原则：** IntelliJ 原生组件为主 + 少量自定义亮点

**布局方案：**
```
┌──────────────────────────────────────────┐
│  IconFont TTF 文件管理           [标题栏] │
├──────────────────────────────────────────┤
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ ★ iconfont.ttf                    │  │
│  │   /src/main/assets/iconfont.ttf   │  │
│  │   [PROJECT]            [开关 ON]  │  │
│  ├────────────────────────────────────┤  │
│  │ T material-icons.ttf              │  │
│  │   /build/intermediates/...        │  │
│  │   [AAR]                [开关 OFF] │  │
│  ├────────────────────────────────────┤  │
│  │ T custom.ttf                      │  │
│  │   /Users/.../custom.ttf           │  │
│  │   [USER]               [开关 ON]  │  │
│  └────────────────────────────────────┘  │
│                                          │
├──────────────────────────────────────────┤
│  [+ 添加 TTF 文件]      [↻ 重新扫描]    │
└──────────────────────────────────────────┘
```

**组件选用：**
| 区域 | 组件 | 说明 |
|------|------|------|
| 外框 | `DialogWrapper` | IntelliJ 标准对话框 |
| 列表 | `JBList` + 自定义 `ListCellRenderer` | 原生列表组件，自定义渲染 |
| 来源标签 | 自定义绘制的彩色圆角标签 | PROJECT=绿色, AAR=蓝色, USER=橙色 |
| 开关 | `SwitchButton`（保留现有实现） | 自定义动画开关，已有代码 |
| 按钮 | `JButton` + IntelliJ 样式 | 原生按钮 |
| 空状态 | `JBLabel` 居中 | "未找到 TTF 文件" 提示 |

**改动文件：**
- `TTFMainPanel.kt` — 重写，使用 `JBList` 替代手动布局
- `TTFInputDialog.kt` — 简化，适配新面板

#### Gutter 弹窗优化

**现状：** 使用 `LightCalloutPopup`（来自 adtui），搜索和列表功能已有基础。

**优化方案：**
- 弹窗数据按需构建（不再使用全局 iconPopupList）
- 搜索使用 `SpeedSearch` + `NameFilteringListModel`（现有方案保留）
- 列表项展示：图标 + 资源名称
- 选中后的代码替换逻辑保持不变

**改动文件：**
- `IconFontLineMarkerNavHandler.kt` — 重构弹窗数据构建逻辑

## 四、数据流设计

### 4.1 启动扫描流程
```
AS 启动
  → SearchStartupActivity.runActivity()
    → ProgressManager.run(Backgroundable)
      → FilenameIndex.getAllFilenames() 过滤 .ttf
      → 对每个 TTF 判断 source（PROJECT/AAR）
      → 与 IconFontState.fontInfos 合并
        → 已有记录：保留用户 enabled 选择
        → 新发现：按 ICONFONT_KEYWORDS 判断默认值
      → 更新 IconFontState
```

### 4.2 Gutter 图标渲染流程
```
IDE 打开/编辑文件
  → IconFontLineMarkerProvider.getLineMarkerInfo()
    → 检查 PSI 元素类型（KtDotQualifiedExpression / PsiReferenceExpression / XmlAttributeValue）
    → 提取 string resource 名称
    → Util.getIconForElement()
      → 遍历所有 enabled 的 FontInfo
      → fontCache.computeIfAbsent() 加载字体
      → ResourceRepositoryManager 查找 string resource 值
      → Font.canDisplayUpTo() 检查字体是否包含该字符
      → 找到匹配 → 返回 (Icon, Font) 元组
    → 创建 LineMarkerInfo + NavHandler（持有 SmartPsiElementPointer + Font）
```

### 4.3 弹窗替换流程
```
用户点击 Gutter 图标
  → IconFontLineMarkerNavHandler.navigate()
    → Util.buildIconListForFont(matchedFont, project)
      → 遍历所有 string resource
      → 过滤出 matchedFont 能渲染的字符
      → 构建 List<IconFontPopupModel>
    → LightCalloutPopup 展示列表 + 搜索框
    → 用户选中一个图标
    → WriteCommandAction 替换代码中的 PSI 元素
```

## 五、文件改动清单

| 文件 | 改动类型 | 改动内容 |
|------|----------|----------|
| `IconFontSettings.kt` | 修改 | 移除 iconPopupList，fontCache 改为 ConcurrentHashMap |
| `SearchStartupActivity.kt` | 修改 | 智能检测关键词扩展，扫描合并逻辑优化 |
| `IconFontLineMarkerProvider.kt` | 修改 | 传递匹配 Font 信息给 NavHandler，使用 SmartPsiElementPointer |
| `IconFontLineMarkerNavHandler.kt` | 重构 | 使用 SmartPsiElementPointer，按需构建弹窗数据 |
| `Util.kt` | 修改 | 新增 buildIconListForFont()，getIconForElement() 返回 Font 信息，线程安全加载 |
| `TTFMainPanel.kt` | 重写 | 使用 JBList + 自定义 Renderer，IntelliJ 原生风格 |
| `TTFInputDialog.kt` | 修改 | 适配新面板 |
| `SwitchButton.kt` | 保留 | 保持现有动画开关实现不变 |
| `Constants.kt` | 修改 | 新增 ICONFONT_KEYWORDS 常量 |
| `extensions.kt` | 保留 | 无需改动 |
| `strings.properties` | 修改 | 新增所有硬编码中文字符串对应的 key |
| `IconFontPopupModel.kt` | 修改 | 可能需要增加 fontPath 字段 |
| `IconFontTtfFileModel.kt` | 保留 | 数据模型已满足需求 |
| `IconFromIconFontCharacter.kt` | 保留 | 渲染逻辑不变 |
| `ScaledIcon.kt` | 保留 | 工具类不变 |
| `MainAction.kt` | 保留 | 入口不变 |
| `plugin.xml` | 保留 | 扩展点注册不变 |

## 六、实施顺序

建议按以下顺序实施，每步可独立验证：

1. **Bug 修复**（基础稳定性）
   - fontCache → ConcurrentHashMap
   - PsiElement → SmartPsiElementPointer
   - 移除全局 iconPopupList

2. **功能补齐**（核心逻辑）
   - 智能 TTF 检测策略
   - 弹窗只展示当前字体图标
   - 持久化偏好合并优化

3. **UI 重构**（体验打磨）
   - TTFMainPanel 重写
   - 中文硬编码迁移到 strings.properties

4. **集成测试**
   - 在 Android 项目中验证完整流程
   - 检查启动扫描、gutter 预览、弹窗替换、面板管理

## 七、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `ResourceRepositoryManager` API 在不同 AS 版本间不兼容 | 资源查找失败 | 使用 try-catch 包裹，失败时静默降级 |
| 大型项目扫描大量 TTF 文件导致启动变慢 | 用户体验差 | 已有 Background Task 机制，确保不阻塞 UI 线程 |
| `buildIconListForFont()` 遍历所有 string resource 耗时 | 弹窗打开慢 | 可加缓存，按 font path 缓存图标列表，字体启用状态变化时清缓存 |
| SwitchButton 自定义组件在新 IDE 主题下显示异常 | UI 不协调 | 使用 IntelliJ named colors（`JBColor`）确保主题兼容 |
