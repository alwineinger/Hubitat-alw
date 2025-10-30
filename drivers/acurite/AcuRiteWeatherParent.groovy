/**
 * AcuRite Weather Station Parent Driver
 *
 * Handles authentication with MyAcurite, schedules polling, and routes
 * sensor readings to child devices that represent each indoor sensor.
 */

import groovy.transform.Field
import java.util.LinkedHashMap

@Field static final Map<String,Integer> POLL_MINUTES = [
  "5 Minutes": 5,
  "10 Minutes": 10,
  "15 Minutes": 15,
  "30 Minutes": 30,
  "1 Hour": 60,
  "3 Hours": 180
]

metadata {
  definition(name: "AcuRite Weather Station Parent", namespace: "alwineinger", author: "ChatGPT") {
    capability "Initialize"
    capability "Refresh"
    capability "Sensor"

    attribute "location_name", "string"
    attribute "location_latitude", "string"
    attribute "location_longitude", "string"
    attribute "location_elevation", "decimal"
    attribute "location_timezone", "string"
    attribute "device_country", "string"
    attribute "device_name", "string"
    attribute "device_model", "string"
    attribute "device_status", "string"
    attribute "device_signal_strength", "number"
    attribute "device_last_checkin", "string"
  }

  preferences {
    input "acurite_username", "text", title: "AcuRite Username", required: true
    input "acurite_password", "password", title: "AcuRite Password", required: true
    input "device_id", "text", title: "Device ID", required: true,
      description: "Found under the 'hubs' request in the MyAcurite dashboard network traffic"
    input name: "poll_interval", type: "enum", title: "Poll Interval", defaultValue: "5 Minutes",
      options: POLL_MINUTES.keySet().toList()
    input name: "debug", type: "bool", title: "Enable debug logging (auto-off in 30m)", defaultValue: false
  }
}

def installed() {
  logInfo "Installed"
  initialize()
}

def updated() {
  logInfo "Preferences updated"
  unschedule()
  initialize()
}

def initialize() {
  if (debug) runIn(1800, "logsOff")
  schedulePolling()
  refresh()
}

def refresh() {
  logDebug "Refresh requested"
  get_acurite_data()
}

def poll() {
  logDebug "Poll requested"
  refresh()
}

def logsOff() {
  log.warn "Disabling debug logging"
  device.updateSetting("debug", [value: "false", type: "bool"])
}

private void schedulePolling() {
  Integer minutes = POLL_MINUTES.get(settings.poll_interval ?: "5 Minutes") ?: 5
  logInfo "Scheduling polling every ${minutes} minute(s)"

  switch (minutes) {
    case 5:
      runEvery5Minutes("refresh")
      break
    case 10:
      runEvery10Minutes("refresh")
      break
    case 15:
      runEvery15Minutes("refresh")
      break
    case 30:
      runEvery30Minutes("refresh")
      break
    case 60:
      runEvery1Hour("refresh")
      break
    case 180:
      runEvery3Hours("refresh")
      break
    default:
      runEvery5Minutes("refresh")
      break
  }
}

def get_acurite_data() {
  if (!acurite_username || !acurite_password || !device_id) {
    log.error "Missing MyAcurite credentials or device ID"
    return
  }

  Map loginParams = [
    uri: "https://marapi.myacurite.com",
    path: "/users/login",
    body: [
      remember: true,
      email: "${acurite_username}",
      password: "${acurite_password}"
    ]
  ]
  logDebug "Login params prepared"

  try {
    httpPostJson(loginParams) { loginResp ->
      String tokenId = loginResp?.data?.token_id
      String accountId = loginResp?.data?.user?.account_users?.getAt(0)?.account_id

      if (!tokenId || !accountId) {
        log.error "Unable to determine token or account from login response"
        return
      }

      Map dataParams = [
        uri: "https://marapi.myacurite.com",
        path: "/accounts/${accountId}/dashboard/hubs/${device_id}",
        headers: [
          "x-one-vue-token": tokenId
        ]
      ]
      logDebug "Requesting hub dashboard data"

      try {
        httpGet(dataParams) { dataResp ->
          logDebug "Received dashboard response status ${dataResp.status}"
          processDashboardData(dataResp?.data)
        }
      } catch (groovyx.net.http.HttpResponseException e2) {
        log.error "AcuRite data fetch failed: ${e2.response?.status}: ${e2.response?.data}"
      }
    }
  } catch (groovyx.net.http.HttpResponseException e1) {
    log.error "AcuRite login failed: ${e1.response?.status}"
  }
}

