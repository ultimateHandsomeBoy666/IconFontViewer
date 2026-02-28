plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
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
        androidStudio("2024.1.2.12")

        // 添加 Android 插件依赖，才能使用 adtui、PsiElementFactory 等 API
        bundledPlugin("org.jetbrains.android")
        javaCompiler()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "IconFontViewer"
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "251.*"
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
}
