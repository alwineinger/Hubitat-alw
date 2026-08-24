/**
 * Copyright 2014 SmartThings
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
 * Virtual garage door state endpoint. Physical feedback is supplied by the
 * companion app; this driver never guesses that a door has completed travel.
 */
metadata {
    definition(name: "Simulated Garage Door Opener V2", namespace: "smartthings/testing", author: "SmartThings") {
        capability "Actuator"
        capability "Garage Door Control"
        capability "Contact Sensor"
        capability "Refresh"
        capability "Sensor"

        command "setDoorState", [[name: "state", type: "ENUM", constraints: ["open", "closed"]]]
    }
}

def installed() {
    log.debug "Executing installed"
    // A new device has no physical feedback until the app performs its first sync.
    if (!device.currentValue("door")) {
        sendEvent(name: "door", value: "unknown", descriptionText: "${device.displayName} state is unknown")
    }
}

def updated() {
    log.debug "Executing updated"
}

def open() {
    publishDoor("opening")
}

def close() {
    publishDoor("closing")
}

/** Called only with terminal state derived from the physical contact sensor. */
def setDoorState(String requestedState) {
    String terminalState = requestedState?.toLowerCase()
    if (!(terminalState in ["open", "closed"])) {
        log.warn "Ignoring invalid terminal door state: ${requestedState}"
        return
    }

    publishDoor(terminalState)
    publishContact(terminalState)
}

/** Manual recovery for integrations that need an authoritative repeat event. */
def refresh() {
    String contactState = device.currentValue("contact")
    String doorState = device.currentValue("door")
    String stableState = contactState in ["open", "closed"] ? contactState : (doorState in ["open", "closed"] ? doorState : null)
    if (!stableState) {
        log.warn "Refresh skipped: no stable open/closed state is available."
        return
    }
    sendEvent(name: "door", value: stableState, descriptionText: "${device.displayName} refreshed as ${stableState}", isStateChange: true)
    sendEvent(name: "contact", value: stableState, descriptionText: "${device.displayName} refreshed as ${stableState}", isStateChange: true)
}

// Backward-compatible aliases for callers that used the former Switch-style API.
def on() {
    open()
}

def off() {
    close()
}

private void publishDoor(String value) {
    sendEvent(name: "door", value: value, descriptionText: "${device.displayName} is ${value}")
}

private void publishContact(String value) {
    sendEvent(name: "contact", value: value, descriptionText: "${device.displayName} is ${value}")
}
