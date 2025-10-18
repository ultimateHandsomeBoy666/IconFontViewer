# IconFontViewer TTF 扫描系统技术实现方案

## 1. 概述

本文档详细描述了 IconFontViewer 插件 TTF 扫描与管理系统的技术实现方案，包括代码架构优化、具体实现细节和代码改动清单。

## 2. 现有代码分析与问题识别

### 2.1 当前实现问题

通过分析现有代码，发现以下问题：

#### 问题1：扫描范围有限
```kotlin
// 当前代码 (SearchStartupActivity.kt:37-56)
private fun buildFontModelListHolder(project: Project) {
    val scope = GlobalSearchScope.allScope(project)
    // 只扫描项目文件，未包含AAR依赖
    if (!file.path.contains("/build/")) { // 过滤规则过于简单
        FontModelListHolder.putFont(file.path, filename)
    }
}
```
**问题**：
- 只扫描项目文件，不包含AAR依赖中的TTF
- 过滤规则过于简单，可能遗漏或误判

#### 问题2：数据模型不完整
```kotlin
// 当前数据模型 (IconFontTtfFileModel.kt:5-10)
data class IconFontTtfFileModel(
    var selected: Boolean,     // 命名不明确
    var ttfFileName: String,
    var ttfAbsolutePath: String,
    var font: Font
    // 缺少来源信息、类型分类等
)
```
**问题**：
- 缺少TTF来源信息（项目/AAR/手动添加）
- 缺少智能分类信息
- 字段命名不够语义化

#### 问题3：UI管理逻辑混乱
```kotlin
// 当前UI代码 (TTFMainPanel.kt:22-24)
FontModelListHolder.getFontModelList().forEach {
    containerPanel.add(buildItemPanel(it))
}
```
**问题**：
- UI直接依赖数据层，耦合度高
- 缺少状态管理和视觉反馈
- 没有区分不同来源的TTF处理逻辑

## 3. 技术架构设计

### 3.1 整体架构

```
┌─────────────────────────┐
│     UI Layer            │
│  ┌─────────────────────┐│
│  │  TTFMainPanel       ││  ← 重构UI组件
│  │  TTFListItemView    ││
│  └─────────────────────┘│
└─────────────────────────┘
           │
┌─────────────────────────┐
│   Service Layer         │
│  ┌─────────────────────┐│
│  │  TTFScanService     ││  ← 新增扫描服务
│  │  TTFClassifier      ││  ← 新增智能分类器
│  │  TTFStateManager    ││  ← 新增状态管理器
│  └─────────────────────┘│
└─────────────────────────┘
           │
┌─────────────────────────┐
│    Data Layer           │
│  ┌─────────────────────┐│
│  │  TTFRepository      ││  ← 重构数据管理
│  │  TTFConfigStorage   ││  ← 新增配置存储
│  └─────────────────────┘│
└─────────────────────────┘
```

### 3.2 核心组件设计

#### 3.2.1 增强的数据模型

```kotlin
// 新的数据模型
data class IconFontTtfFileModel(
    val id: String,                    // 唯一标识
    var enabled: Boolean,              // 是否启用
    val ttfFileName: String,           // 文件名
    val ttfAbsolutePath: String,       // 绝对路径
    val font: Font?,                   // 字体对象（懒加载）
    val source: TTFSource,             // 来源信息
    val type: TTFType,                 // 分类类型
    var userModified: Boolean = false, // 用户是否手动修改过
    val createTime: Long = System.currentTimeMillis(), // 创建时间
    val fileSize: Long = 0             // 文件大小
)

enum class TTFType {
    ICON_FONT,      // 图标字体（命中启发式规则）
    REGULAR_FONT    // 普通字体（未命中规则）
}

sealed class TTFSource {
    object ProjectAssets : TTFSource()
    object UserAdded : TTFSource()
    data class AARDependency(
        val aarName: String,
        val version: String,
        val groupId: String = "",
        val artifactId: String = ""
    ) : TTFSource()
}
```

#### 3.2.2 TTF扫描服务

