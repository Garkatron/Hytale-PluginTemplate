import org.gradle.internal.os.OperatingSystem
import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.runConfigurations

plugins {
    java
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.3"
}

// Determine Hytale home directory
val hytaleHome: String by lazy {
    if (project.hasProperty("hytale_home")) {
        project.property("hytale_home") as String
    } else {
        val os = OperatingSystem.current()
        val userHome = System.getProperty("user.home")
        when {
            os.isWindows -> "$userHome/AppData/Roaming/Hytale"
            os.isMacOsX -> "$userHome/Library/Application Support/Hytale"
            os.isLinux -> {
                val flatpakPath = "$userHome/.var/app/com.hypixel.HytaleLauncher/data/Hytale"
                if (file(flatpakPath).exists()) flatpakPath
                else "$userHome/.local/share/Hytale"
            }
            else -> throw GradleException("Your Hytale install could not be detected automatically. If you are on an unsupported platform or using a custom install location, please define the install location using the hytale_home property.")
        }
    }
}

// Validate Hytale installation
if (!file(hytaleHome).exists()) {
    throw GradleException("Failed to find Hytale at the expected location. Please make sure you have installed the game. The expected location can be changed using the hytale_home property. Currently looking in $hytaleHome")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(project.property("java_version") as String))
    withSourcesJar()
    withJavadocJar()
}

// Quiet warnings about missing Javadocs
tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
}

// Adds the Hytale server as a build dependency
dependencies {
    val patchline = project.property("patchline") as String
    // implementation(files("$hytaleHome/install/$patchline/package/game/latest/Server/HytaleServer.jar"))
    implementation(files("libs/HytaleServer.jar"))

}

// Create the working directory to run the server
val serverRunDir = file("$projectDir/run")
if (!serverRunDir.exists()) {
    serverRunDir.mkdirs()
}

// Updates the manifest.json file with the latest properties
val updatePluginManifest by tasks.registering {
    val manifestFile = file("src/main/resources/manifest.json")

    doLast {
        if (!manifestFile.exists()) {
            throw GradleException("Could not find manifest.json at ${manifestFile.path}!")
        }

        val manifestJson = groovy.json.JsonSlurper().parseText(manifestFile.readText()) as MutableMap<String, Any>
        manifestJson["Version"] = project.version
        manifestJson["IncludesAssetPack"] = (project.property("includes_pack") as String).toBoolean()

        manifestFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(manifestJson)))
    }
}

// Makes sure the plugin manifest is up to date
tasks.processResources {
    dependsOn(updatePluginManifest)
}

// Helper function to create server run arguments
fun createServerRunArguments(srcDir: String): String {
    val patchline = project.property("patchline") as String
    var programParameters = "--allow-op --disable-sentry --assets=/var/home/bazzite/IdeaProjects/Hytale-PluginTemplate/libs/Assets.zip"

    val modPaths = mutableListOf<String>()

    if ((project.property("includes_pack") as String).toBoolean()) {
        modPaths.add(srcDir)
    }

    if ((project.property("load_user_mods") as String).toBoolean()) {
        modPaths.add("$hytaleHome/UserData/Mods")
    }

    if (modPaths.isNotEmpty()) {
        programParameters += " --mods=\"${modPaths.joinToString(",")}\""
    }

    return programParameters
}

// Creates a run configuration in IDEA
idea.project.settings {
    runConfigurations {
        create<Application>("HytaleServer") {
            mainClass = "com.hypixel.hytale.Main"
            moduleName = "${project.idea.module.name}.main"
            programParameters = createServerRunArguments(sourceSets.main.get().java.srcDirs.first().parentFile.absolutePath)
            workingDirectory = serverRunDir.absolutePath
            // Enable interactive console in IDEA
            envs = mapOf("TERM" to "xterm-256color")
            // Add JVM options for native access
            jvmArgs = "--enable-native-access=ALL-UNNAMED"
        }
    }
}
// Creates a launch.json file for VSCode
val generateVSCodeLaunch by tasks.registering {
    val vscodeDir = file("$projectDir/.vscode")
    val launchFile = file("$vscodeDir/launch.json")

    doLast {
        if (!vscodeDir.exists()) {
            vscodeDir.mkdirs()
        }

        val programParams = createServerRunArguments("\${workspaceFolder}")

        val launchConfig = mapOf(
            "version" to "0.2.0",
            "configurations" to listOf(
                mapOf(
                    "type" to "java",
                    "name" to "HytaleServer",
                    "request" to "launch",
                    "mainClass" to "com.hypixel.hytale.Main",
                    "args" to programParams,
                    "cwd" to "\${workspaceFolder}/run"
                )
            )
        )

        launchFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(launchConfig)))
    }
}

val runServer by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Runs the Hytale server with your plugin"

    mainClass.set("com.hypixel.hytale.Main")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = serverRunDir

    val patchline = project.property("patchline") as String
    val argsList = mutableListOf<String>()

    // Add JVM arguments for native access
    jvmArgs("--enable-native-access=ALL-UNNAMED")

    // Add program arguments
    argsList.add("--allow-op")
    argsList.add("--disable-sentry")
    argsList.add("--assets=/var/home/bazzite/IdeaProjects/Hytale-PluginTemplate/libs/Assets.zip")

    val modPaths = mutableListOf<String>()
    if ((project.property("includes_pack") as String).toBoolean()) {
        modPaths.add(sourceSets.main.get().java.srcDirs.first().parentFile.absolutePath)
    }
    if ((project.property("load_user_mods") as String).toBoolean()) {
        modPaths.add("$hytaleHome/UserData/Mods")
    }
    if (modPaths.isNotEmpty()) {
        argsList.add("--mods=${modPaths.joinToString(",")}")
    }

    args(argsList)
    dependsOn(tasks.classes)
}