#!/usr/bin/env python3
"""Publication facts/gates. Official Gradle plugins perform all uploads.

The public plan is read-only. Generated manifests/notes/assets live under build/.
This module never opens .env or puts credentials in a manifest/log. Authenticated
CurseForge metadata lookups consume only the publishing child's environment.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import runpy
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]


def properties(path):
    return dict(line.strip().split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                if "=" in line and not line.lstrip().startswith(("#", "!")))


def dependencies(metadata, loader, pins, config):
    """Actual loader declarations; Minecraft/Java/loaders are constraints."""
    if loader == "fabric":
        meta = json.loads(metadata)
        declared = {key: "required" for key in meta.get("depends", {})}
        declared.update({key: "optional" for section in ("suggests", "recommends")
                         for key in meta.get(section, {})})
    else:
        blocks = re.findall(r'\[\[dependencies\.' + re.escape(config['mod_id']) + r'\]\](.*?)(?=\n\[|\Z)', metadata, re.DOTALL)
        if not blocks:
            raise ValueError('Missing NeoForge dependency declarations for ' + config['mod_id'])
        declared = {}
        for block in blocks:
            fields = dict(re.findall(r'^\s*(\w+)\s*=\s*"([^"\n]*)"', block, re.MULTILINE))
            declared[fields["modId"]] = fields["type"]
    for key in ("minecraft", "java", "fabricloader", "neoforge"):
        declared.pop(key, None)
    result = []
    for mod_id, kind in sorted(declared.items()):
        project_id = config.get("dependency." + mod_id)
        if not project_id or kind not in ("required", "optional"):
            raise ValueError(f"Unmapped/unsupported release dependency: {mod_id} ({kind})")
        slug = config.get("curseforge_dependency." + mod_id)
        if not slug:
            raise ValueError(f"Unmapped CurseForge dependency: {mod_id}")
        result.append({"mod_id": mod_id, "project_id": project_id, "dependency_type": kind,
                       "curseforge_slug": slug})
    return result


def publication_plan(root=ROOT):
    config = properties(root / "gradle/publishing.properties")
    pins = properties(root / "gradle.properties")
    version = pins["mod_version"]
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.+_-]*', version):
        raise ValueError("mod_version must be filename/tag-safe")
    kind = config["version_type"]
    if kind not in ("release", "beta", "alpha"):
        raise ValueError("version_type must be release, beta or alpha")
    notes_path = root / config["notes_file"]
    if not notes_path.is_file():
        raise ValueError(f"Required release notes missing: {notes_path}")
    heading = config["notes_section"].format(mod_version=version)
    match = re.search(r'^## ' + re.escape(heading) + r'\s*\n(.*?)(?=^## |\Z)',
                      notes_path.read_text(encoding="utf-8"), re.MULTILINE | re.DOTALL)
    if not match or not match[1].strip():
        raise ValueError(f"Required nonempty release-notes section '## {heading}' missing in {notes_path}")
    entries = []
    for matrix in sorted((root / "gradle/matrix").glob("*.properties")):
        target = properties(matrix)
        minecraft, loader = target["minecraft_version"], target["loader"]
        if matrix.stem != f"{minecraft}-{loader}" or loader not in ("fabric", "neoforge"):
            raise ValueError(f"Invalid publishing target: {matrix}")
        values = dict(mod_version=version, mod_name=config['mod_name'], minecraft_version=minecraft, loader=loader,
                      loader_name={"fabric": "Fabric", "neoforge": "NeoForge"}[loader])
        artifact = f"build/libs/{matrix.stem}/{pins['archives_name']}-{loader}-{minecraft}-{version}.jar"
        source = root / loader / "src/main/resources" / (
            "fabric.mod.json" if loader == "fabric" else "META-INF/neoforge.mods.toml")
        metadata = source.read_text(encoding="utf-8")
        if loader == 'fabric':
            environment = json.loads(metadata).get('environment', '*')
            if environment not in ('*', 'client', 'server'):
                raise ValueError('Invalid Fabric environment: ' + environment)
            environments = ['Client', 'Server'] if environment == '*' else [environment.title()]
        else:
            # NeoForge has a common bootstrap and client-only registration hooks.
            environments = ['Client', 'Server']
        entries.append(dict(target=matrix.stem, artifact=artifact, minecraft=minecraft, loader=loader,
                            java=int(target["java_version"]), environments=environments,
                            curseforge_versions=[minecraft, values["loader_name"],
                                                 f"Java {target['java_version']}", *environments],
                            version_name=config["version_name"].format(**values),
                            version_number=config["version_number"].format(**values), version_type=kind,
                            dependencies=dependencies(metadata, loader, target, config)))
    if not entries or len({v["version_number"] for v in entries}) != len(entries):
        raise ValueError("Publishing matrix empty or internal version numbers are not unique")
    return dict(project_id=config["modrinth_project_id"], project_slug=config["modrinth_project_slug"],
                curseforge_project_id=config["curseforge_project_id"],
                curseforge_project_slug=config["curseforge_project_slug"],
                mod_name=config['mod_name'],
                modrinth_environments=dict(client_side=config['modrinth_client_side'], server_side=config['modrinth_server_side']),
                mod_version=version, tag=config["github_tag"].format(mod_version=version),
                title=config["github_title"].format(mod_version=version, mod_name=config['mod_name']),
                changelog=match[1].strip() + "\n", entries=entries)


def show_plan(data):
    print(f"Modrinth {data['project_slug']} ({data['project_id']}); CurseForge {data['curseforge_project_slug']} ({data['curseforge_project_id']}); GitHub {data['tag']}: {data['title']}")
    print('Modrinth project environments (unchanged): ' + ', '.join(f'{k}={v}' for k, v in data['modrinth_environments'].items()))
    print("Target | Release artifact | Minecraft | Loader | versionName | versionNumber | Type | Dependencies")
    for row in data["entries"]:
        deps = ", ".join(f"{d['dependency_type']}:{d['mod_id']}(MR:{d['project_id']},CF:{d['curseforge_slug']})" for d in row["dependencies"])
        print(" | ".join(str(row[k]) for k in ("target", "artifact", "minecraft", "loader", "version_name", "version_number", "version_type")) + " | " + deps)
        print("  CurseForge tags: " + ", ".join(row["curseforge_versions"]))


def verified_plan(root=ROOT):
    data = publication_plan(root)
    verifier = runpy.run_path(str(root / "scripts/verify-matrix-artifacts.py"))
    config = properties(root / "gradle/publishing.properties")
    for row in data["entries"]:
        matrix = root / "gradle/matrix" / (row["target"] + ".properties")
        artifact = verifier["inspect"](matrix)
        if artifact.resolve() != (root / row["artifact"]).resolve():
            raise ValueError(f"Unexpected verified artifact: {artifact}")
        with ZipFile(artifact) as jar:
            meta = jar.read("fabric.mod.json" if row["loader"] == "fabric" else "META-INF/neoforge.mods.toml").decode()
        actual = dependencies(meta, row["loader"], properties(matrix), config)
        if actual != row["dependencies"]:
            raise ValueError(f"Packaged dependency metadata differs from publication plan: {row['target']}")
        row["sha256"] = hashlib.sha256(artifact.read_bytes()).hexdigest()
        row["size"] = artifact.stat().st_size
    return data


def has_mod_version(value, version):
    return bool(re.search(r'(?<![A-Za-z0-9.])' + re.escape(version) + r'(?![A-Za-z0-9.])', value or ''))


def check_modrinth_project(data):
    project = get_json('https://api.modrinth.com/v2/project/' + data['project_id'])
    if project.get('id') != data['project_id'] or project.get('slug') != data['project_slug']:
        raise ValueError('Modrinth project identity differs from publication configuration')
    if any(project.get(key) != value for key, value in data['modrinth_environments'].items()):
        raise ValueError('Modrinth project environments differ; review without automatically changing the public project')


def check_duplicates(data, existing=None):
    if existing is None:
        url = f"https://api.modrinth.com/v2/project/{data['project_id']}/version"
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "Vg34100/StructureVoidable-release-pipeline"})
            with urllib.request.urlopen(request, timeout=30) as response:
                existing = json.load(response)
        except (urllib.error.URLError, ValueError) as error:
            raise ValueError(f"Cannot check existing Modrinth versions; refusing upload: {type(error).__name__}") from None
    collisions = []
    for row in data["entries"]:
        for version in existing:
            # Recognize the mod version across historical naming conventions.
            names = [version.get('version_number', ''), version.get('name', '')]
            names += [file.get('filename', '') for file in version.get('files', [])]
            if version["version_number"] == row["version_number"] or (
                any(has_mod_version(name, data['mod_version']) for name in names)
                and row["minecraft"] in version["game_versions"]
                and row["loader"] in version["loaders"]
            ):
                collisions.append(f"{row['target']}: {version['version_number']} (version ID {version['id']})")
    if collisions:
        raise ValueError("Existing Modrinth release(s); choose a new mod version before uploading:\n  " + "\n  ".join(collisions))


def receipts_path(data):
    return ROOT / 'build/publishing' / (data['mod_version'] + '-uploads.jsonl')


def pending_entries(data, platform):
    """Resume only API-acknowledged uploads of these exact verified bytes.

    The public receipt journal is written immediately by each successful task,
    including when a subsequent upload fails or CF has not indexed the file yet.
    Unknown remote duplicates are still refused, never overwritten.
    """
    path = receipts_path(data)
    receipts = [json.loads(line) for line in path.read_text().splitlines() if line.strip()] if path.is_file() else []
    pending = []
    project_id = data['project_id'] if platform == 'modrinth' else data['curseforge_project_id']
    for row in data['entries']:
        found = [r for r in receipts if r['platform'] == platform and r['target'] == row['target']]
        if found:
            if len(found) != 1 or any(found[0].get(k) != v for k, v in dict(
                    mod_version=data['mod_version'], project_id=project_id, sha256=row['sha256'],
                    size=row['size'], artifact=Path(row['artifact']).name).items()) or not found[0].get('id'):
                raise ValueError(f'Conflicting upload receipt: {platform} {row["target"]}; review before retrying')
        else:
            pending.append(row)
    return pending


def get_json(url, headers=None):
    request = urllib.request.Request(url, headers={'User-Agent': 'Vg34100/StructureVoidable-release-pipeline', **(headers or {})})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except (urllib.error.URLError, ValueError):
        raise ValueError(f'Cannot read release metadata from {url}; refusing upload') from None


def check_curseforge_versions(data, versions=None):
    if versions is None:
        token = os.environ.get('CURSEFORGE_TOKEN')
        if not token:
            raise ValueError('CurseForge metadata validation requires CURSEFORGE_TOKEN in the publishing child environment')
        versions = get_json('https://minecraft.curseforge.com/api/game/versions', {'X-Api-Token': token})
    # Same names can also appear in snapshot/Bukkit groups. Minecraft releases
    # use type 1; loaders/Java/environment must resolve in their own groups.
    names = {v['name'] for v in versions if v['gameVersionTypeID'] in (1, 2, 68441, 75208)}
    missing = {row['target']: sorted(set(row['curseforge_versions']) - names)
               for row in data['entries'] if set(row['curseforge_versions']) - names}
    if missing:
        raise ValueError(f'Unsupported CurseForge target metadata; NO uploads permitted: {missing}')
    print(f'CURSEFORGE METADATA PASS ({len(data["entries"])} exact target/loader/Java/environment combinations)')


def check_curseforge_duplicates(data, existing=None):
    # The official upload API has no file-list endpoint. Public CFWidget data
    # catches indexed duplicates; immediate API receipts cover pending scans.
    if existing is None:
        existing = get_json(f'https://api.cfwidget.com/{data["curseforge_project_id"]}')
    if str(existing.get('id')) != data['curseforge_project_id'] or not isinstance(existing.get('files'), list):
        raise ValueError('Cannot validate existing CurseForge files; refusing upload')
    for row in data['entries']:
        for file in existing['files']:
            names = [file.get('name', ''), file.get('display', '')]
            semantic = (any(has_mod_version(name, data['mod_version']) for name in names)
                        and row['minecraft'] in file.get('versions', [])
                        and {'fabric': 'Fabric', 'neoforge': 'NeoForge'}[row['loader']] in file.get('versions', []))
            if file.get('name') in (row['version_name'], Path(row['artifact']).name) or file.get('display') == row['version_name'] or semantic:
                raise ValueError(f'Existing CurseForge file for {row["target"]}: {file["id"]}; refusing duplicate upload')


def check_remote(data, platform):
    pending = dict(data, entries=pending_entries(data, platform))
    if platform == 'modrinth':
        check_modrinth_project(data)
        check_duplicates(pending)
    elif platform == 'curseforge':
        check_curseforge_versions(data)
        check_curseforge_duplicates(pending)
    else:
        raise ValueError('Unknown publication platform: ' + platform)


def check_manifest(path, real=False, platform='modrinth'):
    supplied = json.loads(path.read_text(encoding="utf-8"))
    if supplied != verified_plan():
        raise ValueError("Publication manifest is stale/altered; regenerate it through build-smart.py")
    if real:
        check_remote(supplied, platform)
    print(f"Publication artifact gate PASS ({len(supplied['entries'])} targets)")


def verify_debug_output(lines, data):
    text = "\n".join(lines)
    payloads = [json.JSONDecoder().raw_decode(text[m.end():].lstrip())[0]
                for m in re.finditer(r'Full data to be sent for upload:\s*', text)]
    if len(payloads) != len(data["entries"]) or text.count("Not going to upload this version.") != len(payloads):
        raise ValueError("Did not receive one Minotaur debug payload per target; refusing to count dry-run as passed")
    files = [json.loads(line.removeprefix('PUBLISH FILE ')) for line in lines if line.startswith('PUBLISH FILE ')]
    if len(files) != len(payloads):
        raise ValueError("Missing exact Minotaur resolved-upload-file evidence")
    for row, payload, upload in zip(data["entries"], payloads, files):
        def field(key):
            return payload.get(key, payload.get(re.sub(r'([A-Z])', lambda m: '_' + m[1].lower(), key)))
        expected = {"projectId": data["project_id"], "versionNumber": row["version_number"],
                    "name": row["version_name"], "gameVersions": [row["minecraft"]],
                    "loaders": [row["loader"]], "changelog": data["changelog"]}
        for key, value in expected.items():
            if field(key) != value:
                raise ValueError(f"Minotaur dry payload mismatch for {row['target']}: {key}")
        if str(field("versionType")).lower() != row["version_type"]:
            raise ValueError(f"Minotaur release type mismatch: {row['target']}")
        if upload != dict(target=row["target"], path=str((ROOT / row["artifact"]).resolve()),
                          sha256=row["sha256"], size=row["size"]):
            raise ValueError(f"Minotaur did not select exactly the verified release jar: {row['target']}")
        actual = {(d.get("projectId", d.get("project_id")), d.get("dependencyType", d.get("dependency_type")).lower())
                  for d in field("dependencies")}
        expected_deps = {(d["project_id"], d["dependency_type"]) for d in row["dependencies"]}
        if actual != expected_deps or len(field("dependencies")) != len(expected_deps):
            raise ValueError(f"Minotaur dependency mismatch: {row['target']}")
        print(f"DRY-RUN PASS {row['target']}: {row['version_name']} / {row['version_number']} / {Path(row['artifact']).name}")


def verify_curseforge_debug_output(lines, data):
    text = '\n'.join(lines)
    uploads = [json.loads(line.removeprefix('CURSEFORGE FILE ')) for line in lines if line.startswith('CURSEFORGE FILE ')]
    payloads = [json.JSONDecoder().raw_decode(text[m.end():].lstrip())[0]
                for m in re.finditer(r'Upload file URI for [^\n]+:\s*https://minecraft\.curseforge\.com/api/projects/'
                                     + re.escape(data['curseforge_project_id']) + r'/upload-file\s*', text)]
    if len(uploads) != len(data['entries']) or len(payloads) != len(uploads):
        raise ValueError('Missing one official CurseForge debug payload/exact artifact per target')
    for row, upload, payload in zip(data['entries'], uploads, payloads):
        if upload != dict(target=row['target'], path=str((ROOT / row['artifact']).resolve()),
                          sha256=row['sha256'], size=row['size']):
            raise ValueError(f'CurseForge resolved artifact mismatch: {row["target"]}')
        expected = dict(displayName=row['version_name'], releaseType=row['version_type'],
                        changelog=data['changelog'], changelogType='markdown')
        if any(payload.get(k) != v for k, v in expected.items()):
            raise ValueError(f'CurseForge display/type/changelog mismatch: {row["target"]}')
        if set(payload.get('gameVersionNames', [])) != set(row['curseforge_versions']) or len(payload['gameVersionNames']) != len(row['curseforge_versions']):
            raise ValueError(f'CurseForge exact game/loader/Java/environment mismatch: {row["target"]}')
        relations = payload.get('relations', {}).get('projects', [])
        expected_relations = {(d['curseforge_slug'], d['dependency_type'] + 'Dependency') for d in row['dependencies']}
        if {(d['slug'], d['type']) for d in relations} != expected_relations or len(relations) != len(expected_relations):
            raise ValueError(f'CurseForge relation mismatch: {row["target"]}')
        if payload.get('parentFileID') or payload.get('isMarkedForManualRelease'):
            raise ValueError('CurseForge uploads must be independent automatic-release files')
        print(f'CURSEFORGE DRY-RUN PASS {row["target"]}: {Path(row["artifact"]).name}; SHA-256 {row["sha256"]}; '
              + ', '.join(row['curseforge_versions']))


def run(project, command, args, print_plan, wrapper):
    supported = ("publish:plan", "publish:modrinth-dry-run", "publish:curseforge-dry-run",
                 "publish:all-dry-run", "publish:preflight", "publish:modrinth", "publish:curseforge", "publish:all")
    if command not in supported:
        raise ValueError("Unknown publishing command: " + command)
    real = command in ('publish:modrinth', 'publish:curseforge', 'publish:all')
    if real and "--confirm" not in args:
        print(f"REFUSING REAL {command.removeprefix('publish:').upper()} PUBLISH: use --confirm")
        return 2
    if any(any(token in a for token in ('MODRINTH_TOKEN', 'CURSEFORGE_TOKEN'))
           or a in ("--continue", "--parallel", "--debug", "--info", "--scan")
           or a.startswith(("-Ppublish_", "-Dorg.gradle.project.publish_")) for a in args):
        raise ValueError("Publishing forbids continuation/parallel/debug/scan flags and manual publish_* overrides")
    args = [a for a in args if a != "--confirm"]
    data = publication_plan(project.root)
    if command == "publish:plan" or print_plan:
        show_plan(data)
        if command != "publish:plan":
            steps = ("matrix:package → artifact verification → release-smoke → Minotaur + CurseForgeGradle debugMode=true"
                     if command == "publish:preflight" else f"artifact verification → official upload plugin(s) debugMode={not real} (sequential)")
            print(steps)
        return 0
    if command == "publish:preflight":
        for step in ("matrix:package",):
            if code := wrapper["main"]([step] + args):
                return code
        verified_plan(project.root)
        if code := wrapper["main"](["release-smoke"] + args):
            return code
        if code := wrapper["main"](["publish:all-dry-run"] + args):
            return code
        print(f"PUBLISH PREFLIGHT PASS — {data['mod_name']} {data['mod_version']}; {len(data['entries'])} planned target releases")
        return 0
    data = verified_plan(project.root)
    env = wrapper["publishing_environment"](project.root) if real else os.environ.copy()
    platform = 'curseforge' if command.startswith('publish:curseforge') else 'modrinth'
    required_tokens = ('MODRINTH_TOKEN', 'CURSEFORGE_TOKEN') if command == 'publish:all' else (platform.upper() + '_TOKEN',)
    if real:
        for token in required_tokens:
            if not env.get(token):
                raise ValueError(f"Real publication requires {token} in the environment or ignored root .env")
    if not real:
        for token in ('MODRINTH_TOKEN', 'CURSEFORGE_TOKEN'):
            env.pop(token, None)  # Debug runs use only public lookups, no credentials.
        if platform == 'modrinth' and command != 'publish:all-dry-run':
            check_modrinth_project(data)
            check_duplicates(data)
        elif platform == 'curseforge':
            check_curseforge_duplicates(data)
    output = project.root / "build/publishing"
    output.mkdir(parents=True, exist_ok=True)
    manifest = output / "manifest.json"
    manifest.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    if command in ('publish:all', 'publish:all-dry-run'):
        # Before the first remote mutation, validate BOTH platforms and all CF
        # tags. Secrets enter only this check child's environment, never its CLI.
        if real:
            for destination in ('modrinth', 'curseforge'):
                result = subprocess.run([sys.executable, str(project.root / 'scripts/publish-release.py'),
                                         'check', str(manifest), '--real', destination], cwd=project.root, env=env)
                if result.returncode:
                    return result.returncode
        for destination in ('modrinth', 'curseforge'):
            step = f'publish:{destination}' + ('' if real else '-dry-run')
            if code := wrapper['main']([step] + (['--confirm'] if real else []) + args):
                print(f'ALL PUBLICATION STOPPED at {destination}; earlier reported remote IDs remain published. Retry with the same artifacts to resume safely.')
                return code
        print(f'ALL {"PUBLISH" if real else "DRY-RUN"} PASS — {len(data["entries"])}/{len(data["entries"])} on each platform')
        return 0
    pending = pending_entries(data, platform) if real else data['entries']
    if real:
        for row in data['entries']:
            if row not in pending:
                print(f'RESUME SKIP {platform} {row["target"]}: exact-artifact API receipt already recorded')
    if not pending:
        print(f'{platform.upper()} PUBLISH PASS ({len(data["entries"])}/{len(data["entries"])}, already acknowledged)')
        return 0
    java, message, fatal = wrapper["select_gradle_java"](project, args)
    if message:
        print(message)
    if fatal:
        return 2
    tasks = [f":{row['target']}:{platform}" for row in pending]
    tasks += [f"-Ppublish_manifest={manifest}", f"-Ppublish_python={sys.executable}",
              f"-Ppublish_confirm={'true' if real else 'false'}", f'-Ppublish_platform={platform}',
              "--no-parallel", "--no-configuration-cache"] + args
    code, lines = wrapper["run_gradle"](project, tasks, java, env=env)
    # Debug payloads contain only public release data; never retain real upload logs/credentials.
    if not real:
        (output / f"{platform}-dry-run.log").write_text("\n".join(lines) + "\n", encoding="utf-8")
    else:
        for token in ('MODRINTH_TOKEN', 'CURSEFORGE_TOKEN'):
            if env.get(token):
                lines = [line.replace(env[token], '[REDACTED]') for line in lines]
    wrapper["process_output"](lines, code)
    if code:
        print("Publication stopped on failure. Previously reported successful uploads remain published; no rollback attempted." if real
              else f"Dry-run log: {output / (platform + '-dry-run.log')}")
        return code
    if not real:
        (verify_debug_output if platform == 'modrinth' else verify_curseforge_debug_output)(lines, data)
    elif pending_entries(data, platform):
        raise ValueError("Incomplete real-publication API receipts; inspect the reported remote IDs before retrying")
    if data != verified_plan(project.root):
        raise ValueError("Artifacts changed during publication")
    print(f"{platform.upper()} {'PUBLISH' if real else 'DRY-RUN'} PASS ({len(data['entries'])}/{len(data['entries'])})")
    return 0


def collect():
    data = verified_plan()
    destination = ROOT / "build/publishing/github-assets"
    destination.mkdir(parents=True, exist_ok=True)
    expected = {Path(row["artifact"]).name for row in data["entries"]} | {"manifest.json", "release-notes.md"}
    if any(p.name not in expected for p in destination.iterdir()):
        raise ValueError("Unexpected/stale files in GitHub asset collection; use a fresh checkout/build directory")
    for row in data["entries"]:
        shutil.copyfile(ROOT / row["artifact"], destination / Path(row["artifact"]).name)
    (destination / "manifest.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    (destination / "release-notes.md").write_text(data["changelog"], encoding="utf-8")
    print(f"Collected exactly {len(data['entries'])} installable GitHub JARs in {destination}")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"version={data['mod_version']}\ntag={data['tag']}\n")


def restore(directory):
    data = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    planned = publication_plan()
    stripped = dict(data, entries=[{k: v for k, v in row.items() if k not in ("sha256", "size")} for row in data["entries"]])
    if stripped != planned:
        raise ValueError("Downloaded workflow artifacts do not match this checkout's release plan")
    if (directory / 'release-notes.md').read_text(encoding='utf-8') != data['changelog']:
        raise ValueError('Workflow release notes differ from the authoritative release section')
    jars = {p.name for p in directory.glob("*.jar")}
    if jars != {Path(row["artifact"]).name for row in data["entries"]}:
        raise ValueError("Workflow artifact must contain exactly the planned installable JARs")
    for row in data["entries"]:
        source = directory / Path(row["artifact"]).name
        if hashlib.sha256(source.read_bytes()).hexdigest() != row["sha256"] or source.stat().st_size != row["size"]:
            raise ValueError(f"Workflow artifact hash/size mismatch: {source.name}")
        destination = ROOT / row["artifact"]
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
    if verified_plan() != data:
        raise ValueError("Restored release jars failed publication verification")


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["collect"]:
            collect()
        elif sys.argv[1:2] == ["restore"] and len(sys.argv) == 3:
            restore(Path(sys.argv[2]))
        elif sys.argv[1:2] == ["check"] and len(sys.argv) in (3, 4, 5):
            check_manifest(Path(sys.argv[2]), real=sys.argv[3:4] == ["--real"],
                           platform=sys.argv[4] if len(sys.argv) == 5 else 'modrinth')
        else:
            raise ValueError("Usage: publish-release.py collect | restore <bundle> | check <manifest> [--real]")
    except (ValueError, AssertionError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
