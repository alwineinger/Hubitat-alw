/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0 
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Author: LGKahn kahn-st@lgk.com
 *  version 2 user defineable timeout before checking if door opened or closed correctly. Raised default to 25 secs. You can reduce it to 15 secs. if you have custom simulated door with < 6 sec wait.
 * Version 3. add code to get name of device so messages don't alwasy says garage door for instance if you are using it on a fence gate etc.
 * Version 4 . Port to Hubitat. 
*  version 4.1 change doorcontrol to virtualgaragedoor for hubitat.

* lgk 2/24 v 4.2 add option to blink a light x times with y delay between each blink before closing
*/

definition(
    name: "LGK Virtual Garage Door",
    namespace: "lgkapps",
    author: "lgkahn kahn-st@lgk.com",
    description: "Sync the Simulated garage door device/gate/fence with 2 actual devices, either a tilt or contact sensor and a switch or relay. The simulated device will then control the actual garage door. In addition, the virtual device will sync when the garage door is opened manually, \n It also attempts to double check the door was actually closed in case the beam was crossed. ",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png"
)

preferences {
	section("Choose the switch/relay that opens closes the garage?"){
		input "opener", "capability.switch", title: "Physical Garage Opener?", required: true
	}
	section("Choose the sensor that senses if the garage is open closed? "){
		input "sensor", "capability.contactSensor", title: "Physical Garage Door Open/Closed?", required: true
	}
    
	section("Choose the Virtual Garage Door Device? "){
		input "virtualgd", "capability.garageDoorControl", title: "Virtual Garage Door?", required: true
	}
    
	section("Choose the Virtual Garage Door Device sensor (same as above device)?"){
		input "virtualgdbutton", "capability.contactSensor", title: "Virtual Garage Door Open/Close Sensor?", required: true
	}
    
    section("Timeout before checking if the door opened or closed correctly?"){
		input "checkTimeout", "number", title: "Door Operation Check Timeout?", required: true, defaultValue: 25
	}
    
      
 section("Blinking light on closing options"){
		input "blink", "bool", title: "Enable a light to blink to signal closing?", required: true, defaultValue: false
  
     
		input "theLight", "capability.switch", title: "Choose a light?", required: false
		input "blinkTimes", "number", title: "Number of times to blink before closing door?", required: false, defaultValue: 6
	      
            input "blinkTime", "enum", title: "Blink on/off time in seconds?" , options: [
                                "1/2 second",
                                "1 second",
                                "2 seconds"
                              
			], required: false, defaultValue: "1/2 second"
	} 
    
     section( "Notifications" ) {
       // input("recipients", "contact", title: "Send notifications to") {
        input "sendPushMessage", "capability.notification", title: "Send Push Notifications? - Notification Devices: Hubitat PhoneApp or Other?", multiple: true, required: false

           }
         
       section("Logging" ) {
          input("debug", "bool", title: "Enable logging?", required: true, defaultValue: false)
          input("descLog", "bool", title: "Enable descriptionText logging", required: true, defaultValue: true)
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
    def realgdstate = sensor.currentContact
    def virtualgdstate = virtualgd.currentContact

    subscribe(sensor, "contact", contactHandler)
    subscribe(virtualgdbutton, "contact", virtualgdcontactHandler)

    cancelBlinkSequence(true)
    state.remove("pendingOperations")

    if (realgdstate != virtualgdstate) {
        if (realgdstate == "open") {
            logInfo "Synchronizing virtual door to open."
            virtualgd.open()
        } else {
            logInfo "Synchronizing virtual door to closed."
            virtualgd.close()
        }
    }

    logInfoAlways "Descriptive Text logging is ${descLog ? 'on' : 'off'}."

    if (debug) {
        logInfoAlways "Debug logging is on. Turning off debug logging in 1/2 hour."
        runIn(1800, "logsOff")
    } else {
        logInfoAlways "Debug logging is off."
    }
}


def contactHandler(evt) {
    def virtualgdstate = virtualgd.currentContact

    if ("open" == evt.value) {
        logDebug "Physical contact reported open."
        cancelOperationCheck("open")
        if (virtualgdstate != "open") {
            mysend("${virtualgd.displayName} Opened. Manually syncing with Virtual Device!")
            virtualgd.open()
        }
    }
    if ("closed" == evt.value) {
        logDebug "Physical contact reported closed."
        cancelOperationCheck("close")
        if (virtualgdstate != "closed") {
            mysend("${virtualgd.displayName} Closed. Manually syncing with Virtual Device!")
            virtualgd.close()
        }
    }
}

def virtualgdcontactHandler(evt) {
    def realgdstate = sensor.currentContact

    logDebug "Virtual contact reported ${evt.value}. checkTimeout=${checkTimeout}"

    if ("open" == evt.value) {
        if (realgdstate != "open") {
            logInfo "Opening real garage door to correspond with button press."
            mysend("${virtualgd.displayName} Opened. Syncing with Actual Device!")
            opener.on()
            scheduleOperationCheck("open")
        }
    }
    if ("closed" == evt.value) {
        if (realgdstate != "closed") {
            logInfo "Closing real garage door to correspond with button press."
            mysend("${virtualgd.displayName} Closed. Syncing with Actual Device!")
            closeTheDoor()
        }
    }
}


private mysend(msg) {
    if (location.contactBookEnabled && recipients) {
        logDebug "Sending notifications to ${recipients?.size()} contacts."
        sendNotificationToContacts(msg, recipients)
    } else if (sendPushMessage) {
        logDebug "Sending push notification."
        sendPushMessage.deviceNotification(msg)
    }
}

def checkIfActuallyClosed() {
    def realgdstate = sensor.currentContact
    def virtualgdstate = virtualgd.currentContact

    logDebug "Checking if door actually closed. real=${realgdstate}, virtual=${virtualgd.currentContact}"

    if (realgdstate == "open" && virtualgdstate == "closed") {
        logDebug "Re-opening virtual door because the real door is still open."
        mysend("Resetting ${virtualgd.displayName} to Open as real device didn't close (beam probably crossed)!")
        virtualgd.open()
    }

    clearPendingOperation("close")
}

def checkIfActuallyOpened() {
    def realgdstate = sensor.currentContact
    def virtualgdstate = virtualgd.currentContact

    logDebug "Checking if door actually opened. real=${realgdstate}, virtual=${virtualgd.currentContact}"

    if (realgdstate == "closed" && virtualgdstate == "open") {
        logDebug "Re-closing virtual door because the real door is still closed."
        mysend("Resetting ${virtualgd.displayName} to Closed as real device didn't open! (track blocked?)")
        virtualgd.close()
    }

    clearPendingOperation("open")
}

def closeTheDoor() {
    logDebug "Requested door close. blink=${blink}, lightConfigured=${theLight != null}" 

    cancelBlinkSequence(true)

    if (blink && theLight && (blinkTimes ?: 0) > 0) {
        startBlinkSequence()
    } else {
        performDoorClose()
    }
}

def logsOff()
{
    logInfoAlways "Turning off Debug Logging"
    app.updateSetting("debug", [value: "false", type: "bool"])
}

private void startBlinkSequence() {
    Integer cycles = (blinkTimes ?: 0) as Integer
    Integer delayMillis = blinkDelayMillis()

    if (!cycles) {
        logDebug "Blink sequence skipped due to zero cycles."
        performDoorClose()
        return
    }

    state.blinkSequence = [remaining: cycles * 2, delay: delayMillis, isOn: false]
    logDebug "Starting blink sequence for ${cycles} cycles with ${delayMillis}ms delay."
    handleBlinkStep()
}

def handleBlinkStep() {
    def blinkData = state.blinkSequence

    if (!blinkData) {
        logDebug "Blink sequence no longer active; closing door."
        performDoorClose()
        return
    }

    if (blinkData.remaining <= 0) {
        logDebug "Blink sequence complete."
        if (blinkData.isOn && theLight) {
            theLight.off()
        }
        state.remove("blinkSequence")
        performDoorClose()
        return
    }

    if (!theLight) {
        logDebug "Blink light not configured during sequence; closing door."
        state.remove("blinkSequence")
        performDoorClose()
        return
    }

    if (blinkData.isOn) {
        theLight.off()
    } else {
        theLight.on()
    }

    blinkData.isOn = !blinkData.isOn
    blinkData.remaining = (blinkData.remaining as Integer) - 1
    state.blinkSequence = blinkData

    runInMillis(blinkData.delay as Integer, "handleBlinkStep")
}

private void performDoorClose() {
    unschedule("handleBlinkStep")

    def blinkData = state.remove("blinkSequence")
    if (blinkData?.isOn && theLight) {
        theLight.off()
    }

    logDebug "Sending close command to opener."
    opener.on()
    scheduleOperationCheck("close")
}

private void cancelBlinkSequence(boolean turnOffLight) {
    unschedule("handleBlinkStep")
    def blinkData = state.remove("blinkSequence")
    if (turnOffLight && blinkData?.isOn && theLight) {
        theLight.off()
    }
}

private Integer blinkDelayMillis() {
    switch (blinkTime) {
        case "1 second":
            return 1000
        case "2 seconds":
            return 2000
        default:
            return 500
    }
}

private void scheduleOperationCheck(String type) {
    String handlerName = type == "open" ? "checkIfActuallyOpened" : "checkIfActuallyClosed"
    Integer delaySeconds = (checkTimeout ?: 25) as Integer

    cancelOperationCheck(type, handlerName)

    state.pendingOperations = state.pendingOperations ?: [:]
    state.pendingOperations[type] = now()

    logDebug "Scheduling ${handlerName} in ${delaySeconds} seconds."
    runIn(delaySeconds, handlerName)
}

private void cancelOperationCheck(String type, String handlerName = null) {
    String handler = handlerName ?: (type == "open" ? "checkIfActuallyOpened" : "checkIfActuallyClosed")
    unschedule(handler)

    if (state.pendingOperations?.remove(type)) {
        logDebug "Cancelled pending ${type} operation check."
    }

    if (state.pendingOperations && state.pendingOperations.isEmpty()) {
        state.remove("pendingOperations")
    }
}

private void clearPendingOperation(String type) {
    cancelOperationCheck(type)
}

private void logDebug(String msg) {
    if (debug) {
        log.debug "${app.label ?: app.name}: ${msg}"
    }
}

private void logInfo(String msg) {
    if (descLog) {
        log.info "${app.label ?: app.name}: ${msg}"
    }
}

private void logInfoAlways(String msg) {
    log.info "${app.label ?: app.name}: ${msg}"
}

