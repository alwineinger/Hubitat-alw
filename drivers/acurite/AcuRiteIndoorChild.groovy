/**
 * AcuRite Indoor Child Driver
 */

metadata {
  definition(name: "AcuRite Indoor Sensor", namespace: "alwineinger", author: "ChatGPT") {
    capability "Sensor"
    capability "Temperature Measurement"
    capability "Relative Humidity Measurement"
    capability "Pressure Measurement"
    capability "Battery"

    attribute "sensor_name", "string"
  }
}

def parse(Map sensor) {
  if (!(sensor instanceof Map)) {
    log.warn "parse called without sensor map"
    return
  }

  if (sensor.name) {
    updateAttribute("sensor_name", sensor.name)
  } else if (sensor.sensor_name) {
    updateAttribute("sensor_name", sensor.sensor_name)
  }

  List<Map> readings = []
  if (sensor.sensors instanceof List) {
    readings.addAll(sensor.sensors)
  }
  if (sensor.readings instanceof List) {
    readings.addAll(sensor.readings)
  }
  if (sensor.wireless?.sensors instanceof List) {
    readings.addAll(sensor.wireless.sensors)
  }
  if (sensor.sensor_name && sensor.containsKey("last_reading_value")) {
    readings << sensor
  }

  readings.each { Map reading ->
    handleReading(reading)
  }

  handleBattery(sensor)
}

private void handleReading(Map reading) {
  if (!reading?.sensor_name) return

  String name = (reading.sensor_name as String).toLowerCase()
  def value = reading.last_reading_value

  switch (name) {
    case { it.contains("temp") }:
      updateAttribute("temperature", toBigDecimal(value), reading.chart_unit)
      break
    case { it.contains("humid") }:
      updateAttribute("humidity", toBigDecimal(value))
      break
    case { it.contains("pressure") }:
      updateAttribute("pressure", toBigDecimal(value), reading.chart_unit)
      break
    default:
      break
  }
}

private void handleBattery(Map sensor) {
  if (sensor?.battery_level != null) {
    Integer batt = (sensor.battery_level == "Normal") ? 100 : 0
    updateAttribute("battery", batt)
  } else if (sensor?.battery != null) {
    updateAttribute("battery", sensor.battery as Integer)
  } else if (sensor?.battery_percentage != null) {
    updateAttribute("battery", sensor.battery_percentage as Integer)
  }
}

private void updateAttribute(String name, value, String unit = null) {
  if (value == null) return
  def current = device.currentValue(name)
  if (current != value) {
    Map event = [name: name, value: value]
    if (unit) event.unit = unit
    sendEvent(event)
  }
}

private BigDecimal toBigDecimal(value) {
  try {
    value as BigDecimal
  } catch (Exception ignored) {
    null
  }
}
