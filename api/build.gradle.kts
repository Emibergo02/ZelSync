plugins {
    id("java")
    id("maven-publish")
}

group = "dev.unnm3d.zeltrade"
version = "1.7"

repositories {
    mavenCentral()
    maven(url = "https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.34")
    compileOnly("org.jetbrains:annotations:24.0.1")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.jetbrains:annotations:24.0.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val targetJavaVersion = 21

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)

}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Javadoc>().configureEach {
    options {
        encoding = "UTF-8"
        // Safe casting for Standard options
        (this as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
}

publishing {
    //Check if this project is api
    publications {
        create<MavenPublication>("mavenJavaApi") {
            groupId = rootProject.group.toString()
            artifactId = rootProject.name.toString()
            version = rootProject.version.toString()

            artifact(tasks.named("jar"))
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
}