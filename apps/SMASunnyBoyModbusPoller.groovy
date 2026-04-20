import groovy.transform.Field

/**
 *  SMA Sunny Boy Modbus Poller
 *
 *  Polls SMA Sunny Boy inverters via Modbus TCP and exposes power, energy,
 *  and other values as child devices for use in Rule Machine and dashboards.
 *
 *  Namespace: Hubitat-alw
 *  Author: Andrew Wineinger
 */
definition(
    name: 'SMA Sunny Boy Modbus Poller',
    namespace: 'Hubitat-alw',
    author: 'Andrew Wineinger',
    description: 'Polls SMA Sunny Boy inverters via Modbus TCP and exposes power, energy, and other values as child devices for use in Rule Machine, etc.',
    category: 'Convenience',
    iconUrl: 'https://raw.githubusercontent.com/HubitatCommunity/HubitatPackages/master/resources/icons/network.png',
    iconX2Url: 'https://raw.githubusercontent.com/HubitatCommunity/HubitatPackages/master/resources/icons/network@2x.png',
    singleInstance: true
)

preferences {
    page(name: 'mainPage', title: 'SMA Sunny Boy Modbus Poller', install: true, uninstall: true)
}

@Field static final Integer DEFAULT_PORT = 502
@Field static final Integer DEFAULT_UNIT_ID = 3
@Field static final Integer DEFAULT_POLL_SECONDS = 30
@Field static final Integer MIN_POLL_SECONDS = 10

@Field static final String CHILD_DRIVER_NAMESPACE = 'Hubitat-alw'
@Field static final String CHILD_DRIVER_NAME = 'SMA Sunny Boy Inverter Child'

@Field static final Map ESSENTIAL_REGISTERS = [
    currentPowerW      : [address: 30775, count: 2, dataType: 'S32', scale: 0, unit: 'W',  description: 'Current PV feed-in active power all line conductors'],
    dailyEnergyWh      : [address: 30517, count: 4, dataType: 'U64', scale: 0, unit: 'Wh', description: 'Energy fed in on the current day'],
    accumulatedPowerW  : [address: 30233, count: 2, dataType: 'U32', scale: 0, unit: 'W',  description: 'Accumulated connected power'],
    serialNumber       : [address: 30005, count: 2, dataType: 'U32', scale: 0, unit: '',   description: 'Device serial number']
]

