plugins {
    id("java")
}

group = "me.third"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("Libs/robocode-tankroyale-bot-api-0.28.1.jar"))
}