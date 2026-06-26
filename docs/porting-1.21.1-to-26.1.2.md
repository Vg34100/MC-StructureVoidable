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
    id 'com.gradleup.shadow' version '9.0.0-beta12' apply false
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

## Shadow Plugin Compatibility

**Critical**: Shadow 8.x is incompatible with Gradle 9.x. You will get errors like:

```
groovy.lang.MissingPropertyException: No such property: mode for class: org.gradle.api.internal.file.copy.NormalizingCopyActionDecorator$StubbedFileCopyDetails
```

The fix is to migrate from `com.github.johnrengelman.shadow` to `com.gradleup.shadow`:

In root `build.gradle`:
```gradle
id 'com.gradleup.shadow' version '9.0.0-beta12' apply false
```

In `fabric/build.gradle` and `neoforge/build.gradle`:
```gradle
plugins {
    id 'com.gradleup.shadow'
}
```

## Build Output and Distribution

The Architectury + Shadow setup produces two jar types:

| Jar | Contents | Use |
|-----|----------|-----|
| `sagittary-fabric-X.X.X-raw.jar` | Platform module only, missing common | Do not distribute |
| `sagittary-fabric-X.X.X.jar` | Full mod with common bundled | Distribute this one |

To build the distributable jars, run `shadowJar`:

```bash
gradlew :fabric:shadowJar :neoforge:shadowJar
```

Or in IntelliJ: `sagittary/Tasks/shadow/shadowJar`

The final jars will be in:
- `fabric/build/libs/sagittary-fabric-X.X.X.jar`
- `neoforge/build/libs/sagittary-neoforge-X.X.X.jar`

## JEI Integration (MC 26.1.2 / JEI 29.x)

JEI plugin registration changed. You need both:

1. **Fabric entrypoint** in `fabric.mod.json`:
```json
"entrypoints": {
    "jei_mod_plugin": [
        "net.vg.sagittary.compat.jei.SagittaryJeiPlugin"
    ]
}
```

2. **Service file** at `common/src/main/resources/META-INF/services/mezz.jei.api.IModPlugin`:
```
net.vg.sagittary.compat.jei.SagittaryJeiPlugin
```

### JEI API Changes

- `IRecipeCategory` now uses `getWidth()` and `getHeight()` instead of `getBackground()`
- Direct text rendering in categories is unreliable; use tooltips via `addRichTooltipCallback()` or `addIngredientInfo()` instead
- `GuiGraphics` is now `GuiGraphicsExtractor` in this MC version

## NeoForge-Specific Gotchas

### Creative Tabs

`CreativeTabRegistry.modify()` crashes on NeoForge with empty/new tabs. Use the builder pattern instead:

```java
// DON'T do this on NeoForge:
CreativeTabRegistry.modify(TAB, (flags, output, canUseGameMasterBlocks) -> {
    output.accept(new ItemStack(MY_ITEM.get()));
});

// DO this instead:
CreativeTabRegistry.create(builder -> builder
    .title(Component.translatable("itemGroup.mymod"))
    .icon(() -> new ItemStack(MY_ITEM.get()))
    .displayItems((parameters, output) -> {
        output.accept(new ItemStack(MY_ITEM.get()));
    })
);
```

### Custom Tooltip Components

Custom `TooltipComponent` classes need explicit registration on NeoForge via event:

```java
// In your NeoForge mod class:
modEventBus.addListener(this::registerTooltipComponents);

private void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
    event.register(MyTooltip.class, MyTooltipRenderer::new);
}
```

Architectury's `ClientTooltipComponentRegistry.register()` handles Fabric automatically but NeoForge needs the event.

### Platform-Specific Mixins

Some client mixins may fail on NeoForge due to different method signatures between Fabric (intermediary) and NeoForge (Mojmap) mappings. If a mixin works on Fabric but crashes NeoForge with "failed injection check, (0/1) succeeded", move it to a platform-specific mixin config:

1. Create `fabric/src/main/resources/mymod-fabric.mixins.json`
2. Add it to `fabric.mod.json` mixins array
3. Remove the problematic mixin from the common config

## Architectury Project Structure Notes

Common module resources (like `sagittary.mixins.json`) are bundled into platform jars via `shadowJar`, not the regular `jar` task. If you see "mixin config not found" errors, you're using the wrong jar file.

## MC 26.1.2 Code-Level API Changes

These are the actual code breakages encountered when migrating this mod. The build section above must be working before tackling these.

### ModMenu Version

ModMenu 14.x uses Fabric intermediary names (`class_437` for `Screen`) which clash with Mojang names in `loom-no-remap`. Use ModMenu 18.x for MC 26.1.2:

```properties
modmenu_version=18.0.0-beta.1
```

### fabric.mod.json Version Constraint

