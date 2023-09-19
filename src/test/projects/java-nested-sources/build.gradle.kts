plugins {
    java
    id("org.sonarqube") version "4.3.1.3277"
}

group = "org.example"
version = "1.0-SNAPSHOT"

sourceSets {
    main {
        java {
            srcDir("src/main/java/pck")
            srcDir("src/main/java/pck2")
        }
    }
}