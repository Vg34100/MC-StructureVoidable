# Converting an Architectury mod to a Stonecutter version matrix

This is a practical recipe for taking a Minecraft mod that currently targets one
Minecraft version (for example, 26.1.2) and maintaining it against multiple
versions from one branch and one source tree. It was written from Fishing
Frenzy's forward 26.x migration and its later backward port to 1.21 and
1.21.1 on Fabric and NeoForge.

The aim is not to make every Minecraft version compile by hiding every API
difference. The aim is to give each supported Minecraft/loader pair an explicit
build target, pinned dependencies, isolated development run directory, and a
runtime smoke test.

## What this architecture provides

The target names follow this shape:

```text
<minecraft-version>-fabric
<minecraft-version>-neoforge
```

For Fishing Frenzy, the resulting matrix is:

| Target | Minecraft | Loader | Runtime status |
| --- | --- | --- | --- |
| `1.21-fabric` | 1.21 | Fabric | client and dedicated server verified |
| `1.21-neoforge` | 1.21 | NeoForge | client and dedicated server verified |
| `1.21.1-fabric` | 1.21.1 | Fabric | client and dedicated server verified |
| `1.21.1-neoforge` | 1.21.1 | NeoForge | client and dedicated server verified |
| `26.1-fabric` | 26.1 | Fabric | verified through resource reload |
| `26.1-neoforge` | 26.1 | NeoForge | verified through resource reload |
| `26.1.1-fabric` | 26.1.1 | Fabric | verified through resource reload |
| `26.1.1-neoforge` | 26.1.1 | NeoForge | verified through resource reload |
| `26.1.2-fabric` | 26.1.2 | Fabric | verified through resource reload |
| `26.1.2-neoforge` | 26.1.2 | NeoForge | verified through resource reload |
| `26.2-fabric` | 26.2 | Fabric | verified through resource reload |
| `26.2-neoforge` | 26.2 | NeoForge | verified through resource reload |

Each target is a Gradle project visible in IntelliJ. Its `runClient` and
`runServer` tasks are therefore selectable separately instead of requiring a
branch checkout or a rewrite of root `gradle.properties`.

The shared Java/resources stay canonical in the normal `common/`, `fabric/`,
and `neoforge/` directories. Only a target that needs source-level API changes
gets a generated compatibility source view.

Before declaring a target supported, use the accompanying
[`stonecutter-port-acceptance-checklist.md`](stonecutter-port-acceptance-checklist.md).
It distinguishes a compiling project, a development launch, and a real
installable release jar—the three checks that caught the important failures in
this matrix.

Future agent sessions should follow the context-efficient port order below.
Do not begin with an exhaustive launch sweep.

## Before changing the build

1. Start from a working, single-version build for both loaders.
2. Record the exact dependencies that successfully start each current loader.
3. Save a clean baseline run log for Fabric and NeoForge.
4. Keep existing code canonical for the most actively maintained version unless
   the oldest API is a clean shared abstraction. Generate an explicit legacy
   view for demonstrated backward differences instead of downgrading every
   current target.
5. Do not promise support from a compile result alone. Client mixins, metadata,
   resource formats, and NeoForge's launch pipeline are runtime concerns.

## Context-efficient port order

Use phase gates so one incorrect compatibility assumption is not multiplied
across the entire matrix:

1. Inventory the mod's Minecraft API calls, mixins, resources, dependencies,
   vanilla-derived behavior, and loader-native hooks.
2. Prove the unchanged canonical target still compiles and launches.
3. Choose sentinel targets: the oldest Fabric target, the oldest NeoForge
   target, and any known dependency/API boundary. Work on only those targets.
4. Compile a sentinel after batching related edits. Once it compiles, package
   its actual release artifact and test representative behavior—including
   models and textures—before copying the transform to adjacent versions.
5. Expand only a demonstrated transform to targets in the same API generation.
   Compile and package those targets together in one smart-wrapper invocation.