@Field static final Map FULL_SMA_REGISTERS = [
    // Identity / status
    serialNumber               : [address: 30005, count: 2, dataType: 'U32', scale: 0, unit: '',     description: 'Serial number'],
    deviceClass                : [address: 30051, count: 2, dataType: 'U32', scale: 0, unit: '',     description: 'Device class'],
    modelCode                  : [address: 30053, count: 2, dataType: 'U32', scale: 0, unit: '',     description: 'Type label'],
    firmwareVersion            : [address: 30059, count: 2, dataType: 'U32', scale: 0, unit: '',     description: 'Firmware version'],
    operatingStatus            : [address: 30201, count: 2, dataType: 'U32', scale: 0, unit: '',     description: 'Operating status'],
    gridRelayStatus            : [address: 30215, count: 2, dataType: 'U32', scale: 0, unit: '',     description: 'Grid relay status'],

    // AC side
    currentPowerW              : [address: 30775, count: 2, dataType: 'S32', scale: 0, unit: 'W',    description: 'Current active power'],
    apparentPowerVA            : [address: 30783, count: 2, dataType: 'U32', scale: 0, unit: 'VA',   description: 'Current apparent power'],
    reactivePowerVAr           : [address: 30805, count: 2, dataType: 'S32', scale: 0, unit: 'VAr',  description: 'Current reactive power'],
    gridFrequencyHz            : [address: 30803, count: 2, dataType: 'U32', scale: 2, unit: 'Hz',   description: 'Grid frequency'],
    acVoltageL1                : [address: 30795, count: 2, dataType: 'U32', scale: 2, unit: 'V',    description: 'AC voltage phase L1'],
    acCurrentL1                : [address: 30797, count: 2, dataType: 'U32', scale: 3, unit: 'A',    description: 'AC current phase L1'],
    acVoltageL2                : [address: 30799, count: 2, dataType: 'U32', scale: 2, unit: 'V',    description: 'AC voltage phase L2'],
    acCurrentL2                : [address: 30801, count: 2, dataType: 'U32', scale: 3, unit: 'A',    description: 'AC current phase L2'],
    powerFactor                : [address: 30813, count: 2, dataType: 'S32', scale: 3, unit: '',     description: 'Displacement power factor'],
    totalYieldWh               : [address: 30529, count: 4, dataType: 'U64', scale: 0, unit: 'Wh',   description: 'Total energy yield'],
    dailyEnergyWh              : [address: 30517, count: 4, dataType: 'U64', scale: 0, unit: 'Wh',   description: 'Daily energy yield'],
    accumulatedPowerW          : [address: 30233, count: 2, dataType: 'U32', scale: 0, unit: 'W',    description: 'Connected PV power'],

    // DC inputs (strings)
    dcPowerInput1W             : [address: 30769, count: 2, dataType: 'U32', scale: 0, unit: 'W',    description: 'DC power input 1'],
    dcVoltageInput1V           : [address: 30771, count: 2, dataType: 'U32', scale: 2, unit: 'V',    description: 'DC voltage input 1'],
    dcCurrentInput1A           : [address: 30773, count: 2, dataType: 'U32', scale: 3, unit: 'A',    description: 'DC current input 1'],
    dcPowerInput2W             : [address: 30957, count: 2, dataType: 'U32', scale: 0, unit: 'W',    description: 'DC power input 2'],
    dcVoltageInput2V           : [address: 30959, count: 2, dataType: 'U32', scale: 2, unit: 'V',    description: 'DC voltage input 2'],
    dcCurrentInput2A           : [address: 30961, count: 2, dataType: 'U32', scale: 3, unit: 'A',    description: 'DC current input 2'],

    // Thermal / runtime
    inverterTemperatureC       : [address: 30953, count: 2, dataType: 'S32', scale: 2, unit: 'C',    description: 'Inverter temperature'],
    operatingTimeSeconds       : [address: 30513, count: 4, dataType: 'U64', scale: 0, unit: 's',    description: 'Operating time'],
    feedInTimeSeconds          : [address: 30515, count: 4, dataType: 'U64', scale: 0, unit: 's',    description: 'Feed-in time'],

    // Grid configuration / limits
    nominalGridVoltageV        : [address: 41125, count: 2, dataType: 'U32', scale: 2, unit: 'V',    description: 'Nominal grid voltage'],
    nominalGridFrequencyHz     : [address: 41127, count: 2, dataType: 'U32', scale: 2, unit: 'Hz',   description: 'Nominal grid frequency'],
    activePowerLimitPercent    : [address: 40149, count: 2, dataType: 'U32', scale: 2, unit: '%',    description: 'Active power limit'],

    // Reactive power / control
    reactivePowerSetpointVAr   : [address: 40206, count: 2, dataType: 'S32', scale: 0, unit: 'VAr',  description: 'Reactive power setpoint'],
    cosPhiSetpoint             : [address: 40208, count: 2, dataType: 'S32', scale: 3, unit: '',     description: 'Cos phi setpoint'],

    // Additional diagnostics
    insulationResistanceOhm    : [address: 30875, count: 2, dataType: 'U32', scale: 0, unit: 'Ohm',  description: 'Insulation resistance'],
    residualCurrentmA          : [address: 30877, count: 2, dataType: 'U32', scale: 2, unit: 'mA',   description: 'Residual current'],
    warningCode                : [address: 30213, count: 2, dataType: 'U32', scale: 0, unit: '',     description: 'Current warning code'],
    faultCode                  : [address: 30211, count: 2, dataType: 'U32', scale: 0, unit: '',     description: 'Current fault code'],

    // Extra energy counters
    yieldMonthWh               : [address: 30523, count: 4, dataType: 'U64', scale: 0, unit: 'Wh',   description: 'Monthly yield'],
    yieldYearWh                : [address: 30525, count: 4, dataType: 'U64', scale: 0, unit: 'Wh',   description: 'Yearly yield'],

    // String-level / additional channels
    string1Status              : [address: 30949, count: 2, dataType: 'U32', scale: 0, unit: '',     description: 'String 1 status'],
    string2Status              : [address: 30951, count: 2, dataType: 'U32', scale: 0, unit: '',     description: 'String 2 status'],

    // Derating and controls
    temperatureDeratingPercent : [address: 30965, count: 2, dataType: 'U32', scale: 2, unit: '%',    description: 'Temperature derating']
]

