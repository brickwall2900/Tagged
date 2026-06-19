plugins {
    id("java")
}

group = "io.github.brickwall2900.images"
version = "1.0-SNAPSHOT"

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
}

tasks.test {
    useJUnitPlatform()
}