6. Run the exhaustive matrix compile/package and client/server sweep only after
   sentinel behavior is correct. Do not repeatedly launch unchanged targets.
7. Keep a small validation ledger recording target, artifact type, commit,
   client result, server result, external-launcher result, and gameplay result.

Use an invalidation rule when deciding what to rerun: shared Java changes affect
both loaders in the relevant generation; loader code affects only that loader;
resource transforms require packaging and visual/resource validation; build
logic requires the matrix aggregates. Documentation-only changes do not
invalidate game launches.

This order is both faster and stronger than compiling all targets first and
discovering shared gameplay defects during the final sweep.

## Files to add

```text
settings.gradle
stonecutter.gradle
build.matrix.gradle
gradle/matrix/<mc>-fabric.properties
gradle/matrix/<mc>-neoforge.properties
docs/development/testing.md
```

`settings.gradle` owns the target list. `stonecutter.gradle` owns aggregate
tasks. `build.matrix.gradle` is the central build script applied to every
generated target. The small property files make version pinning inspectable and
avoid a large maze of `if (project.name == ...)` statements.

## 1. Register Stonecutter targets

Add the Stonecutter plugin repository and plugin to `settings.gradle`, then
register every Minecraft/loader pair:

```groovy
pluginManagement {
    repositories {
        maven { url 'https://maven.kikugie.dev/releases' }
        maven { url 'https://maven.fabricmc.net/' }
        maven { url 'https://maven.architectury.dev/' }
        gradlePluginPortal()
    }
}

plugins {
    id 'dev.kikugie.stonecutter' version '0.9.7'
}

rootProject.name = 'examplemod'

stonecutter {
    centralScript = 'build.matrix.gradle'
    kotlinController = false // only when retaining Groovy build scripts

    create(rootProject) {
        version('1.21-fabric', '1.21')
        version('1.21-neoforge', '1.21')
        version('1.21.1-fabric', '1.21.1')
        version('1.21.1-neoforge', '1.21.1')
        version('26.1-fabric', '26.1')
        version('26.1-neoforge', '26.1')
        version('26.1.1-fabric', '26.1.1')
        version('26.1.1-neoforge', '26.1.1')
        version('26.1.2-fabric', '26.1.2')
        version('26.1.2-neoforge', '26.1.2')
        version('26.2-fabric', '26.2')
        version('26.2-neoforge', '26.2')
    }
}
```

Do not leave the old `include 'common'`, `include 'fabric'`, and `include
'neoforge'` setup active alongside this design. Stonecutter's targets are the
new build projects; the original directories become source/resource inputs to
the central script.

### Critical NeoForge setting: make it early

Loom chooses its platform while a project is being configured. A NeoForge node
must have `loom.platform=neoforge` *before* Loom is applied. If it is supplied
too late, the target can compile yet create a Fabric/Knot launch classpath.

Set a project property from `settings.gradle` before each generated project is
evaluated:

```groovy
gradle.beforeProject { project ->
    if (project.name.endsWith('-neoforge')) {
        project.extensions.extraProperties.set('loom.platform', 'neoforge')
    }
}
```

Symptoms of missing this setting include `KnotClient` appearing in a NeoForge
launch command, a `ClassNotFoundException` for `net.neoforged.fml.startup.Client`,
or FML discovering an unmodified generic Minecraft jar rather than
`forge-universal.jar` and a `neoforge-...-minecraft-merged-deobf` jar.

## 2. Put one dependency set in each target file

Example Fabric target (`gradle/matrix/26.2-fabric.properties`):

```properties
minecraft_version=26.2
loader=fabric
architectury_api_version=21.0.7
fabric_loader_version=0.19.4
fabric_api_version=0.156.0+26.2
modmenu_version=20.0.1
mixinextras_version=0.5.5
```

Example NeoForge target:

```properties
minecraft_version=26.2
loader=neoforge
architectury_api_version=21.0.7
neoforge_version=26.2.0.62
mixinextras_version=0.5.5
```

