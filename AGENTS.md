# AGENTS.md

## Purpose
This repository contains Hubitat Elevation automations (Apps and/or Drivers) written in Groovy. The goal is predictable, low-noise, hub-friendly automation code that is easy to review, extend, and safely deploy.

## Core principles
- **Safety first:** Never spam device commands; always check current device state before sending `on()` / `off()`.
- **Be hub-friendly:** Prefer event-driven subscriptions; use scheduled jobs sparingly and keep them lightweight.
- **No surprises:** Clear configuration UI, sane defaults, and explicit behavior around overrides/gates.
- **Driver-agnostic when needed:** If attribute values vary by driver (e.g., thermostat operating state), make them configurable instead of hardcoding.
- **No secrets:** Never commit tokens, hub IPs, Pushover keys, etc.

## Bug-fix learning rule (required)
When addressing a bug:
- **Update this `AGENTS.md`** with one or more concrete instruction(s) that would have prevented the bug from being introduced in the first place.
- The new instruction(s) should be:
  - **Actionable** (something a developer can do/verify)
  - **Specific** (not “be careful”)
  - **Placed in the most relevant section** (e.g., Scheduling, State, Gates, Stale Sensors, Logging, UX)
- Also add a short note in the PR description referencing the new/updated guideline.

Examples:
- A bug caused repeated `runIn()` scheduling → add a rule under Scheduling: “Always `unschedule(handlerName)` before re-scheduling and store scheduled flags keyed by deviceId.”
- A bug mis-handled stale sensors → add a rule under Stale sensors: “Never use `device.currentValue()` unless the device is non-stale; compute metrics only from recent events.”

## Repository structure (recommended)
- `apps/` — Hubitat apps (parent/child)
- `drivers/` — Hubitat drivers
- `docs/` — optional design notes and screenshots
- `README.md` — install/config steps + behavior summary
- `CHANGELOG.md` — human-readable version history

## Coding standards (Hubitat Groovy)
- App metadata shape (Hubitat Apps):
  - Use top-level `definition(...)` in app files; do **not** wrap app definitions in `metadata {}` (that wrapper is for drivers and causes app compile metadata errors).
  - In every app `definition(...)`, set **non-empty** `iconUrl` and `iconX2Url` values; empty icon fields can cause Hubitat compile failures.
  - For parent/child app sets, verify icon fields are present in **every** app file before release (do not assume parent fixes cover children).
- Use standard lifecycle methods:
  - `installed()`, `updated()`, `initialize()`
  - Call `unsubscribe()` and `unschedule()` in `updated()` before re-initializing.
- Keep state minimal:
  - Store primitives and maps keyed by `deviceId` (string/long), not device objects.
  - Prefer `state` unless concurrent updates demand `atomicState`.
- Defensive null-handling:

- Numeric safety in Groovy drivers: when bounding or comparing decimal telemetry values (e.g., humidity offsets), keep operands the same numeric type (prefer `BigDecimal` literals like `0G`/`100G` and `BigDecimal#min`/`max`) to avoid ambiguous `Math.min`/`Math.max` overload errors at runtime.

- Zigbee drivers: keep reporting configuration, refresh reads, and parse handlers aligned to the **same attribute IDs** (e.g., battery percentage is typically `0x0021`); verify these three call sites together before release.
- In drivers, ensure lifecycle methods that call `configure()` actually **send returned Zigbee commands** (e.g., via `sendHubCommand`/`HubMultiAction`) so preferences are applied on install/update.
- For LAN/TCP app networking, use supported Hubitat protocol enums (`hubitat.device.Protocol.LAN`) and verify child driver namespace/name values exactly match an installed driver before release.
  - For raw Modbus/TCP `HubAction` sends from apps, prefer explicit client options (`type: LAN_TYPE_CLIENT`, `destinationAddress`, `destinationPort`, `encoding: HEX_STRING`) instead of only `destination`, then verify callback parsing works without null host/request warnings in live logs.
  - For raw Modbus/TCP `HubAction` option maps, set `type` as the literal string `'LAN_TYPE_CLIENT'` (not `hubitat.device.HubAction.Type.LAN_TYPE_CLIENT`) to avoid runtime `MissingPropertyException` on some hubs.
  - For multi-device Modbus polling, key outstanding request state by a unique per-send `requestId` (store `txId` only as metadata), and resolve callbacks by `requestId` first to prevent cross-device txId collisions.
  - Treat missing attributes/values as unknown; skip logic rather than guessing.
  - Convert numbers explicitly (`toBigDecimal()`, `toInteger()`) and handle exceptions.
