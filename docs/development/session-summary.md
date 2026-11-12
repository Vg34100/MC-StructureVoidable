# StructureVoidable port and standardization handoff

Implementation: 2026-10-16; validation closeout: 2026-10-17.
Baseline: clean `main`, commit `cb00030` after the published
1.0.3 conversion/release. The shared template under
`minecraft/.reference/template-stonecutter-multiversion/` was used read-only.
This repository builds independently of that checkout.

## Scope and preservation

The original twelve targets and dependency pins are retained. Four matrix files
add 1.20.1 Fabric/Forge and 26.3 Fabric/NeoForge. Canonical common/Fabric/NeoForge
source remains in place; native Forge adds only its entrypoint, client hook and
metadata/resources. The original shared modern and alternate immediate renderer,
vanilla Structure Void identifier, block-entity registry path and inherited
serialization are preserved. No registry split, property factory for nonexistent
new content, gameplay redesign, optional integration or networking was added.

The relevant template utilities adopted are the native physical-client hooks,
tracked config provider/ConfigSpec/UTF-8 storage with compatible SimpleConfig,
vanilla options draft/save pattern, ASM 9.11 build classpath, direct-original-JAR
smoke staging, dynamic smoke selection and the bytecode side checker.
The exact early-NeoForge event repair is activated only after reproducing the
failure and checking the pinned dependency bytes. It is absent elsewhere.
See [the durable boundaries](history/structurevoidable-migration-notes.md).

The small source/API boundaries are 26.3's `KEYBOARD` input type and option
callback interface, 1.20.1's options list/footer, native Forge bootstrap/mixin
manifest and format-15 pack metadata, and NeoForge's client-only factory/event
subscriber ownership. Existing renderer alternatives and gameplay mixins remain
unchanged. Config initialization is lazy because vanilla can query shapes before
FML metadata exists. The old `config.display.block=stone` value still loads as
false and its missing block type still defaults to stone; new defaults correctly
declare the separate boolean and block-type keys. No user config is rewritten
just by loading it.

## Validation state

Stage A passed compile, installable package/artifact checks, packaged-client
startup and actual dedicated-server startup/clean stop for all four new targets.
Fabric 26.3 initially failed inside vanilla because the old Java 25 Loom preview
lacks Math.powExact; the identical JAR passed using installed Microsoft 25.0.3.
The wrapper now prefers a final JDK over a same-major preview when auto-discovering.
No system tools were installed or setup/doctor/bootstrap implementation changed.

Stage B uses real old-file fixtures and a draft/save model. Config logic passes
50 storage checks plus 33 StructureVoidable checks on Java 17 and 21. These cover
filename/key/default compatibility, unchanged old-file load, mixed line endings,
UTF-8/comments/unknown keys, appended missing defaults, discard/save/reload,
failed-save isolation and inert config class loading before FML is ready.
The fixture makes an early loader metadata lookup fail, guarding the observed
vanilla shape-query boundary. GUI callback signatures compile through method
references on both Consumer and 26.3 ValueUpdateListener APIs.

The publishing/build/side helper suite passes 19 checks. The exact event repair
passes 58 checks on each early NeoForge node against actual Architectury bytes.
After the affected sentinels stabilized, the final aggregate ran once:

| Gate | Result |
| --- | --- |
| Matrix compile | PASS 16/16 |
| Installable package | PASS 16/16 |
| Artifact metadata/classes/mixins/resources | PASS 16/16 |
| Packaged bytecode side safety | PASS 16/16 |
| Server launch configuration/toolchains | PASS 16/16 |

Final-source dedicated servers passed 8/8: all four new targets, existing 1.21.1
Fabric/NeoForge, and both repaired early NeoForge targets (26.1 and 26.1.1).
Each reached Done, accepted stop, logged shutdown and exited successfully.
These are actual server runs, separately from the aggregate launch-setup check.

Final packaged-release smoke passed **8/8**: 1.20.1 Fabric/Forge, 1.21.1
Fabric/NeoForge, 26.1 NeoForge, 26.1.1 Fabric and 26.3 Fabric/NeoForge. Each
receipt matches its artifact hash from the structural gate.

| New target | Java | Compile/package/artifacts | Final client | Final server |
| --- | --- | --- | --- | --- |
| 1.20.1-fabric | 17 | PASS | PASS | PASS |
| 1.20.1-forge | 17 | PASS | PASS | PASS |
| 26.3-fabric | 25 | PASS | PASS | PASS |
| 26.3-neoforge | 25 | PASS | PASS | PASS |

All original twelve targets retain structural acceptance. Their runtime
regression coverage is the four existing client sentinels above and server
checks on 1.21.1 Fabric/NeoForge and 26.1/26.1.1 NeoForge.

The 26.3 Fabric production task needed the official profile's
`-XX:StackShadowPages=32`, native-access permission and internal-JDK export.
Loom 1.17.493 supplies the stack setting to development clients but omits it
from ClientProductionRunTask. Without these arguments, the packaged client
hit a Windows native shader-compiler access violation; the unchanged JAR
passed after adding the matrix-owned `production_client_jvm_args`. No mod
source, dependencies or artifact bytes changed for this launcher correction.

Local receipts/results live in ignored `build/validation/`; client logs remain
in their isolated temporary instances. Passed sentinel clients were retained
while resuming only the failed/unrun targets. No full matrix gate was repeated.

