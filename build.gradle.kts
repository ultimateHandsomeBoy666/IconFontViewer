plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.bullfrog"
version = "1.2.0"

val androidStudioPath: String by project
val fontE2E = providers.gradleProperty("fontE2E").isPresent

tasks.withType<Jar>().configureEach {
    // Keep test-only code out of normal artifacts when switching build modes.
    if (!fontE2E) exclude("com/bullfrog/iconfontviewer/e2e/**")
}

if (fontE2E) {
    kotlin.sourceSets.named("main") { kotlin.srcDir("src/e2e/kotlin") }
}

tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask>("patchPluginXml") {
    inputs.property("fontE2E", fontE2E)
    doLast {
        if (fontE2E) {
            val descriptor = outputFile.get().asFile
            descriptor.writeText(descriptor.readText()
                .replace("<!-- ICONFONT_E2E_EXTENSIONS -->", "<extensions defaultExtensionNs=\"com.intellij\"><postStartupActivity implementation=\"com.bullfrog.iconfontviewer.e2e.LibraryFontE2EActivity\"/></extensions>")
                .replace(Regex("<postStartupActivity[^>]+com\\.bullfrog\\.iconfontviewer\\.start\\.SearchStartupActivity[^>]*/>"), ""))
            check("com.bullfrog.iconfontviewer.e2e.LibraryFontE2EActivity" in descriptor.readText()) {
                "E2E startup activity was not added to the patched descriptor"
            }
        }
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        local(androidStudioPath)

        bundledPlugin("org.jetbrains.android")
        bundledPlugin("org.jetbrains.kotlin")
        javaCompiler()
    }

    compileOnly(files(
        "$androidStudioPath/plugins/android/lib/android.jar",
        "$androidStudioPath/plugins/android/lib/android-common.jar"
    ))
}

intellijPlatform {
    pluginConfiguration {
        name = "IconFontViewer"
        ideaVersion {
            sinceBuild = "241"
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            local(androidStudioPath)
        }
    }
}

tasks {
    test {
        useJUnit()
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Xbootclasspath/a:$androidStudioPath/lib/nio-fs.jar")
        })
        val fixtureRoot = providers.gradleProperty("fontFixturesDir")
        if (fixtureRoot.isPresent) {
            systemProperty("iconfontviewer.fixtures", file(fixtureRoot.get()).absolutePath)
        } else {
            exclude("**/LibraryFontCorpusTest.class")
        }
    }
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
        providers.gradleProperty("fontTestJava").orNull?.let { executable = it }
        if (fontE2E) {
            val fixtureProject = file(providers.gradleProperty("fontE2EProject").get()).absolutePath
            val report = file(providers.gradleProperty("fontE2EReport").get()).absolutePath
            val exitAfterTests = !providers.gradleProperty("fontE2EKeepOpen").isPresent
            args(fixtureProject)
            jvmArgumentProviders.add(CommandLineArgumentProvider {
                listOf("-Diconfontviewer.e2e.project=$fixtureProject", "-Diconfontviewer.e2e.report=$report",
                    "-Didea.trust.all.projects=true", "-Diconfontviewer.e2e.exit=$exitAfterTests")
            })
        }
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Xbootclasspath/a:$androidStudioPath/lib/nio-fs.jar")
        })
    }
    named<org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask>("buildSearchableOptions") {
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Xbootclasspath/a:$androidStudioPath/lib/nio-fs.jar")
        })
    }
}
