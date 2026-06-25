# Porting From 1.21.1 To 26.1.2

This page records the build and toolchain changes needed to move this repo from the older `1.21.1` Architectury setup to the `26.1.2` line.

It is intentionally focused on the Gradle and environment migration first. Code-level API changes come after the project can sync, compile, and launch again.

## Core Rule

`26.1.x` is not a normal point-version bump.

The important differences are:

- Minecraft `26.1.2` requires Java `25`
- Gradle must be new enough to run on Java `25`
- the old remap-based Architectury Loom setup is not the right model anymore
- the old `modImplementation` / `remapJar` / `namedElements` shape must be replaced

## Order Of Operations

Follow this order. Do not jump to code fixes first.

1. Install Java `25`
2. Make Gradle actually use Java `25`
3. Upgrade the Gradle wrapper
4. Upgrade the Architectury build plugins
5. Switch from `loom` to `loom-no-remap`
6. Remove old remap-era dependency and packaging patterns
7. Sync/build again
8. Only then start fixing compile errors in mod code

## Required Environment

This repo currently uses:

- Java `25`
- Gradle `9.5.1`
- `architectury-plugin` `3.5-SNAPSHOT`
- `dev.architectury.loom-no-remap` `1.17-SNAPSHOT`

If Gradle still runs on Java `21`, the migration will fail before code is even considered.

## Step 1: Gradle Wrapper

Update [gradle-wrapper.properties](/mnt/a/Projects/The%20Experiment%20Lab/Minecraft/sagittary/gradle/wrapper/gradle-wrapper.properties) to a Gradle `9.x` release.

Current working value in this repo:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
```

## Step 2: Force Gradle Onto Java 25

IntelliJ SDK settings alone were not enough in this repo. The reliable fix was setting `org.gradle.java.home` in [gradle.properties](/mnt/a/Projects/The%20Experiment%20Lab/Minecraft/sagittary/gradle.properties).

Current value:

```properties
org.gradle.java.home=C:\\Users\\video\\.jdks\\ms-25.0.3
```

Adjust that path for each machine.

What matters is the Gradle daemon JVM, not the wrapper launcher JVM.

## Step 3: Update Version Pins

Current `26.1.2` values in [gradle.properties](/mnt/a/Projects/The%20Experiment%20Lab/Minecraft/sagittary/gradle.properties):

```properties
minecraft_version=26.1.2
architectury_api_version=20.0.7
fabric_loader_version=0.19.3
fabric_api_version=0.153.0+26.1.2
neoforge_version=26.1.2.76
enabled_platforms=fabric,neoforge
```

## Step 4: Root Build Script Changes

In [build.gradle](/mnt/a/Projects/The%20Experiment%20Lab/Minecraft/sagittary/build.gradle):

1. Change the Loom plugin to `dev.architectury.loom-no-remap`
2. Bump `architectury-plugin`
3. Apply `loom-no-remap` in subprojects
4. Target Java `25`
5. Keep the `minecraft` dependency
6. Do not keep the old explicit `mappings loom.officialMojangMappings()` line

Current root shape:

```gradle
plugins {
    id 'dev.architectury.loom-no-remap' version '1.17-SNAPSHOT' apply false
    id 'architectury-plugin' version '3.5-SNAPSHOT'
    id 'com.github.johnrengelman.shadow' version '8.1.1' apply false
}