```kotlin
@Service(Service.Level.APP)
class TTFScanService : Disposable {

    companion object {
        @JvmStatic
        fun getInstance(): TTFScanService = service()
    }

    private val classifier = TTFClassifier()
    private val excludedPaths = setOf(
        "/build/", "/.git/", "/.idea/", "/.gradle/",
        "/tmp/", "/temp/", "/cache/"
    )

    /**
     * 扫描项目中的所有TTF文件
     */
    suspend fun scanProjectTTFs(project: Project): List<IconFontTtfFileModel> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<IconFontTtfFileModel>()

            // 1. 扫描项目文件
            results.addAll(scanProjectAssets(project))

            // 2. 扫描AAR依赖
            results.addAll(scanAARDependencies(project))

            // 3. 加载用户手动添加的TTF
            results.addAll(loadUserAddedTTFs())

            results
        }
    }

    /**
     * 扫描项目资源文件夹
     */
    private fun scanProjectAssets(project: Project): List<IconFontTtfFileModel> {
        val results = mutableListOf<IconFontTtfFileModel>()
        val scope = GlobalSearchScope.projectScope(project)

        FilenameIndex.getAllFilenames(project)
            .filter { it.endsWith(".ttf", ignoreCase = true) }
            .forEach { filename ->
                FilenameIndex.getVirtualFilesByName(filename, scope)
                    .filterNot { file -> excludedPaths.any { excluded -> file.path.contains(excluded) } }
                    .forEach { file ->
                        val ttfModel = createTTFModel(
                            file = file,
                            source = TTFSource.ProjectAssets
                        )
                        results.add(ttfModel)
                    }
            }

        return results
    }

    /**
     * 扫描AAR依赖中的TTF文件
     */
    private fun scanAARDependencies(project: Project): List<IconFontTtfFileModel> {
        val results = mutableListOf<IconFontTtfFileModel>()

        try {
            // 获取Android模块
            val androidFacet = AndroidFacet.getInstance(project) ?: return results

            // 获取所有AAR依赖
            val aarDependencies = getAARDependencies(androidFacet)

            aarDependencies.forEach { aar ->
                val ttfFiles = findTTFsInAAR(aar)
                ttfFiles.forEach { ttfFile ->
                    val ttfModel = createTTFModel(
                        file = ttfFile,
                        source = TTFSource.AARDependency(
                            aarName = aar.name,
                            version = aar.version,
                            groupId = aar.groupId,
                            artifactId = aar.artifactId
                        )
                    )
                    results.add(ttfModel)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to scan AAR dependencies", e)
        }

        return results
    }

    /**
     * 获取AAR依赖信息
     */
    private fun getAARDependencies(androidFacet: AndroidFacet): List<AARInfo> {
        // 通过Android Gradle Plugin API获取依赖信息
        val gradleModel = GradleAndroidModel.get(androidFacet)
        val variant = gradleModel?.selectedVariant

        return variant?.mainArtifact?.dependencies?.libraries
            ?.filter { it.artifactAddress.endsWith(".aar") }
            ?.map { dependency ->
                AARInfo(
                    name = dependency.artifactAddress,
                    version = extractVersion(dependency.artifactAddress),
                    groupId = extractGroupId(dependency.artifactAddress),
                    artifactId = extractArtifactId(dependency.artifactAddress),
                    path = dependency.artifact?.absolutePath ?: ""
                )
            } ?: emptyList()
    }

    /**
     * 在AAR中查找TTF文件
     */
    private fun findTTFsInAAR(aar: AARInfo): List<VirtualFile> {
        val results = mutableListOf<VirtualFile>()

        try {
            val aarFile = File(aar.path)
            if (!aarFile.exists()) return results

            // 解压AAR并搜索TTF文件
            ZipFile(aarFile).use { zipFile ->
                zipFile.entries().asSequence()
                    .filter { entry ->
                        entry.name.endsWith(".ttf", ignoreCase = true) &&
                        !entry.isDirectory &&
                        isValidTTFPath(entry.name)
                    }
                    .forEach { entry ->
                        // 提取TTF文件到临时目录并创建VirtualFile
                        val tempFile = extractTTFToTemp(zipFile, entry, aar)
                        val virtualFile = VfsUtil.findFileByIoFile(tempFile, true)
                        virtualFile?.let { results.add(it) }
                    }
            }
        } catch (e: Exception) {
            logger.warn("Failed to scan TTF in AAR: ${aar.name}", e)
        }

        return results
    }

    /**
     * 创建TTF模型对象
     */
    private fun createTTFModel(file: VirtualFile, source: TTFSource): IconFontTtfFileModel {
        val type = classifier.classifyTTF(file.name, file.path)
        val enabled = when (source) {
            is TTFSource.UserAdded -> true  // 用户添加的强制启用
            else -> type == TTFType.ICON_FONT  // 其他根据分类决定
        }

        return IconFontTtfFileModel(
            id = generateTTFId(file.path, source),
            enabled = enabled,
            ttfFileName = file.name,
            ttfAbsolutePath = file.path,
            font = null,  // 懒加载
            source = source,
            type = type,
            fileSize = file.length
        )
    }

    override fun dispose() {
        // 清理资源
    }

    companion object {
        private val logger = Logger.getInstance(TTFScanService::class.java)
    }
}

data class AARInfo(
    val name: String,
    val version: String,
    val groupId: String,
    val artifactId: String,
    val path: String
)
```

#### 3.2.3 智能分类器

