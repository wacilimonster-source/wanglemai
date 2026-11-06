#!/usr/bin/env python3
"""Write update/latest.json for a published GitHub tag and local APK."""

import argparse
import datetime
import hashlib
import json
import pathlib
import re

REPO = "wacilimonster-source/wanglemai"
TAG_RE = re.compile(r"^[A-Za-z0-9._-]{1,64}$")


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("tag", help="release tag, e.g. v0.2.1")
    parser.add_argument("apk", type=pathlib.Path)
    parser.add_argument("--release-name", default=None)
    parser.add_argument("--output", type=pathlib.Path,
                        default=pathlib.Path("update/latest.json"))
    args = parser.parse_args()

    tag = args.tag.strip()
    if not TAG_RE.fullmatch(tag):
        raise SystemExit("tag contains unsafe characters")
    apk = args.apk
    if not apk.is_file() or apk.suffix.lower() != ".apk":
        raise SystemExit("APK not found")
    name = apk.name
    if not re.fullmatch(r"[A-Za-z0-9._-]+\.apk", name):
        raise SystemExit("APK name contains unsafe characters")

    release_url = f"https://github.com/{REPO}/releases/tag/{tag}"
    apk_url = f"https://github.com/{REPO}/releases/download/{tag}/{name}"
    mirror_url = f"https://cdn.jsdelivr.net/gh/{REPO}@{tag}/releases/{name}"
    manifest = {
        "schemaVersion": 1,
        "version": tag,
        "releaseName": args.release_name or f"忘了没 {tag}",
        "releaseUrl": release_url,
        "apkUrl": apk_url,
        "apkMirrorUrl": mirror_url,
        "sha256": sha256(apk),
        "publishedAt": datetime.datetime.now(datetime.timezone.utc)
            .replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")
    print(f"wrote {args.output} for {tag} ({name}, {manifest['sha256']})")


if __name__ == "__main__":
    main()