A separate 26.3 Fabric packaged smoke also passed with Mod Menu installed.
Its adapter loaded from the exact staged StructureVoidable JAR; the run without
Mod Menu passed without loading that adapter. This covers optional startup,
not interactive screen acceptance. All sixteen artifact hashes remain identical
to the structural gate.

## Publication boundary

The read-only publication plan discovers all 16 nodes, including distinct Forge
and NeoForge metadata and Java 17/21/25. Architectury is required on all loaders;
Fabric API is required and Mod Menu optional on Fabric. Exact artifact selection,
semantic duplicate checks, sequential receipt-backed uploads and confirmation
requirements are retained. Platform projects/public identifiers are unchanged.

The version remains the already-published **1.0.3**. Release notes and public
version records were not changed. A separate release task must select the next
version and notes, perform platform dry-runs/final preflight and authorize uploads.
This port performs no real upload, tag, push, merge or commit. Local historical
receipt journals are ignored and preserved. GitHub workflows discover the matrix
and provide Java 17/21/25; the release workflow still uploads only GitHub assets.
Hosted Actions were not run here.

## Manual acceptance

Automated clients prove exact staged-JAR bytes/origin, loader acceptance,
initialization, initial resource reload, a stable first screen and owned-JVM
termination. Servers must reach Done, receive stop, log shutdown and exit zero
with client classes still present. The user authorized EULA acceptance only for
these isolated loopback/ephemeral-port test servers; existing worlds were unused.

No interactive widget clicks, in-world visuals, F3+T reload or saved-world gameplay
were performed in this session. Draft/persistence logic tests are not widget QA.
Use [the StructureVoidable checklist](structurevoidable-runtime-checklist.md) for
outline colors/sizes/full-block modes, block disguises/O-key states, keybindings,
Mod Menu/native factories, resize, Done/Escape/Cancel, error recovery and restart/
world reopening. Historical manual passes for the published release do not imply
manual acceptance of these changed artifacts.

## Final worktree

Nothing is staged or committed. `.env` and generated evidence remain ignored.
Version, release notes, public project IDs, Zed tasks and setup helpers are
unchanged. No unrelated pre-existing work was modified.

```text
 M .github/workflows/build-matrix.yml
 M .github/workflows/release.yml
 M AGENTS.md
 M build-smart.py
 M build.matrix.gradle
 M common/src/main/java/net/vg/structurevoidable/StructureVoidableClient.java
 M common/src/main/java/net/vg/structurevoidable/Structurevoidable.java
 M common/src/main/java/net/vg/structurevoidable/client/gui/screen/option/ClientOptionScreen.java
 M common/src/main/java/net/vg/structurevoidable/client/gui/screen/option/MainOptionScreen.java
 M common/src/main/java/net/vg/structurevoidable/client/gui/screen/option/ServerOptionScreen.java
 M common/src/main/java/net/vg/structurevoidable/config/ModConfigProvider.java
 M common/src/main/java/net/vg/structurevoidable/config/ModConfigs.java
 M common/src/main/java/net/vg/structurevoidable/config/SimpleConfig.java
 M common/src/main/java/net/vg/structurevoidable/util/ModKeyMaps.java
 M common/src/main/resources/assets/structurevoidable/lang/en_us.json
 M docs/development/history/structurevoidable-migration-notes.md
 M docs/development/publishing.md
 M docs/development/structurevoidable-runtime-checklist.md
 M docs/development/testing.md
 M docs/development/validation-and-release.md
 M gradle/matrix/1.21.1-fabric.properties
 M gradle/matrix/1.21.1-neoforge.properties
 M gradle/matrix/26.1-neoforge.properties
 M gradle/matrix/26.1.1-fabric.properties
 M gradle/matrix/26.1.1-neoforge.properties
 M gradle/publishing.gradle
 M neoforge/src/main/java/net/vg/structurevoidable/neoforge/StructurevoidableNeoForge.java
 M neoforge/src/main/resources/META-INF/neoforge.mods.toml
 M scripts/publish-release.py
 M scripts/smoke-release-client.py
 M scripts/test-publishing.py
 M scripts/verify-matrix-artifacts.py
 M settings.gradle
 M stonecutter.gradle
?? common/src/main/java/net/vg/structurevoidable/client/gui/screen/option/ConfigOptions.java
?? common/src/main/java/net/vg/structurevoidable/client/gui/screen/option/StructureVoidOptionsScreen.java
?? common/src/main/java/net/vg/structurevoidable/config/ConfigSpec.java
?? common/src/main/java/net/vg/structurevoidable/config/SettingsDraft.java
?? common/src/main/java/net/vg/structurevoidable/config/Utf8Config.java
?? docs/development/session-summary.md
?? forge/
?? gradle/matrix/1.20.1-fabric.properties
?? gradle/matrix/1.20.1-forge.properties
?? gradle/matrix/26.3-fabric.properties
?? gradle/matrix/26.3-neoforge.properties
?? neoforge/src/main/java/net/vg/structurevoidable/neoforge/client/
?? neoforge/src/main/java/net/vg/structurevoidable/neoforge/compat/
?? neoforge/src/main/resources/structurevoidable-early-neoforge.mixins.json
?? scripts/check-side-safety.py
?? scripts/test-config.py
?? tests/
```
