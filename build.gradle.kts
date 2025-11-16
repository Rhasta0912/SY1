plugins {
    java
}

group = "com.stellinova"
version = "0.2.0" // polished SyncBlade

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly(files("libs/EvoCore.jar"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.register<Jar>("shade") {
    from(sourceSets.main.get().output)
    archiveClassifier.set("")
}

tasks.jar {
    dependsOn("shade")
    manifest {
        attributes["Implementation-Title"] = "SyncBlade"
        attributes["Implementation-Version"] = project.version
    }
}
