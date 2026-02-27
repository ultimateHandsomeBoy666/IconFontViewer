import java.net.URI

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.13.1"
}

group = "com.bullfrog"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven {
        url = URI("http://mvnrepo.alibaba-inc.com/mvn/repository")
        isAllowInsecureProtocol = true
    }
    maven {
        url = URI("http://www.jetbrains.com/intellij-repository/releases")
        isAllowInsecureProtocol = true
    }
    maven {
        url = URI("http://cache-redirector.jetbrains.com/intellij-dependencies")
        isAllowInsecureProtocol = true
    }

}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2022.3")
    type.set("IC") // Target IDE Platform
    plugins.set(listOf("Kotlin", "org.jetbrains.android", "java"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("223")
        untilBuild.set("251.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    runIde {
        ideDir.set(file("/Applications/Android Studio.app/Contents"))
    }
}