Pin every client-only development dependency too. A compatible mod can still
crash at the title screen if a development helper targets an older Minecraft
API. Fishing Frenzy's 26.2 Fabric client needed Mod Menu 20.0.1; the older
18.0.0-beta.1 called a removed `I18n.exists(String)` method at runtime.

Avoid copying Fishing Frenzy's version numbers into another mod. Resolve and
test that mod's own Fabric API, Architectury, NeoForge, mixin, and optional
client-mod compatibility.

## 3. Build the target project correctly

The central script should:

1. Read `gradle/matrix/${project.name}.properties`.
2. Derive `isFabric` / `isNeoForge` from `loader`.
3. Set the Architectury Minecraft version for the generated node.
4. Select the Architectury loader transform.
5. Set Loom's native NeoForge platform for NeoForge nodes.
6. Add the universal `minecraft` dependency plus that target's loader/runtime
   dependencies.
7. Point the source set at the canonical source/resource directories.
8. Give every run task an isolated `runs/<target>/<run-task>` directory.

The essential platform setup is:

```groovy
architectury {
    minecraft = matrixValue('minecraft_version')
    platformSetupLoomIde()
    if (isFabric) fabric() else neoForge()
}

if (isNeoForge) {
    loom {
        neoForge { }
    }
}

dependencies {
    minecraft "net.minecraft:minecraft:${matrixValue('minecraft_version')}"
    if (isFabric) {
        implementation "net.fabricmc:fabric-loader:${matrixValue('fabric_loader_version')}"
        implementation "net.fabricmc.fabric-api:fabric-api:${matrixValue('fabric_api_version')}"
        implementation "dev.architectury:architectury-fabric:${matrixValue('architectury_api_version')}"
    } else {
        neoForge "net.neoforged:neoforge:${matrixValue('neoforge_version')}"
        implementation "dev.architectury:architectury-neoforge:${matrixValue('architectury_api_version')}"
    }
}
```

`architectury.neoForge()` and `loom.neoForge {}` are both needed in this
standalone-target arrangement. The first configures Architectury's transform;
the second tells Loom to create NeoForge's patched Minecraft/FML launch
pipeline. Treat them as distinct responsibilities.

For the generated target to resemble Architectury's normal NeoForge module,
preserve the usual `common`, classpath, and `developmentNeoForge` configuration
relationships when the original project needs them:

```groovy
if (isNeoForge) {
    configurations {
        common {
            canBeResolved = true
            canBeConsumed = false
        }
        compileClasspath.extendsFrom common
        runtimeClasspath.extendsFrom common
        developmentNeoForge.extendsFrom common
    }
}
```

## 4. Keep run directories isolated

Never share a run directory between version targets. Saves, configs, generated
server properties, logs, and a loader's cached state can contaminate another
target.

```groovy
loom {
    runs {
        configureEach {
            runDir = rootProject.file("runs/${project.name}/${name}")
        }
    }
}
```

This gives predictable paths such as:

```text
runs/26.2-fabric/client
runs/26.2-neoforge/client
runs/26.2-fabric/server
runs/26.2-neoforge/server
```

## 5. Make metadata version-aware

Do not hard-code the oldest Minecraft version in loader metadata. It will make a
perfectly compiled target fail loader resolution before mod initialization.

For Fabric, replace the Minecraft requirement in `fabric.mod.json` with:

```json
"minecraft": "${minecraft_version}"
```

Then expand it from `processResources`:

```groovy
filesMatching('fabric.mod.json') {
    expand version: project.version, minecraft_version: matrixValue('minecraft_version')
}
```

Do the same for NeoForge metadata if its Minecraft, NeoForge, or Architectury
range varies by target. Verify the processed resource, not only the source file.

## 6. Handle source compatibility deliberately

First compile the newest target unchanged. Read the small set of real API
errors, inspect the corresponding vanilla/loader sources, and choose one of
these mechanisms:

