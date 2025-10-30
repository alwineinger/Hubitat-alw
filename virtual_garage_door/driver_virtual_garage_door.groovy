/**
 * 20250507 ALW - remarked out Switch capability
 *
 *  Z-Wave Garage Door Opener
 *
 *  Copyright 2014 SmartThings
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
 */
metadata {
	definition (name: "Simulated Garage Door Opener V2", namespace: "smartthings/testing", author: "SmartThings") {
		capability "Actuator"
		capability "Door Control"
        capability "Garage Door Control"
		capability "Contact Sensor"
		capability "Refresh"
		capability "Sensor"
		capability "Health Check"   
     /*   capability "Switch" */
        capability "Configuration"
        
	}

	simulator {
		
	}

	tiles {
		standardTile("toggle", "device.door", width: 2, height: 2) {
			state("closed", label:'${name}', action:"door control.open", icon:"st.doors.garage.garage-closed", backgroundColor:"#00A0DC", nextState:"opening")
			state("open", label:'${name}', action:"door control.close", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13", nextState:"closing")
		    state("opening", label:'${name}', icon:"st.doors.garage.garage-closed", backgroundColor:"#e86d13")
			state("closing", label:'${name}', icon:"st.doors.garage.garage-open", backgroundColor:"#00A0DC")
			
		}
        
        standardTile("toggle", "device.garageDoor", width: 2, height: 2) {
			state("closed", label:'${name}', action:"garage door control.open", icon:"st.doors.garage.garage-closed", backgroundColor:"#00A0DC", nextState:"opening")
			state("open", label:'${name}', action:"gharage door control.close", icon:"st.doors.garage.garage-open", backgroundColor:"#e86d13", nextState:"closing")
			state("opening", label:'${name}', icon:"st.doors.garage.garage-closed", backgroundColor:"#e86d13")
			state("closing", label:'${name}', icon:"st.doors.garage.garage-open", backgroundColor:"#00A0DC")
			
		}
		standardTile("open", "device.door", inactiveLabel: false, decoration: "flat") {
			state "default", label:'open', action:"door control.open", icon:"st.doors.garage.garage-opening"
		}
		standardTile("close", "device.door", inactiveLabel: false, decoration: "flat") {
			state "default", label:'close', action:"door control.close", icon:"st.doors.garage.garage-closing"
		}

		standardTile("open", "device.garageDoor", inactiveLabel: false, decoration: "flat") {
			state "default", label:'open', action:"garage door control.open", icon:"st.doors.garage.garage-opening"
		}
		standardTile("close", "device.garageDoor", inactiveLabel: false, decoration: "flat") {
			state "default", label:'close', action:"garage door control.close", icon:"st.doors.garage.garage-closing"
		}
		main "toggle"
		details(["toggle", "open", "close"])

}
}

def parse(String description) {
	log.trace "parse($description)"
}

def open() {
	sendEvent(name: "door", value: "opening")
    sendEvent(name: "garageDoor", value: "opening")
    runIn(2, finishOpening)
}

def close() {
    sendEvent(name: "door", value: "closing")
    sendEvent(name: "garageDoor", value: "closing")
	runIn(2, finishClosing)
}

def finishOpening() {
    sendEvent(name: "door", value: "open")
    sendEvent(name: "garageDoor", value: "open")
    sendEvent(name: "contact", value: "open")
}

def finishClosing() {
    sendEvent(name: "door", value: "closed")
    sendEvent(name: "garageDoor", value: "closed")
    sendEvent(name: "contact", value: "closed")
}

def configure()
{
    installed()
}

def installed() {
	log.debug "Executing 'installed'"
	initialize()
}

def refresh() {
    sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
}

def ping() {
refresh()
}

def updated() {
	log.debug "Executing 'updated'"
	initialize()
}

private initialize() {
	log.debug "Executing 'initialize'"

	sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
	sendEvent(name: "healthStatus", value: "online")
	//sendEvent(name: "DeviceWatch-Enroll", value: [protocol: "cloud", scheme:"untracked"].encodeAsJson(), displayed: false)
}

def on()
{
	open()
}

def off()
{
	close()
}

