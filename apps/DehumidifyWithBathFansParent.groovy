/**
 *  Dehumidify With Bath Fans - Parent
 *
 *  Parent app coordinates:
 *   - Global room thresholds
 *   - Whole-house dehumidification controller
 *   - Shared humidity metrics (inside weighted average, thermostat humidity for room-relative checks, outside average)
 *   - Generic attribute gates
 *   - Stale humidity detection + daily notification summary
 */
def VERSION = "0.1.0"

def LOG_LEVELS = ["Off", "Info", "Debug", "Trace"]

def ROOM_ON_COMBINERS = ["OR", "AND"]

def BOOL_OPS = ["allow", "block"]

metadata {
    definition(
        name: "Dehumidify With Bath Fans Parent",
        namespace: "alw",
        author: "Codex",
        description: "Parent app for whole-house + per-room bathroom fan dehumidification",
        category: "Convenience",
        singleInstance: true,
        installOnOpen: true
    )

    appSetting "logLevel"
}

preferences {
    page(name: "mainPage", title: "Dehumidify With Bath Fans", install: true, uninstall: true)
    page(name: "insideWeightsPage", title: "Inside Humidity Weights")
}

def mainPage() {
    dynamicPage(name: "mainPage") {
        section("Child Room Controllers") {
            app(
                name: "roomChildren",
                appName: "Dehumidify With Bath Fans Room Child",
                namespace: "alw",
                title: "Add a room controller",
                multiple: true
            )
        }

        section("Global room thresholds (applies to all room children)") {
            input "roomAbsOn", "number", title: "Absolute ON threshold (%)", defaultValue: 70, required: true
            input "roomAbsOff", "number", title: "Absolute OFF threshold (%)", defaultValue: 65, required: true
            input "roomRelInsideOnDelta", "number", title: "Relative-to-inside ON delta (%)", defaultValue: 15, required: true
            input "roomRelInsideOffDelta", "number", title: "Relative-to-inside OFF delta (%)", defaultValue: 12, required: true
            input "enableRoomRelOutside", "bool", title: "Enable relative-to-outside thresholds", defaultValue: false, submitOnChange: true
            if (enableRoomRelOutside) {
                input "roomRelOutsideOnDelta", "number", title: "Relative-to-outside ON delta (%)", required: true
                input "roomRelOutsideOffDelta", "number", title: "Relative-to-outside OFF delta (%)", required: true
            }
            input "roomOnCombiner", "enum", title: "Room ON logic combiner", options: ROOM_ON_COMBINERS, defaultValue: "OR", required: true
            input "minimumOffMinutes", "number", title: "Minimum OFF cooldown before automation ON (minutes)", defaultValue: 15, required: true
            input "manualOnAutoOffMinutes", "number", title: "Manual ON auto-OFF delay (minutes)", defaultValue: 10, required: true
            input "physicalOnlyManualOn", "bool", title: "Only treat physical ON as manual when metadata is available", defaultValue: true, required: true
        }

        section("Inside humidity metrics") {
            input "insideHumDevices", "capability.relativeHumidityMeasurement", title: "Inside humidity devices for weighted average", multiple: true, required: true, submitOnChange: true
            href "insideWeightsPage", title: "Configure inside humidity weights", description: "Tap to set per-device weights"
            input "insideRoomCompareDevice", "capability.relativeHumidityMeasurement", title: "Thermostat/inside humidity source for room-relative comparisons", multiple: false, required: true
        }

        section("Outside humidity metrics") {
            input "outsideHumDevices", "capability.relativeHumidityMeasurement", title: "Outside humidity device(s)", multiple: true, required: true
        }

        section("Outside temperature limit for whole-house") {
            input "outsideTempDevice", "capability.temperatureMeasurement", title: "Outside temperature device", multiple: false, required: false
            input "maxOutsideTempToDehum", "number", title: "Max outside temp for whole-house ON (°F)", defaultValue: 82, required: true
        }

        section("Whole-house dehumidify thresholds") {
            input "houseInsideOn", "number", title: "Inside humidity ON threshold (%)", defaultValue: 50, required: true
            input "houseInsideOff", "number", title: "Inside humidity OFF threshold (%)", defaultValue: 45, required: true
            input "houseOutsideBelowInsideBy", "number", title: "Outside must be <= inside - X (for ON)", defaultValue: 7, required: true
            input "houseOutsideOnMax", "number", title: "Outside humidity must be below (%) for ON", defaultValue: 40, required: true
            input "houseOutsideOffMax", "number", title: "Turn OFF if outside humidity above (%)", defaultValue: 45, required: true
            input "wholeHouseEnabled", "bool", title: "Enable whole-house controller", defaultValue: true, required: true
            input "wholeHouseIndicator", "capability.switch", title: "Whole-house indicator switch (optional)", multiple: false, required: false
            input "wholeHouseExtraFans", "capability.switch", title: "Extra whole-house fan/switch devices (optional)", multiple: true, required: false
        }

        section("Thermostat operating-state gate (driver-agnostic)") {
            paragraph "Whole-house ON is blocked when current attribute value matches any blocked value."
            input "opStateDevice", "capability.actuator", title: "Operating-state device (typically thermostat)", multiple: false, required: false, submitOnChange: true
            input "opStateAttributeName", "text", title: "Attribute name", defaultValue: "thermostatOperatingState", required: true
            input "opStateBlockedValues", "text", title: "Blocked values for ON (comma separated)", defaultValue: "cooling,heating,vent economizer", required: true
            input "opStateForceOffValues", "text", title: "Values that should force whole-house OFF (comma separated)", defaultValue: "cooling,heating", required: true
        }

        section("Optional attribute gate (ventilation/windows-open style)") {
            input "ventGateEnabled", "bool", title: "Enable configurable attribute gate", defaultValue: false, submitOnChange: true
            if (ventGateEnabled) {
                input "ventGateDevice", "capability.actuator", title: "Gate device", multiple: false, required: true
                input "ventGateAttribute", "text", title: "Gate attribute name", required: true
                input "ventGateMode", "enum", title: "Gate interpretation", options: BOOL_OPS, defaultValue: "allow", required: true
                input "ventGateValues", "text", title: "Comma-separated gate values", required: true
            }
        }

        section("Mode restriction") {
            input "allowedModes", "mode", title: "Only run automation in these modes (optional)", multiple: true, required: false
        }

        section("Stale-sensor checks + notifications") {
            input "staleSensorHours", "number", title: "Humidity staleness threshold (hours)", defaultValue: 8, required: true
            input "notifyDevice", "capability.notification", title: "Notification device (optional)", multiple: false, required: false
            input "dailyNotifyTime", "time", title: "Daily stale summary check time", required: true, defaultValue: "2024-01-01T09:00:00.000-0000"
        }

        section("Logging") {
            input "logLevel", "enum", title: "Log level", options: LOG_LEVELS, defaultValue: "Info", required: true
            paragraph "Trace is intentionally noisy and intended for short-term troubleshooting."
        }
    }
}

