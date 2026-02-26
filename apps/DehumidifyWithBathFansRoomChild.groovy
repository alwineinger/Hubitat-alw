/**
 *  Dehumidify With Bath Fans - Room Child
 *
 *  One child per room.
 */
def VERSION = "0.2.1"

definition(
    name: "Dehumidify With Bath Fans Room Child",
    namespace: "alw",
    author: "Codex",
    description: "Room controller for bath-fan dehumidification",
    category: "Convenience",
    iconUrl: "https://raw.githubusercontent.com/HubitatCommunity/HubitatPublic/master/resources/icons/app-Coordinator.png",
    iconX2Url: "https://raw.githubusercontent.com/HubitatCommunity/HubitatPublic/master/resources/icons/app-Coordinator@2x.png",
    singleInstance: false,
    parent: "alw:Dehumidify With Bath Fans Parent"
)

preferences {
    page(name: "mainPage", title: "Room Controller", install: true, uninstall: true)
}

def mainPage() {
    dynamicPage(name: "mainPage") {
        section("Room identity") {
            label title: "Child app name (e.g., Primary Bath)", required: true
            paragraph "Tip: rename each child instance to the room it controls for easier management."
        }

        section("Room devices") {
            input "roomHumiditySensors", "capability.relativeHumidityMeasurement", title: "Humidity sensors (one or more)", multiple: true, required: true
            input "roomFans", "capability.switch", title: "Exhaust fan switches (one or more)", multiple: true, required: true, submitOnChange: true
            input "singleFanMode", "bool", title: "When room automation turns ON, run only one selected fan", defaultValue: false, required: true, submitOnChange: true
            if (singleFanMode && roomFans && roomFans.size() > 1) {
                input "singleActiveFan", "capability.switch", title: "Fan to keep ON for this room", multiple: false, required: true
                paragraph "When this room is ON, all other room fans are forced OFF."
            }
            input "roomIndicator", "capability.switch", title: "Optional room humidity-high indicator", multiple: false, required: false
        }

        section("Info") {
            paragraph "Global thresholds and stale-sensor behavior are configured in the Parent app."
        }
    }
}

def installed() {
    parent.logInfo("${app.label} installed v${VERSION}")
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    if (!(state.lastAutoOnMsByFanId instanceof Map)) state.lastAutoOnMsByFanId = [:]
    if (!(state.lastAutoOffMsByFanId instanceof Map)) state.lastAutoOffMsByFanId = [:]
    if (state.roomHumidityHigh == null) state.roomHumidityHigh = false

    subscribe(roomHumiditySensors, "humidity", "humidityHandler")
    subscribe(roomFans, "switch", "fanSwitchHandler")

    requestRoomEvaluate("initialize")
}

def humidityHandler(evt) {
    requestRoomEvaluate("humidity ${evt?.value}")
}

def fanSwitchHandler(evt) {
    if (evt?.value == "on") {
        handleFanTurnedOn(evt)
    }
}

Set getHumidityDevices() {
    return roomHumiditySensors?.findAll { it } as Set
}

Set getFanDevices() {
    return roomFans?.findAll { it } as Set
}

Set getWholeHouseActiveFans() {
    return getRoomOnTargetFans()
}

Map getRoomHumidityMetric() {
    return computeRoomHumidityMetric()
}

def requestRoomEvaluate(String reason = "") {
    evaluateRoom(reason ?: "request")
}

