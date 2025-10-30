/**
 * This legacy driver has been replaced by drivers/acurite/AcuRiteWeatherParent.groovy.
 *
 * The new implementation creates child devices for each indoor sensor and should
 * be used for all new installations. Keeping this stub in place allows existing
 * users to receive a clear migration message instead of silent failures.
 */

metadata {
  definition(name: "AcuRite Weather Station S0 (Deprecated)", namespace: "dlaporte", author: "David LaPorte") {
    capability "Initialize"
  }
}

def installed() {
  log.warn "This driver is deprecated. Please switch to 'AcuRite Weather Station Parent' and re-pair your sensors."
}

def updated() {
  installed()
}

def initialize() {
  installed()
}