```kotlin
class TTFClassifier {

    companion object {
        private val ICON_FONT_KEYWORDS = setOf(
            "icon", "iconfont", "fontawesome", "material",
            "symbol", "glyph", "pictogram", "webfont"
        )

        private val ICON_FONT_PATTERNS = listOf(
            Regex(".*icon.*", RegexOption.IGNORE_CASE),
            Regex(".*font.*awesome.*", RegexOption.IGNORE_CASE),
            Regex(".*material.*icon.*", RegexOption.IGNORE_CASE),
            Regex(".*symbol.*", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * 基于启发式规则分类TTF文件
     */
    fun classifyTTF(fileName: String, filePath: String): TTFType {
        val lowerName = fileName.lowercase()
        val lowerPath = filePath.lowercase()

        // 1. 文件名关键词匹配
        if (ICON_FONT_KEYWORDS.any { lowerName.contains(it) }) {
            return TTFType.ICON_FONT
        }

        // 2. 正则表达式匹配
        if (ICON_FONT_PATTERNS.any { it.matches(lowerName) }) {
            return TTFType.ICON_FONT
        }

        // 3. 路径特征匹配
        if (lowerPath.contains("icons") || lowerPath.contains("iconfont")) {
            return TTFType.ICON_FONT
        }

        return TTFType.REGULAR_FONT
    }

    /**
     * 计算分类置信度
     */
    fun getClassificationConfidence(fileName: String, filePath: String): Float {
        val lowerName = fileName.lowercase()
        var confidence = 0.0f

        // 关键词匹配权重
        ICON_FONT_KEYWORDS.forEach { keyword ->
            if (lowerName.contains(keyword)) {
                confidence += when (keyword) {
                    "iconfont" -> 0.9f
                    "icon" -> 0.7f
                    "fontawesome" -> 0.8f
                    "material" -> 0.6f
                    else -> 0.5f
                }
            }
        }

        return minOf(confidence, 1.0f)
    }
}
```

#### 3.2.4 状态管理器

```kotlin
@Service(Service.Level.PROJECT)
class TTFStateManager(private val project: Project) : Disposable {

    companion object {
        @JvmStatic
        fun getInstance(project: Project): TTFStateManager = project.service()
    }

    private val ttfRepository: TTFRepository by lazy { project.service() }
    private val configStorage: TTFConfigStorage by lazy { service() }
    private val listeners = mutableListOf<TTFStateChangeListener>()

    /**
     * 获取所有TTF状态
     */
    fun getAllTTFs(): List<IconFontTtfFileModel> {
        return ttfRepository.getAll()
    }

    /**
     * 更新TTF启用状态
     */
    fun updateTTFState(ttfId: String, enabled: Boolean) {
        val ttf = ttfRepository.findById(ttfId) ?: return

        // 手动添加的TTF不允许禁用
        if (ttf.source is TTFSource.UserAdded && !enabled) {
            return
        }

        val updatedTTF = ttf.copy(
            enabled = enabled,
            userModified = true
        )

        ttfRepository.update(updatedTTF)
        configStorage.saveTTFState(ttfId, enabled)

        notifyStateChanged(updatedTTF)
    }

    /**
     * 添加用户TTF
     */
    fun addUserTTF(filePath: String): IconFontTtfFileModel? {
        val file = File(filePath)
        if (!file.exists() || !file.name.endsWith(".ttf", ignoreCase = true)) {
            return null
        }

        val ttf = IconFontTtfFileModel(
            id = generateTTFId(filePath, TTFSource.UserAdded),
            enabled = true,  // 强制启用
            ttfFileName = file.name,
            ttfAbsolutePath = filePath,
            font = null,
            source = TTFSource.UserAdded,
            type = TTFClassifier().classifyTTF(file.name, filePath),
            fileSize = file.length()
        )

        ttfRepository.add(ttf)
        configStorage.saveUserTTF(filePath)

        notifyStateChanged(ttf)
        return ttf
    }

    /**
     * 删除用户TTF
     */
    fun removeUserTTF(ttfId: String): Boolean {
        val ttf = ttfRepository.findById(ttfId) ?: return false

        // 只能删除用户添加的TTF
        if (ttf.source !is TTFSource.UserAdded) {
            return false
        }

        ttfRepository.remove(ttfId)
        configStorage.removeUserTTF(ttfId)

        notifyTTFRemoved(ttf)
        return true
    }

    /**
     * 刷新扫描
     */
    suspend fun refreshScan() {
        // 保存用户配置
        val userStates = ttfRepository.getAll()
            .filter { it.userModified }
            .associate { it.id to it.enabled }

        // 重新扫描
        val scanService = service<TTFScanService>()
        val newTTFs = scanService.scanProjectTTFs(project)

        // 应用用户配置
        newTTFs.forEach { ttf ->
            if (userStates.containsKey(ttf.id)) {
                ttf.enabled = userStates[ttf.id] ?: ttf.enabled
                ttf.userModified = true
            }
        }

        // 更新仓库
        ttfRepository.replaceAll(newTTFs)

        notifyRefreshCompleted(newTTFs)
    }

    private fun notifyStateChanged(ttf: IconFontTtfFileModel) {
        listeners.forEach { it.onTTFStateChanged(ttf) }
    }

    private fun notifyTTFRemoved(ttf: IconFontTtfFileModel) {
        listeners.forEach { it.onTTFRemoved(ttf) }
    }

    private fun notifyRefreshCompleted(ttfs: List<IconFontTtfFileModel>) {
        listeners.forEach { it.onRefreshCompleted(ttfs) }
    }

    fun addStateChangeListener(listener: TTFStateChangeListener) {
        listeners.add(listener)
    }

    fun removeStateChangeListener(listener: TTFStateChangeListener) {
        listeners.remove(listener)
    }

    override fun dispose() {
        listeners.clear()
    }
}

interface TTFStateChangeListener {
    fun onTTFStateChanged(ttf: IconFontTtfFileModel)
    fun onTTFRemoved(ttf: IconFontTtfFileModel)
    fun onRefreshCompleted(ttfs: List<IconFontTtfFileModel>)
}
```

