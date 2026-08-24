# Hubitat Automation Repository

This repository contains Hubitat Elevation Apps and Drivers, organized by package for predictable maintenance and clean Hubitat Package Manager (HPM) publishing.

## Repository layout

- `apps/<package-slug>/` — Hubitat app Groovy sources for a single package
- `drivers/<package-slug>/` — Hubitat driver Groovy sources for a single package
- `hpm/` — HPM package manifests plus `packageList.json`
- `tools/` — helper tooling (for example, local proxy scripts)
- `archive/` — legacy/reference code not used for active installs
- `CHANGELOG.md` — human-readable release history

## Package-to-file map

### Dehumidify With Bath Fans
- App: `apps/dehumidify-with-bath-fans/DehumidifyWithBathFansParent.groovy`
- App: `apps/dehumidify-with-bath-fans/DehumidifyWithBathFansRoomChild.groovy`

### Garage Fridge Freezer Open Notifier
- App: `apps/garage-fridge-freezer-open-notifier/GarageFridgeFreezerOpenNotifier.groovy`

### HVAC Pause on Open Contacts
- App: `apps/hvac-pause-on-open-contacts/HVACPauseOnOpenContacts.groovy`

### Honeywell Security Integration
- App: `apps/honeywell-security/HoneywellSecurity.groovy`
- Driver: `drivers/honeywell-security/HoneywellPartition.groovy`
- Driver: `drivers/honeywell-security/HoneywellZoneContact.groovy`
- Driver: `drivers/honeywell-security/HoneywellZoneMotion.groovy`
- Tooling: `tools/honeywell-security/`

### SMA Sunny Boy Modbus Poller
- App: `apps/sma-sunny-boy-modbus-poller/SMASunnyBoyModbusPoller.groovy`
- Driver: `drivers/sma-sunny-boy-modbus-poller/SMASunnyBoyInverterChild.groovy`

### Virtual Garage Door
- App: `apps/virtual-garage-door/VirtualGarageDoorApp.groovy`
- Driver: `drivers/virtual-garage-door/VirtualGarageDoorDriver.groovy`

#### Virtual Garage Door setup and HomeKit rollout

Prerequisites and setup:

- Create one virtual garage-door device with the existing `Simulated Garage Door Opener V2` driver, then select that same device in the Virtual Garage Door app.
- Select one momentary opener relay. The default `Opener switch auto-resets` setting is true. If the relay stays on, disable auto-reset and use the app-managed bounded pulse duration (default 500 ms).
- Select one acceleration/contact sensor: `closed` must prove the door is fully shut; `open` means the door is not fully closed.

For a HomeKit rollout, back up the hub first and update to Hubitat stable 2.5.1.152 or later. The hub used before this rollout was on 2.5.0.159. Install the app and driver update in place, then restart Hubitat's HomeKit Integration and verify the accessory is exported as the Garage Door class. If HomeKit has cached the old class or state, deselect the accessory, save, wait 30 seconds, then reselect it. A routine HomeKit bridge factory reset is not required.

With one sensor, this package cannot report precise mid-travel position, movement direction, or obstruction state. A partially open or stopped door is conservatively represented as open because it is not fully closed.

Hubitat-native manual verification checklist:

1. From Hubitat and Apple Home, complete at least ten open/close cycles; include backgrounding and reopening Apple Home without force-quitting it. Confirm the virtual device reaches the sensor-confirmed final `open` or `closed` state without remaining in `opening` or `closing`.
2. Operate the door with its wall control or remote; confirm the physical sensor updates the virtual device without pulsing the opener relay.
3. Restart HomeKit Integration, reopen Apple Home, and verify the Garage Door class and current state; if cached, use deselect, wait, and reselect.
4. Simulate a blocked close and a failed open; confirm timeout recovery reports the sensor-backed state and does not leave a stale transition.
5. Enable close-warning blinking, then cancel or complete the close; confirm the warning light returns to its prior state and no stale callback pulses the relay.

### Sonoff Zigbee Temperature/Humidity Driver
- Driver: `drivers/sonoff-zigbee-temp-humidity/SonoffZigbeeTempHumidity.groovy`

### AcuRite Weather Station
- Driver: `drivers/acurite-weather-station/AcuRiteWeatherParent.groovy`
- Driver: `drivers/acurite-weather-station/AcuRiteIndoorChild.groovy`

### First Alert Smoke/Carbon Detector Driver
- Driver: `drivers/first-alert-smoke-carbon/FirstAlertSmokeCarbonDetector.groovy`

## Hubitat Package Manager (HPM)

- Package list: `hpm/packageList.json`
- Add this URL to HPM:
  - `https://raw.githubusercontent.com/alwineinger/Hubitat-alw/main/hpm/packageList.json`

When moving or renaming app/driver files, always update:
1. the corresponding `hpm/*.json` manifest `location` URLs,
2. this README package map,
3. any in-code metadata pointers (`importUrl`, docs links) if present.

## Notes

- `archive/originals/` contains legacy/reference files and is not part of active package manifests.
