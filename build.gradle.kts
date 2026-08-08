import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    idea
    alias(libs.plugins.kotlin)
    alias(libs.plugins.runPaper)
    alias(libs.plugins.pluginYml)
}

group = "me.prdis"
version = "1.0.4"
val codeName = "chasingtails"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    library(kotlin("stdlib"))
    compileOnly(libs.paper)
    compileOnly(libs.cloud)

    bukkitLibrary(libs.cloud)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs = listOf("-Dcom.mojang.eula.agree=true")
    }
}

idea {
    module {
        excludeDirs.addAll(listOf(file("run"), file("out"), file(".idea"), file(".kotlin")))
    }
}

bukkit {
    val mc = libs.versions.minecraft.get()
    val split = mc.split(".")

    name = rootProject.name
    version = rootProject.version.toString()
    author = "Paradise Dev Team"

    main = "${project.group}.${codeName}.plugin.${codeName.uppercaseFirstChar()}Plugin"

    apiVersion = split.apply { if (split.size == 3) this.dropLast(1) }.joinToString(".")
}