#### 3.2.5 配置存储

```kotlin
@Service(Service.Level.APP)
class TTFConfigStorage : Disposable {

    companion object {
        private const val TTF_STATES_KEY = "iconfonts.ttf.states"
        private const val USER_TTFS_KEY = "iconfonts.user.ttfs"

        @JvmStatic
        fun getInstance(): TTFConfigStorage = service()
    }

    private val applicationConfig = PropertiesComponent.getInstance()

    /**
     * 保存TTF启用状态
     */
    fun saveTTFState(ttfId: String, enabled: Boolean) {
        val states = getTTFStates().toMutableMap()
        states[ttfId] = enabled

        val stateJson = GsonBuilder().create().toJson(states)
        applicationConfig.setValue(TTF_STATES_KEY, stateJson)
    }

    /**
     * 获取TTF启用状态
     */
    fun getTTFStates(): Map<String, Boolean> {
        val stateJson = applicationConfig.getValue(TTF_STATES_KEY, "{}")
        return try {
            GsonBuilder().create().fromJson(stateJson, object : TypeToken<Map<String, Boolean>>() {}.type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 保存用户添加的TTF
     */
    fun saveUserTTF(filePath: String) {
        val userTTFs = getUserTTFs().toMutableSet()
        userTTFs.add(filePath)

        val ttfsJson = GsonBuilder().create().toJson(userTTFs.toList())
        applicationConfig.setValue(USER_TTFS_KEY, ttfsJson)
    }

    /**
     * 获取用户添加的TTF列表
     */
    fun getUserTTFs(): Set<String> {
        val ttfsJson = applicationConfig.getValue(USER_TTFS_KEY, "[]")
        return try {
            val list: List<String> = GsonBuilder().create().fromJson(ttfsJson, object : TypeToken<List<String>>() {}.type)
            list.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * 移除用户TTF
     */
    fun removeUserTTF(ttfId: String) {
        // 根据ID反推文件路径并移除
        val userTTFs = getUserTTFs().toMutableSet()
        // 实际实现需要维护ID到路径的映射
        // 这里简化处理
        val ttfsJson = GsonBuilder().create().toJson(userTTFs.toList())
        applicationConfig.setValue(USER_TTFS_KEY, ttfsJson)
    }

    override fun dispose() {
        // 配置存储不需要特殊清理
    }
}
```

#### 3.2.6 TTF数据仓库

```kotlin
@Service(Service.Level.PROJECT)
class TTFRepository : Disposable {

    companion object {
        @JvmStatic
        fun getInstance(project: Project): TTFRepository = project.service()
    }

    private val ttfMap = ConcurrentHashMap<String, IconFontTtfFileModel>()

    /**
     * 添加TTF
     */
    fun add(ttf: IconFontTtfFileModel): Boolean {
        if (ttfMap.containsKey(ttf.id)) {
            return false
        }
        ttfMap[ttf.id] = ttf
        return true
    }

    /**
     * 获取所有TTF
     */
    fun getAll(): List<IconFontTtfFileModel> = ttfMap.values.toList()

    /**
     * 根据ID查找TTF
     */
    fun findById(id: String): IconFontTtfFileModel? = ttfMap[id]

    /**
     * 更新TTF
     */
    fun update(ttf: IconFontTtfFileModel) {
        ttfMap[ttf.id] = ttf
    }

    /**
     * 删除TTF
     */
    fun remove(id: String): IconFontTtfFileModel? = ttfMap.remove(id)

    /**
     * 清空所有TTF
     */
    fun clear() {
        ttfMap.clear()
    }

    /**
     * 批量替换所有TTF
     */
    fun replaceAll(ttfs: List<IconFontTtfFileModel>) {
        ttfMap.clear()
        ttfs.forEach { ttfMap[it.id] = it }
    }

    /**
     * 获取指定来源的TTF
     */
    fun getBySource(source: Class<out TTFSource>): List<IconFontTtfFileModel> {
        return ttfMap.values.filter { source.isInstance(it.source) }
    }

    /**
     * 获取启用的TTF
     */
    fun getEnabled(): List<IconFontTtfFileModel> {
        return ttfMap.values.filter { it.enabled }
    }

    override fun dispose() {
        ttfMap.clear()
    }
}
```

