import groovy.transform.Field

/**
 * Garage Fridge/Freezer Open Notifier
 *
 * Sends notifications when configured contact sensors remain open beyond a threshold,
 * repeats reminders until all close, and sends an all-closed summary.
 */
def VERSION = "1.0.0"

@Field static final List<String> LOG_LEVELS = ["Off", "Info", "Debug", "Trace"]

definition(
    name: "Garage Fridge Freezer Open Notifier",
    namespace: "alw",
    author: "Codex",
    description: "Notify when garage fridge/freezer contacts are left open too long, with reminders until all close.",
    category: "Convenience",
    iconUrl: "https://raw.githubusercontent.com/HubitatCommunity/HubitatPublic/master/resources/icons/app-Notification.png",
    iconX2Url: "https://raw.githubusercontent.com/HubitatCommunity/HubitatPublic/master/resources/icons/app-Notification@2x.png",
    singleThreaded: true
)

preferences {
    page(name: "mainPage", title: "Garage Fridge Freezer Open Notifier", install: true, uninstall: true)
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Garage Fridge Freezer Open Notifier", install: true, uninstall: true) {
        section("Sensors") {
            input "contactSensors", "capability.contactSensor", title: "Contact sensor(s) to monitor", multiple: true, required: true
        }

        section("Timing") {
            input "openMinutes", "number", title: "Minutes open before first notification", defaultValue: 12, required: true
            input "repeatMinutes", "number", title: "Reminder repeat interval (minutes)", defaultValue: 10, required: true
            input "maxReminderCount", "number", title: "Max reminder notifications (blank for unlimited)", defaultValue: 3, required: false
        }

        section("Notifications") {
            input "notifyDevice", "capability.notification", title: "Notification device (e.g., Pushover)", multiple: false, required: true
        }

        section("Logging") {
            input "logLevel", "enum", title: "Log level", options: LOG_LEVELS, defaultValue: "Info", required: true
            paragraph "Trace is intentionally noisy and intended for short-term troubleshooting."
        }

        if (app?.id) {
            section("Current Status") {
                List<Map> openNow = getOpenDevicesNow()
                String stateText = openNow ? "Open: ${openNow.collect { it.name }.join(', ')}" : "All monitored sensors closed"
                paragraph stateText
            }
        }
    }
}

def installed() {
    logInfo("Installed v${VERSION}")
    initialize()
}

def updated() {
    logInfo("Updated settings for v${VERSION}")
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    ensureState()
    subscribe(contactSensors, "contact", "contactHandler")
    reconcileOpenState()
    evaluateSchedules("initialize")
}

def contactHandler(evt) {
    ensureState()
    String devId = evt?.deviceId?.toString()
    if (!devId) return

    Map openSince = (state.openSince instanceof Map) ? state.openSince : [:]
    Map cycleDevices = (state.cycleDevices instanceof Map) ? state.cycleDevices : [:]

    if (evt.value == "open") {
        Long openedAt = evt?.date?.time ?: now()
        if (!openSince.containsKey(devId)) {
            openSince[devId] = openedAt
            logDebug("${evt.displayName} opened at ${fmtTime(openedAt)}")
        }
        if (!cycleDevices.containsKey(devId)) {
            cycleDevices[devId] = [name: evt.displayName, openedAt: openedAt]
        }
        if (!(state.cycleStartMs instanceof Number)) {
            state.cycleStartMs = openedAt
        }
    } else if (evt.value == "closed") {
        if (openSince.containsKey(devId)) {
            openSince.remove(devId)
            logDebug("${evt.displayName} closed")
        }
    }

    state.openSince = openSince
    state.cycleDevices = cycleDevices

    evaluateSchedules("contact event ${evt.value}")
}

private void ensureState() {
    if (!(state.openSince instanceof Map)) state.openSince = [:]
    if (!(state.cycleDevices instanceof Map)) state.cycleDevices = [:]
    if (!(state.initialNotified instanceof Boolean)) state.initialNotified = false
    if (!(state.reminderCount instanceof Number)) state.reminderCount = 0
}

