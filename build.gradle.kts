plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "net.clodo"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.helpch.at/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.xerial:sqlite-jdbc:3.46.1.3")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveClassifier.set("raw")
}

tasks.shadowJar {
    archiveBaseName.set("StartFlow")
    archiveClassifier.set("")
    relocate("org.bstats", "net.clodo.startflow.libs.bstats")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
