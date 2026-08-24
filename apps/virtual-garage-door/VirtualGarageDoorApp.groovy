/**
 * Copyright 2015 SmartThings
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 * Synchronizes a virtual GarageDoorControl device with a momentary opener and
 * an authoritative physical contact sensor.
 */
definition(
    name: "LGK Virtual Garage Door",
    namespace: "lgkapps",
    author: "lgkahn kahn-st@lgk.com",
    description: "Sync a virtual garage door with a physical contact sensor and opener relay.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png",
    singleThreaded: true
)

preferences {
    section("Garage door devices") {
        input "opener", "capability.switch", title: "Physical garage opener relay", required: true
        input "sensor", "capability.contactSensor", title: "Physical garage door open/closed sensor", required: true
        input "virtualgd", "capability.garageDoorControl", title: "Virtual garage door", required: true
    }
    section("Operation safety") {
        input "checkTimeout", "number", title: "Operation timeout (seconds)", defaultValue: 25, required: true
        input "openerAutoResets", "bool", title: "Opener relay resets itself", defaultValue: true, required: true
        input "openerPulseMillis", "number", title: "Relay pulse duration when app-managed (milliseconds)", defaultValue: 500, required: true
    }
    section("Blinking light before closing") {
        input "blink", "bool", title: "Enable a light to blink before closing", defaultValue: false, required: true
        input "theLight", "capability.switch", title: "Warning light", required: false
        input "blinkTimes", "number", title: "Number of blink cycles", defaultValue: 6, required: false
        input "blinkTime", "enum", title: "Blink on/off time", options: ["1/2 second", "1 second", "2 seconds"], defaultValue: "1/2 second", required: false
    }
    section("Notifications") {
        input "sendPushMessage", "capability.notification", title: "Notification devices", multiple: true, required: false
    }
    section("Logging") {
        input "debug", "bool", title: "Enable debug logging", defaultValue: false, required: true
        input "descLog", "bool", title: "Enable info logging", defaultValue: true, required: true
    }
}

def installed() {
    logInfoAlways "Installed"
    initialize()
}

def updated() {
    logInfoAlways "Updated"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    cancelAllWork(true)
    subscribe(sensor, "contact", contactHandler)
    subscribe(virtualgd, "door", virtualDoorHandler)
    synchronizeFromSensor("initialization")
    logInfoAlways "Info logging is ${descLog ? 'on' : 'off'}."
    if (debug) {
        runIn(1800, "logsOff")
    }
}

def contactHandler(evt) {
    String physicalState = evt?.value
    if (!(physicalState in ["open", "closed"])) {
        logInfo "Ignoring unknown physical contact state '${physicalState}'."
        return
    }

    def pending = state.pendingOperation
    if (pending && pending.target != physicalState) {
        logDebug "Ignoring physical ${physicalState} while waiting for ${pending.target} (phase ${pending.phase})."
        return
    }

    if (pending) {
        cancelAllWork(true)
        virtualgd.setDoorState(physicalState)
        mysend("${virtualgd.displayName} ${physicalState == 'open' ? 'opened' : 'closed'} (confirmed by physical sensor).")
    } else {
        virtualgd.setDoorState(physicalState)
        logInfo "Physical sensor reported ${physicalState}; virtual door synchronized."
    }
}

def virtualDoorHandler(evt) {
    String requestedState = evt?.value
    if (!(requestedState in ["opening", "closing"])) {
        // Terminal reports come from setDoorState and must never pulse the opener.
        logDebug "Ignoring virtual terminal/unknown door event '${requestedState}'."
        return
    }
    requestDoor(requestedState == "opening" ? "open" : "closed")
}

