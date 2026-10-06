# AGENTS.md

This file defines the default agent workflow for StructureVoidable.

StructureVoidable is transitioning from a branch-split, single-version Architectury workflow to one Stonecutter multiversion branch. Detailed procedures live under `docs/development/`.

## Priorities

1. Preserve existing StructureVoidable behavior.
2. Converge version-specific branches into one maintainable Stonecutter source architecture.
3. Keep one clear source of truth for each version, loader, dependency, artifact, and publishing fact.
4. Keep Fabric and NeoForge aligned where practical without hiding meaningful loader differences.
5. Minimize context use and validation cost.
6. Prefer small, inspectable compatibility changes over broad rewrites.
7. Never overwrite unrelated user work.
8. Stop once the requested acceptance evidence exists.

## Desired Target Matrix

The completed migration should support:

```text
1.21      Fabric / NeoForge
1.21.1    Fabric / NeoForge
26.1      Fabric / NeoForge
26.1.1    Fabric / NeoForge
26.1.2    Fabric / NeoForge
26.2      Fabric / NeoForge
```

Once the matrix exists, the exact registered target set must be discovered from:

```text
gradle/matrix/*.properties
```

Do not hard-code the target count into build logic when it can be derived from those files.

Versions that exist only as historical branches are reference material and are not automatically release targets.

## Migration State

Before Stonecutter migration, StructureVoidable has used multiple branches for Minecraft-version ports.

Known branches include examples such as:

```text
1.20.1
1.21.3
auto/mc-1.21.3
auto/mc-1.21.4
fabric-only
feature/26.1.2-migration
mc-1.21-base
mc-1.21.5-dev
master
```

Treat alternate branches as read-only evidence about previous API ports, behavior fixes, and version boundaries.

Do NOT merge historical branch histories wholesale merely to construct the multiversion source tree.

Instead:

1. establish which branch/source is the best current canonical behavioral baseline;
2. compare historical branches only where they contain relevant version-specific knowledge;
3. preserve behavior using the compatibility policy;
4. express the final result in one Stonecutter branch.

Prefer the current `feature/26.1.2-migration` source as the canonical baseline if inspection confirms that it is a complete and correct current-generation port. Do not assume this without checking.

## Final Repository Model

After migration, responsibilities should be:

- `settings.gradle` — Stonecutter target registration and early platform selection.
- `stonecutter.gradle` — active target and aggregate matrix tasks.
- `build.matrix.gradle` — generic per-target Gradle configuration.
- `gradle/matrix/*.properties` — target-specific Minecraft/loader/Java/dependency facts.
- `common/`, `fabric/`, `neoforge/` — canonical maintained source.
- small compatibility source/resource areas — only where real version boundaries require them.
- `build-smart.py` — normal compile/matrix/runtime/release wrapper.
- `scripts/verify-matrix-artifacts.py` — structural release-artifact verification.
- `scripts/smoke-release-client.py` — packaged-release-JAR production smoke.
- `scripts/setup-environment.py` — fresh-machine doctor/bootstrap implementation when present.

Publishing infrastructure is a later phase and should not block the initial Stonecutter conversion.

Do not duplicate active target dependency pins in root `gradle.properties` once a matrix property owns them.

## StructureVoidable Source Areas

StructureVoidable currently includes systems such as:

- Structure Void block behavior;
- `StructureVoidBlockEntity`;
- block entity registration;
- `StructureVoidBlockEntityRenderer`;
- client-level behavior;
- Structure Void block mixins;
- `BlockEntityType` mixins;
- options/configuration screens;
- key mappings;
- Fabric bootstrap/client/ModMenu integration;
- NeoForge bootstrap.

These are compatibility inspection priorities when errors point to them.

They are NOT instructions to rewrite all of these systems preemptively.

## Context Discipline

Search first, read second, edit last.

- Open only files involved in the current failure or feature.
- Do not read the entire Java tree without a reason.
- Do not dump full Gradle logs when a small error excerpt is enough.
- Batch related compatibility fixes before rebuilding.
- Prefer existing wrapper/build commands over repeatedly deriving raw Gradle commands.
- Treat historical branches as targeted references, not default context.
- Do not inspect every branch up front. Compare a branch only when it can answer a specific compatibility question.
- Do not inspect the full `build-smart.py` once established unless it fails, selects the wrong plan, or must change for the requested task.

For a new session, read only the document relevant to the task:

- migration / adding versions → `docs/development/multiversion-playbook.md`
- representing API/resource drift → `docs/development/compatibility-policy.md`
- deciding what to test → `docs/development/validation-and-release.md`
- new computer / missing tools → `docs/development/fresh-machine-setup.md`
- publishing → `docs/development/publishing.md`

## Minecraft Source Investigation

For Minecraft API changes, mappings, class/method availability, mixin targets, and version comparisons, prefer the `minecraft-dev` MCP.

Escalation order:

1. `minecraft-dev` MCP for Minecraft/version questions.
2. StructureVoidable source and relevant historical branch diff.
3. resolved dependency metadata/source.
4. Gradle-cache/JAR inspection when MCP/source cannot answer or exact resolved bytes matter.
5. `javap`/manual bytecode archaeology only when narrower methods are insufficient.

Do not spend migration context rediscovering vanilla APIs manually when the MCP can answer them.

Historical StructureVoidable branches are especially useful when they already contain a proven solution for an older API. Reuse the knowledge, not necessarily the exact file.

