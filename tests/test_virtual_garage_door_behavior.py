"""Focused static and lightweight behavior contracts for the garage-door code.

These tests intentionally use only the Python standard library because Hubitat
Groovy code runs on the hub rather than in a local Groovy test harness.
"""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
APP = (ROOT / "apps/virtual-garage-door/VirtualGarageDoorApp.groovy").read_text()
DRIVER = (ROOT / "drivers/virtual-garage-door/VirtualGarageDoorDriver.groovy").read_text()


class PendingDoorModel:
    """Small model of the safety decisions required of the Hubitat app."""

    def __init__(self):
        self.pending = None
        self.pulses = 0
        self.reassertions = []
        self.light = "on"
        self.original_light = None

    @staticmethod
    def auto_resets(setting):
        return True if setting is None else bool(setting)

    def request(self, target, physical, blink=False):
        if self.pending:
            if self.pending["target"] == target:
                return "duplicate"
            if self.pending["phase"] == "warning" and target == "open":
                self.cancel()
            else:
                self.reassertions.append(self.pending["target"])
                return "opposite-rejected"
        if physical == target:
            return "already-terminal"
        self.pending = {"target": target, "phase": "warning" if blink else "actuated"}
        if blink:
            self.original_light = self.light
            self.light = "off"
            return "warning"
        self.pulses += 1
        return "actuated"

    def cancel(self):
        if self.original_light is not None:
            self.light = self.original_light
        self.original_light = None
        self.pending = None

    def timeout(self, physical):
        self.pending = None
        if physical in {"open", "closed"}:
            return physical
        return None

    def contact(self, physical):
        if self.pending and self.pending["target"] != physical:
            return "ignored-mismatch"
        if self.pending:
            self.pending = None
            return "completed"
        return "synced"