def insideWeightsPage() {
    dynamicPage(name: "insideWeightsPage") {
        section("Inside humidity weights") {
            if (!insideHumDevices) {
                paragraph "Select inside humidity devices first on the main page."
                return
            }
            insideHumDevices.each { d ->
                def key = insideWeightKey(d.id)
                input key, "decimal", title: "Weight for ${d.displayName}", defaultValue: 1.0, required: true
            }
            paragraph "Weighted average = Σ(humidity × weight) / Σ(weight)."
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
    if (state.houseDehumActive == null) state.houseDehumActive = false
    if (!(state.lastHumidityEventMs instanceof Map)) state.lastHumidityEventMs = [:]
    if (!(state.lastStaleNotifyEpochDay instanceof Number)) state.lastStaleNotifyEpochDay = 0

    subscribeHumidityDevices()
    subscribeGateAttributes()

    runEvery15Minutes("periodicMaintenance")
    if (dailyNotifyTime) {
        schedule(dailyNotifyTime, "dailyStaleNotifyCheck")
    }

    requestHouseDehumEvaluate("initialize")
    getChildApps()?.each { it.requestRoomEvaluate("parent initialize") }
}

private void subscribeHumidityDevices() {
    allHumidityDevices().each { d ->
        subscribe(d, "humidity", "humidityEventHandler")
        primeHumidityTimestampIfMissing(d)
    }
}

private void subscribeGateAttributes() {
    if (opStateDevice && opStateAttributeName) {
        subscribe(opStateDevice, opStateAttributeName as String, "opStateHandler")
    }
    if (ventGateEnabled && ventGateDevice && ventGateAttribute) {
        subscribe(ventGateDevice, ventGateAttribute as String, "ventGateHandler")
    }
}

def humidityEventHandler(evt) {
    trackHumidityEvent(evt?.deviceId)
    logTrace("Humidity event ${evt?.device?.displayName}: ${evt?.value}")
    requestHouseDehumEvaluate("humidity event")
    getChildApps()?.each { it.requestRoomEvaluate("humidity event ${evt?.deviceId}") }
}

def opStateHandler(evt) {
    logDebug("Operating-state event ${evt?.value}")
    requestHouseDehumEvaluate("opState event")
}

def ventGateHandler(evt) {
    logDebug("Vent gate event ${evt?.value}")
    if (!passesVentGate()) {
        logInfo("Ventilation gate blocked; forcing whole-house OFF")
        deactivateWholeHouse("vent gate blocked")
    }
    requestHouseDehumEvaluate("vent gate event")
    getChildApps()?.each { it.requestRoomEvaluate("vent gate event") }
}

def periodicMaintenance() {
    if (!(state.lastHumidityEventMs instanceof Map)) state.lastHumidityEventMs = [:]
    requestHouseDehumEvaluate("periodic")
    getChildApps()?.each { it.requestRoomEvaluate("periodic") }
}

def dailyStaleNotifyCheck() {
    if (!notifyDevice) return
    def staleData = computeStaleSummary()
    if (!staleData?.staleDevices) return
    if (staleData.staleDevices.isEmpty()) return

    long today = epochDayNow()
    if ((state.lastStaleNotifyEpochDay ?: 0L) == today) {
        logTrace("Stale summary already sent today")
        return
    }

    def msg = buildStaleNotificationMessage(staleData)
    if (msg) {
        notifyDevice.deviceNotification(msg)
        state.lastStaleNotifyEpochDay = today
        logInfo("Sent stale sensor daily notification")
    }
}

private Map computeStaleSummary() {
    def staleDevices = [] as Set
    def degradedMetrics = []

    def inside = computeHumidityMetric(insideHumDevices, true, "insideAvg")
    staleDevices.addAll(inside.staleDevices)
    if (inside.value == null) degradedMetrics << "Inside weighted humidity unavailable"

    def roomInside = computeHumidityMetric([insideRoomCompareDevice].findAll { it }, false, "insideRoom")
    staleDevices.addAll(roomInside.staleDevices)
    if (roomInside.value == null) degradedMetrics << "Thermostat humidity for room-relative checks unavailable"

    def outside = computeHumidityMetric(outsideHumDevices, false, "outside")
    staleDevices.addAll(outside.staleDevices)
    if (outside.value == null) degradedMetrics << "Outside humidity unavailable"

    getChildApps()?.each { ch ->
        def roomMetric = ch.getRoomHumidityMetric()
        if (roomMetric?.value == null) degradedMetrics << "${ch.label ?: ch.getLabel()} room humidity unavailable"
        roomMetric?.staleDevices?.each { staleDevices << it }
    }

    [staleDevices: staleDevices as List, degradedMetrics: degradedMetrics]
}

private String buildStaleNotificationMessage(Map staleData) {
    def staleNames = staleData.staleDevices?.collect { it?.displayName ?: "Unknown" }?.unique()?.sort()
    def degraded = staleData.degradedMetrics?.unique()
    if (!staleNames || staleNames.isEmpty()) return null

    def parts = []
    parts << "Dehumidify app stale sensor alert: ${staleNames.join(', ')}"
    if (degraded && !degraded.isEmpty()) {
        parts << "Degraded metrics: ${degraded.join('; ')}"
    }
    return parts.join(". ")
}

Map getGlobalRoomThresholds() {
    [
        roomAbsOn: num(roomAbsOn),
        roomAbsOff: num(roomAbsOff),
        roomRelInsideOnDelta: num(roomRelInsideOnDelta),
        roomRelInsideOffDelta: num(roomRelInsideOffDelta),
        enableRoomRelOutside: boolVal(enableRoomRelOutside),
        roomRelOutsideOnDelta: num(roomRelOutsideOnDelta),
        roomRelOutsideOffDelta: num(roomRelOutsideOffDelta),
        roomOnCombiner: (roomOnCombiner ?: "OR"),
        minimumOffMs: minutesToMs(num(minimumOffMinutes, 15G)),
        manualOnAutoOffMs: minutesToMs(num(manualOnAutoOffMinutes, 10G)),
        physicalOnlyManualOn: boolVal(physicalOnlyManualOn, true)
    ]
}

BigDecimal getInsideHumForRooms() {
    def m = computeHumidityMetric([insideRoomCompareDevice].findAll { it }, false, "insideRoom")
    return m.value
}

BigDecimal getInsideHumWeighted() {
    return computeHumidityMetric(insideHumDevices, true, "insideAvg").value
}

BigDecimal getOutsideHum() {
    return computeHumidityMetric(outsideHumDevices, false, "outside").value
}

Boolean isHouseDehumActive() {
    return state.houseDehumActive == true
}

def requestHouseDehumEvaluate(String reason = "") {
    evaluateWholeHouse(reason ?: "request")
}

private void evaluateWholeHouse(String reason) {
    if (!boolVal(wholeHouseEnabled, true)) {
        if (state.houseDehumActive == true) deactivateWholeHouse("whole-house disabled")
        return
    }
    if (!passesModeRestriction()) {
        if (state.houseDehumActive == true) deactivateWholeHouse("mode restriction")
        logTrace("Whole-house skipped: mode restriction")
        return
    }
    if (!passesVentGate()) {
        if (state.houseDehumActive == true) deactivateWholeHouse("vent gate")
        logTrace("Whole-house skipped: vent gate")
        return
    }

    BigDecimal inside = getInsideHumWeighted()
    BigDecimal outside = getOutsideHum()
    BigDecimal outTemp = getOutsideTemp()
    def opState = getOpStateValue()
    boolean opBlockedForOn = opStateInList(opState, parseCsv(opStateBlockedValues))
    boolean opForceOff = opStateInList(opState, parseCsv(opStateForceOffValues))

    if (state.houseDehumActive == true) {
        boolean shouldOff = false
        if (inside == null || outside == null) {
            shouldOff = true
        } else if (inside < num(houseInsideOff, 45G)) {
            shouldOff = true
        } else if (outside > inside - num(houseOutsideBelowInsideBy, 7G)) {
            shouldOff = true
        } else if (outside > num(houseOutsideOffMax, 45G)) {
            shouldOff = true
        } else if (opForceOff || opBlockedForOn) {
            shouldOff = true
        }

        if (shouldOff) {
            deactivateWholeHouse("OFF thresholds met (${reason})")
        } else {
            ensureWholeHouseOnState()
        }
        return
    }

    boolean onReady = true
    onReady &= (inside != null && outside != null)
    onReady &= (inside != null && inside >= num(houseInsideOn, 50G))
    onReady &= (outside != null && outside <= (inside - num(houseOutsideBelowInsideBy, 7G)))
    onReady &= (outside != null && outside < num(houseOutsideOnMax, 40G))
    onReady &= !opBlockedForOn
    if (outsideTempDevice) {
        onReady &= (outTemp != null && outTemp <= num(maxOutsideTempToDehum, 82G))
    }

    if (onReady) {
        activateWholeHouse("ON thresholds met (${reason})")
    }
}

private void activateWholeHouse(String reason) {
    def fans = getAllWholeHouseFans()
    fans.each { safeSwitchOn(it, "whole-house") }
    safeSwitchOn(wholeHouseIndicator, "whole-house indicator")
    state.houseDehumActive = true
    logInfo("Whole-house dehumidification ACTIVATED: ${reason}")
}

private void ensureWholeHouseOnState() {
    getAllWholeHouseFans().each { safeSwitchOn(it, "whole-house maintain") }
    safeSwitchOn(wholeHouseIndicator, "whole-house maintain indicator")
}

def deactivateWholeHouse(String reason) {
    getAllWholeHouseFans().each { safeSwitchOff(it, "whole-house") }
    safeSwitchOff(wholeHouseIndicator, "whole-house indicator")
    state.houseDehumActive = false
    logInfo("Whole-house dehumidification DEACTIVATED: ${reason}")
}

private Set getAllWholeHouseFans() {
    def fans = [] as Set
    getChildApps()?.each { ch ->
        ch.getFanDevices()?.each { fans << it }
    }
    wholeHouseExtraFans?.each { fans << it }
    return fans.findAll { it } as Set
}

private BigDecimal getOutsideTemp() {
    try {
        def v = outsideTempDevice?.currentValue("temperature")
        return v != null ? toBd(v) : null
    } catch (ignored) {
        return null
    }
}

private String getOpStateValue() {
    if (!opStateDevice || !opStateAttributeName) return null
    try {
        def raw = opStateDevice.currentValue(opStateAttributeName as String)
        return raw?.toString()?.trim()?.toLowerCase()
    } catch (ignored) {
        return null
    }
}

private boolean passesModeRestriction() {
    if (!allowedModes || allowedModes.isEmpty()) return true
    return allowedModes.contains(location.mode)
}

boolean appAutomationAllowed() {
    return passesModeRestriction() && passesVentGate()
}

private boolean passesVentGate() {
    if (!boolVal(ventGateEnabled)) return true
    return evaluateAttributeGate(ventGateDevice, ventGateAttribute, ventGateMode ?: "allow", parseCsv(ventGateValues))
}

private boolean evaluateAttributeGate(dev, String attr, String mode, Set<String> values) {
    if (!dev || !attr || !values || values.isEmpty()) return true
    def current = dev.currentValue(attr)?.toString()?.trim()?.toLowerCase()
    if (current == null) return false
    boolean matches = values.contains(current)
    if ((mode ?: "allow") == "allow") {
        return matches
    }
    return !matches
}

Map computeHumidityMetric(devices, boolean useWeights, String metricName) {
    def stale = []
    if (!devices || devices.isEmpty()) {
        return [value: null, staleDevices: stale, usedDevices: []]
    }

    BigDecimal numerator = 0G
    BigDecimal denominator = 0G
    def used = []

    devices.each { d ->
        if (!d) return
        if (isHumidityDeviceStale(d)) {
            stale << d
            return
        }
        BigDecimal h = readHumidity(d)
        if (h == null) return

        BigDecimal w = 1G
        if (useWeights) {
            w = num(settings[insideWeightKey(d.id)] as Object, 1G)
            if (w <= 0G) w = 1G
        }

        numerator += (h * w)
        denominator += w
        used << d
    }

    if (denominator <= 0G) {
        return [value: null, staleDevices: stale, usedDevices: used]
    }

    BigDecimal out = (numerator / denominator).setScale(1, BigDecimal.ROUND_HALF_UP)
    return [value: out, staleDevices: stale, usedDevices: used]
}

private boolean isHumidityDeviceStale(dev) {
    if (!dev) return true
    long lastMs = getLastHumidityEventMs(dev)
    if (lastMs <= 0L) return true
    long age = now() - lastMs
    long maxAge = hoursToMs(num(staleSensorHours, 8G))
    return age > maxAge
}


Long getLastHumidityEventMsForDevice(Object deviceId) {
    if (deviceId == null) return 0L
    return (state.lastHumidityEventMs["${deviceId}"] ?: 0L) as Long
}

private long getLastHumidityEventMs(dev) {
    def key = "${dev.id}"
    Long ts = (state.lastHumidityEventMs[key] ?: 0L) as Long
    return ts
}

private void trackHumidityEvent(Object deviceId) {
    if (!(state.lastHumidityEventMs instanceof Map)) state.lastHumidityEventMs = [:]
    if (deviceId == null) return
    state.lastHumidityEventMs["${deviceId}"] = now()
}

private void primeHumidityTimestampIfMissing(dev) {
    if (!dev) return
    def key = "${dev.id}"
    if (state.lastHumidityEventMs[key]) return

    try {
        def evt = dev.eventsSince(new Date(now() - hoursToMs(24G)), [max: 1]).find { it?.name == "humidity" }
        if (evt?.date) {
            state.lastHumidityEventMs[key] = evt.date.time
            return
        }
    } catch (ignored) { }

    try {
        def curr = dev.currentValue("humidity")
        if (curr != null) {
            state.lastHumidityEventMs[key] = now()
        }
    } catch (ignored2) { }
}

private Set allHumidityDevices() {
    def out = [] as Set
    insideHumDevices?.each { out << it }
    outsideHumDevices?.each { out << it }
    if (insideRoomCompareDevice) out << insideRoomCompareDevice
    getChildApps()?.each { ch ->
        ch.getHumidityDevices()?.each { out << it }
    }
    return out.findAll { it } as Set
}

String insideWeightKey(Object devId) {
    return "insideWeight_${devId}"
}

private BigDecimal readHumidity(dev) {
    try {
        def v = dev.currentValue("humidity")
        return v != null ? toBd(v) : null
    } catch (ignored) {
        return null
    }
}

private void safeSwitchOn(dev, String source = "") {
    if (!dev) return
    try {
        def sw = dev.currentValue("switch")?.toString()?.toLowerCase()
        if (sw != "on") {
            dev.on()
            logDebug("ON -> ${dev.displayName} (${source})")
        }
    } catch (e) {
        logDebug("Failed ON ${dev?.displayName}: ${e}")
    }
}

private void safeSwitchOff(dev, String source = "") {
    if (!dev) return
    try {
        def sw = dev.currentValue("switch")?.toString()?.toLowerCase()
        if (sw != "off") {
            dev.off()
            logDebug("OFF -> ${dev.displayName} (${source})")
        }
    } catch (e) {
        logDebug("Failed OFF ${dev?.displayName}: ${e}")
    }
}

private Set<String> parseCsv(String csv) {
    if (!csv) return [] as Set
    return csv.split(",").collect { it?.trim()?.toLowerCase() }.findAll { it } as Set
}

private boolean opStateInList(String opState, Set<String> values) {
    if (!opState || !values) return false
    return values.contains(opState.toLowerCase())
}

private BigDecimal num(Object val, BigDecimal fallback = null) {
    if (val == null || "${val}".trim() == "") return fallback
    try {
        return toBd(val)
    } catch (ignored) {
        return fallback
    }
}

private BigDecimal toBd(Object val) {
    if (val instanceof BigDecimal) return (BigDecimal) val
    return new BigDecimal(val.toString().trim())
}

private boolean boolVal(Object v, boolean fallback = false) {
    if (v == null) return fallback
    if (v instanceof Boolean) return v
    return "${v}".toBoolean()
}

private long minutesToMs(BigDecimal mins) {
    return ((mins ?: 0G) * 60G * 1000G).longValue()
}

private long hoursToMs(BigDecimal hrs) {
    return ((hrs ?: 0G) * 60G * 60G * 1000G).longValue()
}

private long epochDayNow() {
    return Math.floor(now() / 86400000L) as long
}

private int levelRank() {
    def lvl = (logLevel ?: "Info")
    switch (lvl) {
        case "Trace": return 3
        case "Debug": return 2
        case "Info": return 1
        default: return 0
    }
}

void logInfo(String msg) {
    if (levelRank() >= 1) log.info "[DehumParent] ${msg}"
}

void logDebug(String msg) {
    if (levelRank() >= 2) log.debug "[DehumParent] ${msg}"
}

void logTrace(String msg) {
    if (levelRank() >= 3) log.trace "[DehumParent] ${msg}"
}