subprojects {
    apply plugin: 'dev.architectury.loom-no-remap'
    apply plugin: 'architectury-plugin'
    apply plugin: 'maven-publish'

    dependencies {
        minecraft "net.minecraft:minecraft:$rootProject.minecraft_version"
    }

    java {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    tasks.withType(JavaCompile).configureEach {
        it.options.release = 25
    }
}
```

## Step 5: Common Module Changes

In [common/build.gradle](/mnt/a/Projects/The%20Experiment%20Lab/Minecraft/sagittary/common/build.gradle):

- replace `modImplementation` with `implementation`
- keep Fabric Loader only for shared annotation usage if needed

Current shape:

```gradle
dependencies {
    implementation "net.fabricmc:fabric-loader:$rootProject.fabric_loader_version"
    implementation "dev.architectury:architectury:$rootProject.architectury_api_version"
}
```

## Step 6: Fabric Module Changes

In [fabric/build.gradle](/mnt/a/Projects/The%20Experiment%20Lab/Minecraft/sagittary/fabric/build.gradle):

- replace `modImplementation` with `implementation`
- replace `common(project(path: ':common', configuration: 'namedElements'))` with direct `project(':common')`
- remove `remapJar`
- make `shadowJar` the primary output artifact

Current shape:

```gradle
dependencies {
    implementation "net.fabricmc:fabric-loader:$rootProject.fabric_loader_version"
    implementation "net.fabricmc.fabric-api:fabric-api:$rootProject.fabric_api_version"
    implementation "dev.architectury:architectury-fabric:$rootProject.architectury_api_version"

    common(project(path: ':common')) { transitive false }
    shadowBundle project(path: ':common', configuration: 'transformProductionFabric')
}

jar {
    archiveClassifier = 'raw'
}

shadowJar {
    dependsOn jar
    mainSpec.sourcePaths.clear()
    from(zipTree(jar.archiveFile))
    configurations = [project.configurations.shadowBundle]
    archiveClassifier = null
}
```

## Step 7: NeoForge Module Changes

In [neoforge/build.gradle](/mnt/a/Projects/The%20Experiment%20Lab/Minecraft/sagittary/neoforge/build.gradle):

- replace `modImplementation` with `implementation`
- replace `namedElements` with direct `project(':common')`
- remove `remapJar`
- make `shadowJar` the primary output artifact

Current shape:

```gradle
dependencies {
    neoForge "net.neoforged:neoforge:$rootProject.neoforge_version"
    implementation "dev.architectury:architectury-neoforge:$rootProject.architectury_api_version"

    common(project(path: ':common')) { transitive false }
    shadowBundle project(path: ':common', configuration: 'transformProductionNeoForge')
}

jar {
    archiveClassifier = 'raw'
}

shadowJar {
    dependsOn jar
    mainSpec.sourcePaths.clear()
    from(zipTree(jar.archiveFile))
    configurations = [project.configurations.shadowBundle]
    archiveClassifier = null
}
```

## What Broke During Migration

These were the real blockers in this repo:

- Gradle was still using Java `21`
- IntelliJ SDK settings alone did not switch the Gradle daemon
- old Gradle `8.x` was incompatible with Java `25`
- old Architectury remap-based setup failed on `26.1.2`
- removing the old `mappings` line alone was not enough while still using the old Loom shape
- the old `modImplementation` / `namedElements` / `remapJar` model had to be replaced

## What Not To Do

- do not switch the codebase to Yarn just because `26.1.x` is new
- do not start renaming code before the project syncs
- do not keep `remapJar` in a `loom-no-remap` project
- do not assume IntelliJ changing the Project SDK automatically changes the Gradle daemon JVM

## Mojang Names vs Yarn

For this migration, stay on Mojang-style names.

The point of the `26.1.x` change is not "move everything to Yarn". The point is that the old remap workflow changed substantially, and the project has to move to the new no-remap build shape first.

## After The Build Works

Only after the project syncs and compiles far enough should you start handling:

- renamed or moved Minecraft classes
- Fabric API API changes
- NeoForge API changes
- Architectury API differences
- run configuration regeneration

## Inspecting Minecraft Classes

Yes. You should still inspect external Minecraft classes directly.

The main options are:

- mapped source jars in Gradle caches
- merged Minecraft jars in Loom caches
- `jar tf` plus targeted source extraction

Practical rule:

1. Search your own code first
2. Identify the likely vanilla class
3. Search the cached jars for that exact class
4. Open only that class or the closest one or two neighbors

This is still the right way to answer:

- menu and screen behavior questions
- renderer changes
- item model behavior
- entity behavior
- worldgen behavior
- registry/bootstrap changes

## Repo Note

If IntelliJ run configurations still point at the old repo folder name, regenerate or fix them separately after the toolchain migration. That is a run-config issue, not the main `26.1.2` build migration.
