#!/usr/bin/env python3
"""Offline config/draft regressions; real Minecraft signatures are checked by the matrix."""
import os
from pathlib import Path
import runpy
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
api = runpy.run_path(str(ROOT / 'build-smart.py'))
candidates = sorted(api['candidate_jdks'](ROOT), key=lambda row: (row[0] != 17, row[0]))
home = next((home for major, home in candidates if major >= 17 and
             (home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')).is_file()), None)
if home is None:
    raise SystemExit('Java 17+ JDK required for the offline config checks')
with tempfile.TemporaryDirectory(prefix='structurevoidable-config-checks-') as directory:
    output = Path(directory)
    sources = sorted((ROOT / 'common/src/main/java/net/vg/structurevoidable/config').glob('*.java'))
    sources += sorted((ROOT / 'tests/java').glob('*Config*Checks.java'))
    # Compile fixtures cover only the Platform, Pair and logger calls used here.
    # No user's config directory or Minecraft runtime is accessed by these tests.
    fixtures = {
        'Platform.java': 'package dev.architectury.platform; public final class Platform { '
                         'public static java.nio.file.Path getConfigFolder(){return java.nio.file.Path.of(System.getProperty("structurevoidable.test.configDir"));} '
                         'public static Mod getMod(String id){return new Mod();} '
                         'public static class Mod{public String getVersion(){return "1.0.3";}}}',
        'Constants.java': 'package net.vg.structurevoidable; public final class Constants { '
                          'public static final String MOD_ID="structurevoidable", MOD_NAME="Structure Voidable", MOD_VERSION="1.0.3"; '
                          'static { if(!Boolean.getBoolean("structurevoidable.test.loaderReady")) '
                          'throw new IllegalStateException("Loader metadata accessed before mod init"); } '
                          'public static final Log LOGGER=new Log(); public static class Log { '
                          'public void info(String s,Object... args){} public void debug(String s,Object... args){} '
                          'public void warn(String s,Object... args){} public void error(String s,Object... args){} }}',
        'Pair.java': 'package com.mojang.datafixers.util; public record Pair<F,S>(F first,S second) { '
                     'public F getFirst(){return first;} public S getSecond(){return second;} '
                     'public static <F,S> Pair<F,S> of(F f,S s){return new Pair<>(f,s);}}',
    }
    for filename, text in fixtures.items():
        path = output / filename
        path.write_text(text, encoding='utf-8')
        sources.append(path)
    subprocess.run([str(home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')),
                    '--release', '17', '-encoding', 'UTF-8', '-d', str(output), *map(str, sources)], check=True)
    for name in ('ConfigStorageChecks', 'net.vg.structurevoidable.config.StructureVoidConfigChecks'):
        subprocess.run([str(api['java_binary'](home)), '-Dstructurevoidable.test.configDir=' + str(output / 'config'), '-cp', str(output), name,
                        str(output), str(ROOT / 'tests/config')], check=True)