/**
 * Dynamic preferences page.
 */
def mainPage() {
    dynamicPage(name: 'mainPage') {
        section('Inverter Configuration') {
            input name: 'inverterIps', type: 'text', title: 'Inverter IP addresses (comma-separated)', required: true,
                description: 'Example: 192.168.1.50,192.168.1.51'
            input name: 'inverterNames', type: 'text', title: 'Optional inverter names (comma-separated, same order as IPs)', required: false,
                description: 'Example: East Roof,West Roof'
            input name: 'modbusPort', type: 'number', title: 'Modbus TCP Port', defaultValue: DEFAULT_PORT, required: true
            input name: 'unitId', type: 'number', title: 'Unit ID / Slave ID', defaultValue: DEFAULT_UNIT_ID, required: true
            input name: 'pollSeconds', type: 'number', title: 'Polling interval seconds', defaultValue: DEFAULT_POLL_SECONDS, required: true
            input name: 'fullRegisterSet', type: 'bool', title: 'Enable full SMA register set', defaultValue: false
        }
        section('Logging') {
            input name: 'debugLogging', type: 'bool', title: 'Enable debug logging', defaultValue: true
            input name: 'traceLogging', type: 'bool', title: 'Enable trace logging', defaultValue: false
        }
    }
}

def installed() {
    log.info 'Installed SMA Sunny Boy Modbus Poller'
    initialize()
}

def updated() {
    log.info 'Updated SMA Sunny Boy Modbus Poller'
    unsubscribe()
    unschedule()
    initialize()
}

def uninstalled() {
    unsubscribe()
    unschedule()
    getChildDevices()?.each { deleteChildDevice(it.deviceNetworkId) }
    log.info 'Uninstalled SMA Sunny Boy Modbus Poller and removed child devices.'
}

/**
 * App initialization and child-device reconciliation.
 */
def initialize() {
    state.txCounter = (state.txCounter ?: 1) as Integer
    state.pending = state.pending ?: [:]
    state.ipMeta = state.ipMeta ?: [:]

    Integer sanitizedPoll = Math.max((settings.pollSeconds ?: DEFAULT_POLL_SECONDS) as Integer, MIN_POLL_SECONDS)
    if (sanitizedPoll != (settings.pollSeconds as Integer)) {
        app.updateSetting('pollSeconds', [value: sanitizedPoll.toString(), type: 'number'])
    }

    List<Map> inverterList = configuredInverters()
    if (!inverterList) {
        log.warn 'No valid inverter IPs configured. Polling will not start.'
        return
    }

    reconcileChildDevices(inverterList)
    scheduleNextPoll(1)
}

/**
 * Parses configured inverter IP/name list.
 */
private List<Map> configuredInverters() {
    List<String> ips = (settings.inverterIps ?: '')
        .split(',')
        .collect { it?.trim() }
        .findAll { it }

    List<String> names = (settings.inverterNames ?: '')
        .split(',')
        .collect { it?.trim() }

    List<Map> results = []
    ips.eachWithIndex { String ip, Integer idx ->
        if (!isValidIpv4(ip)) {
            log.warn "Skipping invalid IP: ${ip}"
            return
        }
        String userName = (idx < names.size() && names[idx]) ? names[idx] : "Inverter ${idx + 1}"
        results << [ip: ip, name: userName, index: idx]
    }
    return results
}

/**
 * Creates/updates one child device per inverter.
 */
