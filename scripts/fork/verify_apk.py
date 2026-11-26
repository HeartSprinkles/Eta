#!/usr/bin/env python3
"""Verify actual fork APK identities without changing the implementation namespace."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess


INSTALLATION_PACKAGES = ("io.github.mangi.etafork", "io.github.mangi.etafork.debug")
NAMESPACE = "io.github.mangi.eta"
COMPONENT_CLASSES = (
    "EtaApp",
    "ui.MainActivity",
    "ui.screens.browser.AgentBrowserActivity",
    "agent.voice.EtaVoiceAssistActivity",
    "agent.voice.EtaVoiceInteractionService",
    "agent.voice.EtaVoiceInteractionSessionService",
    "agent.voice.EtaRecognitionService",
    "agent.voice.EtaAssistantOverlayService",
    "agent.voice.ReadAloudPlaybackService",
    "agent.runtime.AgentRuntimeService",
    "agent.runtime.AgentExecutionService",
    "agent.accessibility.AgentAccessibilityService",
    "agent.device.AgentNotificationHistoryService",
    "agent.accessibility.AgentAccessibilityHealthProvider",
    "agent.scheduler.ScheduledTaskAlarmReceiver",
    "agent.scheduler.ScheduledTaskSystemReceiver",
    "agent.imagegen.EtaFileProvider",
)
VOICE_METADATA_CLASSES = (
    "agent.voice.EtaRecognitionService",
    "agent.voice.EtaVoiceInteractionSessionService",
    "ui.MainActivity",
)


def _is_signature_protection_level(permission_block: str) -> bool:
    """Read aapt2's protectionLevel line, accepting its typed value encodings."""
    attribute = re.compile(
        r"^\s*A:\s*(?:\S*/)?android:protectionLevel(?:\s*\([^)]*\))?\s*=\s*(.*?)\s*$"
    )
    values = [
        match.group(1)
        for line in permission_block.splitlines()
        if (match := attribute.match(line)) is not None
    ]
    if len(values) != 1:
        return False

    value = values[0].strip()
    # xmltree can append the source spelling after the encoded value.
    value = re.sub(r'\s+\(Raw:\s*"[^"]*"\)\s*$', "", value).strip()
    # Type labels vary across aapt2 versions. They identify the binary value's
    # type, not Android's protection level, so remove any leading annotation.
    while True:
        typed_value = re.match(r"^\(type\s+[^)]*\)\s*", value, re.IGNORECASE)
        if typed_value is None:
            break
        value = value[typed_value.end():].strip()

    if value.startswith('"') and value.endswith('"'):
        return value[1:-1] == "signature"
    if value == "signature":
        return True
    if re.fullmatch(r"(?:0[xX][0-9a-fA-F]+|[0-9]+)", value):
        encoded = int(value, 16) if value.lower().startswith("0x") else int(value, 10)
        # Compare the entire value, not just the base protection bits: reject
        # signatureOrSystem (3) and flags such as privileged or development.
        return encoded == 0x2
    return False


def verify_manifest(manifest: str, expected: str) -> None:
    permission = f"{expected}.permission.CONTROL_ACCESSIBILITY_PROTECTION"
    for value in (
        permission,
        f"{expected}.accessibility.health",
        f"{expected}.fileprovider",
        *(f"{NAMESPACE}.{name}" for name in COMPONENT_CLASSES),
    ):
        assert f'"{value}"' in manifest, f"Missing manifest identity: {value}"
    for name in COMPONENT_CLASSES:
        wrong_class = f"{expected}.{name}"
        assert f'"{wrong_class}"' not in manifest, f"Installation ID used as class namespace: {wrong_class}"
    # Ensure both the declaration and request moved, and the protection stays signature-only.
    assert manifest.count(f'"{permission}" (Raw:') == 2, "Expected one permission declaration and one request"
    permission_blocks = re.findall(r"E: permission \(line=.*?(?=\n\s*E:|\Z)", manifest, re.DOTALL)
    permission_block = next((block for block in permission_blocks if f'"{permission}"' in block), None)
    assert permission_block, "Missing signature permission declaration"
    assert _is_signature_protection_level(permission_block), (
        f"Protection must be signature; aapt2 permission declaration:\n{permission_block}"
    )
    for other in (*INSTALLATION_PACKAGES, NAMESPACE, f"{NAMESPACE}.debug"):
        if other != expected:
            for suffix in ("permission.CONTROL_ACCESSIBILITY_PROTECTION", "accessibility.health", "fileprovider"):
                assert f'"{other}.{suffix}"' not in manifest, f"Leaked installation identity: {other}.{suffix}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("expected", choices=INSTALLATION_PACKAGES)
    parser.add_argument("build_type", nargs="?", choices=("debug", "release"), default="debug")
    args = parser.parse_args()
    output = Path("app/build/outputs/apk") / args.build_type
    metadata = json.loads((output / "output-metadata.json").read_text())
    assert metadata["applicationId"] == args.expected, metadata["applicationId"]
    assert len(metadata["elements"]) == 1
    apk = output / metadata["elements"][0]["outputFile"]
    assert apk.is_file(), apk
    build_tools = Path(os.environ["ANDROID_HOME"]) / "build-tools"
    versions = sorted(build_tools.glob("*/aapt2"), key=lambda path: [int(p) for p in re.findall(r"\d+", path.parent.name)])
    assert versions, "aapt2 is required"
    aapt2 = str(versions[-1])
    badging = subprocess.check_output([aapt2, "dump", "badging", str(apk)], text=True)
    match = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
    assert match and match.group(1) == args.expected, badging[:200]
    manifest = subprocess.check_output([aapt2, "dump", "xmltree", str(apk), "--file", "AndroidManifest.xml"], text=True)
    verify_manifest(manifest, args.expected)
    # Release resource optimization may shorten the ZIP path; resolve it from the table.
    resources = subprocess.check_output([aapt2, "dump", "resources", str(apk)], text=True)
    voice_resource = re.search(
        r"resource [^\n]*\bxml/voice_interaction_service\b[^\n]*\n\s*\([^\n]*?\) \(file\) (\S+)",
        resources,
    )
    assert voice_resource, "Missing compiled voice interaction XML resource"
    voice_metadata = subprocess.check_output([aapt2, "dump", "xmltree", str(apk), "--file", voice_resource.group(1)], text=True)
    for name in VOICE_METADATA_CLASSES:
        assert f'"{NAMESPACE}.{name}"' in voice_metadata, f"Missing voice metadata class: {name}"
    print(f"Verified {args.build_type} package={args.expected}; original component/voice namespace; signature permission/authorities isolated; APK={apk}")


if __name__ == "__main__":
    main()
