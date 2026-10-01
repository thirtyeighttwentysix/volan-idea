"""Validate release metadata and assets using only the Python standard library."""
import argparse
import hashlib
from pathlib import Path
import re
import zipfile
import io
import xml.etree.ElementTree as ET

TAG = re.compile(r"v(\d+\.\d+\.\d+(?:-(?:alpha|beta|rc)\.\d+)?)")


def validate(tag: str) -> str:
    match = TAG.fullmatch(tag)
    if not match:
        raise SystemExit("Expected vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-beta.N/alpha.N/rc.N")
    version = match[1]
    props = dict(line.split("=", 1) for line in Path("gradle.properties").read_text().splitlines()
                 if "=" in line and not line.startswith("#"))
    if props.get("pluginVersion") != version:
        raise SystemExit("Tag must match pluginVersion in gradle.properties")
    if f"## [{version}]" not in Path("CHANGELOG.md").read_text():
        raise SystemExit("Missing changelog entry for release")
    if f"<h3>{version}</h3>" not in Path("CHANGE_NOTES.html").read_text():
        raise SystemExit("CHANGE_NOTES.html must describe the released version")
    return version


def check_asset(tag: str, folder: Path) -> None:
    version = validate(tag)
    asset = folder / f"volan-idea-{version}-signed.zip"
    sums = folder / "SHA256SUMS"
    expected = dict(line.split(None, 1)[::-1] for line in sums.read_text().splitlines())
    if expected.get(asset.name) != hashlib.sha256(asset.read_bytes()).hexdigest():
        raise SystemExit("Release asset SHA256 mismatch")
    with zipfile.ZipFile(asset) as archive:
        descriptors = []
        for name in archive.namelist():
            if name.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as jar:
                    if "META-INF/plugin.xml" in jar.namelist():
                        descriptors.append(ET.fromstring(jar.read("META-INF/plugin.xml")))
    if len(descriptors) != 1:
        raise SystemExit("Expected exactly one plugin descriptor")
    descriptor = descriptors[0]
    if descriptor.findtext("version") != version or descriptor.findtext("id") != "io.github.thirtyeighttwentysix.volan.idea":
        raise SystemExit("Release archive plugin identity/version mismatch")
    print(f"Verified {asset.name}: checksum and plugin descriptor")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["validate", "notes", "checksums", "check-asset"])
    parser.add_argument("--tag", required=True)
    parser.add_argument("--directory", type=Path, default=Path("build/distributions"))
    args = parser.parse_args()
    version = validate(args.tag)
    if args.command == "validate":
        print(version)
    elif args.command == "notes":
        text = Path("CHANGELOG.md").read_text()
        section = text.split(f"## [{version}]", 1)[1].split("\n## ", 1)[0]
        body = section.split("\n", 1)[1].strip()
        Path("build").mkdir(exist_ok=True)
        Path("build/release-notes.md").write_text(body + "\n\nInstall the signed ZIP with Settings → Plugins → Install Plugin from Disk.\n", encoding="utf-8")
    elif args.command == "checksums":
        archives = sorted(args.directory.glob(f"volan-idea-{version}*.zip"))
        if not archives or not any(p.name.endswith("-signed.zip") for p in archives):
            raise SystemExit("Missing signed distribution")
        (args.directory / "SHA256SUMS").write_text("".join(
            f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in archives), encoding="utf-8")
    else:
        check_asset(args.tag, args.directory)


if __name__ == "__main__":
    main()
