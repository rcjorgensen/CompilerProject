plugins {
    kotlin("jvm") version "2.0.21" apply false
}

allprojects {
    group = "edu.citadel"
    version = "1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    sourceSets {
        main {
            java.srcDirs("src")
            kotlin.srcDirs("src")
        }
        test {
            java.srcDirs("src/test")
            kotlin.srcDirs("src/test")
        }
    }

    tasks.test {
        useJUnitPlatform()
    }
}
