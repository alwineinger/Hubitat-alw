# CHANGELOG

## v0.4.1
- Added safe configurable Hubitat Safety Monitor to Honeywell command mappings, including support for mapping HSM Armed Night to Honeywell Arm Instant.
- Added advanced Honeywell partition state to HSM status mappings so panel-reported states can be synchronized explicitly while preserving existing defaults.
- Updated the Honeywell Security HPM manifest release metadata for the new HSM mapping settings.

## v0.4.0
- Reorganized repository into package-scoped directories for consistency:
  - Apps moved to `apps/<package-slug>/...`
  - Drivers moved to `drivers/<package-slug>/...`
  - Honeywell helper scripts moved to `tools/honeywell-security/`
  - Legacy AcuRite reference file moved to `archive/originals/`
- Updated all package manifests in `hpm/*.json` to point to new raw GitHub file locations.
- Updated `README.md` with a repository-wide package/file map and maintenance guidance for path changes.
- Updated `AGENTS.md` to enforce package-scoped repository organization for all future changes.

## v0.3.14
- Updated all `hpm/*.json` manifests and `hpm/packageList.json` to use production GitHub URLs under `alwineinger/Hubitat-alw` (removed placeholder owner).
- Bumped manifest package versions to `0.3.14` and updated release notes text to reflect URL hardening for HPM installs.

## v0.3.13
- Added Hubitat Package Manager support files under `hpm/`:
  - `packageList.json` for repository package discovery
  - Individual package manifests for each app/driver bundle in this repo
- Updated `README.md` with HPM setup instructions and release-maintenance workflow details.

## v0.3.12
- Fixed Dehumidify With Bath Fans manual-ON hold behavior so **all** automatic OFF paths now respect the active manual hold window (including room threshold OFF and whole-house release OFF paths).
- Unified manual hold timing to one setting in Parent app: `Manual ON auto-OFF delay`; removed the separate blocked-operating-state grace setting.
- Hardened Room Child state-map access for per-fan hold/cooldown bookkeeping to avoid null-map property errors during evaluation.
- Added a bug-prevention rule to `AGENTS.md`: use one per-fan manual hold window and enforce it consistently across every automation OFF path.

## v0.3.11
- Updated SMA Sunny Boy Modbus Poller startup behavior to use a serial-number-only connectivity probe for inverters that have not yet returned any successful Modbus response.
- This reduces repeated timeout bursts while validating first contact and automatically returns to the configured register set after the first successful response.
- Added a bug-prevention rule to `AGENTS.md`: for no-response inverters, probe a known-good single register before full-batch polling.

## v0.3.10
- Improved SMA Sunny Boy Modbus Poller settings help text for register offset to explicitly map `mbpoll -0` behavior to app offset `0`.
- Added initialization-time info logs that print a copy/paste `mbpoll` command per configured inverter, including current Unit ID, function code selection, and addressing mode for quick external validation.
- Added a bug-prevention rule to `AGENTS.md`: always log a per-inverter `mbpoll` verification command at initialization so on-hub settings can be compared against known-good manual reads.

## v0.3.9
- Fixed Hubitat settings UX for Modbus register offsets: changed `registerAddressOffset` from `number` to `enum` (`0` / `-1`) so hubs that block minus-sign entry can still select `-1`.
- Added explicit integer parsing helper for numeric settings sourced from enum/text values, ensuring robust conversion and fallback behavior.
- Added a bug-prevention rule to `AGENTS.md`: when negative values are required in app settings, avoid `type: 'number'` and parse enum/text inputs explicitly.

## v0.3.8
- Added Modbus interoperability controls in `SMA Sunny Boy Modbus Poller`:
  - `Register address offset` (default `0`, set `-1` for 0-based register maps)
  - `Read function` selector (`03` Holding Registers or `04` Input Registers)
- Updated Modbus request/response handling to support FC03 and FC04 and validate callback function code against the active setting.
- Added request trace details that log both adjusted and raw register starts to simplify troubleshooting address-map mismatches.
- Added a bug-prevention rule to `AGENTS.md`: make Modbus address base and read function configurable and verify a known register via external client (`mbpoll`) before release.

