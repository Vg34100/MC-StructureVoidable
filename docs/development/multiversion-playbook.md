# Multiversion Playbook

This is the canonical end-to-end procedure for converting a working single-version Architectury-style Minecraft mod into a Stonecutter version/loader matrix and carrying that matrix through release.

It is deliberately procedural. Each phase has an entry condition, required work, exit evidence, and a stop condition.

The exact matrix belongs to the repository. Examples below use the Sagittary shape:

```text
1.21      Fabric / NeoForge
1.21.1    Fabric / NeoForge
26.1      Fabric / NeoForge
26.1.1    Fabric / NeoForge
26.1.2    Fabric / NeoForge
26.2      Fabric / NeoForge
```

Do not make build logic depend on this count. Discover targets from `gradle/matrix/*.properties`.

---

## Pipeline Overview

```text
environment readiness
        ↓
single-version baseline
        ↓
Stonecutter skeleton
        ↓
canonical target through matrix
        ↓
modern sentinel
        ↓
legacy sentinel
        ↓
compatibility rules
        ↓
fill adjacent targets
        ↓
optional integrations
        ↓
full matrix compile/package
        ↓
artifact verification
        ↓
representative runtime
        ↓
packaged-JAR production smoke
        ↓
publishing configuration + dry-runs
        ↓
one publication preflight
        ↓
commit/push
        ↓
Modrinth + CurseForge
        ↓
release tag
        ↓
GitHub Release
```

---

# Phase 0 — Environment Readiness

## Entry

A clone or working tree exists.

## Required work

Before interpreting build failures as project failures, establish that the machine can support the repository.

Prefer:

```bash
python build-smart.py doctor
```

when implemented. Otherwise follow `fresh-machine-setup.md`.

At minimum verify:

- Git;
- Python 3;
- Gradle wrapper;
- the Gradle JVM required by the current generation;
- legacy Java toolchain availability where required;
- Windows/WSL interop when the repository uses the WSL + Windows-client smoke path;
- writable native temporary storage;
- optional Node/npm only when using `minecraft-dev`;
- `.env` is ignored;
- publication tokens only when real publication is requested.

Optional sibling mods are not machine prerequisites unless the task specifically requires them.

## Exit evidence

The environment check either:

- reports READY; or
- fails early with a concrete missing prerequisite and setup instruction.

## Stop

Do not debug Minecraft source while a required machine prerequisite is missing.

---

# Phase 1 — Record the Single-Version Baseline

## Entry

The original project builds in its existing structure.

## Inspect

Read only enough to reconstruct:

- canonical Minecraft version;
- Fabric and NeoForge source layout;
- Java version/toolchain;
- Loom/plugin generation;
- packaging path;
- loader metadata;
- required and optional dependencies;
- datagen configuration;
- run configurations;
- mixin configs;
- publication metadata if it already exists.

Record `git status --short` and preserve unrelated work.

## Required evidence

For the canonical version, establish a known baseline on both loaders when both are supported:

- compile/package result;
- basic startup/runtime status;
- known pre-existing warnings/failures;
- the exact installable artifact.

Do not repair unrelated gameplay issues during this phase.

## Stop

Once the pre-migration baseline is recorded, proceed. Do not repeatedly rebuild it unless later evidence suggests a migration regression.

---

# Phase 2 — Create the Stonecutter Skeleton

## Goal

Move the existing canonical version through the future matrix architecture before porting gameplay APIs.

## Target structure

```text
settings.gradle
stonecutter.gradle
build.matrix.gradle

gradle/matrix/
  <minecraft-version>-fabric.properties
  <minecraft-version>-neoforge.properties

common/
fabric/
neoforge/
```

### Responsibilities

`settings.gradle`

- applies/configures Stonecutter;
- discovers/registers target nodes;
- validates target names/properties;
- sets platform facts that must exist before project evaluation.

`stonecutter.gradle`

- selects the IDE-active target;
- defines aggregate compile/package/launch-setup tasks;
- derives task membership from the registered matrix.

`build.matrix.gradle`

- loads target properties;
- configures loader;
- configures Java/toolchains;
- configures Loom/remapping generation;
- combines canonical common + loader source;
- expands loader metadata;
- configures target-isolated run directories;
- wires optional development integrations;
- selects the release artifact task/path.

`gradle/matrix/*.properties`

