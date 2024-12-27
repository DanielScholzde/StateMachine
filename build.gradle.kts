plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("plugin.power-assert") version "2.1.0"
}

group = "de.danielscholz"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}


kotlin {
    jvmToolchain(17)

    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {

        }
        jvmTest {
            dependencies {

            }
        }
    }
}

//dependencies {
//    testImplementation("org.jetbrains.kotlin:kotlin-test")
//    //testImplementation("io.kotest:kotest-assertions-core:5.9.1")
//}

tasks.withType<Test> {
    useJUnitPlatform()
}
