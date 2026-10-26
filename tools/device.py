#!/usr/bin/env python3
"""Finite, root-free ADB setup and role control for CMC Helper."""
import argparse
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.haha5039.cmc"
PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["install", "prepare", "disable-exception", "receiver", "sender", "restore", "status"])
    parser.add_argument("--serial", help="ADB serial (required when more than one device is connected)")
    parser.add_argument("--keep-write-permission", action="store_true",
                        help="Keep WRITE_SECURE_SETTINGS for the in-app role buttons")
    args = parser.parse_args()
    adb = ["adb"] + (["-s", args.serial] if args.serial else [])

    def run(*parts, capture=False):
        result = subprocess.run(adb + list(parts), check=True, text=True,
                                capture_output=capture)
        return result.stdout if capture else ""

    if args.action == "install":
        run("install", "-r", str(ROOT / "cmc-helper.apk"))
        run("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
        return
    if args.action == "status":
        run("shell", "am", "instrument", "-w", "-e", "operation", "status",
            PACKAGE + "/.ControlInstrumentation")
        return
    if args.action in ("prepare", "disable-exception"):
        operation = "prepare" if args.action == "prepare" else "disable"
        output = run("shell", "am", "instrument", "-w", "--no-hidden-api-checks", "-e", "operation", operation,
                     PACKAGE + "/.PrepareInstrumentation", capture=True)
        print(output)
        expected = "PREPARED:" if args.action == "prepare" else "EXCEPTION_DISABLED:"
        if expected not in output or "PREPARATION_FAILED:" in output:
            raise RuntimeError("CMC preparation failed; no role was applied.")
        return
    # Prefer zero continuously granted permissions. Keep only when requested.
    prior = run("shell", "dumpsys", "package", PACKAGE, capture=True)
    was_granted = bool(re.search(r"android\.permission\.WRITE_SECURE_SETTINGS: granted=true", prior))
    run("shell", "pm", "grant", PACKAGE, PERMISSION)
    try:
        output = run("shell", "am", "instrument", "-w", "-e", "operation", args.action,
                     PACKAGE + "/.ControlInstrumentation", capture=True)
        print(output)
        if "CONTROL_OK:" not in output or "CONTROL_FAILED:" in output:
            raise RuntimeError("Role operation failed. Read the result above.")
    finally:
        if not args.keep_write_permission and not was_granted:
            run("shell", "pm", "revoke", PACKAGE, PERMISSION)
    print("WRITE_SECURE_SETTINGS: " + ("retained" if args.keep_write_permission or was_granted
                                        else "revoked after operation"))


if __name__ == "__main__":
    try:
        main()
    except (subprocess.CalledProcessError, RuntimeError) as error:
        print("Failed: " + str(error), file=sys.stderr)
        sys.exit(1)