private void processDashboardData(Map data) {
  if (!data) {
    log.warn "No data returned from MyAcurite"
    return
  }

  sendIfChanged("location_name", data.name)
  sendIfChanged("location_latitude", data.latitude)
  sendIfChanged("location_longitude", data.longitude)
  sendIfChanged("location_elevation", data.elevation, data.elevation_unit)
  sendIfChanged("location_timezone", data.timezone)
  sendIfChanged("device_country", data.country)

  Map firstDevice = data.devices?.getAt(0)
  if (firstDevice) {
    sendIfChanged("device_name", firstDevice.name)
    sendIfChanged("device_model", firstDevice.model_code)
    sendIfChanged("device_status", firstDevice.status_code)
    sendIfChanged("device_signal_strength", firstDevice.signal_strength)
    sendIfChanged("device_last_checkin", firstDevice.last_check_in_at)
  }

  handleChildDevices(data)
}

private void handleChildDevices(Map data) {
  List<Map> sensors = collectIndoorSensors(data)
  logDebug "Processing ${sensors.size()} indoor sensors"

  sensors.each { Map sensor ->
    String childDni = childDeviceNetworkId(sensor)
    def child = getChildDevice(childDni)
    if (!child) {
      child = createChildDevice(sensor, childDni)
    } else {
      updateChildLabelIfNeeded(child, sensor)
    }
    if (child && sensor) {
      try {
        child.parse(sensor)
      } catch (Exception ex) {
        log.error "Child device ${child.displayName} failed to parse data: ${ex.message}", ex
      }
    }
  }
}

private List<Map> collectIndoorSensors(Map data) {
  Map<String, Map> grouped = [:]
  List<Map> devices = data?.devices instanceof List ? data.devices : []

  devices.each { Map device ->
    gatherDeviceSensors(device).each { Map sensor ->
      if (!isIndoorSensor(sensor)) {
        return
      }

      Map sensorCopy = cloneMap(sensor)
      sensorCopy.parentDeviceId = device?.id
      sensorCopy.parentDeviceName = device?.name

      String groupKey = determinePhysicalSensorKey(sensorCopy)
      Map group = grouped[groupKey]
      if (!group) {
        group = initializeGroupedSensor(groupKey, sensorCopy)
        grouped[groupKey] = group
      }
      mergeSensorIntoGroup(group, sensorCopy)
    }
  }

  grouped.values().collect { finalizeGroupedSensor(it) }
}

private boolean isIndoorSensor(Map sensor) {
  if (!sensor) return false
  if (sensor?.sensors instanceof List) {
    return sensor.sensors.any { Map reading ->
      String name = (reading?.sensor_name ?: "").toLowerCase()
      name.contains("indoor") || name.contains("temp") || name.contains("humidity") || name.contains("pressure")
    }
  }

  if (sensor?.sensor_name) {
    String name = (sensor.sensor_name as String).toLowerCase()
    return name.contains("indoor") || name.contains("temp") || name.contains("humidity") || name.contains("pressure")
  }

  return false
}

private def createChildDevice(Map sensor, String dni) {
  String label = childLabel(sensor)
  try {
    def child = addChildDevice(
      "alwineinger",
      "AcuRite Indoor Sensor",
      dni,
      [name: label, label: label, isComponent: true, componentName: dni, componentLabel: label]
    )
    logInfo "Created child device ${label}"
    return child
  } catch (Exception e) {
    log.error "Unable to create child device ${label}: ${e.message}", e
    return null
  }
}

