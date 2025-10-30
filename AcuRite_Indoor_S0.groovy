metadata {
  definition(name: "AcuRite Indoor (A)", namespace: "alwineinger", author: "ChatGPT") {
    capability "Sensor"
    capability "Initialize"
    capability "Refresh"
    capability "Temperature Measurement"
    capability "Relative Humidity Measurement"
    capability "Pressure Measurement"
    capability "Battery"      // keep only if API provides it

    // Optional informational attrs; safe to keep if you use them
    attribute "device_status", "string"
    attribute "device_last_checkin", "string"
    attribute "device_signal_strength", "number"
  }

  preferences {
    input "acurite_username", "text",     title: "AcuRite Username", required: true
    input "acurite_password", "password", title: "AcuRite Password", required: true
    input "device_id",        "text",     title: "Device ID",        required: true
    input name: "poll_minutes", type: "enum", title: "Poll interval", options: ["5","10","15","30","60","180"], defaultValue: "10"
    input name: "debug", type: "bool", title: "Enable debug logging (auto-off in 30m)", defaultValue: false
  }
}

// In your data handler, map ONLY the 3 sensors (+battery if present)
void dataHandler(resp, data) {
  // ...status/401 handling, json = safeJson(resp)...
  def dev = json?.devices?.getAt(0)
  if (dev) {
    sendIfChanged("device_status", dev.status_code)
    sendIfChanged("device_last_checkin", dev.last_check_in_at)
    sendIfChanged("device_signal_strength", dev.signal_strength as Integer)

    // Battery: keep only if meaningful
    if (dev.battery_level != null) {
      Integer batt = (dev.battery_level == "Normal") ? 100 : 0
      sendIfChanged("battery", batt)
    }
  }

  // Sensors: don’t iterate everything; pick the three you care about
  List sensors = (dev?.sensors ?: []) + (dev?.wired_sensors ?: [])
  sensors.each { s ->
    switch (s.sensor_name) {
      case "Temperature":
        sendIfChanged("temperature", toNum(s.last_reading_value)); break
      case "Humidity":
        sendIfChanged("humidity", toNum(s.last_reading_value)); break
      case "Pressure":
        sendIfChanged("pressure", toNum(s.last_reading_value)); break
      default:
        // ignore others to avoid attribute spam
        break
    }
  }
}

// helper: coerce to number & your existing sendIfChanged(...)
private BigDecimal toNum(v) { try { v as BigDecimal } catch(e){ null } }
