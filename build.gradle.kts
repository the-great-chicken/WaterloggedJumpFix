plugins {
    java
}

group = "dev.waterloggedjumpfix"

val semanticVersionPattern = Regex(
    """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*))*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$"""
)
val configuredVersion = providers.gradleProperty("pluginVersion").get()
require(semanticVersionPattern.matches(configuredVersion)) {
    "pluginVersion must be a valid Semantic Version (for example, 1.2.3 or 1.2.3-rc.1); got '$configuredVersion'"
}
version = configuredVersion

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:all")
}

tasks.processResources {
    val projectVersion = project.version.toString()
    inputs.property("version", projectVersion)
    filesMatching("plugin.yml") {
        expand("version" to projectVersion)
    }
}

tasks.jar {
    archiveBaseName.set("WaterloggedJumpFix")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("printVersion") {
    group = "help"
    description = "Prints the validated plugin version for automation."
    val versionToPrint = configuredVersion
    doLast {
        println(versionToPrint)
    }
}
