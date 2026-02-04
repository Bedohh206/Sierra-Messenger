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

	subprojects {
		configurations.all {
			resolutionStrategy {
				force("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
				force("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.22")
				force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
				force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
			}
		}
	}
