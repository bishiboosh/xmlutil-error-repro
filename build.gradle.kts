plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(libs.kotlinx.serialization.xml)
    implementation(libs.kotlinx.serialization.xml.io)
    implementation(libs.xmlutil.core.io)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
