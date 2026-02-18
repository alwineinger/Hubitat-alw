/**
 * App Name: HVAC Pause on Open Contacts
 * Purpose: Turns thermostat off when any monitored contact stays open, then restores settings when all close.
 * Version: 1.0.0
 * Install Notes: Install as a user app, select monitored contacts + thermostat, then configure delays/notifications.
 */

definition(
    name: "HVAC Pause on Open Contacts",
    namespace: "alw",
    author: "Codex",
    description: "Pause HVAC when any monitored door/window is left open, then restore when all are closed.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_vent.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_vent@2x.png",
    singleThreaded: true
)

preferences {
    page(name: "mainPage", title: "HVAC Pause on Open Contacts", install: true, uninstall: true)
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "HVAC Pause on Open Contacts", install: true, uninstall: true) {
        section("Devices") {
            input "contacts", "capability.contactSensor", title: "Contacts to monitor", multiple: true, required: true
            input "tstat", "capability.thermostat", title: "Thermostat", multiple: false, required: true
        }

        section("Timing") {
            input "openDelayMin", "number", title: "Minutes open before turning HVAC off", required: true, defaultValue: 5
            input "repeatEnabled", "bool", title: "Repeat reminder notifications while paused", required: true, defaultValue: true
            if (repeatEnabled != false) {
                input "repeatEveryMin", "number", title: "Repeat every X minutes", required: true, defaultValue: 30
            }
        }

        section("Notifications") {
            input "notificationType", "enum", title: "Notification type", required: true, defaultValue: "Pushover",
                options: ["Pushover", "Notification Devices"]

            if ((notificationType ?: "Pushover") == "Pushover") {
                input "pushoverDevices", "capability.notification", title: "Select your Pushover device(s)", multiple: true, required: false
            } else {
                input "notifyDevices", "capability.notification", title: "Select Hubitat Mobile App notification device(s) or other notification devices", multiple: true, required: false
            }
        }

        section("Logging") {
            input "debugLogging", "bool", title: "Enable debug logging", required: true, defaultValue: false
        }

        if (app?.id) {
            section("Current Status") {
                List openContacts = getOpenContacts()
                String pausedState = state.paused ? "Paused" : "Not paused"
                paragraph "State: ${pausedState}. Open contacts: ${openContacts?.size() ?: 0}."
            }
        }
    }
}

def installed() {
    log.info "Installed '${app.label ?: app.name}'"
    initialize()
}

def updated() {
    log.info "Updated '${app.label ?: app.name}'"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    if (!(state.openSince instanceof Map)) {
        state.openSince = [:]
    }
    if (state.paused == null) {
        state.paused = false
    }

    validateAndNormalizeSettings()

    subscribe(contacts, "contact", contactHandler)

    if (debugLogging) {
        log.info "Debug logging enabled; will auto-disable in 30 minutes"
        runIn(1800, "logsOff", [overwrite: true])
    }

    reconcileOpenState()

    if (state.paused && !getOpenContacts()) {
        log.info "App initialized while paused but no contacts are open; restoring HVAC now"
        restoreHvac()
    } else {
        evaluateAndSchedule()
    }
}

def logsOff() {
    app.updateSetting("debugLogging", [value: "false", type: "bool"])
    log.info "Debug logging disabled automatically"
}

def contactHandler(evt) {
    String id = evt.deviceId?.toString()
    if (!id) {
        return
    }

    Map openSince = (state.openSince instanceof Map) ? state.openSince : [:]

    if (evt.value == "open") {
        if (!openSince.containsKey(id)) {
            Long openedAt = evt?.date?.time ?: now()
            openSince[id] = openedAt
            logDebug "Recorded initial open time for ${evt.displayName}: ${openedAt}"
        }
    } else if (evt.value == "closed") {
        if (openSince.containsKey(id)) {
            openSince.remove(id)
            logDebug "Removed open state for ${evt.displayName}"
        }
    }

    state.openSince = openSince
    evaluateAndSchedule()
}