private void requestDoor(String target) {
    String physicalState = sensor.currentContact
    if (!(physicalState in ["open", "closed"])) {
        logInfo "Cannot ${target == 'open' ? 'open' : 'close'}: physical sensor state is unknown."
        return
    }

    def pending = state.pendingOperation
    if (pending) {
        if (pending.target == target) {
            logDebug "Ignoring duplicate ${target} request."
            return
        }
        if (pending.phase == "warning" && target == "open") {
            logInfo "Open request cancels pending close warning."
            cancelAllWork(true)
        } else {
            reassertPendingTransition(pending.target)
            logInfo "Ignoring ${target} request while ${pending.target} operation is already actuated."
            return
        }
    }

    if (physicalState == target) {
        virtualgd.setDoorState(target)
        return
    }

    if (target == "closed" && blink && theLight && blinkCycles() > 0) {
        beginCloseWarning()
    } else {
        actuateOpener(target)
    }
}

/** Reassert the in-progress state after a rejected opposite HomeKit command. */
private void reassertPendingTransition(String target) {
    if (target == "open") {
        virtualgd.open()
    } else if (target == "closed") {
        virtualgd.close()
    }
}

private void beginCloseWarning() {
    Long token = nextToken()
    state.pendingOperation = [target: "closed", phase: "warning", token: token]
    state.blinkSequence = [token: token, remaining: blinkCycles() * 2, originalSwitch: theLight.currentSwitch]
    logInfo "Close requested; blinking warning light before actuating opener."
    handleBlinkStep([token: token])
}

def handleBlinkStep(data = null) {
    def sequence = state.blinkSequence
    Long token = data?.token as Long
    if (!sequence || sequence.token != token || state.pendingOperation?.phase != "warning") {
        logDebug "Ignoring stale blink callback."
        return
    }
    if (!theLight) {
        finishCloseWarning(token)
        return
    }
    if ((sequence.remaining as Integer) <= 0) {
        restoreWarningLight(sequence)
        state.remove("blinkSequence")
        finishCloseWarning(token)
        return
    }

    setSwitch(theLight, theLight.currentSwitch != "on")
    sequence.remaining = (sequence.remaining as Integer) - 1
    state.blinkSequence = sequence
    runInMillis(blinkDelayMillis(), "handleBlinkStep", [overwrite: false, data: [token: token]])
}

private void finishCloseWarning(Long token) {
    if (state.pendingOperation?.token != token || state.pendingOperation?.phase != "warning") {
        return
    }
    actuateOpener("closed", token)
}

private void actuateOpener(String target, Long existingToken = null) {
    Long token = existingToken ?: nextToken()
    if (state.pendingOperation?.token != token && existingToken) {
        return
    }
    state.remove("blinkSequence")
    state.pendingOperation = [target: target, phase: "actuated", token: token]

    boolean turnedOn = setSwitch(opener, true)
    if (!turnedOn && opener.currentSwitch == "on") {
        logInfo "Opener relay is already on; refusing to send a duplicate pulse."
        state.remove("pendingOperation")
        synchronizeFromSensor("opener relay was already on")
        return
    }
    state.pendingOperation = [target: target, phase: "actuated", token: token, relayTurnedOn: turnedOn]
    mysend("${virtualgd.displayName} ${target == 'open' ? 'opening' : 'closing'} requested.")
    logInfo "Actuated opener for ${target}."

    if (!openerAutoResetsEnabled() && turnedOn) {
        state.relayPulse = [token: token]
        runInMillis(relayPulseMillis(), "releaseOpener", [overwrite: false, data: [token: token]])
    }
    runIn(operationTimeoutSeconds(), "operationTimedOut", [overwrite: false, data: [token: token]])
}

def releaseOpener(data = null) {
    Long token = data?.token as Long
    if (state.relayPulse?.token != token || state.pendingOperation?.token != token || !state.pendingOperation?.relayTurnedOn) {
        return
    }
    setSwitch(opener, false)
    state.remove("relayPulse")
}

