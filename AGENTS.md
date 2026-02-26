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
  - Treat missing attributes/values as unknown; skip logic rather than guessing.
  - Convert numbers explicitly (`toBigDecimal()`, `toInteger()`) and handle exceptions.
- Idempotent device control:
  - Only call `device.on()` if switch is currently off; only call `off()` if on.
- Scheduling best practices:
  - Use `runIn()` for delayed one-shot tasks.
  - Use `schedule()` for daily/cron-like work.
  - If using periodic schedules, keep them coarse (e.g., 15 minutes) and cheap.
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