private void evaluateRoom(String reason) {
    def global = parent.getGlobalRoomThresholds()
    if (!parent.appAutomationAllowed()) {
        if (state.roomHumidityHigh == true) {
            parent.logTrace("${app.label}: automation blocked by parent gates/mode")
        }
        if (parent.isHouseDehumActive()) {
            return
        }
        return
    }

    def roomMetric = computeRoomHumidityMetric()
    BigDecimal roomHum = roomMetric.value
    if (roomHum == null) {
        parent.logDebug("${app.label}: room humidity unavailable due to stale/missing sensors")
        return
    }

    BigDecimal insideRoomHum = parent.getInsideHumForRooms()
    BigDecimal outsideHum = boolVal(global.enableRoomRelOutside) ? parent.getOutsideHum() : null

    boolean absOn = roomHum >= n(global.roomAbsOn)
    boolean absOff = roomHum <= n(global.roomAbsOff)

    boolean relInsideEnabledOn = (insideRoomHum != null)
    boolean relInsideOn = relInsideEnabledOn ? roomHum >= (insideRoomHum + n(global.roomRelInsideOnDelta)) : false
    boolean relInsideOff = relInsideEnabledOn ? roomHum <= (insideRoomHum + n(global.roomRelInsideOffDelta)) : false

    boolean relOutsideEnabled = boolVal(global.enableRoomRelOutside) && outsideHum != null && global.roomRelOutsideOnDelta != null && global.roomRelOutsideOffDelta != null
    boolean relOutsideOn = relOutsideEnabled ? roomHum >= (outsideHum + n(global.roomRelOutsideOnDelta)) : false
    boolean relOutsideOff = relOutsideEnabled ? roomHum <= (outsideHum + n(global.roomRelOutsideOffDelta)) : false

    boolean anyRelOn = relInsideOn || relOutsideOn
    boolean anyRelOff = (!relInsideEnabledOn && !relOutsideEnabled) ? true : ((relInsideEnabledOn ? relInsideOff : true) && (relOutsideEnabled ? relOutsideOff : true))

    boolean shouldOn
    if ((global.roomOnCombiner ?: "OR") == "AND") {
        shouldOn = absOn && anyRelOn
    } else {
        shouldOn = absOn || anyRelOn
    }

    boolean shouldOff = absOff && anyRelOff

    if (shouldOn) {
        if (cooldownAllows(global.minimumOffMs as Long)) {
            turnRoomOn("thresholds (${reason})")
        } else {
            parent.logTrace("${app.label}: ON blocked by minimum-off cooldown")
        }
        return
    }

    if (shouldOff) {
        if (!parent.isHouseDehumActive()) {
            turnRoomOff("thresholds (${reason})")
        } else {
            state.roomHumidityHigh = false
            safeSwitchOff(roomIndicator, "room indicator clear while house active")
        }
    }
}

private Map computeRoomHumidityMetric() {
    def staleDevices = []
    if (!roomHumiditySensors) return [value: null, staleDevices: staleDevices]

    BigDecimal sum = 0G
    int count = 0
    roomHumiditySensors.each { d ->
        if (isDeviceStale(d)) {
            staleDevices << d
            return
        }
        BigDecimal h = readHumidity(d)
        if (h == null) return
        sum += h
        count++
    }

    if (count == 0) {
        return [value: null, staleDevices: staleDevices]
    }

    return [value: (sum / count).setScale(1, BigDecimal.ROUND_HALF_UP), staleDevices: staleDevices]
}

private boolean isDeviceStale(dev) {
    if (!dev) return true
    long lastMs = parent.getLastHumidityEventMsForDevice(dev.id) as Long
    if (!lastMs) return true
    long age = now() - lastMs
    long maxAge = msFromHours(parent.settings?.staleSensorHours ?: 8)
    return age > maxAge
}

private BigDecimal readHumidity(dev) {
    try {
        def v = dev.currentValue("humidity")
        return v != null ? new BigDecimal(v.toString()) : null
    } catch (ignored) {
        return null
    }
}

private boolean cooldownAllows(Long minimumOffMs) {
    long minMs = minimumOffMs ?: msFromMinutes(15)
    long nowMs = now()
    getRoomOnTargetFans()?.every { fan ->
        Long lastOff = state.lastAutoOffMsByFanId["${fan.id}"] as Long
        if (!lastOff) return true
        return (nowMs - lastOff) >= minMs
    }
}

private void turnRoomOn(String reason) {
    def targetFans = getRoomOnTargetFans()
    def nonTargetFans = getRoomNonTargetFans()

    targetFans?.each { fan ->
        safeSwitchOn(fan, reason)
        state.lastAutoOnMsByFanId["${fan.id}"] = now()
    }
    nonTargetFans?.each { fan ->
        safeSwitchOff(fan, "${reason}; non-selected room fan")
        state.lastAutoOffMsByFanId["${fan.id}"] = now()
    }
    safeSwitchOn(roomIndicator, "room indicator")
    state.roomHumidityHigh = true
    parent.logInfo("${app.label}: room fan automation ON (${reason})")
}

private void turnRoomOff(String reason) {
    roomFans?.each { fan ->
        safeSwitchOff(fan, reason)
        state.lastAutoOffMsByFanId["${fan.id}"] = now()
    }
    safeSwitchOff(roomIndicator, "room indicator")
    state.roomHumidityHigh = false
    parent.logInfo("${app.label}: room fan automation OFF (${reason})")
}

