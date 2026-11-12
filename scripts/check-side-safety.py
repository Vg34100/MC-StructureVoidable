#!/usr/bin/env python3
"""Reject client references from side-neutral StructureVoidable bytecode, before runtime."""
from pathlib import Path
import re
import runpy
import struct
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
CLIENT = re.compile(r'(?:^|L)(?:net/minecraft/client/|com/mojang/blaze3d/|'
                    r'net/neoforged/neoforge/client/|net/minecraftforge/client/|'
                    r'net/fabricmc/api/ClientModInitializer|net/fabricmc/fabric/api/client/|'
                    r'com/terraformersmc/modmenu/|dev/architectury/registry/client/|dev/architectury/event/events/client/)')


# Existing public class/package names are retained; these are physical-client roots.
CLIENT_ROOTS = ('client/', 'render/', 'util/ModKeyMaps', 'StructureVoidableClient',
                'mixin/ClientLevelMixin', 'mixin/option/', 'fabric/client/',
                'fabric/modmenu/', 'forge/client/', 'neoforge/client/')


def constants(data):
    """Read JVM UTF-8 constants, including descriptors and annotation signatures."""
    if len(data) < 10 or data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('Invalid class file')
    count = struct.unpack_from('>H', data, 8)[0]
    index, offset, strings = 1, 10, []
    sizes = {3:4, 4:4, 5:8, 6:8, 7:2, 8:2, 9:4, 10:4, 11:4, 12:4,
             15:3, 16:2, 17:4, 18:4, 19:2, 20:2}
    while index < count:
        if offset >= len(data):
            raise ValueError('Truncated class constant pool')
        tag = data[offset]
        offset += 1
        if tag == 1:
            if offset + 2 > len(data):
                raise ValueError('Truncated UTF-8 constant')
            length = struct.unpack_from('>H', data, offset)[0]
            offset += 2
            strings.append(data[offset:offset+length].decode('utf-8', errors='replace'))
            offset += length
        elif tag in sizes:
            offset += sizes[tag]
            if tag in (5, 6):
                index += 1
        else:
            raise ValueError('Unknown class constant tag: ' + str(tag))
        if offset > len(data):
            raise ValueError('Truncated class constant pool')
        index += 1
    return strings


def check_jar(jar, package):
    for name in jar.namelist():
        if (not name.startswith(package) or not name.endswith('.class')
                or any(name.startswith(package + path) for path in CLIENT_ROOTS)):
            continue
        for value in constants(jar.read(name)):
            if CLIENT.search(value):
                raise ValueError(f'Client-only reference in server-safe class {name}: {value}')
            # A client bootstrap must itself live in client/. This prevents a
            # guarded call from introducing eager verifier/linker dependencies.
            if any(re.search(r'(?:^|L)' + re.escape(package + path), value) for path in CLIENT_ROOTS):
                raise ValueError(f'Client bootstrap reference in shared class {name}: {value}')


def main():
    verifier = runpy.run_path(str(ROOT / 'scripts/verify-matrix-artifacts.py'))
    for matrix in sorted((ROOT / 'gradle/matrix').glob('*.properties')):
        artifact = verifier['release_jar'](matrix)
        with ZipFile(artifact) as jar:
            check_jar(jar, 'net/vg/structurevoidable/')
        print('SIDE SAFETY PASS ' + matrix.stem)


if __name__ == '__main__':
    main()
