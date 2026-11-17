// Builds Creator.jar: Java and Kotlin side by side in src/, against the paper-api jars the server itself downloaded.
plugins {
    kotlin("jvm") version "2.4.21"
}

repositories { mavenCentral() }

// The server's own libraries folder (build.sh mounts it at /plib in Docker).
val plib = file(System.getenv("PLIB") ?: "../../data/libraries")

dependencies {
    compileOnly(fileTree(plib) {
        include(
            "**/paper-api-26.3*.jar", "**/adventure-api-*.jar", "**/adventure-key-*.jar", "**/bungeecord-chat-*.jar",
            "**/brigadier-*.jar", "**/annotations-*.jar", "**/jspecify-*.jar", "**/examination-*.jar",
            "**/adventure-text-*.jar", "**/guava-*.jar", "**/gson-*.jar", "**/joml-*.jar",
        )
    })
    // kotlin-stdlib is not packed into the jar: Paper downloads it from the libraries list in plugin.yml.
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        kotlin.setSrcDirs(listOf("src"))
        resources.setSrcDirs(emptyList<String>())
    }
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
kotlin { jvmToolchain(25) }

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-proc:none")
}

tasks.jar {
    archiveFileName = "Creator.jar"
    destinationDirectory = layout.projectDirectory
    from("plugin.yml")
    from("supers") { into("supers") }
}
