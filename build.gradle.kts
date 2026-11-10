plugins {
    java
    `maven-publish`
}

group = "fr.vorace"
version = providers.gradleProperty("version").get()
val paperApi = providers.gradleProperty("paperApi").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") // vorace-core
}

// vorace-core : le .jar indiqué par -PvoraceCore=<chemin> (CI, compilation locale), sinon la version publiée sur JitPack
val voraceCore = providers.gradleProperty("voraceCore")
val core: Any = if (voraceCore.isPresent) files(voraceCore.get())
    else "com.github.VoracePlugin:vorace-core:${providers.gradleProperty("voraceCoreVersion").get()}"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
    compileOnly(core)

    testImplementation("io.papermc.paper:paper-api:$paperApi")
    testImplementation(core)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(providers.gradleProperty("jdk").orElse("25").get().toInt())
    withSourcesJar()
    disableAutoTargetJvm()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.processResources {
    val v = project.version.toString()
    inputs.property("version", v)
    filesMatching("plugin.yml") { expand("version" to v) }
}

// Les tests sont des programmes simples lancés par la tâche "tests" (pas JUnit)
tasks.test { enabled = false }

// Tests sans serveur (rangs, qualité, formule de dégâts, fichiers d'exemple, pack) : gradle tests
tasks.register<JavaExec>("tests") {
    group = "verification"
    description = "Lance les tests sans serveur"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "fr.vorace.item.Tests"
}

tasks.jar { archiveFileName = "VoraceItem-${project.version}.jar" }

tasks.named<Jar>("sourcesJar") { destinationDirectory = layout.buildDirectory.dir("sources") }

// Dépendance pour les autres plugins (JitPack) : compileOnly("com.github.VoracePlugin:vorace-item:<version>")
publishing {
    publications {
        create<MavenPublication>("voraceItem") {
            groupId = "com.github.VoracePlugin"
            artifactId = "vorace-item"
            from(components["java"])
        }
    }
}
