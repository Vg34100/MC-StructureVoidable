#!/usr/bin/env python3
"""Deterministic setup safety tests; synthetic credentials, no downloads/uploads/clients."""
from contextlib import redirect_stdout
import hashlib
import io
import os
from pathlib import Path
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
WRAPPER = runpy.run_path(str(ROOT / "build-smart.py"))
SETUP = runpy.run_path(str(ROOT / "scripts/setup-environment.py"))
SMOKE = runpy.run_path(str(ROOT / "scripts/smoke-release-client.py"))
GLOBALS = SETUP["doctor"].__globals__


class SetupSafety(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="sagittary-setup-test-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True, capture_output=True)
        names = ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
                 "settings.gradle", "stonecutter.gradle", "build.matrix.gradle", "gradle.properties",
                 "gradle/source-compat.gradle", "gradle/resource-compat.gradle", "gradle/dev-integrations.gradle",
                 "scripts/verify-matrix-artifacts.py", "scripts/publish-release.py")
        for name in names:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("", encoding="utf-8")
        for name in ("build-smart.py", "scripts/smoke-release-client.py"):
            (self.root / name).write_text((ROOT / name).read_text(encoding="utf-8"), encoding="utf-8")
        (self.root / "build.matrix.gradle").write_text("ClientProductionRunTask runReleaseClient\n", encoding="utf-8")
        (self.root / ".env.example").write_text("MODRINTH_TOKEN=\nCURSEFORGE_TOKEN=\n", encoding="utf-8")
        (self.root / ".gitignore").write_text(".env\n.gradle/\nbuild/\nruns/\nrun/\nversions/\n__pycache__/\n", encoding="utf-8")
        (self.root / "gradle/matrix").mkdir()
        for name, java, generation in (("1.21.1-fabric", 21, "legacy"), ("26.2-neoforge", 25, "current")):
            version, loader = name.rsplit("-", 1)
            (self.root / f"gradle/matrix/{name}.properties").write_text(
                f"minecraft_version={version}\nloader={loader}\njava_version={java}\nloom_generation={generation}\n", encoding="utf-8")
        self.api = dict(WRAPPER)
        self.api["is_wsl"] = lambda: True

    def ready_doctor(self):
        return SETUP["Report"]()

    def bootstrap(self):
        output = io.StringIO()
        with patch.dict(GLOBALS, {"doctor": lambda *args: self.ready_doctor(),
                                 "java_checks": lambda *args: None,
                                 "portablemc_state": lambda *args: ("valid", self.root / "cached.exe")}), redirect_stdout(output):
            code = SETUP["bootstrap"](self.root, self.api)
        return code, output.getvalue()

    def doctor(self, **overrides):
        mocks = {"java_checks": lambda project, api, report: report.add("Java", "PASS", "mocked native JDKs", "development"),
                 "native_temp_probe": lambda api: None,
                 "portablemc_state": lambda *args: ("valid", self.root / "cached.exe"),
                 "mcp_configured": lambda root: False,
                 "credential_status": lambda root: {key: False for key in SETUP["TOKENS"]}}
        mocks.update(overrides)
        output = io.StringIO()
        with patch.dict(GLOBALS, mocks), patch.dict(os.environ, {"DISPLAY": ":synthetic"}), redirect_stdout(output):
            report = SETUP["doctor"](self.root, self.api)
            code = report.show()
        return report, code, output.getvalue()

    def test_absent_env_bootstrap_copies_empty_template(self):
        code, output = self.bootstrap()
        self.assertEqual(code, 0)
        self.assertEqual((self.root / ".env").read_bytes(), (self.root / ".env.example").read_bytes())
        self.assertTrue(SETUP["ignored"](self.root, ".env"))
        self.assertIn("CREATED ignored .env", output)

    def test_populated_env_is_never_rewritten_or_exposed(self):
        content = b"MODRINTH_TOKEN=synthetic-secret-mr\nCURSEFORGE_TOKEN=synthetic-secret-cf\n"
        env = self.root / ".env"
        env.write_bytes(content)
        before = env.stat().st_mtime_ns
        code, output = self.bootstrap()
        self.assertEqual(code, 0)
        self.assertEqual(env.read_bytes(), content)
        self.assertEqual(env.stat().st_mtime_ns, before)
        self.assertNotIn("synthetic-secret", output)

    def test_tracked_env_fails_without_reading_secrets(self):
        (self.root / ".env").write_text("MODRINTH_TOKEN=synthetic-secret\n", encoding="utf-8")
        subprocess.run(["git", "add", "-f", ".env"], cwd=self.root, check=True, capture_output=True)
        def forbidden(root):
            raise AssertionError("must not load a tracked secret")
        report, code, output = self.doctor(credential_status=forbidden)
        self.assertFalse(report.development)
        self.assertEqual(code, 2)
        self.assertIn("STOP: tracked secret file", output)
        self.assertNotIn("synthetic-secret", output)
        before = {p.relative_to(self.root) for p in self.root.rglob("*") if ".git" not in p.parts}
        with redirect_stdout(io.StringIO()):
            self.assertEqual(SETUP["bootstrap"](self.root, self.api), 2)
        self.assertEqual(before, {p.relative_to(self.root) for p in self.root.rglob("*") if ".git" not in p.parts})

    def test_missing_sibling_is_optional(self):
        _, code, output = self.doctor(spelunkery_root=lambda *args: (self.root / "missing-sibling", False))
        self.assertEqual(code, 0)
        self.assertIn("WARN sibling Spelunkery repository not found", output)

    def test_missing_credentials_do_not_fail_development(self):
        report, code, output = self.doctor()
        self.assertTrue(report.development)
        self.assertEqual(code, 0)
        self.assertIn("WARN MODRINTH_TOKEN missing", output)
        self.assertIn("Real publishing: NOT READY", output)

    def test_missing_required_java_fails_development(self):
        self.api["select_gradle_java"] = lambda *args: (None, "missing current JDK", True)
        self.api["configured_java_home"] = lambda root: (None, None)
        self.api["candidate_jdks"] = lambda root: [(21, self.root / "jdk21")]
        with patch.dict(GLOBALS, {"probe_jdk": lambda home: 21}):
            _, code, output = self.doctor(java_checks=SETUP["java_checks"])
        self.assertEqual(code, 2)
        self.assertIn("Java 25", output)
        self.assertIn("bootstrap cannot install Java", output)

    def test_missing_mcp_is_only_a_warning(self):
        _, code, output = self.doctor()
        self.assertEqual(code, 0)
        self.assertIn("WARN minecraft-dev MCP could not be confirmed", output)

    def test_bootstrap_second_run_is_idempotent(self):
        self.assertEqual(self.bootstrap()[0], 0)
        before = {p.relative_to(self.root): p.stat().st_mtime_ns for p in self.root.rglob("*") if ".git" not in p.parts}
        code, output = self.bootstrap()
        self.assertEqual(code, 0)
        self.assertIn("no changes required", output)
        self.assertEqual(before, {p.relative_to(self.root): p.stat().st_mtime_ns for p in self.root.rglob("*") if ".git" not in p.parts})

    def test_credential_probe_returns_only_booleans_and_never_changes_parent(self):
        (self.root / ".env").write_text("MODRINTH_TOKEN=synthetic-mr-only\nCURSEFORGE_TOKEN='synthetic-cf-only'\n", encoding="utf-8")
        with patch.dict(os.environ, {"PATH": os.defpath}, clear=True):
            statuses = SETUP["credential_status"](self.root)
            self.assertEqual(statuses, {"MODRINTH_TOKEN": True, "CURSEFORGE_TOKEN": True})
            self.assertNotIn("MODRINTH_TOKEN", os.environ)
            self.assertNotIn("CURSEFORGE_TOKEN", os.environ)
            _, code, output = self.doctor(credential_status=lambda root: statuses)
        self.assertEqual(code, 0)
        self.assertNotIn("synthetic-mr-only", output)
        self.assertNotIn("synthetic-cf-only", output)

    def test_doctor_does_not_write_repository_or_download(self):
        before = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob("*") if p.is_file() and ".git" not in p.parts}
        with patch("urllib.request.urlopen", side_effect=AssertionError("doctor must not download")):
            _, code, _ = self.doctor(portablemc_state=lambda *args: ("missing", self.root / "cached.exe"))
        self.assertEqual(code, 0)
        self.assertEqual(before, {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob("*") if p.is_file() and ".git" not in p.parts})

    def test_personal_path_detection_is_bounded_to_active_files(self):
        self.assertEqual(SETUP["machine_paths"](self.root), [])
        old = self.root / "docs/development/history/old.md"
        old.parent.mkdir(parents=True)
        personal = "/home/" + "synthetic-person" + "/jdk"
        old.write_text(personal, encoding="utf-8")
        self.assertEqual(SETUP["machine_paths"](self.root), [])
        (self.root / "gradle.properties").write_text("org.gradle.java.home=" + personal, encoding="utf-8")
        self.assertEqual(SETUP["machine_paths"](self.root), ["gradle.properties"])

    def test_no_personal_paths_written_by_bootstrap(self):
        self.assertEqual(self.bootstrap()[0], 0)
        self.assertEqual(SETUP["machine_paths"](self.root), [])
        for path in (self.root / ".env", self.root / ".env.example"):
            self.assertNotIn(str(self.root), path.read_text(encoding="utf-8"))

    def test_unignored_env_prevents_bootstrap(self):
        ignore = self.root / ".gitignore"
        ignore.write_text(ignore.read_text(encoding="utf-8").replace(".env\n", ""), encoding="utf-8")
        with redirect_stdout(io.StringIO()):
            self.assertEqual(SETUP["bootstrap"](self.root, self.api), 2)
        self.assertFalse((self.root / ".env").exists())

    def test_malformed_or_misnamed_matrix_fails(self):
        path = self.root / "gradle/matrix/26.2-neoforge.properties"
        path.write_text(path.read_text(encoding="utf-8") + "loader=fabric\n", encoding="utf-8")
        self.assertTrue(SETUP["matrix_problems"](self.root, self.api))

    def test_project_and_user_mcp_configuration_detected(self):
        folder = self.root / ".codex"
        folder.mkdir()
        config = folder / "config.toml"
        config.write_text('[mcp_servers.minecraft-dev]\ncommand = "synthetic-tool"\n', encoding="utf-8")
        with patch.dict(os.environ, {"CODEX_HOME": str(self.root / "missing-codex")}):
            self.assertTrue(SETUP["mcp_configured"](self.root))
            config.write_text(config.read_text(encoding="utf-8") + "enabled = false\n", encoding="utf-8")
            self.assertFalse(SETUP["mcp_configured"](self.root))

    def test_temporary_probe_is_removed(self):
        self.api["is_wsl"] = lambda: False
        with patch("tempfile.tempdir", str(self.root)):
            SETUP["native_temp_probe"](self.api)
        self.assertFalse(list(self.root.glob("sagittary-doctor-*")))

    def test_portablemc_read_only_verifier_rejects_modified_binary(self):
        archive = self.root / "portablemc.zip"
        executable = self.root / "portablemc.exe"
        with ZipFile(archive, "w") as output:
            output.writestr("portablemc.exe", b"synthetic-binary")
        executable.write_bytes(b"synthetic-binary")
        smoke_globals = SMOKE["verify_portablemc"].__globals__
        with patch.dict(smoke_globals, {"PORTABLEMC_SHA256": hashlib.sha256(archive.read_bytes()).hexdigest()}), patch("subprocess.check_output", return_value="portablemc 5.0.5\n"):
            SMOKE["verify_portablemc"](executable)
            executable.write_bytes(b"modified")
            with self.assertRaisesRegex(RuntimeError, "differs from verified archive"):
                SMOKE["verify_portablemc"](executable)

    def test_native_only_machine_can_develop_without_neoforge_backend(self):
        self.api["is_wsl"] = lambda: False
        report, code, output = self.doctor(portablemc_state=lambda *args: ("unavailable", None))
        self.assertEqual(code, 0)
        self.assertFalse(report.release)
        self.assertIn("Development: READY", output)

    def test_unconfigured_legacy_path_java_selects_current_jdk(self):
        legacy, current = self.root / "native-legacy", self.root / "native-current"
        for home, version in ((legacy, 21), (current, 25)):
            (home / "bin").mkdir(parents=True)
            (home / "bin" / ("java.exe" if os.name == "nt" else "java")).write_text("", encoding="utf-8")
            (home / "release").write_text(f'JAVA_VERSION="{version}"\n', encoding="utf-8")
        wrapper_globals = WRAPPER["select_gradle_java"].__globals__
        with patch.dict(os.environ, {"JAVA_HOME": str(legacy)}, clear=True), patch.dict(wrapper_globals, {
                "configured_java_home": lambda root: (None, None),
                "candidate_jdks": lambda root: [(21, legacy), (25, current)]}):
            selected, _, fatal = WRAPPER["select_gradle_java"](WRAPPER["Project"](self.root), [])
        self.assertFalse(fatal)
        self.assertEqual(selected, str(current))

    def test_bootstrap_missing_jdk_stops_before_files_or_downloads(self):
        def missing_java(project, api, report):
            report.add("Java", "FAIL", "missing required JDK", "development")
        with patch.dict(GLOBALS, {"java_checks": missing_java,
                                 "doctor": lambda *args: SETUP["Report"](),
                                 "portablemc_state": lambda *args: (_ for _ in ()).throw(AssertionError("no tool acquisition before Java"))}), redirect_stdout(io.StringIO()):
            SETUP["bootstrap"](self.root, self.api)
        self.assertFalse((self.root / ".env").exists())

    def test_explicit_toolchain_paths_use_existing_discovery(self):
        home = self.root / "nonstandard-jdk"
        (home / "bin").mkdir(parents=True)
        (home / "bin" / ("java.exe" if os.name == "nt" else "java")).write_text("", encoding="utf-8")
        (home / "release").write_text('JAVA_VERSION="25"\n', encoding="utf-8")
        (self.root / "gradle.properties").write_text("org.gradle.java.installations.paths=" + home.as_posix(), encoding="utf-8")
        with patch.dict(os.environ, {"GRADLE_USER_HOME": str(self.root / "gradle-user")}, clear=True):
            self.assertIn((25, home), WRAPPER["candidate_jdks"](self.root))


if __name__ == "__main__":
    unittest.main()