## 4. UI层重构

### 4.1 重构后的 TTFMainPanel

```kotlin
class TTFMainPanel(private val project: Project) : JPanel(), TTFStateChangeListener {

    private val stateManager: TTFStateManager by lazy { project.service() }
    private val listModel = DefaultListModel<IconFontTtfFileModel>()
    private val ttfList = JBList(listModel)

    init {
        setupUI()
        stateManager.addStateChangeListener(this)
        loadTTFs()
    }

    private fun setupUI() {
        layout = BorderLayout()

        // 使用说明
        add(createLegendPanel(), BorderLayout.NORTH)

        // TTF 列表
        ttfList.cellRenderer = TTFListCellRenderer()
        ttfList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val scrollPane = JBScrollPane(ttfList).apply {
            border = BorderFactory.createEmptyBorder()
            preferredSize = Dimension(700, 400)
        }
        add(scrollPane, BorderLayout.CENTER)

        // 操作按钮
        add(createActionPanel(), BorderLayout.SOUTH)
    }

    private fun createLegendPanel(): JPanel {
        // 使用说明面板实现
        return JPanel().apply {
            // ... 实现使用说明UI
        }
    }

    private fun createActionPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("重新扫描").apply {
                addActionListener { refreshScan() }
            })

            add(JButton("添加 TTF 文件").apply {
                addActionListener { addUserTTF() }
            })
        }
    }

    private fun loadTTFs() {
        val ttfs = stateManager.getAllTTFs()
        listModel.clear()
        ttfs.forEach { listModel.addElement(it) }
    }

    private fun refreshScan() {
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                stateManager.refreshScan()
            }
        }
    }

    private fun addUserTTF() {
        val chooser = FileChooserDescriptorFactory.createSingleFileDescriptor("ttf")
        chooser.title = "选择 TTF 文件"

        val file = FileChooser.chooseFile(chooser, project, null)
        file?.let {
            stateManager.addUserTTF(it.path)
        }
    }

    // TTFStateChangeListener 实现
    override fun onTTFStateChanged(ttf: IconFontTtfFileModel) {
        SwingUtilities.invokeLater {
            val index = listModel.indexOf(ttf)
            if (index >= 0) {
                listModel.setElementAt(ttf, index)
            }
        }
    }

    override fun onTTFRemoved(ttf: IconFontTtfFileModel) {
        SwingUtilities.invokeLater {
            listModel.removeElement(ttf)
        }
    }

    override fun onRefreshCompleted(ttfs: List<IconFontTtfFileModel>) {
        SwingUtilities.invokeLater {
            listModel.clear()
            ttfs.forEach { listModel.addElement(it) }
        }
    }
}
```

### 4.2 自定义列表项渲染器

