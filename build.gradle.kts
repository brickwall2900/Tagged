plugins {
    id("java")
    id("application")
}

group = "io.github.brickwall2900.tagged"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.flatlaf)
    implementation(libs.flatlaf.extras)
    implementation(libs.sierra)
    implementation(libs.arcana)
    implementation(libs.hashing)
    implementation(libs.fastutil)
    implementation(libs.gson)
}

application {
    mainClass = "io.github.brickwall2900.tagged.Tagged"
}

tasks.test {
    useJUnitPlatform()
}