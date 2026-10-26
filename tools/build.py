#!/usr/bin/env python3
"""Build the framework-only app and optional parent instrumentation; never uses ADB."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JDK = Path(os.environ.get("JAVA_HOME") or os.environ["JAVA_HOME"])
SDK = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.environ["ANDROID_HOME"])
BUILD_TOOLS = SDK / "build-tools/36.0.0"
ANDROID_JAR = SDK / "platforms/android-36/android.jar"
SIGNER = Path(os.environ.get("CMC_SIGNING_HELPER") or os.environ["CMC_SIGNING_HELPER"])
CERTIFICATE = "fac61745dc0903786fb9ede62a962b399f7348f0bb6f899b8332667591033b9c"


def run(command, env, *, private=False):
    result = subprocess.run([str(part) for part in command], env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if result.returncode:
        if not private and result.stdout:
            print(result.stdout, file=sys.stderr)
        # The signing helper's configuration and output are deliberately not inspected.
        raise RuntimeError(f"{Path(str(command[0])).name} failed (exit {result.returncode})")
    if not private and result.stdout.strip():
        print(result.stdout.strip())
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "cmc-helper.apk")
    parser.add_argument("--work-dir", type=Path,
                        default=ROOT.parent.parent / "work/cmc-build")
    parser.add_argument("--extra-java", type=Path, action="append", default=[],
                        help="Additional Java source file/directory; repeat as needed")
    args = parser.parse_args()
    for required in (JDK / "bin/javac", ANDROID_JAR, SIGNER,
                     *(BUILD_TOOLS / name for name in ("aapt2", "d8", "zipalign", "apksigner"))):
        if not required.is_file():
            raise RuntimeError(f"Required build/signing tool unavailable: {required}")
    env = os.environ.copy()
    env["JAVA_HOME"] = str(JDK)
    env["PATH"] = str(JDK / "bin") + os.pathsep + env.get("PATH", "")
    args.work_dir.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="run-", dir=args.work_dir))
    print(f"Build directory: {work}", flush=True)
    generated, classes, dex = (work / name for name in ("generated", "classes", "dex"))
    for directory in (generated, classes, dex):
        directory.mkdir()
    resource_zip = work / "resources.zip"
    run([BUILD_TOOLS / "aapt2", "compile", "--dir", ROOT / "app/res", "-o", resource_zip], env)
    resources = work / "resources.apk"
    run([BUILD_TOOLS / "aapt2", "link", "-o", resources, "-I", ANDROID_JAR,
         "--manifest", ROOT / "app/AndroidManifest.xml", "--min-sdk-version", "33",
         "--target-sdk-version", "36", "--java", generated, resource_zip], env)

    # Parent-owned setup and tests are compiled recursively if present. EXTRA accepts
    # os.pathsep-separated additional source roots, as does repeated --extra-java.
    source_roots = [ROOT / "app", ROOT / "app-preparation", ROOT / "tests", generated]
    extras = args.extra_java + [Path(path) for path in os.environ.get("EXTRA", "").split(os.pathsep) if path]
    for path in extras:
        if not path.exists():
            raise RuntimeError(f"Extra Java source root does not exist: {path}")
    sources = set()
    for path in source_roots + extras:
        if path.is_file() and path.suffix == ".java":
            sources.add(path.resolve())
        elif path.is_dir():
            sources.update(source.resolve() for source in path.rglob("*.java"))
    if not sources:
        raise RuntimeError("No Java sources found")
    # An argument file handles spaces in user paths and large parent test suites.
    source_list = work / "sources.txt"
    source_list.write_text("\n".join('"' + str(path).replace('\\', '\\\\').replace('"', '\\"') + '"'
                                      for path in sorted(sources)), encoding="utf-8")
    print(f"Compiling {len(sources)} Java sources (including app-preparation/tests when present)", flush=True)
    run([JDK / "bin/javac", "-encoding", "UTF-8", "--release", "8",
         "-classpath", ANDROID_JAR, "-d", classes, "@" + str(source_list)], env)
    class_jar = work / "classes.jar"
    with zipfile.ZipFile(class_jar, "w", zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(classes.rglob("*.class")):
            jar.write(path, path.relative_to(classes).as_posix())
    run([BUILD_TOOLS / "d8", "--release", "--min-api", "33", "--lib", ANDROID_JAR,
         "--output", dex, class_jar], env)
    unaligned = work / "unaligned.apk"
    shutil.copyfile(resources, unaligned)
    with zipfile.ZipFile(unaligned, "a", zipfile.ZIP_DEFLATED) as apk:
        for path in sorted(dex.glob("*.dex")):
            apk.write(path, path.name)
    unsigned = work / "aligned-unsigned.apk"
    run([BUILD_TOOLS / "zipalign", "-p", "-f", "4", unaligned, unsigned], env)
    signed = work / "cmc-helper.apk"
    run([sys.executable, SIGNER, "--input", unsigned, "--output", signed], env, private=True)
    verification = run([BUILD_TOOLS / "apksigner", "verify", "--verbose", "--print-certs", signed],
                       env, private=True)
    digests = re.findall(r"Signer #\d+ certificate SHA-256 digest:\s*([a-fA-F0-9:]+)", verification)
    if len(digests) != 1 or digests[0].replace(":", "").lower() != CERTIFICATE:
        signed.unlink(missing_ok=True)
        raise RuntimeError("Required signing certificate mismatch or unverifiable; no APK published")
    run([BUILD_TOOLS / "zipalign", "-c", "-p", "4", signed], env)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    os.replace(signed, args.output)
    print(f"Verified certificate SHA-256: {CERTIFICATE}")
    print(f"Signed APK: {args.output.resolve()}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError) as error:
        print(f"Build failed: {error}", file=sys.stderr)
        sys.exit(1)
