plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.bullfrog"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local("/Applications/Android Studio.app/Contents")

        bundledPlugin("org.jetbrains.android")
        javaCompiler()
    }

    // Android 插件中部分 jar 未被 bundledPlugin 自动解析，手动补充
    compileOnly(files(
        "/Applications/Android Studio.app/Contents/plugins/android/lib/android.jar",
        "/Applications/Android Studio.app/Contents/plugins/android/lib/android-common.jar"
    ))
}

intellijPlatform {
    pluginConfiguration {
        name = "IconFontViewer"
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "253.*"
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
    // 修复 runIde 时 MultiRoutingFileSystemProvider 找不到的问题
    named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Xbootclasspath/a:/Applications/Android Studio.app/Contents/lib/nio-fs.jar")
        })
    }
}