def reconcileOpenState() {
    Map openSince = (state.openSince instanceof Map) ? state.openSince : [:]
    Long nowMs = now()

    contacts?.each { dev ->
        String id = dev.id?.toString()
        String cur = dev.currentValue("contact")

        if (cur == "open") {
            if (!openSince.containsKey(id)) {
                openSince[id] = nowMs
                log.info "Reconcile: ${dev.displayName} is open but had no timestamp; setting openSince to now"
            }
        } else {
            if (openSince.containsKey(id)) {
                openSince.remove(id)
                logDebug "Reconcile: removed stale open timestamp for ${dev.displayName}"
            }
        }
    }

    state.openSince = openSince
}

def evaluateAndSchedule() {
    unschedule("scheduledPauseCheck")

    List openContacts = getOpenContacts()
    if (!openContacts) {
        unschedule("repeatNotifyHandler")
        if (state.paused) {
            restoreHvac()
        }
        return
    }

    if (state.paused) {
        if (repeatEnabled == true) {
            scheduleRepeatNotification()
        } else {
            unschedule("repeatNotifyHandler")
        }
        return
    }

    Long earliestOpen = getEarliestOpenMillis(openContacts)
    Long delayMillis = Math.max((openDelayMin ?: 5) as Integer, 0) * 60_000L
    Long triggerAt = earliestOpen + delayMillis
    Long remaining = triggerAt - now()

    if (remaining <= 0L) {
        pauseHvac()
    } else {
        Date runAt = new Date(triggerAt)
        logDebug "Scheduling pause check for ${runAt} (remaining ${remaining} ms)"
        runOnce(runAt, "scheduledPauseCheck", [overwrite: true])
    }
}

def scheduledPauseCheck() {
    reconcileOpenState()

    if (state.paused) {
        if (repeatEnabled == true) {
            scheduleRepeatNotification()
        }
        return
    }

    List openContacts = getOpenContacts()
    if (!openContacts) {
        return
    }

    Long earliestOpen = getEarliestOpenMillis(openContacts)
    Long delayMillis = Math.max((openDelayMin ?: 5) as Integer, 0) * 60_000L

    if (now() >= (earliestOpen + delayMillis)) {
        pauseHvac()
    } else {
        evaluateAndSchedule()
    }
}

def pauseHvac() {
    if (state.paused) {
        logDebug "pauseHvac skipped: already paused"
        return
    }

    state.savedTstatMode = tstat?.currentValue("thermostatMode")
    state.savedFanMode = tstat?.currentValue("thermostatFanMode")
    state.pausedAtMillis = now()
    state.paused = true

    String currentMode = (state.savedTstatMode ?: "") as String
    if (currentMode != "off") {
        try {
            tstat.setThermostatMode("off")
            log.info "Thermostat set to off"
        } catch (e) {
            log.warn "Failed setting thermostat off: ${e.message}"
        }
    } else {
        log.info "Thermostat already off; marking as paused without sending duplicate off command"
    }

    sendPush(buildStatusMessage(false))

    if (repeatEnabled == true) {
        scheduleRepeatNotification()
    } else {
        unschedule("repeatNotifyHandler")
    }
}

def restoreHvac() {
    if (!state.paused) {
        return
    }

    unschedule("scheduledPauseCheck")
    unschedule("repeatNotifyHandler")

    String restoreMode = state.savedTstatMode
    String restoreFan = state.savedFanMode

    if (restoreMode) {
        try {
            tstat.setThermostatMode(restoreMode)
            log.info "Restored thermostat mode to ${restoreMode}"
        } catch (e) {
            log.warn "Failed restoring thermostat mode to ${restoreMode}: ${e.message}"
        }
    }

    if (restoreFan) {
        state.pendingFanRestore = restoreFan
        runIn(2, "applyFanRestore", [overwrite: true])
    } else {
        state.remove("pendingFanRestore")
    }

    state.paused = false
    state.remove("pausedAtMillis")
    state.remove("savedTstatMode")
    state.remove("savedFanMode")

    sendPush("All doors/windows closed. Thermostat operating mode restored to ${restoreMode ?: 'unchanged'}, fan ${restoreFan ?: 'unchanged'}.")
}