private void handleFanTurnedOn(evt) {
    def global = parent.getGlobalRoomThresholds()
    def dev = evt?.device
    if (!dev) return

    Long lastAutoOn = state.lastAutoOnMsByFanId["${dev.id}"] as Long
    if (lastAutoOn && (now() - lastAutoOn) < msFromMinutes(2)) {
        return
    }

    if (state.roomHumidityHigh == true || parent.isHouseDehumActive()) {
        return
    }

    if (boolVal(global.physicalOnlyManualOn, true)) {
        if (!isPhysicalEvent(evt)) {
            parent.logTrace("${app.label}: ignoring non-physical manual ON for ${dev.displayName}")
            return
        }
    }

    Integer delay = Math.max(30, ((global.manualOnAutoOffMs as Long) / 1000L) as Integer)
    runIn(delay, "manualAutoOffCheck", [overwrite: true, data: [fanId: "${dev.id}"]])
    parent.logDebug("${app.label}: scheduled manual auto-off for ${dev.displayName} in ${delay}s")
}

def manualAutoOffCheck(data) {
    def fanId = data?.fanId?.toString()
    if (!fanId) return
    def fan = roomFans?.find { "${it.id}" == fanId }
    if (!fan) return

    if (parent.isHouseDehumActive() || state.roomHumidityHigh == true) return

    Long lastAutoOn = state.lastAutoOnMsByFanId["${fan.id}"] as Long
    if (lastAutoOn && (now() - lastAutoOn) < msFromMinutes(2)) return

    def sw = fan.currentValue("switch")?.toString()?.toLowerCase()
    if (sw == "on") {
        safeSwitchOff(fan, "manual auto-off")
        state.lastAutoOffMsByFanId["${fan.id}"] = now()
        parent.logInfo("${app.label}: manual ON auto-off executed for ${fan.displayName}")
    }
}

private boolean isPhysicalEvent(evt) {
    try {
        if (evt?.type?.toString()?.toLowerCase() == "physical") return true
        if (evt?.hasProperty("isPhysical") && evt.isPhysical() == true) return true
    } catch (ignored) { }
    return false
}

private Set getRoomOnTargetFans() {
    def fans = getFanDevices()
    if (!boolVal(singleFanMode) || !fans || fans.size() <= 1) {
        return fans
    }

    String selectedId = "${singleActiveFan?.id}"
    if (!selectedId) {
        parent.logDebug("${app.label}: single fan mode enabled but no selected fan is configured; defaulting to all room fans")
        return fans
    }

    def selected = fans.findAll { "${it.id}" == selectedId } as Set
    if (!selected || selected.isEmpty()) {
        parent.logDebug("${app.label}: selected single fan is not in this room's fan list; defaulting to all room fans")
        return fans
    }

    return selected
}

private Set getRoomNonTargetFans() {
    def fans = getFanDevices()
    def targets = getRoomOnTargetFans()
    return fans?.findAll { fan -> !targets?.any { "${it.id}" == "${fan.id}" } } as Set
}

private void safeSwitchOn(dev, String reason = "") {
    if (!dev) return
    def sw = dev.currentValue("switch")?.toString()?.toLowerCase()
    if (sw != "on") {
        dev.on()
        parent.logDebug("${app.label}: ON -> ${dev.displayName} (${reason})")
    }
}

private void safeSwitchOff(dev, String reason = "") {
    if (!dev) return
    def sw = dev.currentValue("switch")?.toString()?.toLowerCase()
    if (sw != "off") {
        dev.off()
        parent.logDebug("${app.label}: OFF -> ${dev.displayName} (${reason})")
    }
}

private long msFromMinutes(Object mins) {
    BigDecimal m = n(mins, 10G)
    return (m * 60G * 1000G).longValue()
}

private long msFromHours(Object hrs) {
    BigDecimal h = n(hrs, 8G)
    return (h * 60G * 60G * 1000G).longValue()
}

private BigDecimal n(Object v, BigDecimal fallback = 0G) {
    if (v == null) return fallback
    try {
        return new BigDecimal(v.toString())
    } catch (ignored) {
        return fallback
    }
}

private boolean boolVal(Object v, boolean fallback = false) {
    if (v == null) return fallback
    if (v instanceof Boolean) return v
    return "${v}".toBoolean()
}