- owns target-specific facts only;
- pins Minecraft, loader, Java, Architectury, Fabric API/Loader, NeoForge, JEI, and other version-bound dependencies.

## Rules

Do not:

- port gameplay APIs yet;
- create a legacy source tree in advance;
- generate Java with large Groovy strings;
- duplicate matrix dependency pins in root properties;
- share run directories across targets.

## Required evidence

The original canonical Fabric and NeoForge targets compile/package through the new matrix and produce the intended installable artifacts.

The source view must be non-empty. A task reporting success while compiling zero Java is a failure.

## Stop

Do not add other versions until the canonical version survives the new architecture.

---

# Phase 3 — Establish Sentinel Targets

Do not attack the full matrix simultaneously.

Choose sentinels that expose the major compatibility boundaries.

For the current Sagittary-style matrix, a useful starting set is:

```text
current/modern: 26.2 Fabric
current/modern: 26.2 NeoForge
legacy:         1.21.1 Fabric
legacy:         1.21.1 NeoForge
```

The exact versions are not sacred. Choose representatives for the repository's real compatibility generations.

A target with a unique API/resource/loader mechanism becomes an additional sentinel.

---

# Phase 4 — Port the Modern Boundary

Use the nearest/current target that differs from the original canonical baseline.

For each compiler or runtime failure:

1. read the smallest useful error;
2. identify the affected symbol/system;
3. inspect only that source and immediate dependencies;
4. query `minecraft-dev` for exact target-version API facts when relevant;
5. classify the difference using `compatibility-policy.md`;
6. make the smallest readable change;
7. batch related fixes;
8. rerun the narrow sentinel.

Do not create a general transform because one class moved.

Do not convert a local API change into a whole-class overlay unless the implementation really diverges.

## Exit evidence

Both loader sentinels compile/package for the modern boundary, plus runtime evidence for any mixin/registration/resource behavior that compilation cannot prove.

---

# Phase 5 — Port the Legacy Boundary

Use one representative legacy version first, then verify adjacent legacy targets.

Keep Minecraft-generation drift separate from loader drift.

Typical legacy categories include:

- changed item/use/tooltip signatures;
- registry key or registration API changes;
- renderer/model architecture;
- networking payload differences;
- mixin descriptors/targets;
- resource/model/recipe schema;
- datagen APIs;
- loader metadata;
- remapping/package tasks.

Prefer local Stonecutter conditions for local drift and small compatibility implementations for genuinely different subsystems.

## Exit evidence

Representative legacy Fabric and NeoForge sentinels compile/package and affected runtime-sensitive code has been exercised.

---

# Phase 6 — Fill Adjacent Targets

Only after the major compatibility groups are understood:

- expand modern rules to adjacent modern versions;
- expand legacy rules to adjacent legacy versions only when verified;
- keep isolated exceptions explicit;
- do not assume two neighboring versions are identical merely because one compiles.

Use narrow compile/package checks while filling the matrix.

## Exit evidence

Every target is registered and can reach at least the compile/package gate.

---

# Phase 7 — Optional Integrations

Optional integrations are a separate acceptance axis.

Rules:

- do not bundle optional mods accidentally;
- do not make them required in release metadata unless they are truly required;
- attach development-only integrations through runtime configurations;
- support a configurable local sibling path when useful;
- sibling absence should warn and continue when integration is optional;
- validate exact loader/version compatibility before attaching a local sibling artifact;
- test both present and absent states when the integration changed.

Do not treat “dependency resolved in Gradle” as proof that runtime integration works.

---

# Phase 8 — Full Matrix Structure and Artifacts

Once sentinel failures are resolved, run the expensive aggregate gates.

```bash
python build-smart.py matrix:compile
python build-smart.py matrix:package
python3 scripts/verify-matrix-artifacts.py
```

The artifact verifier should reject at least:

- missing target artifact;
- duplicate/stale artifact selection;
- dev/source JAR instead of installable JAR;
- wrong loader metadata;
- wrong Minecraft version;
- wrong Java class level;
- missing packaged mixin classes/config;
- malformed transformed legacy resources;
- accidentally embedded optional integrations.

Release artifacts should live in one deterministic location per target, for example:

```text
build/libs/<target>/
```

Legacy targets may use `remapJar`; current unobfuscated targets may use `jar`. The verifier, not memory, determines the correct installable artifact.

## Stop