| Situation | Recommended mechanism |
| --- | --- |
| Same behavior, a few mechanical renamed symbols | Generate a target source view with narrow replacements. |
| New and old APIs need materially different logic | Split an implementation behind a shared interface or add version-specific source files. |
| Behavior is loader-specific | Keep it in Fabric/NeoForge source, not shared/common code. |
| Resource schema changed | Use target-specific resource processing or data generation. |

Fishing Frenzy's 26.2 view is generated into `build/matrix-sources/` from the
canonical 26.1.2 source. It replaces only verified, version-specific API moves
and method renames. This is appropriate for a handful of deterministic changes;
it is not a general text-replacement strategy. Do not use it for behavior
differences or broad identifier substitutions.

Generated source views must be declared as task inputs and compilation must
depend on their generation task. Otherwise IntelliJ/Gradle can compile stale
files and hide a source compatibility failure.

### Backporting a 26.x Architectury mod to 1.21 / 1.21.1

Backward ports cross a larger API and data-format boundary than nearby forward
updates. Treat `1.21` and `1.21.1` as a legacy generation with its own pinned
Loom, Architectury, loader, Java, source, and resource behavior. Do not add
their property files and assume the current build configuration will work.

Fishing Frenzy's verified legacy target properties use this shape:

```properties
# gradle/matrix/1.21.1-neoforge.properties
minecraft_version=1.21.1
loader=neoforge
java_version=21
loom_generation=legacy
architectury_api_version=13.0.8
neoforge_version=21.1.220
```

The legacy generation uses Loom 1.7.414 and Architectury 13. The modern
Architectury plugin/transformer is deliberately not applied to those targets:
Fishing Frenzy has no `@ExpectPlatform` calls that need its runtime transform,
and the later transformer prevented early NeoForge from completing bootstrap.
This is project-specific evidence, not permission to omit Architectury setup
in another mod: search that mod for `@ExpectPlatform`, platform-only classes,
and other Architectury transform requirements first.

Use a generated source/resource view only for concrete differences that have
been inspected and tested. Fishing Frenzy's legacy generator accounts for:

| Difference | Legacy adaptation |
| --- | --- |
| Current Java 25 runtime vs old loader/Mixin | Run game tasks with Gradle's Java 21 `javaLauncher`. |
| `FishingHookRenderer` rod check changed | Replace the current `getHoldingArm` redirect with the 1.21 `getPlayerHandPos` / `ItemStack.is` redirect. |
| Entity/render-state API did not exist | Generate the older entity-model and renderer method shapes. |
| Entity builder registration and attributes differ | Generate only the established 1.21 builder/attribute calls. |
| 26.x item definitions and ingredient strings | Remove `assets/<modid>/items/` definitions; convert recipe ingredients back to `{ "item": ... }` / `{ "tag": ... }`; restore the old rod-model override. |
| 26.x item-definition tint data | Register legacy Fabric/NeoForge item colors for every targeted bait; callbacks must return opaque `0xFFRRGGBB` values. Override fish-textured spawn eggs to opaque white so `SpawnEggItem` does not tint the fish artwork. |
| NeoForge client renderer events | Add `EventBusSubscriber.Bus.MOD` only in the legacy NeoForge generated source. |
| NeoForge-only current compatibility mixin | Remove its declaration and file when it addresses a 26.x API rename that is absent in 1.21. |
| NeoForge 26.2 metadata | Use `iconFile` in canonical metadata and generate the old `logoFile` key for legacy targets. |

Do not use broad global replacements for a backport. Each replacement must be
scoped to one source/resource, explain the old API, and be covered by a legacy
client and server smoke test.

### Java 21 and IntelliJ run tasks

The 1.21 generation must run on Java 21 even if Gradle itself uses Java 25.
Set the target project's Java toolchain before Loom creates its run tasks:

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

