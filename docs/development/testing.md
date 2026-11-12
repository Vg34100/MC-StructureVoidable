# Build and test matrix

StructureVoidable maintains 16 target nodes discovered from `gradle/matrix/`:
1.20.1 Fabric/Forge (Java 17), 1.21/1.21.1 Fabric/NeoForge (Java 21), and
26.1/26.1.1/26.1.2/26.2/26.3 Fabric/NeoForge (Java 25).
The first two generations use `remapJar`; 26.x uses no-remap `jar`.
Dependencies and loader facts belong to each target's property file.

Use the existing wrapper from the repository root:

```text
python build-smart.py compile:1.20.1-forge
python build-smart.py compile:26.3-fabric
python build-smart.py matrix:compile
python build-smart.py matrix:package
python scripts/verify-matrix-artifacts.py
python scripts/check-side-safety.py
python scripts/test-config.py
python scripts/test-publishing.py
python build-smart.py matrix:server
python build-smart.py release-smoke
```

`matrix:server` verifies launch configurations/toolchains without launching a
server. Real server smoke uses `smoke-server:<exact-target>`; passing requires
Done, a sent stop command, shutdown and successful exit. A fresh test directory
can be supplied with `-Pserver_smoke_dir=<path>`. It needs explicit EULA acceptance;
use loopback binding and an ephemeral port for isolated checks. Never reuse an
existing player world for these tests.

Run tasks use Gradle's target-specific `javaLauncher`. Do not also set
`JavaExec.executable`. Gradle/current Minecraft need a final Java 25 runtime;
the old `25-loom` preview lacks `Math.powExact`, used by 26.3's renderer.
Automatic local JDK discovery prefers a final runtime over a preview of the same
major. Existing explicit Java settings remain authoritative.

The 26.3 Fabric production task reads `production_client_jvm_args` from its
matrix file. These are Minecraft's official stack/native-access/internal-export
arguments, omitted by Loom's production task. Keep them when running that task;
PortableMC already consumes the official version metadata.

Packaged smoke uses Loom for modern Fabric and checksum-pinned PortableMC 5.0.5
for legacy Fabric and Forge/NeoForge through the existing Windows backend.
It stages original parent dependencies, validates the exact mod JAR hash/origin,
waits for completed startup/resource reload and terminates its owned JVM.
Mod Menu stays optional; `-Prelease_smoke_modmenu=true` adds it for an integration
sentinel. Production sentinels are selected by `release_smoke=true`, separately
from the complete structural matrix. No twelve- or sixteen-target list is hidden
in the wrapper.

On the original 26.1/26.1.1 NeoForge pins, `early_neoforge_event_fix=true`
activates the template's exact Architectury event-bridge repair. Run
`python build-smart.py :26.1-neoforge:verifyEarlyNeoForgeCompat :26.1.1-neoforge:verifyEarlyNeoForgeCompat`
when either pin or the repair changes. These checks use actual dependency bytes;
production artifacts must omit the patch outside those two targets.

The side checker rejects client GUI/key/render references from common and native
common bootstrap bytecode. It allows the existing client package/class roots;
real dedicated servers with those client classes still packaged complement this
check. The config tests run copies of old-file fixtures in temporary directories.
They do not access a user's config directory or run Minecraft.

See [the runtime checklist](structurevoidable-runtime-checklist.md) for manual
visual/UI/world checks and [the session summary](session-summary.md) for actual
per-gate evidence. Startup alone is not interactive gameplay acceptance.
