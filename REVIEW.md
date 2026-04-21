# IconFontViewer 项目代码审查报告

**审查日期**: 2026-04-20  
**审查范围**: 全部 15 个 Kotlin 源文件（1314 行）  
**审查重点**: Bug、性能隐患、代码质量

---

## 一、Bug（共 5 个）

### BUG-1: [严重] 字体清理逻辑永远不生效

**文件**: `SearchStartupActivity.kt:104-107`

```kotlin
val allKnownPaths = discoveredPaths + existingPaths.keys
settings.state.fontInfos.removeAll { fi ->
    fi.source != FontSource.USER && fi.path !in allKnownPaths && !java.io.File(fi.path).exists()
}
```

`existingPaths` 是从 `settings.state.fontInfos` 构建的，包含所有当前条目的 path。因此 `allKnownPaths` 永远包含所有已有条目的 path，`fi.path !in allKnownPaths` **永远为 false**，`removeAll` 永远不会移除任何条目。

**影响**: 删除的 TTF 文件对应的条目永远不会被自动清理。

**修复建议**: 改为只检查 `discoveredPaths`：
```kotlin
settings.state.fontInfos.removeAll { fi ->
    fi.source != FontSource.USER && fi.path !in discoveredPaths && !java.io.File(fi.path).exists()
}
```

---

### BUG-2: [中等] Gutter 图标在暗色主题下不可见

**文件**: `IconFromIconFontCharacter.kt:40`

```kotlin
graphics.color = JBColor.BLACK
```

图标颜色硬编码为黑色。在 IDE 暗色主题（Darcula/New UI Dark）下，黑色图标在深色 gutter 背景上几乎不可见。

**修复建议**:
```kotlin
graphics.color = JBColor.foreground()
```

---

### BUG-3: [中等] animTimer 没有在面板销毁时停止

**文件**: `TTFMainPanel.kt:59-73`

```kotlin
private val animTimer = Timer(12) { ... }
```

`TTFMainPanel` 没有实现 `Disposable`，对话框关闭时 Timer 不会停止。Timer 持有对面板和 `fontList` 的引用，导致：
- Timer 线程继续运行
- 已关闭的 Swing 组件无法被 GC 回收（内存泄漏）

**修复建议**: 让 `TTFMainPanel` 实现 `Disposable`，在 `dispose()` 中停止 Timer；或在 `TTFInputDialog` 关闭时清理。

---

### BUG-4: [低] `isValidResXmlToken` 匹配范围过宽

**文件**: `extensions.kt:45-47`

```kotlin
fun PsiElement.isValidResXmlToken(): Boolean {
    return this is XmlTag
}
```

这个函数对**所有** `XmlTag` 返回 true，没有任何约束条件。虽然目前只在 `removePrefix()` 和 `doReplace()` 中作为最后的 fallback 使用，但如果其他匹配条件都不满足时可能导致意外替换。

---

### BUG-5: [低] `decodeIconFontValue` 未处理 `&#xE88A` 缺少分号的情况

**文件**: `extensions.kt:89-101`

某些编辑器或工具可能生成不带分号的 `&#xE88A` 格式。当前正则 `&#x([0-9a-fA-F]+);` 要求分号结尾，这类格式会被跳过。实际影响较小，因为标准 XML 都带分号。

---

## 二、性能隐患（共 5 个）

### PERF-1: [高] 字体加载失败时每次都重试

**文件**: `Util.kt:46-54`、`Util.kt:124-131`

```kotlin
val font = instance.fontCache.computeIfAbsent(fontInfo.path) { path ->
    try {
        Font.createFont(Font.TRUETYPE_FONT, File(path))
    } catch (e: Exception) {
        null  // ConcurrentHashMap 不存储 null，下次还会重试
    }
} ?: continue
```

`ConcurrentHashMap.computeIfAbsent()` 当 mapping function 返回 null 时不会存入任何值。如果某个字体文件损坏或不可读，**每次** LineMarkerProvider 调用都会重新尝试 `Font.createFont()`，触发磁盘 IO 和异常抛出。

**修复建议**: 使用哨兵值标记加载失败：
```kotlin
private val FAILED_FONT = Font("Dialog", Font.PLAIN, 1)  // 哨兵

val font = instance.fontCache.computeIfAbsent(fontInfo.path) { path ->
    try { Font.createFont(...) } catch (e: Exception) { FAILED_FONT }
}
if (font === FAILED_FONT) continue
```

---

### PERF-2: [高] `buildIconListForFont` 每次点击都全量遍历 string 资源

