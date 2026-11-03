# Development and public source

Develop, commit and push on `work`. After each push, **Update public main** copies
the committed source onto `main`, excluding the paths in `.github/public-sync.json`.
No local branch switch or manual merge is needed. Uncommitted changes stay local.

`main` is generated: make fixes on `work`, including fixes to public documentation.
Changes made only on `main` will be replaced by the next snapshot. Keep `main` as
the repository default branch for the public landing page. For your own clone:

```sh
git clone --branch work <repository-url>
```

For work-only context, use `.development/`, agent instruction files (`AGENTS.md`,
`CLAUDE.md`, `GEMINI.md`), tool settings (`.claude/`, `.codex/`, `.agents/`), or
`prompts/`. Historical work-note paths are listed explicitly per project. New
notes outside these locations need an entry in `paths`. Do not store credentials
on either branch: `work` is public too. This is presentation filtering, not privacy.

Source, resources, attribution, licenses, changelogs, build and test tooling,
and current developer instructions remain on `main`. Filtering does not detect
AI authorship or rewrite prose, commit messages, existing history, branches or tags.
Links to excluded notes may still exist in historical documents.

Preview locally without touching your checkout or branch refs:

```sh
python scripts/sync-public.py
```

The script uses a temporary index, preserves file modes and symlinks, skips
identical snapshots, and creates commits whose only parent is the previous
`main`. It never merges `work` history into `main` and never force-pushes.
Concurrent pushes to `main` cause a safe failure. A stale work revision is rejected;
the queued latest-work run can retry. Inspect failures in GitHub Actions and rerun
**Update public main** on branch `work` if needed.

Automation requires Actions and write permission for its `GITHUB_TOKEN`. Branch
protection/rulesets may require allowing this workflow to update `main`; otherwise
the workflow fails visibly rather than bypassing policy. Normal token-generated
pushes do not trigger further push workflows. Run code checks on `work`; releases
remain an explicit, separately validated process. Existing release tags are not
moved, and this workflow never uploads mod releases. For future clean source
archives, create release tags on the validated public snapshot and record the
corresponding work revision from its commit message.

To pause automatic updates, disable **Update public main** in GitHub Actions.
Never merge generated `main` back into `work`; apply intended public changes to
`work` instead. Updating the filter requires a normal commit and push on `work`.
