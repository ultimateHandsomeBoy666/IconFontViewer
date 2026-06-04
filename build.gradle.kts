plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.bullfrog"
version = "1.0.1"

val androidStudioPath: String by project

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
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
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
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