```kotlin
class TTFListCellRenderer : ListCellRenderer<IconFontTtfFileModel> {

    override fun getListCellRendererComponent(
        list: JList<out IconFontTtfFileModel>,
        value: IconFontTtfFileModel,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        return TTFListItemPanel(value, isSelected)
    }
}

class TTFListItemPanel(
    private val ttf: IconFontTtfFileModel,
    private val isSelected: Boolean
) : JPanel() {

    init {
        setupUI()
    }

    private fun setupUI() {
        layout = BorderLayout()

        // 左侧图标
        add(createIconPanel(), BorderLayout.WEST)

        // 中间信息
        add(createInfoPanel(), BorderLayout.CENTER)

        // 右侧控制
        add(createControlPanel(), BorderLayout.EAST)

        // 应用状态样式
        applyStateStyle()
    }

    private fun createIconPanel(): JComponent {
        val iconPanel = JPanel().apply {
            preferredSize = Dimension(50, 40)
            layout = BorderLayout()
        }

        val iconLabel = JLabel().apply {
            text = if (ttf.type == TTFType.ICON_FONT) "★" else "T"
            horizontalAlignment = SwingConstants.CENTER
            font = font.deriveFont(20f)

            if (ttf.type == TTFType.ICON_FONT) {
                foreground = JBColor(0x4c7fef, 0x6366f1)
                // 添加渐变背景效果
            } else {
                foreground = JBColor.GRAY
            }
        }

        iconPanel.add(iconLabel, BorderLayout.CENTER)
        return iconPanel
    }

    private fun createInfoPanel(): JComponent {
        val infoPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // 文件名
        val nameLabel = JLabel(ttf.ttfFileName).apply {
            font = font.deriveFont(Font.BOLD, 14f)
        }

        // 文件路径
        val pathLabel = JLabel(ttf.ttfAbsolutePath).apply {
            font = Font("JetBrains Mono", Font.PLAIN, 11)
            foreground = JBColor.GRAY
        }

        // AAR信息（如果适用）
        if (ttf.source is TTFSource.AARDependency) {
            val aarLabel = JLabel("AAR: ${ttf.source.aarName}").apply {
                font = font.deriveFont(10f)
                foreground = JBColor.GRAY
            }
            infoPanel.add(aarLabel)
        }

        infoPanel.add(nameLabel)
        infoPanel.add(pathLabel)

        return infoPanel
    }

    private fun createControlPanel(): JComponent {
        val controlPanel = JPanel(FlowLayout()).apply {
            preferredSize = Dimension(150, 40)
        }

        // 来源标签
        val sourceLabel = JLabel(getSourceText()).apply {
            isOpaque = true
            background = getSourceColor()
            foreground = Color.WHITE
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        }

        controlPanel.add(sourceLabel)

        // 控制组件
        when (ttf.source) {
            is TTFSource.UserAdded -> {
                // 删除按钮
                val deleteBtn = JButton("×").apply {
                    preferredSize = Dimension(24, 24)
                    addActionListener { deleteTTF() }
                }
                controlPanel.add(deleteBtn)
            }
            else -> {
                // 开关
                val toggle = JCheckBox().apply {
                    isSelected = ttf.enabled
                    addActionListener { toggleTTF() }
                }
                controlPanel.add(toggle)
            }
        }

        return controlPanel
    }

    private fun applyStateStyle() {
        if (!ttf.enabled) {
            // 应用禁用样式
            components.forEach { component ->
                component.foreground = JBColor.GRAY
                if (component is Container) {
                    setComponentsEnabled(component, false)
                }
            }
        }
    }

    private fun getSourceText(): String = when (ttf.source) {
        is TTFSource.ProjectAssets -> "项目扫描"
        is TTFSource.AARDependency -> "AAR依赖"
        is TTFSource.UserAdded -> "手动添加"
    }

    private fun getSourceColor(): Color = when (ttf.source) {
        is TTFSource.ProjectAssets -> JBColor(0x2a4e32, 0x81c784)
        is TTFSource.AARDependency -> JBColor(0x1a365d, 0x63b3ed)
        is TTFSource.UserAdded -> JBColor(0x7c2d12, 0xffa726)
    }

    private fun toggleTTF() {
        // 需要通过项目实例获取服务
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        project.service<TTFStateManager>().updateTTFState(ttf.id, !ttf.enabled)
    }

    private fun deleteTTF() {
        val result = Messages.showYesNoDialog(
            "确定要移除 ${ttf.ttfFileName} 吗？",
            "确认删除",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
            project.service<TTFStateManager>().removeUserTTF(ttf.id)
        }
    }
}
```

## 5. 启动活动重构

### 5.1 重构后的 SearchStartupActivity

```kotlin
class SearchStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        // 检查是否为Android项目
        if (!isAndroidProject(project)) {
            return
        }

        DumbService.getInstance(project).runWhenSmart {
            initializeTTFSystem(project)
        }
    }

    private fun initializeTTFSystem(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                try {
                    val stateManager = project.service<TTFStateManager>()
                    stateManager.refreshScan()

                    // 通知UI更新
                    ApplicationManager.getApplication().invokeLater {
                        project.messageBus.syncPublisher(TTF_SCAN_TOPIC)
                            .scanCompleted()
                    }
                } catch (e: Exception) {
                    logger.error("Failed to initialize TTF system", e)
                }
            }
        }
    }

    private fun isAndroidProject(project: Project): Boolean {
        return AndroidFacet.getInstance(project) != null
    }

    companion object {
        val TTF_SCAN_TOPIC = Topic.create("TTF_SCAN_EVENTS", TTFScanListener::class.java)
        private val logger = Logger.getInstance(SearchStartupActivity::class.java)
    }
}

interface TTFScanListener {
    fun scanCompleted()
}
```

## 6. 代码改动清单

### 6.1 新增文件

```
src/main/kotlin/com/bullfrog/iconfontviewer/
├── service/
│   ├── TTFScanService.kt                # TTF扫描服务
│   ├── TTFClassifier.kt                 # 智能分类器
│   └── TTFStateManager.kt               # 状态管理器
├── repository/
│   ├── TTFRepository.kt                 # TTF数据仓库
│   └── TTFConfigStorage.kt              # 配置存储
├── ui/renderer/
│   ├── TTFListCellRenderer.kt           # 列表渲染器
│   └── TTFListItemPanel.kt              # 列表项面板
└── listener/
    └── TTFStateChangeListener.kt        # 状态变化监听器
```

### 6.2 修改文件

