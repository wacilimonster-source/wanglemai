"""Package source + docs + distributable APK, excluding local machine state."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parent.parent
destination = root / "wanglemai-design-handoff-v1.1.zip"
files = ["README.md", ".gitignore", "settings.gradle.kts", "build.gradle.kts",
         "gradle.properties", "gradlew", "gradlew.bat", "package.json", "package-lock.json"]
directories = ["app", "gradle", "docs", "prototype", "tests", "scripts", "releases"]
excluded = {"build", ".gradle", "node_modules", "__pycache__"}
with ZipFile(destination, "w", ZIP_DEFLATED, compresslevel=6) as archive:
    for name in files:
        archive.write(root / name, name)
    for name in directories:
        for file in sorted((root / name).rglob("*")):
            relative = file.relative_to(root)
            if file.is_file() and not excluded.intersection(relative.parts):
                archive.write(file, relative.as_posix())
with ZipFile(destination) as archive:
    assert archive.testzip() is None
    assert "app/src/main/AndroidManifest.xml" in archive.namelist()
    assert "gradle/wrapper/gradle-wrapper.jar" in archive.namelist()
    assert "releases/wanglemai-0.1.0-debug.apk" in archive.namelist()
    assert not any("local.properties" == n or "/build/" in n for n in archive.namelist())
    print(f"{destination} | {len(archive.namelist())} files | {destination.stat().st_size} bytes")
