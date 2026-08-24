#!/usr/bin/env python3
"""Release-contract tests for Virtual Garage Door 0.5.0 documentation and HPM metadata."""

from __future__ import annotations

import json
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]


class VirtualGarageDoorReleaseTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.manifest = json.loads(
            (REPO_ROOT / "hpm" / "virtual-garage-door.json").read_text(encoding="utf-8")
        )
        cls.readme = (REPO_ROOT / "README.md").read_text(encoding="utf-8")
        cls.changelog = (REPO_ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
        cls.guidance = (REPO_ROOT / "AGENTS.md").read_text(encoding="utf-8")

    def test_manifest_releases_050_without_changing_install_identity(self) -> None:
        self.assertEqual(self.manifest["packageName"], "Virtual Garage Door")
        self.assertEqual(self.manifest["version"], "0.5.0")
        self.assertEqual(self.manifest["dateReleased"], "2026-08-24")
        self.assertEqual(self.manifest["minimumHEVersion"], "2.3.0")
        self.assertEqual(
            self.manifest["releaseNotes"],
            "Synchronizes HomeKit door state through canonical door events, uses physical sensor feedback for final states, and hardens relay/blink safety while preserving in-place app and driver compatibility.",
        )

        app = self.manifest["apps"][0]
        self.assertEqual(app["id"], "virtual-garage-door-app")
        self.assertEqual(app["name"], "LGK Virtual Garage Door")
        self.assertEqual(app["namespace"], "lgkapps")
        self.assertEqual(
            app["location"],
            "https://raw.githubusercontent.com/alwineinger/Hubitat-alw/main/apps/virtual-garage-door/VirtualGarageDoorApp.groovy",
        )
        self.assertEqual(app["alternateNames"], [{"name": "Virtual Garage Door", "namespace": "alw"}])

        driver = self.manifest["drivers"][0]
        self.assertEqual(driver["id"], "virtual-garage-door-driver")
        self.assertEqual(driver["name"], "Simulated Garage Door Opener V2")
        self.assertEqual(driver["namespace"], "smartthings/testing")
        self.assertEqual(
            driver["location"],
            "https://raw.githubusercontent.com/alwineinger/Hubitat-alw/main/drivers/virtual-garage-door/VirtualGarageDoorDriver.groovy",
        )
        self.assertEqual(driver["alternateNames"], [{"name": "Virtual Garage Door", "namespace": "alw"}])

    def test_readme_documents_setup_homekit_recovery_and_limits(self) -> None:
        for text in (
            "Create one virtual garage-door device",
            "momentary opener relay",
            "auto-resets",
            "app-managed bounded pulse duration (default 500 ms)",
            "`closed` must prove the door is fully shut",
            "`open` means the door is not fully closed",
            "back up the hub first",
            "Hubitat stable 2.5.1.152 or later",
            "2.5.0.159",
            "restart Hubitat's HomeKit Integration",
            "deselect the accessory, save, wait 30 seconds, then reselect it",
            "factory reset is not required",
            "precise mid-travel position, movement direction, or obstruction state",
            "Hubitat-native manual verification checklist",
            "at least ten open/close cycles",
            "backgrounding and reopening Apple Home without force-quitting it",
            "wall control or remote",
            "blocked close and a failed open",
            "warning light returns to its prior state",
        ):
            self.assertIn(text, self.readme)

    def test_changelog_captures_050_behavior_and_compatibility(self) -> None:
        section = self.changelog.split("## v0.4.0", 1)[0]
        for text in (
            "## v0.5.0 — 2026-08-24",
            "HomeKit state synchronization",
            "canonical `door` events",
            "physical sensor feedback authoritative",
            "relay and close-warning blink behavior",
            "update in place",
        ):
            self.assertIn(text, section)

    def test_governance_prevents_the_same_garage_door_failures(self) -> None:
        for text in (
            "publish only the standard `door` attribute",
            "physical sensor feedback as the sole authority",
            "Subscribe garage-door apps to `door` command transitions",
            "Tokenize delayed close checks and blink callbacks",
            "Capture and restore the warning light's prior state",
            "Check the opener relay's current state",
        ):
            self.assertIn(text, self.guidance)


if __name__ == "__main__":
    unittest.main()