The old constraint `~1.21.5` will not match `26.1.2`. Fabric loader will refuse to load the mod silently. Change to an exact match:

```json
"depends": {
  "minecraft": "26.1.2",
  "java": ">=25",
  "architectury": ">=20.0.0"
}
```

### FMLEnvironment.dist (NeoForge)

`FMLEnvironment.dist` exists in FancyModLoader at runtime but is **not on the NeoForge compile classpath** in the 26.1.2 Loom setup. Any reference to it will fail with `variable dist not found`.

The fix is to remove the dist check entirely. `FMLClientSetupEvent` only fires on the client, so the listener is safe to register unconditionally:

```java
// BEFORE (broken):
if (FMLEnvironment.dist == Dist.CLIENT) {
    modEventBus.addListener(this::clientSetup);
}

// AFTER (correct):
modEventBus.addListener(this::clientSetup);
```

### BlockEntityRenderer: New Two-Type Interface

`BlockEntityRenderer` is now `BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState>`. The old single `render()` method is replaced by three methods:

```java
public class MyBER implements BlockEntityRenderer<MyBlockEntity, MyBER.RenderState> {

    public static class RenderState extends BlockEntityRenderState {
        // snapshot fields — no live game references
    }

    @Override public RenderState createRenderState() { return new RenderState(); }

    @Override
    public void extractRenderState(MyBlockEntity be, RenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderState.extractBase(be, state, overlay);
        // read from 'be' and write to 'state' — this runs on the game thread
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
        // render using only 'state' — this runs on the render thread
    }
}
```

Key design rule: `extractRenderState` touches live game state; `submit` must only use the snapshot in `RenderState`. Do not pass live `Level` or `BlockEntity` references into the render state.

### Rendering World Blocks from a BER: Use submitMovingBlock

`BlockModelRenderState.submit()` / `submitWithZOffset()` are for **item and entity model rendering** (held items, item frames, dropped items). They use the wrong render pass for world blocks and will produce white/untextured results.

To render an actual block with correct textures, lighting, and AO from inside a BER `submit()`, use `submitMovingBlock`:

```java
// In extractRenderState — grab lighting context from ClientLevel:
if (be.getLevel() instanceof ClientLevel clientLevel) {
    state.biome = clientLevel.getBiome(be.getBlockPos());
    state.cardinalLighting = clientLevel.cardinalLighting();
    state.lightEngine = clientLevel.getLightEngine();
}

// In submit — create a MovingBlockRenderState and call submitMovingBlock:
MovingBlockRenderState movingState = new MovingBlockRenderState();
movingState.blockState = Blocks.STONE.defaultBlockState();
movingState.blockPos = targetPos;
movingState.randomSeedPos = targetPos;
movingState.biome = state.biome;
movingState.cardinalLighting = state.cardinalLighting;
movingState.lightEngine = state.lightEngine;

poseStack.pushPose();
poseStack.translate(dx, dy, dz); // relative to block entity position
nodeCollector.submitMovingBlock(poseStack, movingState);
poseStack.popPose();
```

`MovingBlockRenderState` carries the world lighting context that the block renderer needs. This is the same pattern `PistonHeadRenderer` uses for piston blocks.

### Z-Fighting: One Render Per Block Entity Position

Each structure void block has its own `BlockEntity`. If your BER scans a range (e.g. a 2×2×2 area) and adjacent block entities have overlapping scan areas, you will get Z-fighting — diagonal lines and camera-dependent flickering where two block entities both try to render a solid block at the same position.

Fix: each block entity should render **only its own position**, not a scan range:

```java
// WRONG — causes overlap between adjacent block entities:
for (BlockPos pos : BlockPos.betweenClosed(blockPos, blockPos.offset(1, 1, 1))) { ... }

// CORRECT — each entity owns only its own position:
if (level.getBlockState(blockPos).is(Blocks.STRUCTURE_VOID)) {
    state.structureVoidPositions.add(blockPos.immutable());
}
```

### Gizmos: World-Space Outline Rendering

`ShapeRenderer.renderLineBox()` is gone. Use the Gizmos system for debug/outline shapes:

```java
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;

GizmoStyle style = GizmoStyle.stroke(color); // ARGB int, alpha must be 0xFF
Gizmos.cuboid(new AABB(pos), style);         // world-space coordinates
```

Call `Gizmos.cuboid()` directly from `submit()` — no push/pop needed, Gizmos handles world-space positioning internally.

### KeyMapping Category

`KeyMapping` category changed from `String` to `KeyMapping.Category`:

```java
// BEFORE:
new KeyMapping("key.mymod.action", InputConstants.Type.KEYSYM, InputConstants.KEY_F, "key.categories.misc")

// AFTER:
new KeyMapping("key.mymod.action", InputConstants.Type.KEYSYM, InputConstants.KEY_F, KeyMapping.Category.MISC)
```