private void reconcileChildDevices(List<Map> inverterList) {
    Set<String> desiredDnis = [] as Set
    inverterList.each { Map inv ->
        String dni = childDniForIp(inv.ip as String)
        desiredDnis << dni

        def child = getChildDevice(dni)
        if (!child) {
            try {
                child = addChildDevice(CHILD_DRIVER_NAMESPACE, CHILD_DRIVER_NAME, dni, [
                    label: "SMA Inverter ${(inv.name ?: inv.ip)}",
                    name : "SMA Inverter ${(inv.name ?: inv.ip)}",
                    isComponent: true
                ])
                log.info "Created child device ${child?.displayName} (${dni})"
            } catch (Exception ex) {
                log.error "Unable to create child device for ${inv.ip}: ${ex.message}"
                return
            }
        }

        state.ipMeta[inv.ip] = [name: inv.name, dni: dni, lastPoll: state.ipMeta[inv.ip]?.lastPoll ?: 0L]
    }

    getChildDevices()?.each { cd ->
        if (!desiredDnis.contains(cd.deviceNetworkId)) {
            log.info "Deleting child device no longer configured: ${cd.displayName}"
            deleteChildDevice(cd.deviceNetworkId)
        }
    }
}

/**
 * Main recurring poll entry point. This re-schedules itself via runIn().
 */
def pollAll() {
    List<Map> inverterList = configuredInverters()
    if (!inverterList) {
        scheduleNextPoll()
        return
    }

    Integer idx = 0
    inverterList.each { inv ->
        Integer staggerMs = idx * 700
        runInMillis(staggerMs, 'pollSingleInverter', [overwrite: false, data: [ip: inv.ip]])
        idx++
    }

    scheduleNextPoll()
}

private void scheduleNextPoll(Integer inSeconds = null) {
    Integer seconds = inSeconds ?: Math.max((settings.pollSeconds ?: DEFAULT_POLL_SECONDS) as Integer, MIN_POLL_SECONDS)
    runIn(seconds, 'pollAll', [overwrite: true])
    logDebug "Next full poll scheduled in ${seconds}s"
}

/**
 * Poll one inverter by building one or more Modbus TCP FC03 requests.
 */
def pollSingleInverter(Map data = [:]) {
    String ip = data?.ip as String
    if (!ip) return

    Integer port = (settings.modbusPort ?: DEFAULT_PORT) as Integer
    Integer slave = (settings.unitId ?: DEFAULT_UNIT_ID) as Integer

    List<Map> registerDefs = selectedRegisterDefinitions()
    List<Map> batches = buildReadBatches(registerDefs, 20)

    if (!batches) {
        log.warn "No register batches to poll for ${ip}"
        return
    }

    batches.eachWithIndex { Map batch, Integer i ->
        Integer txId = nextTransactionId()
        String hexCmd = buildModbusReadRequest(txId, slave, batch.start as Integer, batch.quantity as Integer)

        state.pending[txId.toString()] = [
            ip: ip,
            startReg: batch.start,
            quantity: batch.quantity,
            at: now(),
            defs: batch.defs.collect { [name: it.name, address: it.address, count: it.count, dataType: it.dataType, scale: it.scale, unit: it.unit, description: it.description] }
        ]

        Map options = [
            type: 'LAN_TYPE_CLIENT',
            destinationAddress: ip,
            destinationPort: "${port}",
            encoding: hubitat.device.HubAction.Encoding.HEX_STRING,
            ignoreResponse: false,
            callback: 'parseResponse',
            requestId: txId
        ]
        if (traceLogging) {
            log.trace "TX ${txId} -> ${ip}:${port} start=${batch.start} qty=${batch.quantity} hex=${hexCmd}"
        }

        try {
            sendHubCommand(new hubitat.device.HubAction(hexCmd, hubitat.device.Protocol.LAN, options))
        } catch (Exception ex) {
            log.error "Failed to send Modbus request tx=${txId} ip=${ip}: ${ex.message}"
            state.pending.remove(txId.toString())
        }

        if (i < batches.size() - 1) {
            pauseExecution(150)
        }
    }

    state.ipMeta[ip] = (state.ipMeta[ip] ?: [:]) + [lastPoll: now()]
}

/**
 * Manual refresh command routed from component child devices.
 */
def componentRefresh(cd) {
    String ip = ipFromChildDni(cd?.deviceNetworkId)
    if (!ip) {
        log.warn "componentRefresh called with unknown child DNI: ${cd?.deviceNetworkId}"
        return
    }
    log.info "Manual refresh requested for ${cd.displayName} (${ip})"
    pollSingleInverter([ip: ip])
}

