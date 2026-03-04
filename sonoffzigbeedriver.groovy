metadata {
    definition(name: "Sonoff SNZB-02P Driver", namespace: "Bithome", author: "Alexander Tivadze") {
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Battery"
        capability "Configuration"
        capability "Refresh"
        attribute "presence", "string" // Attribute for tracking presence (present/not present)

        fingerprint profileId: "0104", inClusters: "0x0000,0x0001,0x0402,0x0405", manufacturer: "eWeLink", model: "SNZB-02P"
    }

    preferences {
        input name: "tempMinInterval", type: "number", title: "Temperature Min Interval (seconds)", defaultValue: 60, range: "1..3600"
        input name: "tempMaxInterval", type: "number", title: "Temperature Max Interval (seconds)", defaultValue: 600, range: "1..86400"
        input name: "tempChangeThreshold", type: "number", title: "Temperature Change Threshold (\u00b0C x100)", defaultValue: 30, range: "1..1000"

        input name: "humidityMinInterval", type: "number", title: "Humidity Min Interval (seconds)", defaultValue: 60, range: "1..3600"
        input name: "humidityMaxInterval", type: "number", title: "Humidity Max Interval (seconds)", defaultValue: 600, range: "1..86400"
        input name: "humidityChangeThreshold", type: "number", title: "Humidity Change Threshold (% x100)", defaultValue: 100, range: "1..1000"

        input name: "batteryMinInterval", type: "number", title: "Battery Min Interval (seconds)", defaultValue: 3600, range: "1..86400"
        input name: "batteryMaxInterval", type: "number", title: "Battery Max Interval (seconds)", defaultValue: 7200, range: "1..86400"
        input name: "batteryChangeThreshold", type: "number", title: "Battery Change Threshold", defaultValue: 1, range: "1..255"

        input name: "recoveryInterval", type: "number", title: "Recovery Interval (minutes)", defaultValue: 30, range: "1..1440"
        input name: "presenceTrigger", type: "number", title: "Consecutive Recovery Attempts Before Marking Not Present", defaultValue: 3, range: "1..10"
    }
}

def installed() {
    log.debug "Device installed - initializing."
    state.recoveryCounter = 0
    initialize()
}

def updated() {
    log.debug "Device updated - reconfiguring."
    unschedule()
    state.recoveryCounter = 0
    scheduleRecoveryCheck()

    // Call configure and execute returned commands
    def configCommands = configure()
    if (configCommands) {
        sendHubCommand(new hubitat.device.HubMultiAction(configCommands, hubitat.device.Protocol.ZIGBEE))
    }
    initialize()
}

def configure() {
    log.debug "Configuring Zigbee Reporting with user preferences."

    def commands = []

    // Temperature Reporting
    commands += zigbee.configureReporting(0x0402, 0x0000, 0x29,
        (tempMinInterval ?: 60) as Integer, (tempMaxInterval ?: 600) as Integer,
        (tempChangeThreshold ?: 30) as Integer
    )

    // Humidity Reporting
    commands += zigbee.configureReporting(0x0405, 0x0000, 0x21,
        (humidityMinInterval ?: 60) as Integer, (humidityMaxInterval ?: 600) as Integer,
        (humidityChangeThreshold ?: 100) as Integer
    )

    // Battery Reporting
    commands += zigbee.configureReporting(0x0001, 0x0020, 0x20,
        (batteryMinInterval ?: 3600) as Integer, (batteryMaxInterval ?: 7200) as Integer,
        (batteryChangeThreshold ?: 1) as Integer
    )

    log.debug "Zigbee Reporting Configuration Commands: $commands"
    return commands.isEmpty() ? [] : commands // Return an empty list if no commands
}

def refresh() {
    log.debug "Refreshing device attributes."
    state.recoveryCounter = 0 // Reset counter on successful refresh
    sendEvent(name: "presence", value: "present") // Mark device as present
    return zigbee.readAttribute(0x0402, 0x0000) + // Temperature
           zigbee.readAttribute(0x0405, 0x0000) + // Humidity
           zigbee.readAttribute(0x0001, 0x0020) // Battery
}

def parse(String description) {
    log.debug "Parsing Zigbee message: $description"
    def map = zigbee.getEvent(description)
    if (map) {
        sendEvent(map)
    } else {
        def descMap = zigbee.parseDescriptionAsMap(description)
        log.debug "Parsed Zigbee description map: $descMap"
        handleCustomAttributes(descMap)
    }
}

private handleCustomAttributes(descMap) {
    def map = [:]
    if (descMap.cluster == "0402" && descMap.attrId == "0000") { // Temperature
        map.name = "temperature"
        map.value = zigbee.convertHexToInt(descMap.value) / 100.0
        map.unit = "\u00b0C"
    } else if (descMap.cluster == "0405" && descMap.attrId == "0000") { // Humidity
        map.name = "humidity"
        map.value = zigbee.convertHexToInt(descMap.value) / 100.0
        map.unit = "%"
    } else if (descMap.cluster == "0001" && descMap.attrId == "0021") { // Battery
        map.name = "battery"
        map.value = zigbee.convertHexToInt(descMap.value) / 2
        map.unit = "%"
    }
    if (map) {
        log.debug "Sending event: $map"
        sendEvent(map)
    }
}

def recoveryEvent() {
    state.recoveryCounter = (state.recoveryCounter ?: 0) + 1
    log.warn "Running recoveryEvent() - Attempt #${state.recoveryCounter}."

    if (state.recoveryCounter >= (presenceTrigger ?: 3)) {
        log.warn "Device marked as not present after ${state.recoveryCounter} recovery attempts."
        sendEvent(name: "presence", value: "not present")
    } else {
        refresh() + configure()
    }
}

private scheduleRecoveryCheck() {
    def interval = (recoveryInterval ?: 30) * 60 // Convert minutes to seconds
    log.debug "Scheduling recovery check every ${interval / 60} minutes."
    runIn(interval, checkEventInterval)
}

def checkEventInterval() {
    log.debug "Running checkEventInterval() to verify device events."

    def lastEventTime = device.currentState("temperature")?.date?.time ?: 0
    def currentTime = now()
    def timeSinceLastEvent = (currentTime - lastEventTime) / 1000 // in seconds

    log.debug "Time since last temperature event: ${timeSinceLastEvent}s"

    if (timeSinceLastEvent > ((tempMaxInterval ?: 600) * 2)) {
        log.warn "No temperature event received within expected interval. Initiating recovery."
        recoveryEvent()
    }
}

private initialize() {
    log.debug "Initializing device configuration."
    configure()
}
