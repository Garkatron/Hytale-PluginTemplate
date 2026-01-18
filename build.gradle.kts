plugins {
    id("java")
}

val pluginGroup: String by project
val pluginName: String by project
val pluginVersion: String by project
val pluginDescription: String by project
val authorName: String by project
val authorEmail: String by project
val authorUrl: String by project
val pluginWebsite: String by project
val serverVersion: String by project
val pluginMain: String by project

group = pluginGroup
version = pluginVersion

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf(
        "group" to pluginGroup,
        "name" to pluginName,
        "version" to pluginVersion,
        "description" to pluginDescription,
        "authorName" to authorName,
        "authorEmail" to authorEmail,
        "authorUrl" to authorUrl,
        "website" to pluginWebsite,
        "serverVersion" to serverVersion,
        "main" to pluginMain
    )

    inputs.properties(props)
    filteringCharset = "UTF-8"

    filesMatching("manifest.json") {
        expand(props)
    }
}