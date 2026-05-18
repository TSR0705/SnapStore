plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.filex"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

javafx {
    version = "21"
    modules("javafx.controls", "javafx.fxml", "javafx.graphics")
}

application {
    mainClass.set("com.filex.app.FileXApplication")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    
    // UI Testing
    testImplementation("org.testfx:testfx-core:4.0.18")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testImplementation("org.testfx:openjfx-monocle:jdk-12.0.1+2")
    testImplementation("org.hamcrest:hamcrest:2.2")
    
    // Mocking
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    systemProperty("testfx.robot", "glass")
    systemProperty("testfx.headless", "true")
    systemProperty("prism.order", "sw")
    systemProperty("prism.text", "t2k")
    systemProperty("java.awt.headless", "true")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.filex.app.FileXApplication",
            "Implementation-Title" to "FileX",
            "Implementation-Version" to version
        )
    }
}

tasks.withType<JavaExec> {
    // Pass all system properties starting with "filex." to the application
    System.getProperties().forEach { (key, value) ->
        if (key.toString().startsWith("filex.")) {
            systemProperty(key.toString(), value)
        }
    }
}
