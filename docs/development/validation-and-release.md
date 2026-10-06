# Validation and Release Gates

This document defines what evidence is required at each stage of multiversion development and release.

The central rule is:

> Test distinct compatibility shapes, not every permutation for symmetry.

All targets still receive structural build/artifact verification. Runtime depth is assigned by risk and compatibility boundaries.

## Level 0 — Baseline

Purpose: know whether a failure was introduced by the migration/change.

Evidence:

- original canonical target build status;
- known runtime status;
- known pre-existing failures/warnings;
- clean understanding of the worktree.

Use before a migration or large compatibility refactor.

## Level 1 — Compile

Purpose: prove source compatibility.

For a complete matrix:

```bash
python build-smart.py matrix:compile
```

During development, use the narrowest affected target(s).

Compilation does not prove mixin application, runtime dependency compatibility, resources, optional integrations, or installable artifact correctness.

## Level 2 — Package + Artifact Structure

Purpose: prove every matrix node produces the intended installable release artifact.

```bash
python build-smart.py matrix:package
python3 scripts/verify-matrix-artifacts.py
```

The verifier should validate per-target facts such as:

- artifact exists;
- exactly one expected installable artifact is selected;
- no dev/source/stale JAR substitution;
- loader metadata;
- Minecraft version;
- Java/class level;
- required mixin classes/configs;
- transformed resource formats;
- release artifact task generation (`remapJar` vs current `jar`);
- optional integrations are not accidentally embedded.

Level 2 is required for every release target.

## Level 3 — Development Runtime

Purpose: exercise runtime-sensitive code while compatibility work is still changing.

Use representative sentinels and any uniquely affected target.

Check as applicable:

- stable title screen;
- resource reload;
- mixin application;
- loader registration;
- networking;
- rendering/UI;
- datagen;
- optional integrations;
- affected gameplay feature.

Do not run the full GUI matrix after every small edit.

## Level 4 — Packaged-JAR Production Smoke

Purpose: prove the actual release JAR starts in a production-style client rather than only through the development classpath.

Required properties:

- package first;
- stage the exact verified release JAR;
- prove loaded class origin resolves to that staged JAR;
- reach a deterministic startup milestone;
- own and terminate only the test client;
- use bounded timeouts;
- report failure on missing/duplicate/changed artifacts.

Reference backends:

### Fabric

Prefer Loom's production client task (`ClientProductionRunTask`) when supported.

### NeoForge

Use the pinned PortableMC Windows x64 backend through WSL/Windows interop when there is no equivalent Loom production-client path.

PortableMC must be fetched/cached reproducibly and checksum-verified. Do not depend on an existing user launcher profile.

### Default sentinel set

For the current two-generation/two-loader matrix:

```text
current Fabric
current NeoForge
legacy Fabric
legacy NeoForge
```

Sagittary's established representatives are the current 26.2 pair and legacy 1.21.1 pair.

This is not a permanent magic list. Add a runtime target when it contains unique compatibility behavior not represented by the default four.

A target that only differs by dependency pins and shares the already-proven compatibility path does not automatically need another production launch.

## Level 5 — Affected-Feature QA

Purpose: prove behavior, not merely startup.

Choose checks from the systems changed by the migration/feature.

Examples:

- crafting/menu interaction;
- item use;
- custom entity/projectile behavior;
- networking;
- selection/state persistence;
- render/tooltip/UI;
- loader-specific events;
- JEI/recipe integration;
- optional accessory/integration support;
- survival vs creative behavior when relevant.

Do not claim full gameplay acceptance from a title-screen smoke.

Manual testing is valid evidence when automation would be more expensive or less trustworthy than the feature itself.

## Level 6 — Publication Preflight

Purpose: prove that the exact release revision is ready to publish.

After version and release notes are final:

```bash
python build-smart.py publish:preflight
```

The established preflight should compose:

```text
matrix package
→ artifact verification
→ Level 4 representative release-smoke
→ Modrinth dry-run
→ CurseForge dry-run
```

Expected terminal marker:

```text
PUBLISH PREFLIGHT PASS
```

Run the complete preflight once for the final release revision.

## Sentinel Selection

Choose a sentinel because it exercises a distinct compatibility shape.

Useful axes:

- legacy vs current Minecraft generation;
- Fabric vs NeoForge;
- remapped vs unobfuscated packaging;
- materially different renderer/menu/network API;
- unique mixin target/descriptor;
- unique resource transform;
- unique optional integration provider;
- unique Java/toolchain generation.

If a new target introduces a new shape, add it.

If two targets share the same implementation path and differ only in pinned dependency versions, Level 1/2 still covers both; runtime duplication is optional unless the dependency difference is itself risky.

## Change-to-Validation Mapping

Use the smallest set that can disprove the change.

| Change | Minimum useful validation |
| --- | --- |
| common Java behavior | affected generation on both loaders |
| Fabric-only code | affected Fabric target(s) |
| NeoForge-only code | affected NeoForge target(s) |
| Stonecutter local condition | both sides of the condition |
| resource transform | processed file + package + relevant reload/gameplay |
| mixin descriptor/target | runtime on each distinct injection shape |
| optional integration | present + absent on supported shape |
| dependency/runtime pin | target startup where dependency is loaded |
| artifact selector | package + artifact verifier |
| production-smoke script | representative smoke backend(s) only |
| publishing config | tests + platform dry-run |
| release version/notes only | publication plan + final preflight |

## Dedicated Server Evidence

`matrix:server` may validate launch configuration/toolchains without proving a running server.

A dedicated-server smoke is a pass only when the wrapper observes:

1. server process launched;
2. Minecraft reached `Done (...)!`;
3. wrapper sent `stop`;
4. shutdown began;
5. process/Gradle exited successfully;
6. no unexpected runtime error invalidated the run.

EULA exit before `Done` is not a pass.

Only require server runtime coverage when the release criteria or changed shared/server code makes it relevant.

## Optional Integration Evidence

When optional integration behavior changes:

- resolve the supported integration on the intended target;
- run with it installed;
- run without it installed;
- confirm release metadata remains optional;
- confirm release artifact does not bundle the optional mod;
- confirm missing local sibling paths warn rather than hard-fail when the sibling is optional.

Configuration resolution alone is not gameplay evidence.

## Release Acceptance

A normal matrix release is acceptable when all applicable items are true:

```text
Level 1: all targets compile
Level 2: all targets package + artifact verifier passes
Level 3: affected runtime paths exercised during development
Level 4: every distinct release-runtime shape has a packaged-JAR smoke
Level 5: changed/high-risk gameplay behavior has representative QA
Level 6: one final publication preflight passes
```

For the current Sagittary matrix, the normal structural/runtime balance is:

```text
12/12 compile
12/12 package + artifact verification
4 representative packaged-JAR production smokes
targeted/manual feature QA for affected systems
12/12 Modrinth dry-run
12/12 CurseForge dry-run
```

This is intentionally not twelve identical manual client sessions.

## Stopping Rule

After a required gate passes:

- record it;
- move to the next phase;
- do not repeat it solely for reassurance.

Repeat a gate only if:

- a later change invalidated its evidence;
- a required criterion was not actually covered;
- a new deterministic failure appears;
- the user explicitly asks for another run.

Do not invent new validation infrastructure after the requested acceptance criteria are already satisfied.
