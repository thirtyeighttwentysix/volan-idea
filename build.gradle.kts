import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.thirtyeighttwentysix.volan"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.1")
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null) local(localIde)
        else intellijIdea(providers.gradleProperty("platformVersion").get())
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        zipSigner()
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3
    }
    sourceSets.main { kotlin.srcDirs("vendor/core", "vendor/schema", "vendor/ir") }
}

intellijPlatform {
    pluginConfiguration {
        name = "Volan Schema"
        version = project.version.toString()
        changeNotes = providers.fileContents(layout.projectDirectory.file("CHANGE_NOTES.html")).asText
        // Deliberately restrict installation to the IDE line covered by our tests.
        ideaVersion { sinceBuild = "261"; untilBuild = "261.*" }
    }
    signing {
        certificateChainFile = layout.projectDirectory.file("certificates/volan.pem")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("marketplaceChannel").orElse("default").map { listOf(it) }
    }
    pluginVerification {
        ides {
            val localIde = providers.gradleProperty("localIdePath").orNull
            if (localIde != null) local(file(localIde)) else current()
        }
    }
}

// Publish the exact signed asset from a GitHub Release, without rebuilding it.
providers.gradleProperty("publishArchive").orNull?.let { archive ->
    tasks.publishPlugin {
        archiveFiles.setFrom(file(archive))
        dependsOn(tasks.verifyPluginSignature)
    }
    tasks.verifyPluginSignature { inputArchiveFile = file(archive) }
} ?: tasks.verifyPluginSignature {
    dependsOn(tasks.signPlugin)
}

tasks.test { maxHeapSize = "1g" }

tasks.processResources {
    from("LICENSE") { into("META-INF") }
    from("vendor/NOTICE.md") { into("META-INF"); rename { "NOTICE.md" } }
}