def operationTimedOut(data = null) {
    Long token = data?.token as Long
    def pending = state.pendingOperation
    if (!pending || pending.token != token || pending.phase != "actuated") {
        logDebug "Ignoring stale operation timeout."
        return
    }
    String physicalState = sensor.currentContact
    String target = pending.target
    cancelAllWork(true)
    if (physicalState in ["open", "closed"]) {
        virtualgd.setDoorState(physicalState)
        if (physicalState == target) {
            mysend("${virtualgd.displayName} ${physicalState == 'open' ? 'opened' : 'closed'} (confirmed by physical sensor after timeout).")
        } else {
            mysend("${virtualgd.displayName} failed to ${target == 'open' ? 'open' : 'close'}; physical sensor is still ${physicalState}.")
        }
    } else {
        logInfo "Operation timeout skipped because physical sensor is '${physicalState}'."
    }
}

private void synchronizeFromSensor(String reason) {
    String physicalState = sensor.currentContact
    if (!(physicalState in ["open", "closed"])) {
        logInfo "Skipping ${reason} synchronization because physical sensor state is '${physicalState}'."
        return
    }
    virtualgd.setDoorState(physicalState)
    logInfo "Synchronized virtual door to ${physicalState} during ${reason}."
}

private void cancelAllWork(boolean restoreLight) {
    unschedule("handleBlinkStep")
    unschedule("operationTimedOut")
    unschedule("releaseOpener")
    if (restoreLight && state.blinkSequence) {
        restoreWarningLight(state.blinkSequence)
    }
    // A manual-pulse relay must be released when its completion or timeout is
    // cancelled; auto-reset relays are deliberately left to their hardware.
    if (!openerAutoResetsEnabled() && state.pendingOperation?.relayTurnedOn) {
        setSwitch(opener, false)
    }
    state.remove("blinkSequence")
    state.remove("pendingOperation")
    state.remove("relayPulse")
}

private void restoreWarningLight(def sequence) {
    if (!theLight || !(sequence?.originalSwitch in ["on", "off"])) {
        return
    }
    setSwitch(theLight, sequence.originalSwitch == "on")
}

/** Returns true only when this app actually issued the command. */
private boolean setSwitch(deviceToSet, boolean on) {
    String desired = on ? "on" : "off"
    if (deviceToSet?.currentSwitch == desired) {
        return false
    }
    if (on) {
        deviceToSet.on()
    } else {
        deviceToSet.off()
    }
    return true
}

private Integer operationTimeoutSeconds() {
    Integer value = safeInteger(checkTimeout, 25)
    return Math.max(5, Math.min(value, 300))
}

private Integer relayPulseMillis() {
    Integer value = safeInteger(openerPulseMillis, 500)
    return Math.max(100, Math.min(value, 5000))
}

/** Existing installations have no saved value for this new preference. */
private boolean openerAutoResetsEnabled() {
    if (openerAutoResets == null) {
        return true
    }
    return openerAutoResets instanceof Boolean ? openerAutoResets : openerAutoResets.toString().toBoolean()
}

private Integer blinkCycles() {
    return Math.max(0, Math.min(safeInteger(blinkTimes, 6), 20))
}

private Integer blinkDelayMillis() {
    switch (blinkTime) {
        case "1 second": return 1000
        case "2 seconds": return 2000
        default: return 500
    }
}

private Integer safeInteger(def value, Integer fallback) {
    try {
        return value == null ? fallback : value as Integer
    } catch (Exception ignored) {
        return fallback
    }
}

private Long nextToken() {
    Long token = ((state.operationToken ?: 0L) as Long) + 1L
    state.operationToken = token
    return token
}

private void mysend(String msg) {
    if (sendPushMessage) {
        sendPushMessage.deviceNotification(msg)
    }
}

def logsOff() {
    app.updateSetting("debug", [value: "false", type: "bool"])
}

private void logDebug(String msg) { if (debug) log.debug "${app.label ?: app.name}: ${msg}" }
private void logInfo(String msg) { if (descLog) log.info "${app.label ?: app.name}: ${msg}" }
private void logInfoAlways(String msg) { log.info "${app.label ?: app.name}: ${msg}" }
