# Publishing

This document defines the release-publication workflow for the Stonecutter matrix.

Publishing is deliberately separate from validation. The presence of credentials must never cause an upload by itself.

## Sources of Truth

- `gradle/matrix/*.properties` — target Minecraft/loader/dependency facts.
- `gradle.properties` — authoritative mod version if that is where the repository currently owns it.
- `gradle/publishing.properties` — public project IDs, naming templates, release type, dependency mappings, tag/title templates.
- `docs/wiki/release-notes.md` — human-authored version-specific release notes.
- `scripts/verify-matrix-artifacts.py` — exact installable artifact verification.
- `build/publishing/` — generated manifests/dry-run output/receipt journal; ignored build output.

Do not hard-code a version-specific release plan in documentation.

## Local Credentials

Repository root:

```text
.env
```

Canonical keys:

```dotenv
MODRINTH_TOKEN=...
CURSEFORGE_TOKEN=...
```

`.env` must be ignored and untracked.

A committed `.env.example` may contain empty keys only.

Rules:

- never print token values;
- never put tokens in command-line arguments;
- never place them in generated public manifests;
- explicit environment values may override `.env`;
- dry-runs remove/avoid real credentials;
- a tracked `.env` is a hard failure for credential loading.

GitHub Release creation uses GitHub Actions' built-in `GITHUB_TOKEN`; a local GitHub CLI or personal GitHub PAT is not required for the normal tag workflow.

## Platform Model

### Modrinth

Use the pinned Minotaur plugin.

Publish one Modrinth version record per matrix target.

Each record contains exactly one corresponding installable JAR and exact:

- Minecraft version;
- loader;
- visible version name;
- machine-facing version number;
- release type;
- target-aware dependency declarations.

Visible naming is configured from a template such as:

```text
[{loader_name}] {mod_name} {mod_version} ({minecraft_version})
```

A machine-facing number should include enough target information to avoid ambiguity, for example:

```text
{mod_version}-{minecraft_version}-{loader}
```

Preserve historical records; do not rename old versions merely to match the current convention.

### CurseForge

Use the pinned CurseForgeGradle plugin.

Publish one file per matrix target with exact:

- installable JAR;
- Minecraft version;
- Fabric/NeoForge loader;
- Java generation where CurseForge metadata supports it;
- Client/Server environment;
- release type;
- target-aware required/optional relations.

Disable unsafe automatic version detection when explicit target metadata is available.

If CurseForge does not recognize a requested target version, stop rather than silently labeling it as another version.

### GitHub

One GitHub Release per mod version.

Attach exactly the installable matrix JARs.

Do not attach:

- dev JARs;
- source JARs;
- manifests;
- logs;
- caches;
- smoke-runtime files.

The pushed release tag is:

```text
v<mod_version>
```

unless `gradle/publishing.properties` defines another convention.

## Dependency Metadata

Do not guess platform dependency projects.

Resolve and record the correct Modrinth IDs and CurseForge slugs/project relations.

Public metadata must reflect actual end-user requirements.

Rules:

- required runtime dependencies → required;
- optional integrations → optional;
- loader-only dependencies only on that loader;
- provider not supported on a target → no relation;
- transitive implementation dependencies do not become direct public dependencies unless users independently need them.

When a repository has a developer-owned fork with a similar name to another public project, pin the intended project explicitly.

## Release Notes

Maintain one human-authored version section.

Do not generate release notes from build output.

The same version section should feed:

- Modrinth changelog;
- CurseForge changelog;
- GitHub Release body.

A missing or empty section for the current version should fail before real publication.

## Safe Planning and Dry-Run Commands

Start with:

```bash
python build-smart.py publish:plan
python build-smart.py publish:modrinth-dry-run
python build-smart.py publish:curseforge-dry-run
python build-smart.py publish:all-dry-run
```

`publish:plan` should be side-effect-free and show, per target:

- target;
- exact artifact;
- Minecraft version;
- loader;
- visible name;
- machine-facing version;
- release type;
- dependency declarations;
- CurseForge tags where applicable.

A platform dry-run must verify the exact selected artifact, ideally including SHA-256.

Dry-run success is configuration evidence, not publication.

## Publication Preflight

After the version bump and release notes are final:

```bash
python build-smart.py publish:preflight
```

Expected sequence:

```text
matrix package
→ artifact verification
→ packaged-JAR release-smoke
→ Modrinth dry-run
→ CurseForge dry-run
```

Success must end with:

```text
PUBLISH PREFLIGHT PASS
```

Run this full preflight once for the final release revision.

## Real Publication

Real uploads require explicit confirmation:

```bash
python build-smart.py publish:modrinth --confirm
python build-smart.py publish:curseforge --confirm
python build-smart.py publish:all --confirm
```

Without `--confirm`, refuse.

The unified release path should:

1. validate both credentials before the first upload;
2. verify all intended artifacts;
3. run duplicate/equivalent-version guards;
4. validate platform metadata;
5. upload sequentially;
6. report every successful remote ID/URL immediately;
7. append a receipt to the local publication journal;
8. stop visibly on failure;
9. never silently claim total success after partial success.

Do not perform destructive rollback of successful remote uploads automatically.

## Receipt Journal and Resume Safety

Use a generated journal such as:

```text
build/publishing/<version>-uploads.jsonl
```

Record enough information to prove:

- platform;
- target;
- local artifact path;
- artifact hash;
- remote ID;
- remote URL when available.

On retry:

- identical, journaled successful uploads may be skipped safely;
- changed artifact bytes must not reuse an old receipt;
- an unjournaled remote duplicate should stop for review;
- do not depend only on eventual public indexing when the upload API already returned a success receipt.

Keep the journal until the release is complete and platform processing/scanning has settled.

## Recommended Release Order

For a real release:

```text
1. bump version
2. author release notes
3. publish:plan
4. one publish:preflight
5. git diff/status review
6. commit release revision
7. push release revision
8. publish:all --confirm
9. verify all matrix targets on Modrinth and CurseForge
10. create v<version> tag
11. push tag
12. GitHub Actions builds/verifies the tag and creates the GitHub Release
```

Do not push the release tag before the external platform publication succeeds unless the repository intentionally uses a different release policy.

Do not force-move an unexpected existing tag.

## GitHub Actions

The tag-triggered path should be reproducible on a clean hosted runner and should not depend on local `.env`.

It should:

```text
checkout tagged commit
→ install required Java generations
→ package matrix
→ artifact verification
→ collect exactly the installable JARs
→ create GitHub Release
→ attach exactly those JARs
```

The GitHub job needs only the minimum permission required for release creation:

```yaml
permissions:
  contents: write
```

The normal tag path should not republish Modrinth/CurseForge if those platforms are intentionally published locally first.

Manual `workflow_dispatch` may remain for build-only or draft-release workflows. All mutating manual inputs should default to false.

## Failure Behavior

If a real upload fails after earlier targets succeeded:

- stop;
- report successful targets and IDs;
- preserve the receipt journal;
- fix only the deterministic blocker;
- resume safely;
- do not rerun unrelated production smoke/preflight unless the fix changed release artifacts or invalidated previous evidence.

If the only problem is platform indexing/moderation delay after a successful API response, do not re-upload.

## Secret Hygiene

Never:

- commit `.env`;
- echo tokens;
- pass tokens as CLI arguments;
- include tokens in Gradle `--info`/`--debug` output;
- store tokens in build manifests;
- use build scans for real publication;
- paste tokens into agent prompts.

Use environment inheritance for the child publishing process.