When all matrix artifacts pass structural verification, do not rebuild the full matrix again unless later work changes source/build outputs.

---

# Phase 9 — Runtime Validation

Use `validation-and-release.md`.

The key rule is representative coverage, not symmetry.

A normal release does not need twelve identical GUI launches when the compatibility matrix has four meaningful shapes.

Default representative production-smoke shapes for the current suite:

```text
modern Fabric
modern NeoForge
legacy Fabric
legacy NeoForge
```

Add a target if it has unique compatibility code not covered by those shapes.

Manual gameplay should focus on systems actually touched by compatibility work.

---

# Phase 10 — Packaged-JAR Production Smoke

Development `runClient` is not release-artifact proof.

The production smoke must:

1. package the actual installable JAR;
2. stage that exact file in a clean production-style runtime;
3. prove the loaded mod class origin points to that staged JAR;
4. reach a deterministic startup milestone;
5. terminate only the owned client/process;
6. report success only after clean-enough completion for the chosen backend.

Current reference backends:

### Fabric

Use Loom's production client facility (`ClientProductionRunTask`) when available.

### NeoForge

When Loom does not provide an equivalent production path, use the pinned PortableMC Windows x64 backend through WSL/Windows interop.

Do not:

- parse NeoForge installers into a custom launcher unless the official/pinned backend cannot work;
- work around host glibc by modifying the system;
- depend on a pre-existing `.minecraft`, Prism, or Modrinth launcher profile.

Use fresh/native temporary runtime directories to avoid mounted-drive live-log problems.

---

# Phase 11 — Publishing Configuration

Only after build/runtime architecture is stable.

Configure:

- public platform project IDs;
- visible naming template;
- machine-facing version-number template;
- target-specific dependency relations;
- one human-authored release-note source;
- exact artifact path;
- dry-run mode;
- duplicate guards;
- sequential upload;
- receipt journal;
- explicit real-publish confirmation.

Use:

```bash
python build-smart.py publish:plan
python build-smart.py publish:modrinth-dry-run
python build-smart.py publish:curseforge-dry-run
python build-smart.py publish:all-dry-run
```

No dry-run should require exposing real credentials.

---

# Phase 12 — One Publication Preflight

After the version bump, release notes, and publication configuration are final, run exactly one complete preflight:

```bash
python build-smart.py publish:preflight
```

Expected composition:

```text
matrix package
→ artifact verification
→ representative packaged-JAR release smoke
→ Modrinth dry-run
→ CurseForge dry-run
```

Success must end in an unambiguous marker such as:

```text
PUBLISH PREFLIGHT PASS
```

## Stop

If preflight is green, do not repeat it solely for reassurance.

---

# Phase 13 — Release

Recommended order:

1. inspect `git status --short` and `git diff --check`;
2. commit the intended release revision;
3. push that revision;
4. real Modrinth + CurseForge publication using explicit confirmation;
5. verify/report every remote upload ID;
6. only after external publication succeeds, create `v<mod_version>`;
7. push the tag;
8. tag-triggered GitHub Actions rebuilds/verifies and creates the GitHub Release with exactly the installable matrix JARs.

This keeps the Git tag tied to a revision that already passed local release acceptance.

Do not force-move an existing release tag.

Do not silently roll back successful remote uploads when a later target fails. Record partial success and resume safely.

---

# Failure Escalation Ladder

When something fails, classify it before changing code.

```text
machine/environment
→ fresh-machine checks / doctor

Gradle target selection
→ build-smart --print-plan

Minecraft API/version question
→ minecraft-dev MCP

exact third-party artifact question
→ resolved Gradle/JAR inspection

compile failure
→ narrow target compile

resource/metadata failure
→ processed output + package inspection

mixin/runtime failure
→ affected dev runtime

release-only failure
→ packaged-JAR production smoke

publishing metadata
→ platform dry-run

real upload
→ duplicate/receipt/API checks
```

Do not jump directly to broad cache archaeology, custom launcher infrastructure, or full-matrix rebuilds.

---

# Ongoing Feature Development

After the migration is complete:

1. develop a feature deeply on one current canonical target;
2. runtime-test it there;
3. port the proven feature across the matrix;
4. add compatibility only where a real difference appears;
5. use sentinel checks during the port;
6. save aggregate matrix/release gates for release preparation.

This is faster and produces cleaner compatibility code than treating every target as an equal development environment.
