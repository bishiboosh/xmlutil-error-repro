plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(libs.kotlinx.serialization.xml)
    implementation(libs.kotlinx.serialization.xml.io)
    implementation(libs.xmlutil.core.io)
    implementation(libs.kotlinx.io.core)
    implementation(libs.kotlinx.io.okio)
    implementation(libs.okhttp)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "MainKt"
}