Loom derives each run task's `javaLauncher` from that project toolchain. Never
set `JavaExec.executable`, and do not mutate a run task's `javaLauncher` after
Loom has finalized it. Gradle may accept an executable-path override in some
command-line cases, but IntelliJ's Gradle runner rejects the conflict with:

```text
Toolchain from `executable` property does not match toolchain from
`javaLauncher` property.
```

After refreshing Gradle in IntelliJ, run the generated target directly under
`<target> > Tasks > loom > runClient` (for example,
`1.21.1-neoforge > Tasks > loom > runClient`). This is the correct task. If
Java 21 is not auto-detected, point Gradle's Java toolchain discovery at a JDK
21 installation; do not hard-code a Prism runtime executable into the build.

## 7. Add aggregate verification tasks

Keep the list of supported targets in one place. Then expose tasks that an IDE,
CI job, or agent can invoke without remembering every target:

```groovy
def matrixTargets = [
    '1.21-fabric', '1.21-neoforge',
    '1.21.1-fabric', '1.21.1-neoforge',
    '26.1-fabric', '26.1-neoforge',
    '26.1.1-fabric', '26.1.1-neoforge',
    '26.1.2-fabric', '26.1.2-neoforge',
    '26.2-fabric', '26.2-neoforge'
]

tasks.register('compileMatrix') {
    dependsOn matrixTargets.collect { ":${it}:compileJava" }
}

tasks.register('packageMatrix') {
    dependsOn matrixTargets.collect { target ->
        (target.startsWith('1.21-') || target.startsWith('1.21.1-'))
                ? ":${target}:remapJar"
                : ":${target}:jar"
    }
}

tasks.register('verifyServerLaunchSetup') {
    dependsOn matrixTargets.collect { ":${it}:configureLaunch" }
}

tasks.register('verifyMatrix') {
    dependsOn 'compileMatrix', 'packageMatrix', 'verifyServerLaunchSetup'
}
```

`configureLaunch` validates that Loom can assemble a dedicated-server launch
configuration. It does **not** start Minecraft. Retain normal dedicated-server
runtime smoke tests before releases or server-sensitive changes.

## Required verification ladder

Use this order for every newly added target:

1. `compileJava` — catches source/API issues.
2. `jar` — catches resource processing and metadata expansion issues. On legacy Loom
   targets it is a Mojang-named `-dev.jar`, strictly for development; do not install it.
3. `remapJar` — required release artifact for legacy Fabric/NeoForge targets. It remaps
   Mojang names to the runtime namespace and is the jar users install. Configure its output
   beside the matrix release jars, then make `packageMatrix` depend on it.
4. `configureLaunch` — catches basic development launch wiring.
5. `runClient` — wait past initial resource reload to a stable title screen.
6. `runServer` — for releases or server-sensitive changes, reach the ready log
   line and stop cleanly.
7. Connect a matching development client to the matching server when networking,
   entities, registries, loot, or gameplay behavior changed.

Steps 1–3 are necessary automation. They are not proof the target works.

Before performing steps 5–7 for every matrix node, complete the whole ladder on
the sentinel targets and exercise representative transformed behavior. A title
screen proves startup, not item transforms, entity geometry, texture selection,
recipes, or interaction behavior.

For every temporary client/server started by an agent, record its exact process
and stop it after the smoke test. Never leave `runServer` bound to port 25565;
it will make the next loader appear broken.

## Runtime failure triage

| Runtime symptom | Likely cause | First place to check |
| --- | --- | --- |
| Loader says mod requires the old Minecraft version | Hard-coded Fabric/NeoForge metadata | Processed `fabric.mod.json` / `neoforge.mods.toml` |
| Fabric title-screen `NoSuchMethodError` from Mod Menu or another helper | Optional client dependency targets the old game API | Target's dependency property file |
| `Mixin transformation ... failed` after a version jump | MixinExtras, mappings, or injector target mismatch | Target dependency versions and the exact mixin target |
| NeoForge starts `KnotClient` | `loom.platform=neoforge` was missing before plugin application | `settings.gradle` early project property |
| NeoForge cannot find FML Client/Server class | Generic Fabric-style launch path rather than NeoForge pipeline | Same platform configuration; inspect generated launch command |
| FML finds generic Minecraft instead of patched game jars | Loom did not use its Forge-like userdev pipeline | `loom { neoForge {} }`, `neoForge` dependency, generated arg file |
| `Address already in use` during server test | Previous run server still owns port 25565 | Stop the exact prior process; do not alter mod code |

