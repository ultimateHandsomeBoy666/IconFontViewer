package com.bullfrog.iconfontviewer.model

data class MavenCoordinate(
    val groupId: String = "",
    val artifactId: String = "",
    val version: String = ""
) {
    fun buildName() = "$groupId-$artifactId-$version"
}