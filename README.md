# Dehumidify With Bath Fans (Hubitat Parent + Child Apps)

Production-ready Hubitat app set that recreates a webCoRE-style bathroom-fan dehumidification ruleset using a Parent app + one Child app per room.

## Files
- `apps/DehumidifyWithBathFansParent.groovy`
- `apps/DehumidifyWithBathFansRoomChild.groovy`

## Install
1. In Hubitat **Apps Code**, create a new app and paste `DehumidifyWithBathFansParent.groovy`, then save.
2. Create another app and paste `DehumidifyWithBathFansRoomChild.groovy`, then save.
3. In Hubitat **Apps**, add **Dehumidify With Bath Fans Parent**.
4. Add one or more Room Controller child apps from the parent.

## Configuration overview

### Parent app
- Global room thresholds (absolute + relative with hysteresis)
- Inside humidity weighted-average source(s)
- Separate thermostat humidity source for room-relative comparisons
- Outside humidity source(s)
- Whole-house controller thresholds and optional indicator/extra fans
- Driver-agnostic thermostat operating-state gate (attribute + value lists)
- Optional generic attribute gate (ventilation/windows-open style)
- App-wide mode restrictions
- Stale sensor threshold and daily notification device/time
- Logging level (Off/Info/Debug/Trace)

### Room child app (one per room)
- 1+ room humidity sensors
- 1+ exhaust fan switches
- Optional single-fan mode: when room automation turns ON, keep one selected fan ON while forcing other room fans OFF
- Optional room humidity-high indicator switch

## How stale sensors work
- Every humidity device event timestamp is tracked by device ID in app state.
- A humidity device is **stale** if no new humidity event arrives within `staleSensorHours` (default: 8h).
- Stale devices are excluded from averages and automation logic.
- If all sensors for a metric are stale/unavailable, that metric becomes **unknown**.
- Automations that depend on unknown metrics are skipped safely.
- Optional stale notifications are throttled to **max once per day** and sent at the configured daily time.

## Troubleshooting
- Set log level to **Debug** or **Trace** temporarily for diagnosis.
- Verify selected devices actually emit `humidity` events.
- If room fans won’t turn on, check:
  - Allowed modes
  - Vent/attribute gate status
  - Thermostat operating-state block values
  - Minimum-off cooldown
- If room fans won’t turn off, confirm whole-house mode is not active.
- If relative room comparisons are not running, verify thermostat humidity source is not stale.

## Manual test checklist
1. **Install & pair**
   - Install parent + child apps, configure at least 2 room controllers.
2. **Smoke tests**
   - Confirm humidity/switch events trigger app logs.
   - Confirm no repetitive ON/OFF command spam.
3. **Room automation**
   - Raise room humidity above ON threshold and verify room fans/indicator turn on.
   - If single-fan mode is enabled for a room, verify only the selected fan turns on and other room fans are turned off.
   - Lower below OFF conditions and verify room fans/indicator turn off.
4. **Whole-house mode**
   - Simulate ON thresholds (inside high, outside favorable, temp/gates pass) and verify whole-house runs all designated fans + active flag behavior.
   - Simulate OFF condition and verify whole-house mode exits.
5. **Gates**
   - Put thermostat op-state into blocked value and verify whole-house ON is blocked.
   - Fail optional attribute gate and verify fan auto-ON is blocked and active whole-house run is canceled.
6. **Stale sensors**
   - Reduce stale threshold and stop updates on a device; verify stale exclusion and degraded metric behavior.
   - Verify stale notifications are sent no more than once per day.
7. **Manual ON auto-off**
   - Manually turn on a room fan while room humidity is not high and whole-house inactive; verify delayed auto-off.
   - Confirm auto-on by automation is not immediately undone by manual timer logic.
