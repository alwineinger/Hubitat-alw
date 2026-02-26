# CHANGELOG

## v0.3.0
- Added new Hubitat app `Garage Fridge Freezer Open Notifier` for one-or-more contact sensors.
- Supports configurable open threshold before first alert (default 12 minutes).
- Supports reminder repeats (default every 10 minutes) with optional max reminder count (default 3).
- Sends open alerts with per-device opened timestamp/duration and sends one summary notification when all monitored contacts close.


## v0.2.0
- Added Room Child single-fan mode for rooms with multiple exhaust fans: when enabled, automation keeps one selected fan ON and forces the other room fans OFF.
- Updated whole-house fan aggregation to respect each child room's single-fan selection when whole-house mode activates.

## v0.1.3
- Fixed Room Child app compile error by setting non-empty `iconUrl` and `iconX2Url` in `definition(...)`.
- Added a bug-prevention rule to `AGENTS.md`: for parent/child app sets, verify icon fields in every app file before release.

## v0.1.2
- Fixed Parent app compile error by setting non-empty `iconUrl` and `iconX2Url` in `definition(...)`.
- Added a bug-prevention rule to `AGENTS.md`: app definitions must always provide non-empty icon URLs.

## v0.1.1
- Fixed Hubitat app compile error by converting Parent/Child app definitions to top-level `definition(...)` blocks (removed invalid `metadata {}` wrapper in app code).
- Updated `AGENTS.md` with a specific Hubitat app metadata rule to prevent this class of compile failure.

## v0.1.0
- Initial release of **Dehumidify With Bath Fans** Hubitat app set.
- Added Parent app with:
  - Global room threshold controls (absolute + relative + hysteresis)
  - Whole-house dehumidification controller and active flag management
  - Driver-agnostic thermostat operating-state gating
  - Optional generic attribute gate for ventilation/windows-open style suppression
  - Weighted inside humidity metric and outside humidity metric support
  - Stale humidity sensor detection with daily notification throttling
  - App-wide logging level controls
- Added Room Child app with:
  - Room-level humidity averaging from non-stale sensors
  - Fan ON/OFF control and optional room indicator switch
  - Whole-house priority-aware OFF behavior
  - Manual ON auto-off behavior with optional physical-event preference
