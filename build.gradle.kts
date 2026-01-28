plugins {
    java
    id("io.freefair.lombok") version "8.13.1"
    id("com.gradleup.shadow") version "9.0.0-beta16"
    id("xyz.jpenilla.run-velocity") version "2.3.1"
}

val pluginVersion: String by project
val pluginName: String by project
val pluginAuthor: String by project
val pluginMain: String by project
val javaVersion: String by project
val velocityVersion: String by project
val limboApiVersion: String by project

version = pluginVersion

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.elytrium.net/repo/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:$velocityVersion")
    compileOnly("com.velocitypowered:velocity-proxy:$velocityVersion")
    annotationProcessor("com.velocitypowered:velocity-api:$velocityVersion")

    compileOnly("net.elytrium.limboapi:api:$limboApiVersion")

    implementation("org.spongepowered:configurate-yaml:4.1.2")

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    compileOnly("io.netty:netty-all:4.1.119.Final")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    archiveClassifier.set("")

    relocate("org.spongepowered.configurate", "kr.rtustudio.continuous.libs.configurate")
    relocate("org.yaml.snakeyaml", "kr.rtustudio.continuous.libs.snakeyaml")
}

tasks.runVelocity {
    velocityVersion(velocityVersion)
}

tasks.processResources {
    val props = mapOf(
        "version" to pluginVersion,
        "name" to pluginName,
        "author" to pluginAuthor,
        "main" to pluginMain
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("velocity-plugin.json") {
        expand(props)
    }
}

java {
    val javaVersionInt = javaVersion.toInt()
    sourceCompatibility = JavaVersion.toVersion(javaVersionInt)
    targetCompatibility = JavaVersion.toVersion(javaVersionInt)
    if (JavaVersion.current() < JavaVersion.toVersion(javaVersionInt)) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersionInt))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion.toInt())
}
