# Build and test matrix

Fishing Frenzy has verified Fabric and NeoForge targets for Minecraft 1.21, 1.21.1, 26.1, 26.1.1,
26.1.2, and 26.2 from one source tree. Each matrix target has pinned dependencies in
`gradle/matrix/` and writes its jar and future run files to a target-specific directory.

The 1.21 / 1.21.1 targets are a legacy generation, not aliases for the current build: they use
Java 21, Architectury 13, legacy resource/source generation, and target-specific loader
coordinates. Their `runClient` and `runServer` tasks must use Gradle's Java 21 `javaLauncher`.
Do not configure `JavaExec.executable` as well: IntelliJ will reject mismatched `executable` and
`javaLauncher` toolchains. The generated Gradle task under `<target> > Tasks > loom` is the
intended IntelliJ launch entry point. See the legacy-backport section in
[Stonecutter multi-version migration](stonecutter-multiversion-migration.md).

The two earliest NeoForge targets carry a narrow runtime compatibility transform in Fishing Frenzy.
Their published NeoForge releases expose `BlockEvent.BreakEvent`, whereas Architectury 20.0.4
references the later `BreakBlockEvent` name. The NeoForge-only Mixin config rewrites that one
Architectury handler's descriptors before the JVM links it; it is a no-op when the newer name is
already present. It neither replaces Architectury nor adds a fake `net.neoforged.*` class. This was
validated in a production Prism instance with the ordinary Architectury 20.0.4 jar. Re-evaluate the
adapter whenever either dependency changes.

For the reusable conversion procedure and the NeoForge platform-wiring pitfalls, see
[Stonecutter multi-version migration](stonecutter-multiversion-migration.md).

Use the smart wrapper from the repository root:

```text
python build-smart.py matrix:compile
python build-smart.py matrix:package
python build-smart.py matrix:server
python build-smart.py matrix
```

`matrix:compile` compiles all twelve targets. `matrix:package` processes their resources and builds
their jars. `matrix:server` runs Loom's `configureLaunch` task for each target, generating the
dedicated-server launch configuration without starting a server. A release still requires the
normal Fabric and NeoForge `runServer` runtime smoke tests.

Compilation and launch configuration do not prove a client can survive resource reload. Before
declaring a Minecraft-version target usable, manually run that target's `runClient` task and wait
through the initial resource reload to a stable title screen. Stop the client normally before
testing the next target.
