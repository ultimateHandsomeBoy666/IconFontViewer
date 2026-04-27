# IconFontViewer

<img src="src/main/resources/META-INF/pluginIcon.svg" width="36" height="36" align="top"/> 

Android Studio 插件 — 在编辑器 gutter 中预览 iconfont 图标。

## 功能

- **Gutter 预览** — Kotlin/Java 中的 `R.string.xxx`、XML 中的 `@string/xxx`、strings.xml 中的 `<string>` 定义，均可在行号旁直接看到图标
- **点击替换** — 点击 gutter 图标，浏览该字体所有可用图标并一键替换
- **智能扫描** — 启动时自动扫描项目和 AAR 依赖中的 TTF 字体文件
- **字体管理** — 通过 Tools → IconFontViewer 管理字体的启用/禁用、手动添加和删除

## 截图

### Gutter 图标预览
![Gutter Preview](pics/iconfont-preview.png)

### 图标搜索与替换
![Icon Popup](pics/iconfont-popup.png)

### 字体管理面板
![Font Panel](pics/iconfont-panel.png)

## 安装

**方式一：Marketplace（推荐）**

Settings → Plugins → Marketplace → 搜索 "IconFontViewer" → Install

**方式二：本地安装**

1. 下载 `IconFontViewer-1.0.0.zip`
2. Settings → Plugins → ⚙️ → Install Plugin from Disk → 选择 zip 文件
3. 重启 Android Studio

## 使用

1. 项目中准备好 iconfont TTF 文件（放在 `assets/` 或 `res/font/` 下，或通过 AAR 依赖引入）
2. 在 `strings.xml` 中定义图标资源：`<string name="icon_home">&#xE88A;</string>`
3. 在代码中引用 `R.string.icon_home`，gutter 处自动显示图标预览
4. 通过 Tools → IconFontViewer 管理字体列表

## 兼容性

| 项目 | 要求 |
|------|------|
| Android Studio | 2024.1 (Koala) 及以上所有版本 |
| 字体格式 | TrueType (.ttf) |
| 字符编码 | `&#xHHHH;`、`&#DDDD;`、`\uHHHH`、原始 Unicode 字符 |

## 工作原理

```mermaid
flowchart LR
    A["AS 启动"] --> B["扫描 TTF"]
    B --> B1["项目源码<br/>FilenameIndex"]
    B --> B2["AAR 依赖<br/>OrderEnumerator"]
    B1 & B2 --> C["FontInfo 列表<br/>IconFontSettings"]

    D["编辑器打开文件"] --> E["LineMarkerProvider"]
    E --> E1{"PSI 元素类型?"}
    E1 -->|"R.string.xxx"| F1["查找 string 资源值"]
    E1 -->|"@string/xxx"| F1
    E1 -->|"strings.xml 定义"| F2["读取标签文本<br/>解码字符引用"]

    F1 & F2 --> G["遍历已启用字体"]
    G --> H{"font.canDisplayUpTo<br/>== -1 ?"}
    H -->|"匹配"| I["渲染 gutter 图标"]
    H -->|"不匹配"| G

    I --> J["点击 → 弹窗浏览<br/>所有可用图标"]
    J --> K["选择 → 替换引用"]
```

## 测试项目

[IconFontDemo](https://github.com/ultimateHandsomeBoy666/IconFontDemo)

## License

Apache 2.0