## Porting vanilla-derived behavior safely

When a mod adapts vanilla rendering, models, menus, entities, or data, inspect
the exact vanilla implementation for the target Minecraft version before
writing the compatibility layer. Check all three when applicable:

- the vanilla resource JSON, including inherited display transforms;
- the concrete runtime class, not merely its base class or baked layer;
- the method body that actually chooses models, textures, or behavior.

A compatible constructor or successful compile does not prove behavioral
parity. A concrete subclass may add required model parts, and a renderer may
read a private model/texture table instead of calling an apparently overridable
method. Validate the visible or interactive result on a sentinel target before
expanding the implementation.

Apply the same rule to Mixins and reflective adapters. An invoker must match the
target member's exact JVM descriptor; a locally declared interface with the same
method shape is still a different descriptor and can compile before failing at
Mixin application time. Inspect the target bytecode when visibility prevents a
normal source reference, and verify the result in a real runtime.

## Adding another Minecraft version later

Do **not** add all targets at once and guess their dependencies. Use this
controlled expansion:

1. Create the Fabric property file with known published dependencies.
2. Register only `<version>-fabric` and compile it.
3. Add the NeoForge property file and target only after resolving its matching
   NeoForge/FML versions.
4. Compile both loaders.
5. Add a source/resource compatibility view only for demonstrated differences.
6. Run each client's title-screen smoke test.
7. Add the targets to aggregate task lists and CI only after the smoke tests
   pass.

This keeps a bad dependency coordinate or an API regression localized to one
new target instead of destabilizing the established matrix.

### A real compatibility boundary: early NeoForge 26.1.x

Fishing Frenzy's `26.1-neoforge` and `26.1.1-neoforge` projects demonstrate
why compile success is not a support claim. Their published NeoForge releases
provide `net.neoforged.neoforge.event.level.BlockEvent.BreakEvent`, while
Architectury 20.0.4 was compiled against the later renamed
`net.neoforged.neoforge.event.level.block.BreakBlockEvent`. The result is a
startup `NoClassDefFoundError` inside Architectury, despite a successful mod
compile.

Fishing Frenzy handles this narrowly with a NeoForge-only Mixin configuration.
Its configuration plugin rewrites the affected Architectury handler's obsolete
type descriptors to the real early-NeoForge `BlockEvent.BreakEvent` type before
the JVM links the class. The transform is conditional: it does nothing when
the installed Architectury class already uses the newer API. Event delivery
remains real, Architectury stays the user's ordinary dependency, and the build
does not introduce a fake `net.neoforged.*` class. This exact setup was
validated in a production Prism instance with official Architectury 20.0.4.

This is an adapter for a demonstrated binary-name mismatch, not a general
solution for library incompatibility. Keep it small, keep it target-local, and
rerun the title-screen smoke test whenever Architectury or NeoForge changes.

## What belongs in project documentation versus an agent guide

Keep this migration guide in `docs/development/` because it explains the
project's build architecture and gives future maintainers inspectable examples.
Keep the short, enforceable rules in `AGENTS.md`:

- compile all targets through the smart wrapper;
- do not call a target supported from compilation alone;
- use distinct run directories;
- smoke-test `runClient` through resource reload;
- smoke-test dedicated servers for release/server changes;
- stop every temporary test process.

That division gives both humans and future agent sessions a short operational
checklist plus a detailed reference when the build needs to change.
