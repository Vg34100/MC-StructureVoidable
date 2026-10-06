# Fresh-Machine Setup

This document defines the portability contract for the multiversion workflow.

Goal:

> After cloning the repository on a new computer, the build should either become ready through safe repo-local setup or fail early with an exact missing prerequisite and how to satisfy it.

Do not allow an environment problem to masquerade as a Minecraft compatibility problem.

## Supported Workflow Shape

The current reference workflow assumes:

- Windows 11 host;
- WSL2 Linux development shell;
- Windows interop available from WSL;
- repository cloned to a path accessible to WSL;
- Gradle wrapper committed;
- Python 3 available;
- Git available;
- Java generations required by the matrix available through configured JDKs/toolchains;
- graphics only when local GUI smoke tests are requested.

The project should not depend on a specific username, drive letter, existing launcher profile, or global Gradle installation.

## `doctor` Contract

Recommended command:

```bash
python build-smart.py doctor
```

`doctor` is read-only apart from a temporary writable-storage probe that is removed immediately. It never installs packages or edits configuration. Credential presence is checked by the existing secret loader in a short-lived child; only `configured` / `missing` booleans return, never secret values.

It returns zero for development readiness. Release-preflight gaps and missing publishing tokens are warnings, not development failures. Push authorization is explicitly unconfirmed rather than tested with a remote mutation. Backend checks do not launch Minecraft; graphical-session configuration is not proof of a working GUI.

### Required checks

#### Repository

- Gradle wrapper exists;
- matrix property directory exists when the repository is already migrated;
- matrix filenames/properties parse;
- expected build scripts exist;
- build output/run directories are ignored;
- `.env` is ignored if present;
- no tracked `.env`;
- no committed obvious machine-specific absolute paths in active configuration.

#### Python/Git

- Python version is usable;
- Git is installed;
- repository has a valid worktree;
- remote availability is reported when publishing is requested, not as a requirement for ordinary local compilation.

#### Java

Distinguish:

- Gradle JVM;
- per-target game/toolchain JVM.

For the current matrix shape, verify access to the Java generations required by the matrix rather than assuming one global Java is correct.

Report the actual selected JDKs without mutating `JAVA_HOME`.

Install the matrix-required native JDKs manually; bootstrap does not acquire Java. For WSL builds these must be Linux JDKs. Set `BUILD_SMART_JAVA_HOME` to the Gradle JDK home when necessary, and list nonstandard toolchain homes in `org.gradle.java.installations.paths` in your Gradle user-home `gradle.properties` (`GRADLE_USER_HOME`, or normally `~/.gradle`; `%USERPROFILE%\.gradle` on Windows). `gradle.local.properties` only configures optional dev integrations, not Gradle Java toolchains.

#### WSL / Windows Interop

When the repository uses Windows-backed production smoke:

- detect WSL;
- detect Windows executable interop;
- verify Windows temp/local-app-data resolution can be obtained dynamically;
- do not hard-code `C:\Users\<name>` or `/mnt/c/Users/<name>`.

#### Writable temporary storage

Verify a native Linux temp/cache location is writable for Gradle/project-cache intermediates.

High-churn build/runtime logs should not require a Windows-mounted path.

#### Production-smoke backend

Fabric:

- Loom production-task support is discovered from the build.

NeoForge:

- pinned PortableMC metadata is available;
- cached binary may be absent if it can be downloaded later;
- checksum/pin information exists;
- no existing Prism/Modrinth Launcher/.minecraft profile is required.

#### Optional tooling

Report but do not fail ordinary builds for:

- Node/npm;
- `minecraft-dev` MCP;
- optional local sibling repositories.

`minecraft-dev` is strongly recommended for migration work but is not a runtime dependency of the mod.

#### Publishing

Only treat these as required when real publication is requested:

```text
MODRINTH_TOKEN
CURSEFORGE_TOKEN
```

`doctor` may report `configured` / `missing`; it must never print token contents or prefixes.

## Suggested Doctor Output

```text
Repository
  PASS Gradle wrapper
  PASS matrix configuration
  PASS .env ignored
  PASS no tracked secret file

Python / Git
  PASS Python 3
  PASS Git

Java
  PASS Gradle/current JDK
  PASS legacy Java toolchain

Interop / runtime
  PASS WSL detected
  PASS Windows interop
  PASS native temp writable

Optional tooling
  WARN minecraft-dev MCP not configured
  WARN sibling Spelunkery repo not found

Publishing
  PASS MODRINTH_TOKEN configured
  PASS CURSEFORGE_TOKEN configured

RESULT: READY
```

On failure:

```text
RESULT: NOT READY
Required: Java 25 not found
Used by: current-generation Gradle/Minecraft tasks
Fix: <exact repository-supported setup instructions>
```