def applyFanRestore() {
    String fan = state.pendingFanRestore
    if (!fan) {
        return
    }

    try {
        tstat.setThermostatFanMode(fan)
        log.info "Restored thermostat fan mode to ${fan}"
    } catch (e) {
        log.warn "Failed restoring thermostat fan mode to ${fan}: ${e.message}"
    } finally {
        state.remove("pendingFanRestore")
    }
}

def repeatNotifyHandler() {
    if (!state.paused) {
        return
    }

    reconcileOpenState()
    List openContacts = getOpenContacts()
    if (!openContacts) {
        restoreHvac()
        return
    }

    sendPush(buildStatusMessage(true))

    if (repeatEnabled == true) {
        scheduleRepeatNotification()
    }
}

def buildStatusMessage(Boolean isRepeat = false) {
    List openContacts = getOpenContacts()
    if (!openContacts) {
        return isRepeat ? "Reminder: HVAC is paused, but no open contacts were found." : "Thermostat turned OFF. No currently open contacts found."
    }

    Long earliestOpen = getEarliestOpenMillis(openContacts)
    String details = openContacts.collect { dev ->
        Long openedAt = getOpenedMillisForDevice(dev)
        "${dev.displayName} (opened ${formatTimestamp(openedAt)})"
    }.join(", ")

    String earliestText = formatTimestamp(earliestOpen)

    if (isRepeat) {
        return "Reminder: Thermostat remains OFF. Open: ${details}. Earliest open: ${earliestText}."
    }

    return "Thermostat turned OFF. Open: ${details}. Earliest open: ${earliestText}."
}

def sendPush(String msg) {
    if (!msg) {
        return
    }

    String type = notificationType ?: "Pushover"
    def targets = (type == "Notification Devices") ? notifyDevices : pushoverDevices

    if (!targets) {
        log.warn "No notification devices selected for '${type}'. Message not sent: ${msg}"
        return
    }

    targets.each { dev ->
        try {
            dev.deviceNotification(msg)
        } catch (e) {
            log.warn "Failed sending notification to ${dev?.displayName ?: dev}: ${e.message}"
        }
    }
}

private void scheduleRepeatNotification() {
    Integer minutes = Math.max((repeatEveryMin ?: 30) as Integer, 1)
    Integer seconds = minutes * 60
    runIn(seconds, "repeatNotifyHandler", [overwrite: true])
    logDebug "Scheduled repeat notification in ${minutes} minute(s)"
}

private List getOpenContacts() {
    return contacts?.findAll { it.currentValue("contact") == "open" } ?: []
}

private Long getEarliestOpenMillis(List openContacts) {
    Map openSince = (state.openSince instanceof Map) ? state.openSince : [:]
    List<Long> values = openContacts.collect { dev ->
        String id = dev.id?.toString()
        Long ts = (openSince[id] as Long)
        if (!ts) {
            ts = now()
            openSince[id] = ts
        }
        ts
    }
    state.openSince = openSince
    return values.min() as Long
}

private Long getOpenedMillisForDevice(dev) {
    Map openSince = (state.openSince instanceof Map) ? state.openSince : [:]
    String id = dev.id?.toString()
    Long ts = openSince[id] as Long
    if (!ts) {
        ts = now()
        openSince[id] = ts
        state.openSince = openSince
    }
    return ts
}

private String formatTimestamp(Long ms) {
    if (!ms) {
        return "unknown"
    }
    TimeZone tz = location?.timeZone ?: TimeZone.getDefault()
    return new Date(ms).format("MMM d h:mm a", tz)
}

private void validateAndNormalizeSettings() {
    Integer delay = (openDelayMin == null ? 5 : (openDelayMin as Integer))
    if (delay < 0) {
        app.updateSetting("openDelayMin", [value: "0", type: "number"])
        log.warn "openDelayMin was < 0; normalized to 0"
    }

    if (repeatEnabled == true) {
        Integer repeatMin = (repeatEveryMin == null ? 30 : (repeatEveryMin as Integer))
        if (repeatMin < 1) {
            app.updateSetting("repeatEveryMin", [value: "1", type: "number"])
            log.warn "repeatEveryMin was < 1; normalized to 1"
        }
    }
}

private void logDebug(String msg) {
    if (debugLogging) {
        log.debug msg
    }
}
