import java.util.zip.ZipFile

group = "net.sibbl.chatgpt"

patches {
    about {
        name = "sibbl ChatGPT patches"
        description = "Experimental ChatGPT clone and callback preservation; device login unverified"
        source = "https://github.com/sibbl/sibbl-chatgpt-patches"
        author = "sibbl"
        contact = "https://github.com/sibbl/sibbl-chatgpt-patches/issues"
        website = "https://github.com/sibbl/sibbl-chatgpt-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}

// Tests use synthetic XML only; no proprietary APK fixtures.
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
tasks.test { useJUnitPlatform() }
tasks.named("buildAndroid") { dependsOn(tasks.test) }

// The upstream plugin removes META-INF license files; our notices live under licenses/.
tasks.named("buildAndroid") {
    doLast {
        val bundle = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        ZipFile(bundle).use { zip ->
            check(zip.getEntry("classes.dex") != null)
            check(zip.getEntry("licenses/LICENSE") != null)
            check(zip.getEntry("licenses/NOTICE") != null)
            check(zip.entries().asSequence().none { it.name.endsWith(".apk") || it.name.endsWith(".apkm") })
        }
    }
}
