package com.bullfrog.iconfontviewer.e2e

import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.bullfrog.iconfontviewer.IconFontSettings
import com.bullfrog.iconfontviewer.start.SearchStartupActivity
import com.bullfrog.iconfontviewer.ui.IconFontLineMarkerProvider
import com.bullfrog.iconfontviewer.util.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest

/** Compiled and registered ONLY with -PfontE2E; never part of a normal plugin build. */
class LibraryFontE2EActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        val expectedProject = System.getProperty("iconfontviewer.e2e.project") ?: return
        if (project.basePath != expectedProject) return
        val report = File(requireNotNull(System.getProperty("iconfontviewer.e2e.report")))
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Library font IDE tests", false) {
            override fun run(indicator: ProgressIndicator) {
                val checks = mutableListOf<Map<String, Any>>()
                val result = linkedMapOf<String, Any>("ide" to ApplicationInfo.getInstance().build.asString(), "checks" to checks)
                fun writeReport() {
                    report.parentFile.mkdirs()
                    report.writeText(GsonBuilder().setPrettyPrinting().create().toJson(result))
                }
                try {
                    check(File(expectedProject, ".font-e2e-sandbox").isFile) { "E2E requires a disposable project copy" }
                    result["status"] = "waiting_for_gradle_sync"
                    writeReport()
                    awaitDependencies(project, indicator)
                    result["status"] = "running"
                    writeReport()
                    runChecks(project, File(expectedProject), indicator, checks)
                    result["status"] = "passed"
                } catch (failure: Throwable) {
                    result["status"] = "failed"
                    result["error"] = failure.stackTraceToString()
                } finally {
                    writeReport()
                    if (System.getProperty("iconfontviewer.e2e.exit") == "true") {
                        ApplicationManager.getApplication().invokeLater {
                            ApplicationManager.getApplication().exit(true, true, false)
                        }
                    } else if (result["status"] == "passed") {
                        ApplicationManager.getApplication().invokeLater {
                            val settings = IconFontSettings.getInstance(project)
                            val path = settings.getFontInfosSnapshot().first { it.path.endsWith("/icon_probe_jar_root.ttf") }.path
                            settings.mutateFontInfos { fonts -> fonts.forEach { it.enabled = it.path == path } }
                            val file = File(expectedProject, "app/src/main/java/com/example/iconfontdemo/LibraryFontProbes.kt")
                            val virtual = LocalFileSystem.getInstance().findFileByIoFile(file)!!
                            val offset = file.readText().indexOf("R.string.probe_jar_root_home")
                            FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtual, offset), true)
                        }
                    }
                }
            }
        })
    }

    private fun awaitDependencies(project: Project, indicator: ProgressIndicator) {
        val deadline = System.nanoTime() + 300_000_000_000L
        val sync = GradleSyncState.getInstance(project)
        val sourceFont = File(project.basePath!!, "iconfont-lib/src/main/assets/test-probes/icon_probe_source_baseline.ttf")
        while (System.nanoTime() < deadline) {
            indicator.checkCanceled()
            check(!project.isDisposed)
            if (!sync.isSyncInProgress && !DumbService.isDumb(project)) {
                val ready = ReadAction.compute<Boolean, Throwable> {
                    val dependenciesImported = ModuleManager.getInstance(project).modules.any { module ->
                        AndroidFacet.getInstance(module)?.getModuleSystem()
                            ?.getAndroidLibraryDependencies(DependencyScopeType.MAIN)
                            ?.any { "com.example.iconfontfixtures" in it.address } == true
                    }
                    // Sync may publish the model before scheduling the source-index scan.
                    // Wait for the source baseline too, so an early smart-mode gap cannot pass.
                    dependenciesImported && FilenameIndex.getVirtualFilesByName(
                        sourceFont.name, GlobalSearchScope.projectScope(project)
                    ).any { it.path == sourceFont.path }
                }
                if (ready) return
            }
            check(!sync.lastSyncFailed()) { "Gradle sync failed; inspect the sandbox IDE log" }
            Thread.sleep(500)
        }
        error("Timed out waiting for Android Studio to import real fixture dependencies")
    }

    private fun runChecks(project: Project, root: File, indicator: ProgressIndicator, checks: MutableList<Map<String, Any>>) {
        val manifest = JsonParser.parseString(File(root, "fixtures/library-font-cases.json").readText()).asJsonObject
        val cases = manifest.getAsJsonArray("cases").map { it.asJsonObject }
        val settings = IconFontSettings.getInstance(project)
        val remoteHome = System.getProperty("iconfontviewer.e2e.gradleHome")?.let { File(it).canonicalFile }
        val downloaded = if (remoteHome != null) {
            check(!File(root, "fixtures/repository").exists()) { "Local repository could mask HTTP dependency discovery" }
            val effectiveHome = GradleSettings.getInstance(project).serviceDirectoryPath
                ?: System.getProperty("gradle.user.home") ?: System.getenv("GRADLE_USER_HOME")
            check(effectiveHome != null && File(effectiveHome).canonicalFile == remoteHome) { "IDE Gradle home is not isolated: $effectiveHome" }
            val repositoryRoot = File(System.getProperty("iconfontviewer.e2e.repositoryRoot")).canonicalFile
            check(!repositoryRoot.toPath().startsWith(root.canonicalFile.toPath())) { "HTTP repository must be outside the project" }
            manifest.getAsJsonArray("artifacts").associate { item ->
                val artifact = item.asJsonObject
                val directory = File(remoteHome, "caches/modules-2/files-2.1/com.example.iconfontfixtures/${artifact["id"].asString}/${manifest["version"].asString}")
                val name = File(artifact["path"].asString).name
                val file = directory.walkTopDown().filter { it.isFile && it.name == name }.single()
                val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                check(digest == artifact["sha256"].asString) { "Wrong downloaded bytes: $file" }
                artifact["id"].asString to file.canonicalFile
            }
        } else emptyMap()
        SearchStartupActivity.scanForFonts(project, indicator)
        val initial = settings.getFontInfosSnapshot()
        check(initial.map { it.path }.distinct().size == initial.size) { "Duplicate discovered paths" }
        val extractedRoots = ReadAction.compute<List<File>, Throwable> {
            ModuleManager.getInstance(project).modules.flatMap { module ->
                AndroidFacet.getInstance(module)?.getModuleSystem()
                    ?.getAndroidLibraryDependencies(DependencyScopeType.MAIN)
                    ?.filter { ":path-fixtures:" in it.address }
                    ?.mapNotNull { it.manifestFile?.parent?.toFile() } ?: emptyList()
            }.distinct()
        }
        check(extractedRoots.isNotEmpty()) { "No real Android dependency model root for path-fixtures" }
        if (remoteHome != null) {
            check(extractedRoots.all { it.canonicalFile.toPath().startsWith(remoteHome.toPath()) }) { "Model exposed a non-isolated transform root: $extractedRoots" }
            checks += mapOf("case" to "remote_maven", "gradle_user_home" to remoteHome.path,
                "project_fixture_repository_absent" to true, "downloaded_artifacts" to downloaded.mapValues { it.value.path },
                "extracted_roots" to extractedRoots.map { it.path })
        }
        val provider = IconFontLineMarkerProvider()
        for (case in cases) {
            indicator.checkCanceled()
            val id = case["id"].asString
            val entry = case["entry"].asString
            val paths = initial.filter { font ->
                if (case["artifact"].asString == "source") font.path == File(root, entry).path
                else font.path.endsWith("/$entry")
            }
            check(paths.isNotEmpty()) { "Missing dependency font: $id ($entry)" }
            if (remoteHome != null && case["artifact"].asString != "source") {
                val expectedArchivePath = downloaded.getValue(case["artifact"].asString).path + "!/" + entry
                check(paths.any { it.path == expectedArchivePath }) { "Downloaded archive font missing: $expectedArchivePath" }
                check(paths.all { File(it.path.substringBefore("!/")).canonicalFile.toPath().startsWith(remoteHome.toPath()) }) {
                    "Project or shared-cache fallback masked remote case $id: ${paths.map { it.path }}"
                }
            }
            if (case["artifact"].asString == "path-fixtures") {
                val firstEntry = entry.substringBefore("!/")
                val heads = if (firstEntry == "classes.jar" || firstEntry.startsWith("libs/"))
                    listOf(firstEntry, "jars/$firstEntry") else listOf(firstEntry)
                for (directory in extractedRoots) for (head in heads) {
                    val physical = File(directory, head)
                    if (physical.isFile) {
                        val expectedPath = physical.path + entry.removePrefix(firstEntry)
                        check(paths.any { it.path == expectedPath }) {
                            "Archive fallback masked an undiscovered extracted font: $expectedPath"
                        }
                    }
                }
            }
            for (fontInfo in paths) {
                check(fontInfo.enabled == case["default_enabled"].asBoolean) { "Wrong default enabled state: ${fontInfo.path}" }
                settings.mutateFontInfos { fonts -> fonts.forEach { it.enabled = it.path == fontInfo.path } }
                settings.iconListCache.clear()
                val names = case.getAsJsonArray("resources").map { it.asString }
                val files = linkedMapOf(
                    "kotlin" to if (id == "library_resource") "app/src/main/java/com/example/iconfontdemo/ExternalLibraryResourceProbes.kt" else "app/src/main/java/com/example/iconfontdemo/LibraryFontProbes.kt",
                    "java" to if (id == "library_resource") "app/src/main/java/com/example/iconfontdemo/ExternalLibraryJavaResourceProbes.java" else "app/src/main/java/com/example/iconfontdemo/LibraryFontJavaProbes.java",
                    "layout" to "app/src/main/res/layout/library_font_probes.xml"
                )
                if (id != "library_resource") {
                    files["definition"] = "app/src/main/res/values/library_font_probes.xml"
                }
                ReadAction.run<Throwable> {
                    for ((kind, relative) in files) {
                        val element = findProbe(project, File(root, relative), names[0])
                        val match = if (kind == "definition") getIconForStringResourceTag(element) else {
                            getIconForElement(element.findParentRStringExpression() ?: element.parent)
                        }
                        check(match != null && match.fontPath == fontInfo.path) { "Wrong or missing $kind font for $id: ${match?.fontPath}" }
                        check(provider.getLineMarkerInfo(element) != null) { "No gutter marker for $id/$kind" }
                        val image = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
                        image.createGraphics().apply {
                            match.icon.paintIcon(object : java.awt.Component() {}, this, 2, 2)
                            dispose()
                        }
                        check((0 until 40).any { x -> (0 until 40).any { y -> image.getRGB(x, y) ushr 24 != 0 } }) { "Empty gutter icon: $id/$kind" }
                        if (kind == "kotlin") {
                            val target = element.findParentRStringExpression() ?: error("Missing R expression")
                            val popupNames = buildIconListForFont(match.font, match.fontPath, target).map { it.key }.toSet()
                            check(popupNames == names.toSet()) { "Wrong popup entries for $id: $popupNames" }
                        }
                    }
                }
                settings.setFontEnabled(fontInfo.path, false)
                ReadAction.run<Throwable> {
                    val element = findProbe(project, File(root, files.getValue("kotlin")), names[0])
                    check(provider.getLineMarkerInfo(element) == null) { "Disabled font still previews: $id" }
                }
                checks += mapOf("case" to id, "path" to fontInfo.path,
                    "representation" to if ("!/" in fontInfo.path) "archive" else "physical",
                    "surfaces" to files.keys.toList(), "isolation" to true)
            }
        }
        val manualPath = initial.first { it.path.endsWith("/assets/custom/plainface.ttf") }.path
        settings.setFontEnabled(manualPath, true)
        SearchStartupActivity.scanForFonts(project, indicator)
        check(settings.isFontEnabled(manualPath) == true) { "Rescan discarded explicit enabled state" }
        check(settings.getFontInfosSnapshot().map { it.path }.toSet() == initial.map { it.path }.toSet()) { "Rescan changed the discovered path set" }
        checkSamePathReplacement(project, root, cases, indicator)
        checks += mapOf("case" to "rescan", "preserves_enabled_state" to true, "same_path_cache_invalidation" to true)
    }

    private fun checkSamePathReplacement(project: Project, root: File, cases: List<JsonObject>, indicator: ProgressIndicator) {
        val baseline = cases.first { it["id"].asString == "source_baseline" }
        val other = cases.first { it["id"].asString == "assets_deep" }
        val file = File(root, baseline["path"].asString)
        val original = file.readBytes()
        val settings = IconFontSettings.getInstance(project)
        val replacementPath = settings.getFontInfosSnapshot().first { it.path.endsWith("/${other["entry"].asString}") }.path
        settings.mutateFontInfos { fonts -> fonts.forEach { it.enabled = it.path == file.path } }
        val name = baseline.getAsJsonArray("resources")[0].asString
        val kotlinFile = File(root, "app/src/main/java/com/example/iconfontdemo/LibraryFontProbes.kt")
        fun matches(): Boolean = ReadAction.compute<Boolean, Throwable> {
            val leaf = findProbe(project, kotlinFile, name)
            getIconForElement(leaf.findParentRStringExpression()!!) != null
        }
        check(matches())
        try {
            file.writeBytes(FontFileAccess.read(replacementPath) { it.readBytes() })
            SearchStartupActivity.scanForFonts(project, indicator)
            check(!matches()) { "Same-path replacement reused the stale font cache" }
        } finally {
            file.writeBytes(original)
            SearchStartupActivity.scanForFonts(project, indicator)
        }
        check(matches()) { "Restored font did not reload" }
    }

    private fun findProbe(project: Project, file: File, resourceName: String): PsiElement {
        val virtual = LocalFileSystem.getInstance().findFileByIoFile(file) ?: error("Missing file: $file")
        val psi = PsiManager.getInstance(project).findFile(virtual) ?: error("Missing PSI: $file")
        return PsiTreeUtil.collectElements(psi) { element ->
            when {
                element.isLeafOfRStringExpression() -> element.findParentRStringExpression()?.removePrefix() == resourceName
                element.isLeafOfXmlStringAttribute() -> (element.parent as? XmlAttributeValue)?.value == "@string/$resourceName"
                element.isStringResourceTagName() -> element.getParentStringResourceTag()?.getAttributeValue("name") == resourceName
                else -> false
            }
        }.firstOrNull() ?: error("No probe $resourceName in $file")
    }
}
