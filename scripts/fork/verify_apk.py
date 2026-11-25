#!/usr/bin/env python3
"""Verify the actual APK package, signature permission, authorities, and component namespace."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys

expected = sys.argv[1]
output = Path("app/build/outputs/apk/debug")
metadata = json.loads((output / "output-metadata.json").read_text())
assert metadata["applicationId"] == expected, metadata["applicationId"]
assert len(metadata["elements"]) == 1
apk = output / metadata["elements"][0]["outputFile"]
assert apk.is_file(), apk
build_tools = Path(os.environ["ANDROID_HOME"]) / "build-tools"
versions = sorted(build_tools.glob("*/aapt2"), key=lambda path: [int(p) for p in re.findall(r"\d+", path.parent.name)])
assert versions, "aapt2 is required"
aapt2 = str(versions[-1])
badging = subprocess.check_output([aapt2, "dump", "badging", str(apk)], text=True)
match = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
assert match and match.group(1) == expected, badging[:200]
manifest = subprocess.check_output([aapt2, "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"], text=True)
for value in (
    f"{expected}.permission.CONTROL_ACCESSIBILITY_PROTECTION",
    f"{expected}.accessibility.health",
    f"{expected}.fileprovider",
    "io.github.mangi.eta.agent.runtime.AgentRuntimeService",
    "io.github.mangi.eta.agent.voice.EtaVoiceInteractionService",
    "io.github.mangi.eta.agent.accessibility.AgentAccessibilityService",
):
    assert f'"{value}"' in manifest, f"Missing manifest identity: {value}"
assert f'"{expected}.agent.voice.EtaVoiceInteractionService"' not in manifest or expected == "io.github.mangi.eta"
print(f"Verified package={expected}; component namespace unchanged; permission/authorities isolated; APK={apk}")
