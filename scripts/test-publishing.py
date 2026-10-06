#!/usr/bin/env python3
"""Small stdlib-only regression tests for publication safety; no real secrets/API writes."""
import os
from pathlib import Path
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
WRAPPER = runpy.run_path(str(ROOT / "build-smart.py"))
PUBLISH = runpy.run_path(str(ROOT / "scripts/publish-release.py"))


class PublishingSafety(unittest.TestCase):
    def test_dotenv_is_child_only_and_environment_wins(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".env").write_text("# comment\n\nMODRINTH_TOKEN='synthetic-test-value'\nCURSEFORGE_TOKEN=synthetic-cf-value\nJAVA_HOME=ignored\n")
            result = subprocess.CompletedProcess([], 0, stdout=b"", stderr=b"")
            with patch.dict(os.environ, {}, clear=True), patch('subprocess.run', return_value=result):
                child = WRAPPER["publishing_environment"](root)
                self.assertEqual(child, {"MODRINTH_TOKEN": "synthetic-test-value", "CURSEFORGE_TOKEN": "synthetic-cf-value"})
                self.assertNotIn("MODRINTH_TOKEN", os.environ)
            with patch.dict(os.environ, {"MODRINTH_TOKEN": "explicit-test-value", "CURSEFORGE_TOKEN": "explicit-cf-value"}, clear=True), patch('subprocess.run', return_value=result):
                with patch.object(Path, 'open', side_effect=AssertionError("must not open .env")):
                    self.assertEqual(WRAPPER["publishing_environment"](root)["MODRINTH_TOKEN"], "explicit-test-value")

    def test_tracked_env_stops_before_open(self):
        result = subprocess.CompletedProcess([], 0, stdout=b".env\n", stderr=b"")
        with patch('subprocess.run', return_value=result), patch.object(Path, 'open', side_effect=AssertionError("must not open .env")):
            with self.assertRaisesRegex(WRAPPER["PlanError"], "STOP: .env is tracked"):
                WRAPPER["publishing_environment"](ROOT)

    def test_confirmation_required_before_other_work(self):
        project = WRAPPER["Project"](ROOT)
        self.assertEqual(PUBLISH["run"](project, "publish:modrinth", [], False, {}), 2)
        self.assertEqual(PUBLISH["run"](project, "publish:curseforge", [], False, {}), 2)
        self.assertEqual(PUBLISH["run"](project, "publish:all", [], False, {}), 2)

    def test_plan_is_read_only_and_matrix_derived(self):
        with patch.object(Path, 'write_text', side_effect=AssertionError("read-only")), patch('subprocess.run', side_effect=AssertionError("no subprocess")):
            data = PUBLISH["publication_plan"]()
        self.assertEqual({r["target"] for r in data["entries"]}, {p.stem for p in (ROOT / 'gradle/matrix').glob('*.properties')})
        self.assertEqual(len({r['version_number'] for r in data['entries']}), len(data['entries']))
        for row in data['entries']:
            loader = {'fabric': 'Fabric', 'neoforge': 'NeoForge'}[row['loader']]
            self.assertEqual(row['version_name'], f"[{loader}] Structure Voidable {data['mod_version']} ({row['minecraft']})")
            mods = {d['mod_id'] for d in row['dependencies']}
            self.assertEqual(mods, {'architectury', 'fabric-api', 'modmenu'} if row['loader'] == 'fabric' else {'architectury'})
            kinds = {d['mod_id']: d['dependency_type'] for d in row['dependencies']}
            self.assertEqual(kinds['architectury'], 'required')
            if row['loader'] == 'fabric':
                self.assertEqual(kinds['fabric-api'], 'required')
                self.assertEqual(kinds['modmenu'], 'optional')
            self.assertEqual(row['curseforge_versions'], [row['minecraft'], loader, f"Java {row['java']}", 'Client', 'Server'])
            self.assertEqual(row['environments'], ['Client', 'Server'])

    def test_curseforge_exact_metadata_and_duplicate_guard(self):
        data = PUBLISH['publication_plan']()
        names = {name for row in data['entries'] for name in row['curseforge_versions']}
        versions = [dict(name=name, gameVersionTypeID=1) for name in names]
        PUBLISH['check_curseforge_versions'](data, versions)
        with self.assertRaisesRegex(ValueError, 'Unsupported CurseForge'):
            PUBLISH['check_curseforge_versions'](data, [v for v in versions if v['name'] != '26.2'])
        existing = dict(id=1072628, files=[dict(id=1, display=data['entries'][0]['version_name'])])
        with self.assertRaisesRegex(ValueError, 'Existing CurseForge'):
            PUBLISH['check_curseforge_duplicates'](data, existing)
        PUBLISH['check_curseforge_duplicates'](data, dict(id=1072628, files=[]))

    def test_resume_requires_exact_artifact_receipt(self):
        import json
        data = PUBLISH['publication_plan']()
        row = data['entries'][0]
        for entry in data['entries']:
            entry.update(sha256='synthetic-hash', size=100)
        for platform in ('modrinth', 'curseforge'):
            receipt = dict(platform=platform, target=row['target'], mod_version=data['mod_version'],
                           project_id=data['project_id'] if platform == 'modrinth' else data['curseforge_project_id'],
                           artifact=Path(row['artifact']).name, sha256=row['sha256'], size=row['size'], id='synthetic-id')
            with patch.object(Path, 'is_file', return_value=True), patch.object(Path, 'read_text', return_value=json.dumps(receipt)):
                self.assertEqual(len(PUBLISH['pending_entries'](data, platform)), len(data['entries'])-1)
            for change in ({'sha256': 'wrong'}, {'project_id': 'wrong'}, {'artifact': 'wrong.jar'}):
                with patch.object(Path, 'is_file', return_value=True), patch.object(Path, 'read_text', return_value=json.dumps(dict(receipt, **change))):
                    with self.assertRaisesRegex(ValueError, 'Conflicting upload receipt'):
                        PUBLISH['pending_entries'](data, platform)

    def test_exact_and_historical_duplicate_guards(self):
        data = PUBLISH["publication_plan"]()
        row = next(r for r in data['entries'] if r['target'] == '26.1.2-fabric')
        for number in (row['version_number'], data['mod_version'], 'structurevoidable-' + data['mod_version'] + '-v26.1.2-fabric'):
            existing = [dict(id='synthetic-version', version_number=number, game_versions=[row['minecraft']], loaders=[row['loader']])]
            with self.assertRaisesRegex(ValueError, 'Existing Modrinth'):
                PUBLISH['check_duplicates'](data, existing)
        PUBLISH['check_duplicates'](data, [])

    def test_semantic_duplicates_check_target_and_loader(self):
        data = PUBLISH['publication_plan']()
        row = next(r for r in data['entries'] if r['target'] == '26.2-fabric')
        existing = dict(id='synthetic-version', version_number='historical-name',
                        name='Structure Voidable ' + data['mod_version'], game_versions=['26.2'], loaders=['fabric'])
        with self.assertRaisesRegex(ValueError, 'Existing Modrinth'):
            PUBLISH['check_duplicates'](data, [existing])
        for change in ({'name': 'Structure Voidable ' + data['mod_version'] + '0'}, {'loaders': ['quilt']}, {'game_versions': ['1.20.1']}):
            PUBLISH['check_duplicates'](data, [dict(existing, **change)])
        file = dict(id=1, name='structurevoidable-' + data['mod_version'] + '-v26.2-fabric.jar', versions=['26.2', 'Fabric'])
        with self.assertRaisesRegex(ValueError, 'Existing CurseForge'):
            PUBLISH['check_curseforge_duplicates'](data, dict(id=1072628, files=[file]))
        PUBLISH['check_curseforge_duplicates'](data, dict(id=1072628, files=[dict(file, versions=['26.2', 'Quilt'])]))

    def test_public_project_identity_and_environments_must_match(self):
        data = PUBLISH['publication_plan']()
        project = dict(id=data['project_id'], slug=data['project_slug'], **data['modrinth_environments'])
        for change in ({'id': 'wrong'}, {'slug': 'wrong'}, {'server_side': 'unsupported'}):
            with patch('urllib.request.urlopen') as urlopen:
                import json
                urlopen.return_value.__enter__.return_value.read.return_value = json.dumps(dict(project, **change)).encode()
                with self.assertRaises(ValueError):
                    PUBLISH['check_modrinth_project'](data)

    def test_unmapped_metadata_and_wrong_mod_namespace_fail(self):
        config = PUBLISH['properties'](ROOT / 'gradle/publishing.properties')
        with self.assertRaisesRegex(ValueError, 'Unmapped'):
            PUBLISH['dependencies']('{"depends":{"unexpected-mod":"*"}}', 'fabric', {}, config)
        with self.assertRaisesRegex(ValueError, 'Missing NeoForge dependency'):
            PUBLISH['dependencies']('[[dependencies.unrelated]]\nmodId="architectury"\ntype="required"\n', 'neoforge', {}, config)

    def test_cli_overrides_cannot_bypass_confirmation_or_debug_mode(self):
        project = WRAPPER['Project'](ROOT)
        for argument in ('--parallel', '--continue', '--scan', '--debug', '--info', '-Ppublish_confirm=true', '-Dorg.gradle.project.publish_confirm=true', '-PMODRINTH_TOKEN=synthetic'):
            with self.assertRaisesRegex(ValueError, 'Publishing forbids'):
                PUBLISH['run'](project, 'publish:modrinth-dry-run', [argument], False, {})

    def test_dry_run_strips_credentials_and_never_opens_dotenv(self):
        data = PUBLISH['publication_plan']()
        project = WRAPPER['Project'](ROOT)
        calls = []
        def gradle(project, tasks, java, env):
            calls.append((tasks, env))
            return 0, []
        wrapper = dict(select_gradle_java=lambda *args: (None, None, False), run_gradle=gradle,
                       process_output=lambda *args: None,
                       publishing_environment=lambda *args: self.fail('dry-run requested credentials'))
        replacements = dict(publication_plan=lambda *args: data, verified_plan=lambda *args: data,
                            check_modrinth_project=lambda *args: None, check_duplicates=lambda *args: None,
                            verify_debug_output=lambda *args: None)
        with patch.dict(PUBLISH['run'].__globals__, replacements), patch.dict(os.environ, {'MODRINTH_TOKEN': 'synthetic-mr', 'CURSEFORGE_TOKEN': 'synthetic-cf'}), patch.object(Path, 'open', side_effect=AssertionError('must not open .env')), patch.object(Path, 'write_text'), patch.object(Path, 'mkdir'), patch('builtins.print'):
            self.assertEqual(PUBLISH['run'](project, 'publish:modrinth-dry-run', [], False, wrapper), 0)
        tasks, env = calls[0]
        self.assertNotIn('MODRINTH_TOKEN', env)
        self.assertNotIn('CURSEFORGE_TOKEN', env)
        self.assertIn('-Ppublish_confirm=false', tasks)
        self.assertIn('--no-parallel', tasks)

    def test_debug_payload_verification_rejects_wrong_loader_or_relations(self):
        import json
        data = PUBLISH['publication_plan']()
        modrinth, curseforge = [], []
        for row in data['entries']:
            row.update(sha256='synthetic-hash', size=100)
            upload = dict(target=row['target'], path=str((ROOT / row['artifact']).resolve()), sha256=row['sha256'], size=row['size'])
            payload = dict(projectId=data['project_id'], versionNumber=row['version_number'], name=row['version_name'],
                           gameVersions=[row['minecraft']], loaders=[row['loader']], changelog=data['changelog'], versionType=row['version_type'],
                           dependencies=[dict(projectId=d['project_id'], dependencyType=d['dependency_type']) for d in row['dependencies']])
            modrinth.extend(['PUBLISH FILE ' + json.dumps(upload), 'Full data to be sent for upload: ' + json.dumps(payload), 'Not going to upload this version.'])
            cf_payload = dict(displayName=row['version_name'], releaseType=row['version_type'], changelog=data['changelog'], changelogType='markdown',
                              gameVersionNames=row['curseforge_versions'], relations=dict(projects=[dict(slug=d['curseforge_slug'], type=d['dependency_type']+'Dependency') for d in row['dependencies']]))
            curseforge.extend(['CURSEFORGE FILE ' + json.dumps(upload), f'Upload file URI for file: https://minecraft.curseforge.com/api/projects/{data["curseforge_project_id"]}/upload-file\n' + json.dumps(cf_payload)])
        with patch('builtins.print'):
            PUBLISH['verify_debug_output'](modrinth, data)
            PUBLISH['verify_curseforge_debug_output'](curseforge, data)
            with self.assertRaisesRegex(ValueError, 'loaders'):
                PUBLISH['verify_debug_output']([line.replace('"loaders": ["fabric"]', '"loaders": ["quilt"]') for line in modrinth], data)
            with self.assertRaisesRegex(ValueError, 'relation mismatch'):
                PUBLISH['verify_curseforge_debug_output']([line.replace('optionalDependency', 'requiredDependency') for line in curseforge], data)

    def test_missing_and_empty_notes_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'gradle').mkdir()
            (root / 'gradle.properties').write_text('mod_version=1.0.3\narchives_name=structurevoidable\n')
            config = (ROOT / 'gradle/publishing.properties').read_text()
            (root / 'gradle/publishing.properties').write_text(config)
            with self.assertRaisesRegex(ValueError, 'release notes missing'):
                PUBLISH['publication_plan'](root)
            (root / 'docs/wiki').mkdir(parents=True)
            (root / 'docs/wiki/release-notes.md').write_text('## 1.0.3\n\n## Next\n')
            with self.assertRaisesRegex(ValueError, 'nonempty release-notes'):
                PUBLISH['publication_plan'](root)


if __name__ == '__main__':
    unittest.main()
