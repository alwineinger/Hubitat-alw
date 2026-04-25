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