private void reconcileOpenState() {
    Map openSince = (state.openSince instanceof Map) ? state.openSince : [:]
    Map cycleDevices = (state.cycleDevices instanceof Map) ? state.cycleDevices : [:]
    Long nowMs = now()

    contactSensors?.each { dev ->
        String id = dev.id?.toString()
        String val = dev.currentValue("contact")
        if (val == "open") {
            if (!openSince.containsKey(id)) openSince[id] = nowMs
            if (!cycleDevices.containsKey(id)) cycleDevices[id] = [name: dev.displayName, openedAt: (openSince[id] as Long)]
            if (!(state.cycleStartMs instanceof Number)) state.cycleStartMs = openSince[id]
        } else {
            openSince.remove(id)
        }
    }

    state.openSince = openSince
    state.cycleDevices = cycleDevices
}

private void evaluateSchedules(String reason) {
    unschedule("openThresholdCheck")
    unschedule("reminderCheck")

    List<Map> openNow = getOpenDevicesNow()
    if (!openNow) {
        handleAllClosed(reason)
        return
    }

    if (state.initialNotified == true) {
        scheduleReminderIfNeeded()
        return
    }

    Long earliestOpen = openNow.collect { it.openedAt as Long }.min() as Long
    Long triggerAt = earliestOpen + (safeInt(openMinutes, 12) * 60_000L)
    Long remainingMs = triggerAt - now()

    if (remainingMs <= 0L) {
        sendOpenNotification(false)
    } else {
        runIn(Math.max((remainingMs / 1000L).toInteger(), 1), "openThresholdCheck", [overwrite: true])
        logTrace("Scheduled open-threshold check in ${Math.ceil(remainingMs / 1000.0d)} seconds (${reason})")
    }
}

def openThresholdCheck() {
    reconcileOpenState()
    List<Map> openNow = getOpenDevicesNow()
    if (!openNow) {
        handleAllClosed("openThresholdCheck")
        return
    }

    Long earliestOpen = openNow.collect { it.openedAt as Long }.min() as Long
    Long requiredMs = safeInt(openMinutes, 12) * 60_000L

    if (now() >= (earliestOpen + requiredMs)) {
        sendOpenNotification(false)
    } else {
        evaluateSchedules("threshold not yet met")
    }
}

def reminderCheck() {
    reconcileOpenState()
    if (!getOpenDevicesNow()) {
        handleAllClosed("reminderCheck")
        return
    }

    if (state.initialNotified != true) {
        openThresholdCheck()
        return
    }

    Integer maxCount = safeNullableInt(maxReminderCount, 3)
    Integer sentCount = safeInt(state.reminderCount, 0)
    if (maxCount != null && sentCount >= maxCount) {
        logInfo("Max reminders reached (${maxCount}); waiting for all contacts to close")
        return
    }

    sendOpenNotification(true)
}

private void scheduleReminderIfNeeded() {
    Integer intervalMin = Math.max(safeInt(repeatMinutes, 10), 1)
    runIn(intervalMin * 60, "reminderCheck", [overwrite: true])
    logTrace("Scheduled reminder in ${intervalMin} minute(s)")
}

private void sendOpenNotification(boolean reminder) {
    List<Map> openNow = getOpenDevicesNow()
    if (!openNow) return

    state.initialNotified = true

    String heading = reminder ? "Reminder: Garage fridge/freezer still open" : "Alert: Garage fridge/freezer left open"
    String details = openNow.collect { d ->
        "- ${d.name}: opened ${fmtTime(d.openedAt as Long)} (${fmtDuration(now() - (d.openedAt as Long))} ago)"
    }.join("\n")

    Integer maxCount = safeNullableInt(maxReminderCount, 3)
    Integer sentCount = safeInt(state.reminderCount, 0)
    String reminderInfo = (maxCount == null) ? "unlimited reminders" : "reminder ${sentCount + 1} of ${maxCount}"

    String msg = "${heading}\n${details}\nOpen threshold: ${safeInt(openMinutes, 12)} min"
    if (reminder) {
        msg += "\nRepeat every ${safeInt(repeatMinutes, 10)} min (${reminderInfo})"
    }

    sendNotification(msg)

    if (reminder) {
        state.reminderCount = sentCount + 1
    } else {
        state.reminderCount = 0
    }

    scheduleReminderIfNeeded()
}

