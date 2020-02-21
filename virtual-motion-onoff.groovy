/*
 * modified from cwwilson08's code for a settable delay for off in case some
 * thing attached to IFTTT doesn't send an OFF. (wyze motion for example)
 * Also logs a message when triggered
*/

metadata {
	definition (name: "Virtual Motion with auto-off Switch", namespace: "ccoupe", author: "Many") {
		capability "Sensor"
		capability "Motion Sensor"
    capability "Switch"
	}   
}

preferences {
  section {
		input (
      name: "AutoOff",
      type: "bool",
      title: "Enable auto off", 
      required: false, 
      displayDuringSetup: false, 
      defaultValue: false
    )
    input (
      name: "OffMilli",
      type: "number",
      title: "Time to Off",
      required: false,
      displayDuringSetup: true,
      defaultValue: 500,
      description: "Milliseconds for turning off"
      )
    input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
  }
}


def on() {
  sendEvent(name: "motion", value: "active")
  sendEvent(name: "switch", value: "on")
  if (logEnable) log.info "Virtual Motion active/on"
  if (AutoOff) {
        runInMillis(settings.OffMilli, off)
    }
}

def off() {
  sendEvent(name: "motion", value: "inactive")
  sendEvent(name: "switch", value: "off")
  if (logEnable) log.info "Virtual Motion inactive/off"
}   

def installed() {
}
