// Top-level build file restored to classic buildscript style for Gradle 8.5 compatibility
buildscript {
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
	dependencies {
		classpath("com.android.tools.build:gradle:9.0.0")
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.10")
    }
}