class VirtualGarageDoorContractTests(unittest.TestCase):
    def test_driver_uses_one_standard_garage_door_stream(self):
        self.assertIn('capability "Garage Door Control"', DRIVER)
        self.assertNotIn('capability "Door Control"', DRIVER)
        self.assertNotIn('name: "garageDoor"', DRIVER)
        self.assertNotIn("finishOpening", DRIVER)
        self.assertNotIn("finishClosing", DRIVER)
        self.assertNotIn("travelTime", DRIVER)
        self.assertNotIn("tiles {", DRIVER)

    def test_driver_terminal_state_contract_and_refresh(self):
        self.assertIn('command "setDoorState"', DRIVER)
        self.assertIn('terminalState in ["open", "closed"]', DRIVER)
        self.assertIn('publishDoor(terminalState)', DRIVER)
        self.assertIn('publishContact(terminalState)', DRIVER)
        self.assertIn('publishDoor("opening")', DRIVER)
        self.assertIn('publishDoor("closing")', DRIVER)
        self.assertIn('String stableState = contactState in ["open", "closed"]', DRIVER)
        self.assertIn('value: stableState', DRIVER)
        self.assertIn('Refresh skipped: no stable open/closed state is available.', DRIVER)
        self.assertIn('def on()', DRIVER)
        self.assertIn('def off()', DRIVER)

    def test_app_has_one_virtual_input_and_subscribes_to_door(self):
        self.assertEqual(APP.count('input "virtualgd",'), 1)
        self.assertNotIn("virtualgdbutton", APP)
        self.assertIn('subscribe(virtualgd, "door", virtualDoorHandler)', APP)
        self.assertIn('virtualgd.setDoorState(physicalState)', APP)

    def test_terminal_virtual_events_do_not_actuate(self):
        handler = APP[APP.index("def virtualDoorHandler"):APP.index("private void requestDoor")]
        self.assertIn('!(requestedState in ["opening", "closing"])', handler)
        self.assertIn("return", handler)
        self.assertNotIn("opener.on()", handler)

    def test_initialization_sync_has_no_relay_actuation(self):
        init = APP[APP.index("def initialize()"):APP.index("def contactHandler")]
        self.assertIn('synchronizeFromSensor("initialization")', init)
        self.assertNotIn("actuateOpener", init)
        sync = APP[APP.index("private void synchronizeFromSensor"):APP.index("private void cancelAllWork")]
        self.assertIn('virtualgd.setDoorState(physicalState)', sync)
        self.assertIn("Skipping", sync)

    def test_contact_handler_only_cancels_a_matching_pending_operation(self):
        handler = APP[APP.index("def contactHandler"):APP.index("def virtualDoorHandler")]
        self.assertIn('if (pending && pending.target != physicalState)', handler)
        self.assertIn('Ignoring physical ${physicalState} while waiting for ${pending.target}', handler)
        self.assertLess(handler.index('if (pending && pending.target != physicalState)'), handler.index('cancelAllWork(true)'))

        model = PendingDoorModel()
        model.request("closed", "open")
        self.assertEqual(model.contact("open"), "ignored-mismatch")
        self.assertIsNotNone(model.pending)
        self.assertEqual(model.timeout("open"), "open")

    def test_rejected_opposite_reasserts_pending_transition_without_pulse(self):
        request = APP[APP.index("private void requestDoor"):APP.index("private void beginCloseWarning")]
        self.assertIn('reassertPendingTransition(pending.target)', request)
        self.assertIn('reassertPendingTransition(pending.target)\n            logInfo "Ignoring ${target} request while ${pending.target} operation is already actuated."\n            return', request)
        reassert = APP[APP.index("private void reassertPendingTransition"):APP.index("private void beginCloseWarning")]
        self.assertIn("virtualgd.open()", reassert)
        self.assertIn("virtualgd.close()", reassert)
        model = PendingDoorModel()
        model.request("open", "closed")
        self.assertEqual(model.request("closed", "closed"), "opposite-rejected")
        self.assertEqual(model.reassertions, ["open"])
        self.assertEqual(model.pulses, 1)

    def test_runtime_files_retain_apache_attribution_and_serialized_app_handlers(self):
        for source in (APP, DRIVER):
            self.assertIn("Licensed under the Apache License, Version 2.0", source)
            self.assertIn("http://www.apache.org/licenses/LICENSE-2.0", source)
        self.assertIn("singleThreaded: true", APP)

    def test_timeout_duplicate_and_opposite_direction_safety_model(self):
        model = PendingDoorModel()
        self.assertEqual(model.request("open", "closed"), "actuated")
        self.assertEqual(model.pulses, 1)
        self.assertEqual(model.request("open", "closed"), "duplicate")
        self.assertEqual(model.pulses, 1)
        self.assertEqual(model.request("closed", "closed"), "opposite-rejected")
        self.assertEqual(model.timeout("closed"), "closed")

    def test_timeout_always_restores_any_known_physical_terminal_state(self):
        model = PendingDoorModel()
        model.request("open", "closed")
        self.assertEqual(model.timeout("open"), "open")
        model.request("closed", "open")
        self.assertEqual(model.timeout("closed"), "closed")

        timeout = APP[APP.index("def operationTimedOut"):APP.index("private void synchronizeFromSensor")]
        self.assertIn('if (physicalState in ["open", "closed"])', timeout)
        self.assertIn('virtualgd.setDoorState(physicalState)', timeout)
        self.assertIn("confirmed by physical sensor after timeout", timeout)

    def test_refused_relay_pulse_reads_current_sensor_for_virtual_recovery(self):
        actuate = APP[APP.index("private void actuateOpener"):APP.index("def releaseOpener")]
        self.assertIn('synchronizeFromSensor("opener relay was already on")', actuate)
        self.assertNotIn("knownPhysicalState", actuate)
        sync = APP[APP.index("private void synchronizeFromSensor"):APP.index("private void cancelAllWork")]
        self.assertIn("String physicalState = sensor.currentContact", sync)
        self.assertIn('if (!(physicalState in ["open", "closed"]))', sync)
        self.assertIn('virtualgd.setDoorState(physicalState)', sync)

    def test_preclose_warning_can_cancel_and_restores_light(self):
        model = PendingDoorModel()
        self.assertEqual(model.request("closed", "open", blink=True), "warning")
        self.assertEqual(model.light, "off")
        self.assertEqual(model.request("open", "closed"), "actuated")
        self.assertEqual(model.light, "on")
        self.assertEqual(model.pulses, 1)

    def test_app_guards_relay_auto_off_and_stale_callbacks(self):
        self.assertIn('input "openerAutoResets"', APP)
        self.assertIn('input "openerPulseMillis"', APP)
        self.assertIn("if (!openerAutoResetsEnabled() && turnedOn)", APP)
        self.assertIn("private boolean openerAutoResetsEnabled()", APP)
        self.assertIn("if (openerAutoResets == null)", APP)
        self.assertIn("def releaseOpener(data = null)", APP)
        self.assertIn("state.relayPulse?.token != token", APP)
        self.assertIn("state.pendingOperation?.relayTurnedOn", APP)
        self.assertIn("def handleBlinkStep(data = null)", APP)
        self.assertIn("Ignoring stale blink callback", APP)
        self.assertIn("restoreWarningLight", APP)
        self.assertIn("deviceToSet?.currentSwitch == desired", APP)

    def test_unset_relay_auto_reset_preference_preserves_existing_behavior(self):
        self.assertTrue(PendingDoorModel.auto_resets(None))
        self.assertTrue(PendingDoorModel.auto_resets(True))
        self.assertFalse(PendingDoorModel.auto_resets(False))


if __name__ == "__main__":
    unittest.main()