```
src/main/kotlin/com/bullfrog/iconfontviewer/
├── model/IconFontTtfFileModel.kt        # 扩展数据模型
├── ui/TTFMainPanel.kt                   # 重构UI面板
├── start/SearchStartupActivity.kt       # 重构启动活动
└── FontModelListHolder.kt               # 重构为TTFRepository
```

### 6.3 删除文件

```
src/main/kotlin/com/bullfrog/iconfontviewer/
└── FontPopupModelListHolder.kt          # 合并到TTFRepository
```

### 6.4 数据迁移方案

#### 6.4.1 从 FontModelListHolder 迁移到 TTFRepository

```kotlin
class TTFDataMigrator {

    /**
     * 迁移现有的 FontModelListHolder 数据到新的存储架构
     */
    fun migrateLegacyData(project: Project) {
        try {
            // 1. 获取旧数据
            val legacyTTFs = FontModelListHolder.getFontModelList()

            if (legacyTTFs.isEmpty()) return

            // 2. 转换为新数据模型
            val newTTFs = legacyTTFs.mapIndexed { index, oldTTF ->
                IconFontTtfFileModel(
                    id = generateTTFId(oldTTF.ttfAbsolutePath, TTFSource.ProjectAssets),
                    enabled = oldTTF.selected, // 保持用户的原有选择
                    ttfFileName = oldTTF.ttfFileName,
                    ttfAbsolutePath = oldTTF.ttfAbsolutePath,
                    font = oldTTF.font,
                    source = TTFSource.ProjectAssets, // 假设都是项目文件
                    type = TTFClassifier().classifyTTF(oldTTF.ttfFileName, oldTTF.ttfAbsolutePath),
                    userModified = true, // 标记为用户已配置
                    createTime = System.currentTimeMillis() - (legacyTTFs.size - index) * 1000L
                )
            }

            // 3. 保存到新存储系统
            val stateManager = project.service<TTFStateManager>()
            val repository = project.service<TTFRepository>()
            val configStorage = service<TTFConfigStorage>()

            newTTFs.forEach { ttf ->
                repository.add(ttf)
                configStorage.saveTTFState(ttf.id, ttf.enabled)
            }

            // 4. 清空旧数据（可选，为了兼容性可保留）
            // FontModelListHolder.clear()

            logger.info("Successfully migrated ${newTTFs.size} TTF entries from legacy storage")

        } catch (e: Exception) {
            logger.error("Failed to migrate legacy TTF data", e)
        }
    }
}

// 在 SearchStartupActivity 中调用迁移
class SearchStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        if (!isAndroidProject(project)) return

        DumbService.getInstance(project).runWhenSmart {
            // 首先执行数据迁移
            TTFDataMigrator().migrateLegacyData(project)

            // 然后初始化新系统
            initializeTTFSystem(project)
        }
    }
}
```

#### 6.4.2 存储位置映射

| 数据类型 | 原存储位置 | 新存储位置 |
|----------|------------|------------|
| **运行时TTF列表** | `FontModelListHolder.fontModelList` | `TTFRepository.ttfMap` |
| **TTF启用状态** | ❌ 内存临时 | ✅ `PropertiesComponent["iconfonts.ttf.states"]` |
| **用户添加TTF** | ❌ 不支持 | ✅ `PropertiesComponent["iconfonts.user.ttfs"]` |
| **字体对象缓存** | `FontModelListHolder` | `TTFRepository + LazyFont` |

#### 6.4.3 TTFRepository vs FontModelListHolder 详细对比

```kotlin
// === 原来的方式 ===
object FontModelListHolder {
    // ❌ 问题：全局单例，所有项目共享同一份数据
    private val fontModelList = Collections.synchronizedList(mutableListOf<IconFontTtfFileModel>())

    fun putFont(fontPath: String, fontFileName: String): Boolean {
        // ❌ 问题：重复检查效率低
        if (fontModelList.firstOrNull { it.ttfAbsolutePath == fontPath } != null) {
            return false
        }
        // ❌ 问题：直接操作集合，没有事务性
        fontModelList.add(IconFontTtfFileModel(...))
        return true
    }

    // ❌ 问题：返回可变集合，容易被误修改
    fun getFontModelList(): List<IconFontTtfFileModel> = fontModelList
}

// === 新的方式 ===
@Service(Service.Level.PROJECT)
class TTFRepository : Disposable {
    // ✅ 改进：使用Map提高查找效率，每个项目独立实例
    private val ttfMap = ConcurrentHashMap<String, IconFontTtfFileModel>()

    fun add(ttf: IconFontTtfFileModel): Boolean {
        // ✅ 改进：O(1)复杂度的重复检查
        if (ttfMap.containsKey(ttf.id)) {
            return false
        }
        ttfMap[ttf.id] = ttf
        return true
    }

    // ✅ 改进：返回不可变集合，防止外部修改
    fun getAll(): List<IconFontTtfFileModel> = ttfMap.values.toList()

    // ✅ 新增：支持更多操作
    fun findById(id: String): IconFontTtfFileModel? = ttfMap[id]
    fun update(ttf: IconFontTtfFileModel) { ttfMap[ttf.id] = ttf }
    fun remove(id: String): IconFontTtfFileModel? = ttfMap.remove(id)
    fun clear() { ttfMap.clear() }

    // ✅ 新增：批量操作支持
    fun replaceAll(ttfs: List<IconFontTtfFileModel>) {
        ttfMap.clear()
        ttfs.forEach { ttfMap[it.id] = it }
    }

    override fun dispose() {
        ttfMap.clear()
    }
}
```

