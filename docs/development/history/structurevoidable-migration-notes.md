# StructureVoidable migration boundaries

The canonical behavior comes from `feature/26.1.2-migration`, including the current renderer and the fix that each block entity renderer draws only its own position. `master` and `mc-1.21.5-dev` have no later source commits absent from that baseline. Targeted `mc-1.21-base` and `auto/mc-1.21.4` inspection supplied legacy renderer/registration knowledge; historical branch architecture was not merged.

## Source and build ownership

`common/`, `fabric/` and `neoforge/` are maintained source, not Gradle subprojects. Each Stonecutter node combines common and loader source and uses native preparation. `prepareTargetSources` merges changed and unchanged files and rejects an empty result. Generated `versions/` views are disposable.

`gradle/matrix/*.properties` register the supported targets and own dependency/toolchain facts. `stonecutter.gradle` derives aggregate tasks from them. Current targets use no-remap Loom and `jar`; legacy uses Mojang mappings and `remapJar`. Installable artifacts live in `build/libs/<target>/` with the existing mod version 1.0.2.

## Compatibility boundaries

- **26.2 GUI and submission API:** local Stonecutter conditions select `minecraft.gui.setScreen` and the moving-block flags argument (`0`, matching vanilla piston rendering). Older targets retain their existing calls.
- **Legacy rendering:** only `StructureVoidBlockEntityRenderer` has an alternate implementation under `gradle/compat/legacy/common/`. It uses immediate buffers rather than extracted render state, while preserving distance, visibility, display block, color, box size and per-position rendering behavior.
- **Legacy registration and keys:** local conditions select the vanilla block-entity builder and string key category. Modern targets retain the constructor invoker and typed category.
- **Legacy mixin ownership:** a parsed JSON transform removes `BlockEntityTypeMixin`; the class is also conditioned out. The legacy constructor has a data-fixer argument and must not receive the modern two-argument invoker.
- **Fabric 26.1/26.1.1 supplier access:** a matrix flag enables one class access widener and adds its metadata field through a parsed transform. The supplier interface is private in this generation with the pinned dependency. No-remap Loom requires the `official` namespace. Other artifacts do not carry this widener. This is an additional runtime sentinel shape.
- **Loader metadata:** target pins expand Minecraft, loader, API, Java and mixin compatibility facts. NeoForge 26.2 uses `iconFile`; earlier targets use `logoFile`. Fabric retains optional ModMenu integration. No unrelated optional integrations are added.

## Validation limits

Minecraft-dev validated Structure Void, ClientLevel and Options injection targets on 26.2 and 1.21.1. Build success is supplemented by packaged-artifact checks and production startup, initial resource reload and staged-JAR class-origin evidence. Title/first-screen startup does not prove in-world rendering or interaction; use the manual checklist in `../structurevoidable-runtime-checklist.md`.

Production smoke uses Loom for Fabric and checksum-pinned PortableMC 5.0.5 for NeoForge. Instances are isolated, timeouts are bounded, and only the owned client is terminated. Publication and fresh-machine tooling are deferred.

## Conversion acceptance

Validated on the existing development machine using its Windows Python/Java installations through WSL interop:

- 12/12 matrix compilation, packaging, installable-artifact verification and launch-setup checks passed.
- Packaged release smoke passed for 26.2 Fabric/NeoForge and 1.21.1 Fabric/NeoForge. Fabric 26.1.1 additionally passed the access-widener runtime shape.
- Each smoke proved the unchanged staged JAR's SHA-256 and class origin, common/client registration, initial resource reload, startup completion and owned-client shutdown. Legacy NeoForge uses a SecureJarHandler `union:` class origin; the monitor resolves it to the staged archive.
- The smoke helper supports native Windows through interop as well as its existing WSL paths: create the fresh NeoForge log parent before its client directory, and normalize Windows drive paths when checking Fabric class origins.
- Evidence summaries and per-target smoke receipts are in ignored `build/validation/`; raw launcher logs remain in isolated temporary instances.

No full-matrix rebuild or passed client was repeated after acceptance. Subsequent user review reported manual gameplay PASS on 26.2 NeoForge and 1.21.1 NeoForge; 26.1.1 Fabric manual gameplay is unconfirmed. The runtime checklist records the visual/gameplay checks separately from automated startup evidence. During conversion, no publication, version bump, commit, push or machine-setup changes were performed.
