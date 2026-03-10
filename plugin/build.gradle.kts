import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import proguard.gradle.ProGuardTask

plugins {
    id("java")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.3.1"
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.8.2")
    }
}

version = rootProject.version
group = rootProject.group

repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://jitpack.io")
    maven(url = "https://oss.sonatype.org/content/groups/public/")
    maven(url = "https://repo.codemc.io/repository/maven-public/")
    maven(url = "https://repo.xenondevs.xyz/releases")
    maven(url = "https://maven.enginehub.org/repo/")
    maven(url = "https://repo.rosewooddev.io/repository/public/")
    maven(url = "https://repo.opencollab.dev/main/")
    maven(url = "https://repo.william278.net/releases")
}

dependencies {
    implementation(project(":api"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("org.postgresql:postgresql:42.7.10")
    compileOnly("org.projectlombok:lombok:1.18.34")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.github.Emibergo02:RedisEconomy:4.3.19")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9-SNAPSHOT")
    compileOnly("org.black_ixx:playerpoints:3.3.3")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    compileOnly("org.xerial.snappy:snappy-java:1.1.10.8")
    compileOnly("net.william278.husksync:husksync-bukkit:3.8.7+1.21.8")

    implementation("de.exlll:configlib-paper:4.6.4") {
        exclude(group = "org.snakeyaml", module = "snakeyaml-engine")
    }
    implementation("com.github.Emibergo02:drink:8859698119")
    implementation("io.lettuce:lettuce-core:6.7.1.RELEASE")
    implementation("org.apache.commons:commons-pool2:2.12.0")
    implementation("xyz.xenondevs.invui:invui-core:1.50")
    implementation("xyz.xenondevs.invui:inventory-access-r22:1.49")
    implementation("xyz.xenondevs.invui:inventory-access-r23:1.49")
    implementation("xyz.xenondevs.invui:inventory-access-r24:1.49")
    implementation("xyz.xenondevs.invui:inventory-access-r25:1.49")
    implementation("xyz.xenondevs.invui:inventory-access-r26:1.49")
    implementation("com.davidehrmann.vcdiff:vcdiff-core:0.1.1")
    implementation("com.github.luben:zstd-jni:1.5.6-4")

    annotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))

}

val targetJavaVersion = 21

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)

}

tasks.withType<Jar>().configureEach {
    archiveBaseName = "${rootProject.name}-${project.name}"
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    destinationDirectory.set(file("$rootDir/target"))
    archiveClassifier.set("")
    minimize {
        include(dependency("io.lettuce:lettuce-core:.*"))
        include(dependency("org.apache.commons:commons-pool2:.*"))
        include(dependency("io.netty:.*:.*"))
        include(dependency("io.projectreactor:reactor-core:.*"))
        include(dependency("org.reactivestreams:.*:.*"))
        include(dependency("xyz.xenondevs.invui:invui-core:.*"))
    }

    relocate("de.exlll.configlib", "dev.unnm3d.zelsync.libraries.configlib")
    relocate("com.jonahseguin.drink", "dev.unnm3d.zelsync.libraries.drink")
    relocate("xyz.xenondevs.invui", "dev.unnm3d.zelsync.libraries.invui")
    relocate("xyz.xenondevs.inventoryaccess", "dev.unnm3d.zelsync.libraries.inventoryaccess")

    relocate("reactor", "dev.unnm3d.zelsync.libraries.reactor")
    relocate("redis.clients", "dev.unnm3d.zelsync.libraries.redisclient")
    relocate("io.lettuce", "dev.unnm3d.zelsync.libraries.lettuce")
    relocate("io.netty", "dev.unnm3d.zelsync.libraries.netty")
    relocate("org.reactivestreams", "dev.unnm3d.zelsync.libraries.reactivestreams")
    relocate("org.apache.commons.pool2", "dev.unnm3d.zelsync.libraries.commonspool2")


    exclude("org/intellij/**", "org/jetbrains/**", "org/slf4j/**", "colors.bin")
}

tasks.register<ProGuardTask>("obfuscate") {
    dependsOn(shadowJarTask)

    val shadowFile = shadowJarTask.get().archiveFile.get().asFile
    injars(shadowFile)
    outjars(shadowFile.resolveSibling("${shadowFile.nameWithoutExtension}-obfuscated.jar"))
    printmapping(shadowFile.resolveSibling("mappings.txt"))

    libraryjars(configurations.named("compileClasspath"))
    libraryjars("${System.getProperty("java.home")}/jmods")

    verbose(); dontwarn(); dontshrink()
    flattenpackagehierarchy()

    keepattributes("*Annotation*,Record,Signature,Exceptions")

    listOf(
        "dev.unnm3d.zelsync.api.**",
        "dev.unnm3d.zelsync.configs.**",
        "dev.unnm3d.zelsync.libraries.configlib.**",
        "dev.unnm3d.zelsync.libraries.drink.**",
        "dev.unnm3d.zelsync.libraries.inventoryaccess.**",
        "dev.unnm3d.zelsync.libraries.lettuce.**",
        "dev.unnm3d.zelsync.libraries.netty.**",
        "dev.unnm3d.zelsync.libraries.reactivestreams.**",
        "dev.unnm3d.zelsync.libraries.reactor.**",
        "dev.unnm3d.zelsync.libraries.commonspool2.**",
        "dev.unnm3d.zelsync.libraries.redisclient.**"
//"dev.unnm3d.zelsync.utils.Metrics",
    ).forEach { keep("class $it { *; }") }

    keepnames("class dev.unnm3d.zelsync.zelsync { public *; }")
    keepnames("class dev.unnm3d.zelsync.commands.** { public *; }")
}

tasks.named<Delete>("clean") {
    // Deletes all files in the target folder that start with this project's base name
    delete(fileTree("$rootDir/target") {
        include("*${project.name}*")
    })
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
    filesMatching("source") {
        expand(props)
    }
}