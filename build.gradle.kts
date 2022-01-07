import de.undercouch.gradle.tasks.download.Download
import com.felipefzdz.gradle.shellcheck.Shellcheck

plugins {
    id("base")
    id("de.undercouch.download") version "4.1.2"
    id("com.felipefzdz.gradle.shellcheck") version "1.4.6"
}

repositories {
    mavenCentral()
}

allprojects {
    version = "0.0.1-SNAPSHOT"
}

val commonComponents by configurations.creating
val mavenComponents by configurations.creating

dependencies {
    commonComponents(project(path =":fetch-build-scan-data-cmdline-tool", configuration = "shadow"))
    mavenComponents(project(":capture-published-build-scan-maven-extension"))
    mavenComponents("com.gradle:gradle-enterprise-maven-extension:1.12")
    mavenComponents("com.gradle:common-custom-user-data-maven-extension:1.9")
}

val argbashVersion by extra("2.10.0")

shellcheck {
    additionalArguments = "-a -x"
    shellcheckVersion = "v0.7.2"
}

tasks.register<Download>("downloadArgbash") {
    group = "argbash"
    description = "Downloads Argbash."
    src("https://github.com/matejak/argbash/archive/refs/tags/${argbashVersion}.zip")
    dest(file("${buildDir}/argbash/argbash-${argbashVersion}.zip"))
    overwrite(false)
}

tasks.register<Copy>("unpackArgbash") {
    group = "argbash"
    description = "Unpacks Argbash."
    from(zipTree(tasks.getByName("downloadArgbash").outputs.files.singleFile))
    into(layout.buildDirectory.dir("argbash"))
    dependsOn("downloadArgbash")
}

tasks.register<ApplyArgbash>("generateBashCliParsers") {
    group = "argbash"
    description = "Uses Argbash to generate Bash command line argument parsing code."
    argbashVersion.set(project.extra["argbashVersion"].toString())
    scriptTemplates.set(fileTree("components/scripts") {
        include("**/*-cli-parser.m4")
        exclude("gradle/.data/")
        exclude("maven/.data/")
    })
    supportingTemplates.set(fileTree("components/scripts") {
        include("**/*.m4")
        exclude("gradle/.data/")
        exclude("maven/.data/")
    })
    dependsOn("unpackArgbash")
}

tasks.register<Copy>("copyGradleScripts") {
    group = "build"
    description = "Copies the Gradle source and generated scripts to output directory."
    from(layout.projectDirectory.dir("LICENSE"))
    from(layout.projectDirectory.dir("components/scripts/gradle")) {
        exclude(".data/")
        filter { line: String -> line.replace("/../lib", "/lib").replace("<HEAD>","${project.version}") }
    }
    from(layout.projectDirectory.dir("components/scripts")) {
        include("README.md")
        include("lib/**")
        exclude("maven")
        exclude("lib/cli-parsers")
        exclude("**/*.m4")
        filter { line: String -> line.replace("/../lib", "/lib").replace("<HEAD>","${project.version}") }
    }
    from(layout.buildDirectory.dir("generated/scripts/lib/cli-parsers/gradle")) {
        into("lib/")
    }
    from(commonComponents) {
        into("lib/export-api-clients/")
    }
    into(layout.buildDirectory.dir("scripts/gradle"))
    dependsOn("generateBashCliParsers")
}

tasks.register<Copy>("copyMavenScripts") {
    group = "build"
    description = "Copies the Maven source and generated scripts to output directory."
    from(layout.projectDirectory.dir("LICENSE"))
    from(layout.projectDirectory.dir("components/scripts/maven")) {
        exclude(".data/")
        filter { line: String -> line.replace("/../lib", "/lib").replace("<HEAD>","${project.version}") }
    }
    from(layout.projectDirectory.dir("components/scripts/")) {
        include("README.md")
        include("lib/**")
        exclude("gradle")
        exclude("lib/cli-parsers")
        exclude("**/*.m4")
        filter { line: String -> line.replace("/../lib", "/lib").replace("<HEAD>","${project.version}") }
    }
    from(layout.buildDirectory.dir("generated/scripts/lib/cli-parsers/maven")) {
        into("lib/")
    }
    from(commonComponents) {
        into("lib/export-api-clients/")
    }
    from(mavenComponents) {
        into("lib/maven/")
    }
    into(layout.buildDirectory.dir("scripts/maven"))
    dependsOn("generateBashCliParsers")
}

tasks.register<Task>("copyScripts") {
    group = "build"
    description = "Copies source scripts and autogenerated scripts to output directory."
    dependsOn("copyGradleScripts")
    dependsOn("copyMavenScripts")
}

tasks.register<Zip>("assembleGradleScripts") {
    group = "build"
    description = "Packages the Gradle experiment scripts in a zip archive."
    archiveBaseName.set("gradle-enterprise-gradle-build-validation")
    archiveFileName.set("${archiveBaseName.get()}.zip")
    from(layout.buildDirectory.dir("scripts/gradle")) {
        exclude("**/.data")
    }
    into(archiveBaseName.get())
    dependsOn("generateBashCliParsers")
    dependsOn("copyGradleScripts")
}

tasks.register<Zip>("assembleMavenScripts") {
    group = "build"
    description = "Packages the Maven experiment scripts in a zip archive."
    archiveBaseName.set("gradle-enterprise-maven-build-validation")
    archiveFileName.set("${archiveBaseName.get()}.zip")
    from(layout.buildDirectory.dir("scripts/maven")) {
        exclude("**/.data")
    }
    into(archiveBaseName.get())
    dependsOn("generateBashCliParsers")
    dependsOn("copyMavenScripts")
}

tasks.named("assemble") {
    dependsOn("assembleGradleScripts")
    dependsOn("assembleMavenScripts")
}

tasks.register<Shellcheck>("shellcheckGradleScripts") {
    group = "verification"
    description = "Perform quality checks on Gradle build validation scripts using Shellcheck."
    sourceFiles = fileTree("${buildDir}/scripts/gradle") {
        include("**/*.sh")
        exclude("lib/")
    }
    workingDir = file("${buildDir}/scripts/gradle")
    reports {
        html.destination = file("${buildDir}/reports/shellcheck-gradle/shellcheck.html")
        xml.destination = file("${buildDir}/reports/shellcheck-gradle/shellcheck.xml")
        txt.destination = file("${buildDir}/reports/shellcheck-gradle/shellcheck.txt")
    }
    dependsOn("generateBashCliParsers")
    dependsOn("copyGradleScripts")
}

tasks.register<Shellcheck>("shellcheckMavenScripts") {
    group = "verification"
    description = "Perform quality checks on Maven build validation scripts using Shellcheck."
    sourceFiles = fileTree("${buildDir}/scripts/maven") {
        include("**/*.sh")
        exclude("lib/")
    }
    workingDir = file("${buildDir}/scripts/maven")
    reports {
        html.destination = file("${buildDir}/reports/shellcheck-maven/shellcheck.html")
        xml.destination = file("${buildDir}/reports/shellcheck-maven/shellcheck.xml")
        txt.destination = file("${buildDir}/reports/shellcheck-maven/shellcheck.txt")
    }
    dependsOn("generateBashCliParsers")
    dependsOn("copyMavenScripts")
}

tasks.named("check") {
    dependsOn("shellcheckGradleScripts")
    dependsOn("shellcheckMavenScripts")
}