private void updateChildLabelIfNeeded(child, Map sensor) {
  String label = childLabel(sensor)
  if (label && child.label != label) {
    child.setLabel(label)
  }
}

private String childDeviceNetworkId(Map sensor) {
  String sensorId = sensor?.combinedId ?: sensor?.groupId
  if (!sensorId && sensor?.primarySensorId) {
    sensorId = sensor.primarySensorId.toString()
  }
  if (!sensorId && sensor?.parentDeviceName) {
    sensorId = sanitizeIdentifier(sensor.parentDeviceName)
  }
  if (!sensorId && sensor?.device_name) {
    sensorId = sanitizeIdentifier(sensor.device_name)
  }
  if (!sensorId && sensor?.name) {
    sensorId = sanitizeIdentifier(sensor.name)
  }
  if (!sensorId && sensor?.sensor_name) {
    sensorId = sanitizeIdentifier(sensor.sensor_name)
  }
  if (!sensorId && sensor?.id) {
    sensorId = sensor.id.toString()
  }
  if (!sensorId && sensor?.sensor_id) {
    sensorId = sensor.sensor_id.toString()
  }
  String parentId = sensor?.parentDeviceId ? sensor.parentDeviceId.toString() : ""
  return "acurite-${device.deviceNetworkId}-${parentId}-${sensorId ?: sensor.hashCode()}"
}

private String childLabel(Map sensor) {
  String sensorName = sensor?.parentDeviceName ?: sensor?.device_name ?: sensor?.name ?: sensor?.sensor_name ?: sensor?.id
  if (!sensorName && sensor?.combinedId) {
    sensorName = sensor.combinedId
  }
  return sensorName ? sensorName.toString() : "AcuRite Indoor Sensor"
}

private List<Map> gatherDeviceSensors(Map device) {
  List<Map> sensors = []
  if (device?.sensors instanceof List) {
    sensors.addAll(device.sensors as List<Map>)
  }
  if (device?.wired_sensors instanceof List) {
    sensors.addAll(device.wired_sensors as List<Map>)
  }
  if (device?.wireless_sensors instanceof List) {
    sensors.addAll(device.wireless_sensors as List<Map>)
  }
  if (device?.wireless?.sensors instanceof List) {
    sensors.addAll(device.wireless.sensors as List<Map>)
  }
  sensors
}

private Map initializeGroupedSensor(String groupKey, Map sensor) {
  Map grouped = [
    combinedId      : groupKey,
    parentDeviceId  : sensor?.parentDeviceId,
    parentDeviceName: sensor?.parentDeviceName,
    device_name     : sensor?.parentDeviceName ?: sensor?.device_name ?: sensor?.deviceName ?: sensor?.name,
    name            : sensor?.parentDeviceName ?: sensor?.device_name ?: sensor?.name ?: sensor?.sensor_name,
    primarySensorId : sensor?.id ?: sensor?.sensor_id,
    sensorIds       : [],
    readings        : []
  ]

  mergeBatteryFields(grouped, sensor)
  grouped
}

private void mergeSensorIntoGroup(Map grouped, Map sensor) {
  if (!grouped.parentDeviceName && sensor?.parentDeviceName) {
    grouped.parentDeviceName = sensor.parentDeviceName
  }
  if (!grouped.device_name && sensor?.device_name) {
    grouped.device_name = sensor.device_name
  }
  if (!grouped.name && sensor?.name) {
    grouped.name = sensor.name
  }
  if (!grouped.primarySensorId && (sensor?.id || sensor?.sensor_id)) {
    grouped.primarySensorId = sensor?.id ?: sensor?.sensor_id
  }

  mergeBatteryFields(grouped, sensor)

  if (sensor?.id) {
    grouped.sensorIds << sensor.id
  }
  if (sensor?.sensor_id) {
    grouped.sensorIds << sensor.sensor_id
  }

  List<Map> readings = []
  if (sensor?.sensors instanceof List) {
    readings.addAll((sensor.sensors as List<Map>).collect { cloneMap(it) })
  }
  if (sensor?.readings instanceof List) {
    readings.addAll((sensor.readings as List<Map>).collect { cloneMap(it) })
  }
  if (sensor?.wireless?.sensors instanceof List) {
    readings.addAll((sensor.wireless.sensors as List<Map>).collect { cloneMap(it) })
  }
  if (sensor?.sensor_name && sensor.containsKey("last_reading_value")) {
    readings << cloneMap(sensor)
  }

  if (!readings && sensor) {
    readings << cloneMap(sensor)
  }

  if (readings) {
    grouped.readings.addAll(readings)
  }
}

