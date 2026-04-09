allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    }
}

val newBuildDir: Directory = rootProject.layout.buildDirectory.dir("../../build").get()
rootProject.layout.buildDirectory.value(newBuildDir)

val rootDrive = rootProject.projectDir.toPath().root?.toString()?.uppercase()

subprojects {
    val projectDrive = project.projectDir.toPath().root?.toString()?.uppercase()
    // Avoid Windows cross-drive relativize errors for plugins located in Pub cache (e.g. C:) when
    // the workspace is on another drive (e.g. E:).
    if (projectDrive == rootDrive) {
        val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
        project.layout.buildDirectory.value(newSubprojectBuildDir)
    }
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}