**文件**: `Util.kt:69-95`

每次点击 gutter 图标弹出列表时，都会遍历项目中**所有** string 资源，逐一检查 `looksLikeIconChar()` 和 `font.canDisplayUpTo()`。

对于大型 Android 项目（几千个 string 资源），弹窗打开会有明显延迟。

**修复建议**: 
- 对结果做缓存（key = fontPath + 资源修改时间戳）
- 或在后台线程预计算，弹窗打开时直接使用

---

### PERF-3: [中等] Regex 对象在 `decodeIconFontValue` 中每次调用都重新编译

**文件**: `extensions.kt:89-101`

```kotlin
result = result.replace(Regex("&#x([0-9a-fA-F]+);")) { ... }
result = result.replace(Regex("&#(\\d+);")) { ... }
result = result.replace(Regex("\\\\u([0-9a-fA-F]{4})")) { ... }
```

`getStringTagValue()` 在编辑器滚动/输入时被 LineMarkerProvider 频繁调用，每次创建 3 个 Regex 对象。Kotlin 的 `Regex` 构造会编译正则表达式，有不可忽略的开销。

**修复建议**: 提取为顶层常量：
```kotlin
private val HEX_CHAR_REF = Regex("&#x([0-9a-fA-F]+);")
private val DEC_CHAR_REF = Regex("&#(\\d+);")
private val UNICODE_ESCAPE = Regex("\\\\u([0-9a-fA-F]{4})")
```

---

### PERF-4: [中等] `getAllFilenames` 一次性加载所有文件名

**文件**: `SearchStartupActivity.kt:50-51`

```kotlin
val allTtfFiles = FilenameIndex.getAllFilenames(project)
    .filter { it.endsWith(".ttf", ignoreCase = true) }
```

先加载项目中**所有文件名**到内存，再过滤出 `.ttf`。对于超大型项目（几万个文件），有内存压力。

**修复建议**: 使用 `FilenameIndex.processAllFileNames()` 流式处理，或直接用 `FilenameIndex.getVirtualFilesByName()` 配合已知的常见字体文件名模式。

---

### PERF-5: [低] 动画 Timer 间隔过短

**文件**: `TTFMainPanel.kt:59`

```kotlin
private val animTimer = Timer(12) { ... }
```

12ms 间隔约 83fps。对于简单的开关滑动动画，16ms（60fps）足够流畅，且减少 CPU 占用。

---

## 三、代码质量（共 5 个）

### QUALITY-1: 死代码 — SwitchButton.kt

`SwitchButton.kt`（120 行）整个类未被项目中任何地方引用。`TTFMainPanel` 使用自定义的 `paintToggle()` 方法绘制开关。建议删除。

### QUALITY-2: 死代码 — ScaledIcon.kt

`ScaledIcon.kt`（33 行）未被项目中任何地方引用。建议删除。

### QUALITY-3: 文件名与内容不匹配

`model/IconFontTtfFileModel.kt` 实际包含 `FontInfo` 数据类和 `FontSource` 枚举，文件名具有误导性。建议重命名为 `FontInfo.kt`。

### QUALITY-4: `getIconForElement` 与 `getIconForStringResourceTag` 逻辑重复

**文件**: `Util.kt:36-63` 与 `Util.kt:117-140`

两个函数的字体匹配逻辑几乎完全一样（遍历 enabledFontInfos → computeIfAbsent 加载字体 → canDisplayUpTo 检查 → 创建 Icon），仅获取 `charText` 的方式不同。可提取公共方法减少重复。

### QUALITY-5: `Util.kt:72` 使用了废弃的 `ResourceNamespace.TODO()`

```kotlin
val namespace = ResourceNamespace.TODO()
```

`ResourceNamespace.TODO()` 是 Android 工具链中标记"待迁移到正确命名空间"的占位 API，未来版本可能移除。应改为 `ResourceNamespace.RES_AUTO`。

---

## 四、汇总

| 类别 | 严重 | 中等 | 低 |
|------|------|------|------|
| Bug | 1 | 2 | 2 |
| 性能 | 2 | 2 | 1 |
| 质量 | — | — | 5 |

**建议优先处理**:
1. BUG-1（清理逻辑失效）— 一行代码修复
2. BUG-2（暗色主题图标不可见）— 一行代码修复
3. PERF-1（字体加载失败重试）— 影响编辑器流畅度
4. PERF-3（Regex 重复编译）— 简单优化
5. BUG-3（Timer 泄漏）— 需要少量重构