## v0.3.7
- Added configurable Modbus pacing in `SMA Sunny Boy Modbus Poller`:
  - `Delay between Modbus requests (ms)` (default 350ms)
  - `Delay between inverter poll starts (ms)` (default 1200ms)
  These defaults reduce back-to-back socket pressure that can cause repeated Hubitat `Read timed out` warnings on some inverter/network combinations.
- Added a targeted warning when an inverter has been polled but no Modbus response has ever been received, guiding checks for inverter Modbus/TCP enablement, Unit ID, and network ACL/firewall.
- Added a bug-prevention rule to `AGENTS.md`: multi-device Modbus polling should expose conservative pacing controls to avoid inverter TCP read timeouts.

## v0.3.6
- Fixed SMA Sunny Boy Modbus Poller request tracking to key pending Modbus callbacks by unique per-send `requestId` instead of by transaction ID alone, preventing cross-inverter collisions when multiple devices are polled concurrently.
- Added a txId fallback matcher that only resolves when exactly one pending request exists for that txId, avoiding accidental mis-association.
- Reduced log noise by safely ignoring `componentRefresh` calls that do not include a valid child device context.
- Added a bug-prevention rule to `AGENTS.md`: in multi-device Modbus polling, track outstanding requests by unique `requestId` and treat `txId` as metadata.

## v0.3.5
- Fixed SMA Sunny Boy Modbus Poller Modbus/TCP send options to set HubAction `type` as the literal `'LAN_TYPE_CLIENT'`, resolving runtime `MissingPropertyException: No such property: LAN_TYPE_CLIENT for class: hubitat.device.HubAction$Type` in `pollSingleInverter`.
- Added a bug-prevention rule to `AGENTS.md`: for app-based raw Modbus/TCP option maps, use string literal `'LAN_TYPE_CLIENT'` instead of `hubitat.device.HubAction.Type.LAN_TYPE_CLIENT`.

## v0.3.4
- Fixed SMA Sunny Boy Modbus Poller Modbus TCP request options to use explicit LAN client fields (`destinationAddress`, `destinationPort`, `encoding`) for raw hex requests, addressing repeated `Cannot get property 'host' on null object` warnings during polling.
- Added a bug-prevention rule to `AGENTS.md`: for app-based raw Modbus/TCP, use explicit `LAN_TYPE_CLIENT` option fields and validate callback behavior in live logs to catch null host/request issues early.

## v0.3.3
- Fixed SMA Sunny Boy Modbus Poller child creation by switching to a repository-provided child driver (`SMA Sunny Boy Inverter Child`) instead of relying on missing built-in `Generic Component Sensor`.
- Added new `drivers/SMASunnyBoyInverterChild.groovy` with `Refresh`, `PowerMeter`, and `EnergyMeter` capabilities plus SMA telemetry attributes used by the app.
- Fixed Modbus request transport by using `hubitat.device.Protocol.LAN` for `HubAction` (resolves `No such property: TCP` send failures).
- Added a bug-prevention rule to `AGENTS.md`: for LAN/TCP app networking, use supported Hubitat protocol enums and verify child driver namespace/name matches an installed driver.

## v0.3.2
- Fixed `sonoffzigbeedriver.groovy` humidity offset clamping to avoid Groovy `Math.min`/`Math.max` ambiguous overload errors when mixing `Integer` and `BigDecimal` types.
- Added a bug-prevention rule to `AGENTS.md`: use consistent numeric types (prefer `BigDecimal` literals and `BigDecimal#min`/`max`) when bounding decimal telemetry values.

## v0.3.1
- Removed watchdog-style presence handling from `sonoffzigbeedriver.groovy`.
- Removed recovery scheduling settings and logic (`recoveryInterval`, `presenceTrigger`, recovery checks/events).
- Driver now focuses on Zigbee reporting configuration and explicit refresh reads for temperature, humidity, and battery only.

## v0.3.0
- Added new Hubitat app `Garage Fridge Freezer Open Notifier` for one-or-more contact sensors.
- Supports configurable open threshold before first alert (default 12 minutes).
- Supports reminder repeats (default every 10 minutes) with optional max reminder count (default 3).
- Sends open alerts with per-device opened timestamp/duration and sends one summary notification when all monitored contacts close.

## v0.2.1
- Fixed Room Child configuration UX so each child instance exposes a required app label field and can be renamed to a room-specific name.
- Added a bug-prevention rule to `AGENTS.md`: every child app dynamic page must include a required `label` control.

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