/**
 * HubAction TCP callback for Modbus responses.
 *
 * Validates MBAP/PDU, associates response using transaction ID and decodes
 * register values to child attributes.
 */
def parseResponse(resp) {
    try {
        if (resp?.status && resp.status != 200) {
            log.error "TCP response error status=${resp.status} error=${resp.errorMessage ?: 'unknown'}"
            return
        }

        byte[] bytes = responseToBytes(resp)
        if (!bytes || bytes.length < 9) {
            log.error 'Invalid or empty Modbus TCP response payload.'
            return
        }

        Map parsed = parseModbusTcpResponse(bytes)
        Integer txId = parsed.txId as Integer
        Map pending = state.pending.remove(txId.toString())
        if (!pending) {
            log.warn "Received response for unknown txId=${txId}"
            return
        }

        String ip = pending.ip as String
        def child = getChildDevice(childDniForIp(ip))
        if (!child) {
            log.warn "No child device found for IP ${ip}"
            return
        }

        if (parsed.error) {
            log.error "Modbus exception from ${ip} tx=${txId} code=${parsed.exceptionCode}"
            sendChildEvent(child, 'lastError', "Exception code ${parsed.exceptionCode}", '')
            return
        }

        List<Integer> regs = parsed.registers as List<Integer>
        List<Map> defs = (pending.defs ?: []) as List<Map>

        defs.each { Map defn ->
            Integer offset = (defn.address as Integer) - (pending.startReg as Integer)
            Integer words = defn.count as Integer
            if (offset < 0 || (offset + words) > regs.size()) {
                logDebug "Skipping out-of-range definition ${defn.name} offset=${offset} words=${words}"
                return
            }
            List<Integer> slice = regs.subList(offset, offset + words)
            BigDecimal val = decodeTypedValue(slice, defn.dataType as String, (defn.scale ?: 0) as Integer)
            if (val == null) {
                logDebug "${ip} ${defn.name}: NaN/sentinel value"
                return
            }

            Object finalVal = (defn.scale ?: 0) as Integer == 0 ? val.longValue() : val
            sendChildEvent(child, defn.name as String, finalVal, defn.unit as String)
            if ((defn.name as String) == 'currentPowerW') {
                sendChildEvent(child, 'power', finalVal, 'W')
            } else if ((defn.name as String) == 'dailyEnergyWh' && finalVal instanceof Number) {
                BigDecimal kwh = (((finalVal as Number).toBigDecimal()) / 1000G).setScale(3, BigDecimal.ROUND_HALF_UP)
                sendChildEvent(child, 'energy', kwh, 'kWh')
            }

            if ((defn.name as String) == 'serialNumber') {
                String last4 = "${finalVal}".takeRight(4)
                String userName = (state.ipMeta[ip]?.name ?: ip) as String
                child.setLabel("SMA Inverter ${userName} ${last4}")
            }
        }

        sendChildEvent(child, 'lastPoll', new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX", location.timeZone), '')
        sendChildEvent(child, 'lastPollEpoch', now(), 'ms')

    } catch (Exception ex) {
        log.error "parseResponse failure: ${ex.message}"
        if (traceLogging) log.trace ex.toString()
    }
}

/**
 * Converts Hubitat callback payload into byte[].
 */
private byte[] responseToBytes(resp) {
    if (resp?.payload instanceof byte[]) return (byte[]) resp.payload
    if (resp?.body instanceof byte[]) return (byte[]) resp.body

    String body = (resp?.body ?: resp?.payload ?: '').toString().trim()
    if (!body) return null

    body = body.replaceAll(/[^0-9A-Fa-f]/, '')
    if (body.size() % 2 != 0) {
        log.warn 'Response body has odd number of hex chars; ignoring final nibble.'
        body = body.substring(0, body.size() - 1)
    }

    byte[] out = new byte[body.size() / 2]
    for (int i = 0; i < body.size(); i += 2) {
        out[i / 2] = (byte) Integer.parseInt(body.substring(i, i + 2), 16)
    }
    return out
}

/**
 * Parses a Modbus TCP response frame.
 *
 * MBAP (7 bytes):
 *   0-1 transaction ID
 *   2-3 protocol ID (must be 0)
 *   4-5 length
 *   6   unit ID
 * PDU:
 *   function code
 *   byte count / exception code
 *   register bytes...
 */
private Map parseModbusTcpResponse(byte[] response) {
    int txId = ((response[0] & 0xFF) << 8) | (response[1] & 0xFF)
    int protocolId = ((response[2] & 0xFF) << 8) | (response[3] & 0xFF)
    int length = ((response[4] & 0xFF) << 8) | (response[5] & 0xFF)
    int unit = response[6] & 0xFF
    int function = response[7] & 0xFF

    if (protocolId != 0) {
        throw new IllegalStateException("Invalid protocolId=${protocolId} txId=${txId}")
    }

    if ((length + 6) > response.length) {
        throw new IllegalStateException("Length mismatch txId=${txId} mbapLen=${length} bytes=${response.length}")
    }

    if ((function & 0x80) == 0x80) {
        int exceptionCode = response[8] & 0xFF
        return [txId: txId, unit: unit, function: function, error: true, exceptionCode: exceptionCode]
    }

    if (function != 0x03) {
        throw new IllegalStateException("Unexpected function=${function} txId=${txId}")
    }

    int byteCount = response[8] & 0xFF
    if (byteCount + 9 > response.length) {
        throw new IllegalStateException("Byte-count mismatch txId=${txId} byteCount=${byteCount}")
    }

    List<Integer> regs = []
    for (int i = 0; i < byteCount; i += 2) {
        int hi = response[9 + i] & 0xFF
        int lo = response[9 + i + 1] & 0xFF
        regs << ((hi << 8) | lo)
    }

    return [txId: txId, unit: unit, function: function, error: false, registers: regs]
}

/**
 * Builds a raw Modbus TCP Read Holding Registers request (FC03) as hex string.
 */
private String buildModbusReadRequest(Integer txId, Integer unitId, Integer startReg, Integer quantity) {
    byte[] frame = new byte[12]

    // MBAP
    frame[0] = (byte) ((txId >> 8) & 0xFF)
    frame[1] = (byte) (txId & 0xFF)
    frame[2] = 0x00
    frame[3] = 0x00
    frame[4] = 0x00
    frame[5] = 0x06 // bytes after this field: UnitID + PDU(5)
    frame[6] = (byte) (unitId & 0xFF)

    // PDU
    frame[7] = 0x03
    frame[8] = (byte) ((startReg >> 8) & 0xFF)
    frame[9] = (byte) (startReg & 0xFF)
    frame[10] = (byte) ((quantity >> 8) & 0xFF)
    frame[11] = (byte) (quantity & 0xFF)

    return frame.collect { String.format('%02X', (it & 0xFF)) }.join()
}

/**
 * Decode SMA register slices into signed/unsigned integers with FIX scaling.
 * Returns null for known SMA NaN sentinel values.
 */
private BigDecimal decodeTypedValue(List<Integer> regs, String type, Integer scale = 0) {
    if (!regs || !type) return null

    Long raw
    switch (type) {
        case 'U16':
            raw = (long) (regs[0] & 0xFFFF)
            if (raw == 0xFFFFL) return null
            break
        case 'S16':
            int v16 = regs[0] & 0xFFFF
            raw = (long) (v16 > 0x7FFF ? v16 - 0x10000 : v16)
            if (v16 == 0x8000 || v16 == 0xFFFF) return null
            break
        case 'U32':
        case 'S32':
            long u32 = ((regs[0] & 0xFFFFL) << 16) | (regs[1] & 0xFFFFL)
            if (u32 == 0xFFFFFFFFL) return null
            if (type == 'S32' && (u32 & 0x80000000L)) {
                raw = u32 - 0x100000000L
            } else {
                raw = u32
            }
            break
        case 'U64':
        case 'S64':
            if (regs.size() < 4) return null
            BigInteger bi = BigInteger.ZERO
            regs.each { r ->
                bi = bi.shiftLeft(16).or(BigInteger.valueOf((long) (r & 0xFFFF)))
            }
            BigInteger nan64 = new BigInteger('FFFFFFFFFFFFFFFF', 16)
            if (bi == nan64) return null
            if (type == 'S64' && bi.testBit(63)) {
                bi = bi.subtract(BigInteger.ONE.shiftLeft(64))
            }
            BigDecimal out64 = new BigDecimal(bi)
            return applyScale(out64, scale)
        default:
            // Treat unknown as U32 if 2 registers, else U16
            if (regs.size() >= 2) {
                long d = ((regs[0] & 0xFFFFL) << 16) | (regs[1] & 0xFFFFL)
                if (d == 0xFFFFFFFFL) return null
                raw = d
            } else {
                raw = (long) (regs[0] & 0xFFFF)
                if (raw == 0xFFFFL) return null
            }
    }

    return applyScale(raw as BigDecimal, scale)
}

private BigDecimal applyScale(BigDecimal value, Integer scale) {
    Integer s = scale ?: 0
    if (s <= 0) return value
    BigDecimal divisor = BigDecimal.TEN.pow(s)
    return value.divide(divisor, s, BigDecimal.ROUND_HALF_UP)
}

/**
 * Uses configured register set and annotates each entry with canonical `name`.
 */
private List<Map> selectedRegisterDefinitions() {
    Map source = settings.fullRegisterSet ? FULL_SMA_REGISTERS : ESSENTIAL_REGISTERS
    source.collect { String name, Map defn ->
        [name: name] + defn
    }.sort { a, b -> (a.address as Integer) <=> (b.address as Integer) }
}

/**
 * Builds contiguous register requests limited by max span.
 */
private List<Map> buildReadBatches(List<Map> defs, Integer maxRegsPerRequest) {
    List<Map> batches = []
    if (!defs) return batches

    List<Map> sorted = defs.sort { a, b -> (a.address as Integer) <=> (b.address as Integer) }

    Integer currentStart = null
    Integer currentEnd = null
    List<Map> currentDefs = []

    sorted.each { Map d ->
        Integer s = d.address as Integer
        Integer e = s + (d.count as Integer) - 1

        if (currentStart == null) {
            currentStart = s
            currentEnd = e
            currentDefs = [d]
            return
        }

        Integer candidateEnd = Math.max(currentEnd, e)
        Integer candidateSpan = (candidateEnd - currentStart + 1)
        boolean contiguousEnough = s <= (currentEnd + 1)

        if (contiguousEnough && candidateSpan <= maxRegsPerRequest) {
            currentEnd = candidateEnd
            currentDefs << d
        } else {
            batches << [start: currentStart, quantity: (currentEnd - currentStart + 1), defs: currentDefs]
            currentStart = s
            currentEnd = e
            currentDefs = [d]
        }
    }

    if (currentStart != null) {
        batches << [start: currentStart, quantity: (currentEnd - currentStart + 1), defs: currentDefs]
    }

    return batches
}

private Integer nextTransactionId() {
    Integer current = (state.txCounter ?: 1) as Integer
    Integer next = current + 1
    if (next > 0xFFFF) next = 1
    state.txCounter = next
    return current
}

private void sendChildEvent(child, String name, Object value, String unit = null) {
    if (value == null) return
    Map evt = [name: name, value: value, isStateChange: true]
    if (unit) evt.unit = unit
    child.sendEvent(evt)
}

private String childDniForIp(String ip) {
    return "sma-modbus-${app.id}-${ip.replace('.', '-') }"
}

private String ipFromChildDni(String dni) {
    if (!dni) return null
    String prefix = "sma-modbus-${app.id}-"
    if (!dni.startsWith(prefix)) return null
    return dni.substring(prefix.length()).replace('-', '.')
}

private boolean isValidIpv4(String ip) {
    if (!ip) return false
    def m = ip =~ /^(\d{1,3}\.){3}\d{1,3}$/
    if (!m.matches()) return false
    return ip.tokenize('.').every {
        try {
            Integer v = it.toInteger()
            v >= 0 && v <= 255
        } catch (ignored) {
            false
        }
    }
}

private void logDebug(String msg) {
    if (settings.debugLogging) log.debug msg
}