## Compatibility Rule

Represent version drift using the smallest mechanism that keeps behavior visible:

1. unchanged shared source;
2. native Stonecutter condition for a small local difference;
3. narrow deterministic replacement for a truly mechanical rename;
4. parsed resource/data transform for serialized-format drift;
5. separate compatibility implementation when behavior or lifecycle materially differs.

Do not create a large legacy overlay tree in advance.

Do not turn `build.matrix.gradle` into a Java source generator or broad regex-rewrite engine.

Do not solve branch convergence by copying entire version-specific source trees into compatibility directories.

See `docs/development/compatibility-policy.md`.

## Loader Rule

Minecraft-version differences and loader differences are separate axes.

Keep Fabric-only behavior in Fabric source where practical.

Keep NeoForge-only behavior in NeoForge source where practical.

Check both loaders independently for:

- bootstrap;
- client registration;
- block entity registration;
- rendering;
- events;
- metadata;
- ModMenu/config integration where applicable;
- mixins;
- key handling.

Do not force shared abstraction when loader-native implementations are clearer.

## Mixins

Treat StructureVoidable's mixins as runtime-sensitive compatibility code.

For an affected target, verify:

- target class exists;
- target method name and descriptor;
- actual dispatch/override path;
- injection point;
- loader/version ownership;
- whether the mixin should exist on that target at all.

Compilation is not proof that a mixin applies or affects gameplay correctly.

## Resources and Rendering

Compilation is not sufficient evidence for block models, block entity rendering, metadata, recipes, or transformed resources.

When a resource/rendering boundary changes:

1. inspect processed output;
2. inspect the packaged release JAR;
3. run resource reload / representative client behavior;
4. verify the Structure Void rendering behavior itself when practical.

Prefer parsed resource transforms over broad textual rewrites.

## Build Wrapper

Use `build-smart.py` once the migration establishes it.

Before trusting an unfamiliar machine or a newly converted matrix:

```bash
python build-smart.py compile --print-plan
```

Expected eventual commands include:

```bash
python build-smart.py doctor
python build-smart.py bootstrap

python build-smart.py compile
python build-smart.py compile:fabric
python build-smart.py compile:neoforge
python build-smart.py compile:modern
python build-smart.py compile:legacy

python build-smart.py matrix:compile
python build-smart.py matrix:package
python build-smart.py matrix:server

python build-smart.py smoke-release-client:<target>
python build-smart.py release-smoke
```

Do not add publication commands during the initial conversion unless explicitly requested.

## Optional Dependencies

Do not inherit Sagittary-specific optional integrations.

StructureVoidable should only carry dependencies/integrations that StructureVoidable actually uses.

In particular, do not add:

- Spelunkery;
- Trinkets;
- JEI;
- Sagittary-specific development integrations;

unless StructureVoidable independently requires them.

ModMenu or other existing StructureVoidable integrations should remain loader/target-aware as appropriate.

## Validation Strategy

Use the smallest validation set that can disprove the current change.

Examples:

- common Java change → affected generation on both loaders;
- Fabric-only change → affected Fabric target(s);
- NeoForge-only change → affected NeoForge target(s);
- local Stonecutter condition → check both sides;
- resource transform → processed resource + package + relevant client check;
- mixin change → runtime on every distinct injection shape;
- build-matrix change → affected sentinel first, full matrix only when sentinel work is stable.

Do not rerun the full matrix after every small compatibility edit.

Full release acceptance is defined by `docs/development/validation-and-release.md`.

## Sentinel Rule

Start with representative compatibility shapes rather than all twelve clients.

For the intended matrix, the initial sentinel set should normally cover:

```text
current Fabric
current NeoForge
legacy Fabric
legacy NeoForge
```

For this suite, likely representatives are:

```text
26.2 Fabric
26.2 NeoForge
1.21.1 Fabric
1.21.1 NeoForge
```

This is not a magic permanent list.

If StructureVoidable introduces a unique compatibility mechanism on another target, add that target to the sentinel set.

## Stopping Rule

Once the requested acceptance evidence is green, stop.

Do not repeat:

- full matrix builds;
- production clients;
- artifact verification;
- branch comparisons;
- runtime checks;

solely for reassurance.

Continue only when:

- a required criterion remains unresolved;
- a later code change invalidates previous evidence;
- a new deterministic failure appears;
- the user explicitly asks for more validation.

## Feature Development After Migration

After the migration is complete, do not develop new features twelve times in parallel.

Default workflow:

1. implement/deep-test on one current canonical target;
2. prove the feature there;
3. port the proven behavior through the compatibility policy;
4. use sentinel checks while porting;
5. save full matrix/release gates for release preparation.

## Dirty Worktree and Branch Safety

Before editing:

```bash
git status --short
```

Never revert unrelated user changes.

Historical branches are read-only references during migration unless the user explicitly requests branch changes.

Do not merge, delete, rewrite, or force-push historical branches just because their knowledge has been consolidated.

Do not stage:

- build output;
- run directories;
- caches;
- local `.env`;
- downloaded tools;
- unrelated documentation/files.

Do not commit, push, tag, publish, or delete branches unless the user explicitly authorizes it.

## Closeout

Report only:

- architecture/source decisions made;
- files/systems changed;
- targets/gates checked;
- what passed;
- what remains manual/untested;
- any new reusable compatibility boundary.

Keep raw logs and migration diary material out of the closeout.
