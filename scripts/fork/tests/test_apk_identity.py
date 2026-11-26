"""APK verifier regression fixtures; executed only on GitHub Actions."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_apk import COMPONENT_CLASSES, INSTALLATION_PACKAGES, NAMESPACE, verify_manifest


def manifest_for(package, protection_level="(type 0x10)0x2"):
    permission = f"{package}.permission.CONTROL_ACCESSIBILITY_PROTECTION"
    components = "\n".join(f'        A: android:name="{NAMESPACE}.{name}"' for name in COMPONENT_CLASSES)
    return f'''E: manifest (line=1)
  E: permission (line=10)
    A: android:name="{permission}" (Raw: "{permission}")
    A: android:protectionLevel(0x01010009)={protection_level}
  E: uses-permission (line=11)
    A: android:name="{permission}" (Raw: "{permission}")
  E: application (line=12)
{components}
    A: android:authorities="{package}.accessibility.health"
    A: android:authorities="{package}.fileprovider"
'''


class ApkIdentityVerifierTest(unittest.TestCase):
    def test_both_fork_packages_keep_original_component_classes(self):
        for package in INSTALLATION_PACKAGES:
            with self.subTest(package=package):
                verify_manifest(manifest_for(package), package)

    def test_rejects_missing_original_component(self):
        package = INSTALLATION_PACKAGES[0]
        manifest = manifest_for(package).replace(f'{NAMESPACE}.ui.MainActivity', f'{package}.ui.MainActivity')
        with self.assertRaisesRegex(AssertionError, "Missing manifest identity"):
            verify_manifest(manifest, package)

    def test_rejects_additional_component_under_installation_namespace(self):
        package = INSTALLATION_PACKAGES[1]
        manifest = manifest_for(package) + f'    A: android:name="{package}.agent.voice.EtaVoiceInteractionService"\n'
        with self.assertRaisesRegex(AssertionError, "Installation ID used as class namespace"):
            verify_manifest(manifest, package)

    def test_rejects_original_or_other_fork_package_authorities(self):
        package = INSTALLATION_PACKAGES[1]
        for other in (NAMESPACE, f'{NAMESPACE}.debug', INSTALLATION_PACKAGES[0]):
            with self.subTest(other=other):
                manifest = manifest_for(package) + f'    A: android:authorities="{other}.fileprovider"\n'
                with self.assertRaisesRegex(AssertionError, "Leaked installation identity"):
                    verify_manifest(manifest, package)

    def test_accepts_aapt2_integer_encodings_of_signature(self):
        package = INSTALLATION_PACKAGES[0]
        for protection_level in ("(type 0x10)0x2", "(type 0x11)0x2", "0x2", "2", '"signature"'):
            with self.subTest(protection_level=protection_level):
                verify_manifest(manifest_for(package, protection_level), package)

    def test_rejects_non_signature_or_extended_protection(self):
        package = INSTALLATION_PACKAGES[0]
        for protection_level in ("(type 0x10)0x0", "0x1", "0x3", "0x10002"):
            with self.subTest(protection_level=protection_level):
                with self.assertRaisesRegex(AssertionError, "Protection must be signature"):
                    verify_manifest(manifest_for(package, protection_level), package)


if __name__ == '__main__':
    unittest.main()