Exit nonzero when a required prerequisite for the requested operation is missing.

## `bootstrap` Contract

Recommended command:

```bash
python build-smart.py bootstrap
```

`bootstrap` may perform safe, deterministic, repo-local setup.

Good automatic actions:

- create ignored cache/runtime directories;
- create `.env` from `.env.example` if absent, without inserting secrets;
- create ignored local configuration templates;
- fetch checksum-pinned PortableMC into a dynamic per-user cache;
- warm Gradle wrapper/dependencies;
- optionally fetch pinned repo-local JDK distributions if the project deliberately adopts that model;
- verify the result by running `doctor`.

`bootstrap` must be idempotent and must not overwrite user configuration/secrets.

The implemented setup creates an absent ignored `.env` from the empty template, prepares the existing `.gradle/`, `build/`, and `runs/` directories, and reuses release-smoke's pinned/checksummed Windows PortableMC cache. It does not warm Gradle or install JDKs. It finishes by running doctor; on a second run with a valid cache/configuration no changes are required. PortableMC needs Windows x64 or working WSL Windows interop; its absence does not block ordinary development.

### Do not silently automate system mutations

Do not automatically:

- `sudo apt install`;
- install/enable WSL;
- change `.wslconfig`;
- change WSL networking mode;
- alter system `JAVA_HOME`;
- modify global Git configuration;
- globally install Node/npm packages;
- change Windows PATH;
- install GPU drivers;
- disable antivirus/firewall/security controls.

For these, stop and print the exact manual prerequisite.

## First Clone Procedure

Desired end-state workflow:

Examples use `python` for Python 3.9+. Use `python3` instead where that is the available executable.

```bash
git clone <repo>
cd <repo>

python build-smart.py doctor
python build-smart.py bootstrap   # only if doctor reports bootstrap-manageable gaps
python build-smart.py doctor

python build-smart.py compile --print-plan
python build-smart.py compile
```

For migration work, then configure/check `minecraft-dev`.

For real publishing, create `.env` from `.env.example` and fill the required platform tokens locally.

## Local Secrets

`.env` is machine-local state.

Expected:

```dotenv
MODRINTH_TOKEN=
CURSEFORGE_TOKEN=
```

Rules:

- `.env` ignored;
- `.env.example` committed;
- bootstrap never overwrites a populated `.env`;
- moving computers means recreating the secret file or configuring the equivalent secret store;
- secrets are not recoverable from Git.

GitHub Actions secrets are separate from local `.env`.

## Optional Sibling Mods

A local sibling integration may be discovered through:

1. explicit Gradle/local property;
2. environment variable;
3. conventional sibling path.

The exact order is repository-defined.

If the sibling is optional:

- missing repository → warning, continue;
- no matching target artifact → warning, continue;
- ambiguous matching artifacts → fail that integration check rather than selecting randomly;
- never modify the sibling repository.

A fresh computer must still compile/package the main mod without the sibling.

## Machine-Specific Paths

Active committed configuration must not contain fixed personal paths such as:

```text
A:\Projects\...
/mnt/a/projects/<personal path>
C:\Users\<name>\...
/home/<name>/...
/tmp/<one-specific-run>
```

Use:

- repository-relative paths;
- environment-derived Windows locations;
- native temporary directories;
- ignored local properties for optional overrides.

Documentation may show placeholders, not a developer's actual path.

## WSL Filesystem Guidance

When Gradle or Minecraft produces heavy live I/O, prefer native Linux temporary/project-cache locations for intermediates if the mounted Windows filesystem exhibits locking or live-log errors.

Release artifacts may still be copied to deterministic repository output paths.

Do not treat one machine's mounted-drive workaround as an application compatibility requirement.

## MCP Setup Guidance

For migration work, configure the static Minecraft development MCP at the user or repository level so future agents do not rediscover vanilla APIs manually.

Desired behavior:

```text
Minecraft API/mappings/version/mixin question
→ minecraft-dev MCP first
```

Exact package/config syntax should be verified at implementation time because external tool installation details can change.

Do not make the mod build fail because the MCP is unavailable.

## Fresh-Machine Acceptance

A machine is ready for ordinary development when:

- repository checks pass;
- Python/Git pass;
- required Java generations are available;
- Gradle wrapper can configure the intended target;
- `compile --print-plan` selects the expected JVM/task;
- ordinary compile works.

It is ready for full local release preflight when, additionally:

- GUI/runtime prerequisites work;
- both production-smoke backends can run;
- required publication dry-run tooling resolves.

It is ready for real publication when, additionally:

- local platform tokens are configured;
- Git remote push authorization works.
