#!/usr/bin/env python3
"""Set versionCode/versionName in app/build.gradle.kts for a release."""

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEFAULT_BUILD_FILE = ROOT / "app" / "build.gradle.kts"
VERSION_RE = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
NAME_RE = re.compile(r"^([0-9]+\.[0-9]+\.[0-9]+)(?:-.*)?$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Bump versionCode and set versionName for the next release.")
    parser.add_argument("version", help="target version, e.g. 0.4.0 or v0.4.0")
    parser.add_argument("--build-file", type=pathlib.Path,
                        default=DEFAULT_BUILD_FILE)
    parser.add_argument("--allow-same", action="store_true",
                        help="accept a versionName that already equals the target")
    return parser.parse_args()


def read_current(text: str) -> tuple[int, str]:
    code = re.search(r"^(\s*)versionCode\s*=\s*(\d+)\s*$", text, re.MULTILINE)
    name = re.search(r'^(\s*)versionName\s*=\s*"([^"]*)"\s*$', text, re.MULTILINE)
    if code is None or name is None:
        raise SystemExit("could not find versionCode/versionName in build file")
    return int(code.group(2)), name.group(2)


def main() -> None:
    args = parse_args()
    version = args.version.strip()
    if version.startswith("v"):
        version = version[1:]
    if not VERSION_RE.fullmatch(version):
        raise SystemExit(f"invalid version {args.version!r}; expected form 0.4.0")
    if not args.build_file.is_file():
        raise SystemExit(f"build file not found: {args.build_file}")

    text = args.build_file.read_text(encoding="utf-8")
    old_code, old_name = read_current(text)
    old_match = NAME_RE.fullmatch(old_name)
    if old_match is None:
        raise SystemExit(f"unexpected versionName {old_name!r}")
    old_version = old_match.group(1)

    if version == old_version:
        if not args.allow_same:
            raise SystemExit(
                f"already at {old_name}; pass --allow-same to keep it")
        new_code, new_name = old_code, old_name
    else:
        old_parts = tuple(int(part) for part in old_version.split("."))
        new_parts = tuple(int(part) for part in version.split("."))
        if new_parts <= old_parts:
            raise SystemExit(
                f"target {version} is not newer than current {old_version}")
        new_code, new_name = old_code + 1, f"{version}-mvp"

    updated = re.sub(r"^(\s*)versionCode\s*=\s*\d+\s*$",
                     rf"\g<1>versionCode = {new_code}",
                     text, count=1, flags=re.MULTILINE)
    updated = re.sub(r'^(\s*)versionName\s*=\s*"[^"]*"\s*$',
                     rf'\g<1>versionName = "{new_name}"',
                     updated, count=1, flags=re.MULTILINE)

    if updated != text:
        args.build_file.write_text(updated, encoding="utf-8")
        print(f"versionCode {old_code} -> {new_code}")
        print(f"versionName {old_name} -> {new_name}")
    else:
        print(f"versionCode {old_code}, versionName {old_name} (unchanged)")


if __name__ == "__main__":
    try:
        main()
    except BrokenPipeError:
        sys.exit(1)