- Idempotent device control:
  - Only call `device.on()` if switch is currently off; only call `off()` if on.
- Manual ON classification: classify fan ON events as `physical`, `digital`, or `unknown` using both `evt.type` and `evt.isPhysical()` when available; treat `digital` as non-manual and make `unknown` behavior explicitly configurable in Parent settings/help text.
- Scheduling best practices:
  - Use `runIn()` for delayed one-shot tasks.
  - Use `schedule()` for daily/cron-like work.
  - If using periodic schedules, keep them coarse (e.g., 15 minutes) and cheap.
  - When an operating-state gate would force an automatic OFF, preserve manual **physical** fan ON events for a configurable grace window, persist hold-until timestamps by fan ID, and re-check the gate at grace expiry before sending `off()`.
- Subscriptions:
  - Subscribe only to attributes you need.
  - Avoid duplicate subscriptions; rely on re-init via `updated()`.
- Logging:
  - Provide log levels Off/Info/Debug/Trace.
  - Define settings `options` lists used by dynamic preference inputs as `@Field static final` constants (not plain script vars) so Hubitat renders enum dropdown choices reliably in the settings UI.
  - Default Info. Trace is for short-term troubleshooting.
  - Never log sensitive values.

## Configuration UX guidelines
- Group settings into sections with short help text.
- Use defaults that work “out of the box”.
- For every child app dynamic page, include a required `label` control so each instance can be renamed to a room-specific name from the UI.
- For complex inputs (e.g., weights per device), use dynamic pages and generate per-device inputs with stable keys.
- Make driver-specific values configurable (e.g., attribute name + allowed/blocked values lists).

## Testing workflow (manual, Hubitat-native)
There is no official unit test framework for Hubitat Groovy. Use a manual test checklist for each release:

1) **Install & pair**
- Install app(s) in Apps Code.
- Add Parent app, create 1+ Child instances, select devices.

2) **Smoke tests**
- Confirm subscriptions are set (watch Logs when changing a sensor value).
- Confirm no repeated command spam.

3) **Room automation**
- Raise room humidity above ON thresholds; verify fans turn on and indicator (if configured) turns on.
- Lower humidity below OFF thresholds; verify fans turn off (unless whole-house mode is active).

4) **Whole-house mode**
- Simulate inside/outside conditions that should activate whole-house; verify all room fans turn on and active flag set.
- Simulate an OFF condition (e.g., inside below threshold or operating state becomes cooling); verify all are released and flag clears.

5) **Gates**
- Toggle each configured gate into “blocked” and confirm automation does not turn fans on.
- If whole-house is active, blocking a gate should shut it down safely.

6) **Stale sensors**
- Temporarily stop updates (or set stale threshold small) and confirm stale devices are excluded.
- Confirm automation skips when a required metric becomes unknown.
- Confirm notifications are throttled (≤ 1/day) and fire at configured time.

7) **Manual ON auto-off**
- Turn a fan on manually while humidity is not high; verify it turns off after configured delay.
- Ensure auto-off does not interfere when the app turned the fan on.

## Release checklist
- Update `CHANGELOG.md` with a new version entry.
- Verify install/update path works without requiring re-creation of child apps.
- Confirm defaults are safe (no accidental mass fan activation).
- Confirm logging defaults to Info and is not noisy.
- Optionally add Hubitat Package Manager (HPM) metadata if publishing.

## Pull request expectations
- Include a short description of the behavior change.
- Note any new settings and their defaults.
- Provide a brief manual test note (“tested with 2 rooms + whole-house, stale sensor simulation”).
- If the PR fixes a bug, **include the `AGENTS.md` guideline update** described in “Bug-fix learning rule (required)”.