private Map finalizeGroupedSensor(Map grouped) {
  Map result = cloneMap(grouped)
  List<String> sensorIds = (grouped.sensorIds ?: []).collect { it?.toString() }.findAll { it }
  result.sensorIds = sensorIds?.unique() ?: []
  result.parentDeviceName = grouped.parentDeviceName
  if (!result.device_name && result.parentDeviceName) {
    result.device_name = result.parentDeviceName
  }
  result.device_name = result.device_name ?: result.name
  result.name = result.device_name ?: result.name
  result.id = result.primarySensorId ?: result.combinedId
  List<Map> readings = grouped.readings instanceof List ? grouped.readings : []
  result.sensors = readings.collect { cloneMap(it) }
  result.readings = readings.collect { cloneMap(it) }
  return result
}

private void mergeBatteryFields(Map target, Map source) {
  if (source?.containsKey("battery_level") && source.battery_level != null) {
    target.battery_level = source.battery_level
  }
  if (source?.containsKey("battery") && source.battery != null) {
    target.battery = source.battery
  }
  if (source?.containsKey("battery_percentage") && source.battery_percentage != null) {
    target.battery_percentage = source.battery_percentage
  }
}

private Map cloneMap(Map input) {
  input ? new LinkedHashMap(input) : [:]
}

private String determinePhysicalSensorKey(Map sensor) {
  List candidates = [
    sensor?.parentDeviceId,
    sensor?.parentDeviceName ? sanitizeIdentifier(sensor.parentDeviceName) : null,
    sensor?.device_sensor_id,
    sensor?.sensor_device_id,
    sensor?.sensor_deviceid,
    sensor?.device_id,
    sensor?.parent_sensor_id,
    sensor?.bridge_device_id,
    sensor?.sensor_serial,
    sensor?.sensor_mac,
    sensor?.wireless_sensor_id
  ]

  for (Object candidate : candidates) {
    if (candidate) {
      return candidate.toString()
    }
  }

  if (sensor?.device_name) {
    return sanitizeIdentifier(sensor.device_name)
  }
  if (sensor?.name) {
    return sanitizeIdentifier(sensor.name)
  }
  if (sensor?.sensor_name) {
    return sanitizeIdentifier(sensor.sensor_name)
  }
  if (sensor?.id) {
    return sensor.id.toString()
  }
  if (sensor?.sensor_id) {
    return sensor.sensor_id.toString()
  }

  return sensor.hashCode().toString()
}

private String sanitizeIdentifier(Object value) {
  String sanitized = value?.toString()?.trim()?.toLowerCase()?.replaceAll(/[^a-z0-9]+/, "-")
  sanitized ? sanitized.replaceAll(/^-+|-+$/, "") : null
}

private void sendIfChanged(String name, value, String unit = null) {
  if (value == null) return
  def current = device.currentValue(name)
  if (current != value) {
    Map eventData = [name: name, value: value]
    if (unit) eventData.unit = unit
    sendEvent(eventData)
  }
}

private void logDebug(String msg) {
  if (debug) log.debug "AcuRiteParent: ${msg}"
}

private void logInfo(String msg) {
  log.info "AcuRiteParent: ${msg}"
}
