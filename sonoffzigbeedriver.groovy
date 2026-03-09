metadata {
    definition(name: "Sonoff SNZB-02P Driver", namespace: "Bithome", author: "Alexander Tivadze") {
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Battery"
        capability "Configuration"
        capability "Refresh"

        fingerprint profileId: "0104", inClusters: "0x0000,0x0001,0x0402,0x0405", manufacturer: "eWeLink", model: "SNZB-02P"
    }

    preferences {
        input name: "tempMinInterval", type: "number", title: "Temperature Min Interval (seconds)", defaultValue: 60, range: "1..3600"
        input name: "tempMaxInterval", type: "number", title: "Temperature Max Interval (seconds)", defaultValue: 600, range: "1..86400"
        input name: "tempChangeThreshold", type: "number", title: "Temperature Change Threshold (°C x100)", defaultValue: 30, range: "1..1000"

        input name: "humidityMinInterval", type: "number", title: "Humidity Min Interval (seconds)", defaultValue: 60, range: "1..3600"
        input name: "humidityMaxInterval", type: "number", title: "Humidity Max Interval (seconds)", defaultValue: 600, range: "1..86400"
        input name: "humidityChangeThreshold", type: "number", title: "Humidity Change Threshold (% x100)", defaultValue: 100, range: "1..1000"
        input name: "humidityOffset", type: "decimal", title: "Humidity Offset (%)", defaultValue: 0, range: "-20..20"

        input name: "batteryMinInterval", type: "number", title: "Battery Min Interval (seconds)", defaultValue: 3600, range: "1..86400"
        input name: "batteryMaxInterval", type: "number", title: "Battery Max Interval (seconds)", defaultValue: 7200, range: "1..86400"
        input name: "batteryChangeThreshold", type: "number", title: "Battery Change Threshold", defaultValue: 1, range: "1..255"
    }
}

def installed() {
    log.debug "Device installed - initializing."
    initialize()
}

def updated() {
    log.debug "Device updated - reconfiguring."
    unschedule()
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
    commands += zigbee.configureReporting(0x0001, 0x0021, 0x20,
        (batteryMinInterval ?: 3600) as Integer, (batteryMaxInterval ?: 7200) as Integer,
        (batteryChangeThreshold ?: 1) as Integer
    )

    log.debug "Zigbee Reporting Configuration Commands: $commands"
    return commands.isEmpty() ? [] : commands // Return an empty list if no commands
}

def refresh() {
    log.debug "Refreshing device attributes."
    return zigbee.readAttribute(0x0402, 0x0000) + // Temperature
           zigbee.readAttribute(0x0405, 0x0000) + // Humidity
           zigbee.readAttribute(0x0001, 0x0021) // Battery percentage remaining
}

def parse(String description) {
    log.debug "Parsing Zigbee message: $description"
    def map = zigbee.getEvent(description)
    if (map) {
        if (map.name == "humidity") {
            map.value = applyHumidityOffset(map.value)
            map.unit = "%"
        }
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
        map.unit = "°C"
    } else if (descMap.cluster == "0405" && descMap.attrId == "0000") { // Humidity
        map.name = "humidity"
        map.value = applyHumidityOffset(zigbee.convertHexToInt(descMap.value) / 100.0)
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

private initialize() {
    log.debug "Initializing device configuration."
    def commands = []
    commands += configure()
    commands += refresh()
    if (commands) {
        sendHubCommand(new hubitat.device.HubMultiAction(commands, hubitat.device.Protocol.ZIGBEE))
    }
}

private BigDecimal applyHumidityOffset(def humidityValue) {
    BigDecimal rawHumidity = 0
    try {
        rawHumidity = humidityValue.toBigDecimal()
    } catch (Exception ignored) {
        log.warn "Received non-numeric humidity value: ${humidityValue}"
        return 0
    }

    BigDecimal offset = 0
    try {
        offset = (humidityOffset ?: 0).toBigDecimal()
    } catch (Exception ignored) {
        offset = 0
    }

    def adjustedHumidity = Math.max(0, Math.min(100, rawHumidity + offset))
    log.debug "Humidity calibration raw=${rawHumidity}% offset=${offset}% adjusted=${adjustedHumidity}%"
    return adjustedHumidity as BigDecimal
}