private void handleAllClosed(String reason) {
    if (state.initialNotified == true) {
        Map cycleDevices = (state.cycleDevices instanceof Map) ? state.cycleDevices : [:]
        Long closeMs = now()
        String lines = cycleDevices.values().collect { data ->
            String nm = data.name ?: "Sensor"
            Long openedAt = (data.openedAt instanceof Number) ? (data.openedAt as Long) : closeMs
            "- ${nm}: opened ${fmtTime(openedAt)}, closed ${fmtTime(closeMs)} (open ${fmtDuration(closeMs - openedAt)})"
        }.join("\n")

        String msg = "All monitored fridge/freezer contacts are now closed.\n${lines}"
        sendNotification(msg)
        logInfo("All closed notification sent (${reason})")
    }

    state.openSince = [:]
    state.cycleDevices = [:]
    state.cycleStartMs = null
    state.initialNotified = false
    state.reminderCount = 0
}

private List<Map> getOpenDevicesNow() {
    Map openSince = (state.openSince instanceof Map) ? state.openSince : [:]
    List<Map> out = []
    contactSensors?.each { dev ->
        String id = dev.id?.toString()
        String val = dev.currentValue("contact")
        if (val == "open") {
            Long openedAt = (openSince[id] instanceof Number) ? (openSince[id] as Long) : now()
            out << [id: id, name: dev.displayName, openedAt: openedAt]
        }
    }
    return out
}

private void sendNotification(String msg) {
    logInfo(msg)
    try {
        notifyDevice?.deviceNotification(msg)
    } catch (Exception ex) {
        log.error "Failed sending notification: ${ex.message}"
    }
}

private Integer safeInt(def value, Integer fallback) {
    try {
        if (value == null || value.toString().trim() == "") return fallback
        return value.toString().toInteger()
    } catch (Exception ignored) {
        return fallback
    }
}

private Integer safeNullableInt(def value, Integer fallback) {
    try {
        if (value == null) return fallback
        String raw = value.toString().trim()
        if (raw == "") return null
        return raw.toInteger()
    } catch (Exception ignored) {
        return fallback
    }
}

private String fmtTime(Long epochMs) {
    if (!(epochMs instanceof Number)) return "unknown"
    TimeZone tz = location?.timeZone ?: TimeZone.getTimeZone("UTC")
    return new Date(epochMs).format("yyyy-MM-dd HH:mm:ss z", tz)
}

private String fmtDuration(Long ms) {
    Long totalSec = Math.max(((ms ?: 0L) / 1000L) as Long, 0L)
    Long h = (totalSec / 3600L) as Long
    Long m = ((totalSec % 3600L) / 60L) as Long
    Long s = (totalSec % 60L) as Long
    if (h > 0) return "${h}h ${m}m ${s}s"
    if (m > 0) return "${m}m ${s}s"
    return "${s}s"
}

private boolean isLevelEnabled(String requested) {
    Integer configured = LOG_LEVELS.indexOf((logLevel ?: "Info") as String)
    Integer desired = LOG_LEVELS.indexOf(requested)
    if (configured < 0) configured = 1
    if (desired < 0) desired = 1
    return configured >= desired && configured > 0
}

private void logTrace(String msg) { if (isLevelEnabled("Trace")) log.trace msg }
private void logDebug(String msg) { if (isLevelEnabled("Debug")) log.debug msg }
private void logInfo(String msg)  { if (isLevelEnabled("Info"))  log.info msg }