#### 6.4.4 服务注册配置

现代 IntelliJ Platform 使用 `@Service` 注解自动注册服务，无需在 plugin.xml 中手动配置。服务会根据注解自动注册：

```kotlin
// 应用级服务 - 全局共享
@Service(Service.Level.APP)
class TTFScanService : Disposable

@Service(Service.Level.APP)
class TTFConfigStorage : Disposable

// 项目级服务 - 每个项目独立实例
@Service(Service.Level.PROJECT)
class TTFStateManager(private val project: Project) : Disposable

@Service(Service.Level.PROJECT)
class TTFRepository : Disposable
```

**注意**：
- 使用 `@Service` 注解的服务会自动注册，无需 plugin.xml 配置
- 项目级服务会自动注入 `Project` 实例
- 应用级服务在所有项目间共享
- 所有服务都应该实现 `Disposable` 接口进行资源清理

## 7. 性能优化策略

### 7.1 懒加载字体对象

```kotlin
class LazyFont(private val ttfPath: String) {
    private var _font: Font? = null

    val font: Font?
        get() {
            if (_font == null) {
                _font = createFont()
            }
            return _font
        }

    private fun createFont(): Font? {
        return try {
            Font.createFont(Font.TRUETYPE_FONT, File(ttfPath))
                .deriveFont(IconFromIconFontCharacter.FONT_SIZE)
        } catch (e: Exception) {
            logger.warn("Failed to create font: $ttfPath", e)
            null
        }
    }
}
```

### 7.2 缓存机制

```kotlin
class TTFCache {
    private val scanCache = ConcurrentHashMap<String, List<IconFontTtfFileModel>>()
    private val fontCache = LRUCache<String, Font>(maxSize = 50)

    fun getCachedScanResult(projectPath: String): List<IconFontTtfFileModel>? {
        return scanCache[projectPath]
    }

    fun cacheScanResult(projectPath: String, ttfs: List<IconFontTtfFileModel>) {
        scanCache[projectPath] = ttfs
    }

    fun getCachedFont(ttfPath: String): Font? {
        return fontCache[ttfPath]
    }

    fun cacheFont(ttfPath: String, font: Font) {
        fontCache[ttfPath] = font
    }
}
```

## 8. 测试策略

### 8.1 单元测试

```kotlin
class TTFClassifierTest {

    private val classifier = TTFClassifier()

    @Test
    fun testIconFontClassification() {
        // 测试图标字体识别
        assert(classifier.classifyTTF("iconfont.ttf", "/assets/") == TTFType.ICON_FONT)
        assert(classifier.classifyTTF("fontawesome.ttf", "/fonts/") == TTFType.ICON_FONT)
        assert(classifier.classifyTTF("material-icons.ttf", "/res/") == TTFType.ICON_FONT)
    }

    @Test
    fun testRegularFontClassification() {
        // 测试普通字体识别
        assert(classifier.classifyTTF("roboto.ttf", "/fonts/") == TTFType.REGULAR_FONT)
        assert(classifier.classifyTTF("noto-sans.ttf", "/assets/") == TTFType.REGULAR_FONT)
    }
}
```

### 8.2 集成测试

```kotlin
class TTFScanServiceTest {

    @Test
    fun testProjectScan() {
        // 测试项目扫描功能
        val project = createTestProject()
        val service = TTFScanService()

        runBlocking {
            val results = service.scanProjectTTFs(project)
            assert(results.isNotEmpty())
            assert(results.any { it.source is TTFSource.ProjectAssets })
        }
    }
}
```

## 9. 部署和发布

### 9.1 版本兼容性

- 保持对现有配置的兼容性
- 提供数据迁移脚本
- 渐进式功能发布

### 9.2 错误监控

```kotlin
class TTFErrorReporter {
    fun reportScanError(project: Project, error: Throwable) {
        logger.error("TTF scan failed for project: ${project.name}", error)

        // 发送错误报告到统计服务
        Analytics.reportError("ttf_scan_error", mapOf(
            "project_type" to getProjectType(project),
            "error_type" to error.javaClass.simpleName,
            "error_message" to error.message
        ))
    }
}
```

---

**文档版本**: v1.0
**创建时间**: 2025-01-13
**最后更新**: 2025-01-13
**实现优先级**: P0 (核心功能必须实现)
**预计开发时间**: 2-3 周