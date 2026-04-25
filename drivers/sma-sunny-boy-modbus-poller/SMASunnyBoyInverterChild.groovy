/**
 *  SMA Sunny Boy Inverter Child
 *
 *  Child device used by the SMA Sunny Boy Modbus Poller app.
 *  Receives events from the parent app and exposes inverter telemetry.
 */
metadata {
    definition(
        name: 'SMA Sunny Boy Inverter Child',
        namespace: 'Hubitat-alw',
        author: 'Andrew Wineinger',
        importUrl: ''
    ) {
        capability 'Sensor'
        capability 'Refresh'
        capability 'PowerMeter'
        capability 'EnergyMeter'

        attribute 'currentPowerW', 'number'
        attribute 'dailyEnergyWh', 'number'
        attribute 'accumulatedPowerW', 'number'
        attribute 'serialNumber', 'number'
        attribute 'deviceClass', 'number'
        attribute 'modelCode', 'number'
        attribute 'firmwareVersion', 'number'
        attribute 'operatingStatus', 'number'
        attribute 'gridRelayStatus', 'number'
        attribute 'apparentPowerVA', 'number'
        attribute 'reactivePowerVAr', 'number'
        attribute 'gridFrequencyHz', 'number'
        attribute 'acVoltageL1', 'number'
        attribute 'acCurrentL1', 'number'
        attribute 'acVoltageL2', 'number'
        attribute 'acCurrentL2', 'number'
        attribute 'powerFactor', 'number'
        attribute 'totalYieldWh', 'number'
        attribute 'dcPowerInput1W', 'number'
        attribute 'dcVoltageInput1V', 'number'
        attribute 'dcCurrentInput1A', 'number'
        attribute 'dcPowerInput2W', 'number'
        attribute 'dcVoltageInput2V', 'number'
        attribute 'dcCurrentInput2A', 'number'
        attribute 'inverterTemperatureC', 'number'
        attribute 'operatingTimeSeconds', 'number'
        attribute 'feedInTimeSeconds', 'number'
        attribute 'nominalGridVoltageV', 'number'
        attribute 'nominalGridFrequencyHz', 'number'
        attribute 'activePowerLimitPercent', 'number'
        attribute 'reactivePowerSetpointVAr', 'number'
        attribute 'cosPhiSetpoint', 'number'
        attribute 'insulationResistanceOhm', 'number'
        attribute 'residualCurrentmA', 'number'
        attribute 'warningCode', 'number'
        attribute 'faultCode', 'number'
        attribute 'yieldMonthWh', 'number'
        attribute 'yieldYearWh', 'number'
        attribute 'string1Status', 'number'
        attribute 'string2Status', 'number'
        attribute 'temperatureDeratingPercent', 'number'
        attribute 'lastPoll', 'string'
        attribute 'lastPollEpoch', 'number'
        attribute 'lastError', 'string'
    }
}

def installed() {
    sendEvent(name: 'power', value: 0, unit: 'W')
    sendEvent(name: 'energy', value: 0, unit: 'kWh')
}

def refresh() {
    parent?.componentRefresh(this)
}
