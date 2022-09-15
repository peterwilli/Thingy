import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm") version "1.6.21"
    application
    id("com.google.protobuf") version "0.8.19"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "org.thingy"
version = "2.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.github.minndevelopment:jda-ktx:081a177281")
    implementation("net.dv8tion:JDA:5.0.0-alpha.18")
    implementation("io.grpc:grpc-kotlin-stub:1.3.0")
    implementation("io.grpc:grpc-protobuf:1.47.0")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("com.google.protobuf:protobuf-kotlin:3.21.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("io.grpc:grpc-netty:1.47.0")
    implementation("com.beust:klaxon:5.6")
    implementation("com.sksamuel.hoplite:hoplite-core:2.4.0")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.4.0")
    implementation("com.j256.ormlite:ormlite-core:6.1")
    implementation("com.j256.ormlite:ormlite-jdbc:6.1")
    implementation("org.xerial:sqlite-jdbc:3.39.2.1")
    implementation("org.atteo:evo-inflector:1.3")
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation(kotlin("reflect"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "18"
}

application {
    mainClass.set("MainKt")
}

tasks.named("shadowJar", com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    mergeServiceFiles()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.5"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.49.0"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.3.0:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
            it.builtins {
                id("kotlin")
            }
        }
    }
}