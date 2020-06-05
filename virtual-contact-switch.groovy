/*
 * modified from cwwilson08's code for a settable delay for off (button like)
 * opens contact when switched on
 * logs a message when triggered
*/

metadata {
	definition (name: "Virtual Contact/Switch", namespace: "ccoupe", author: "Many") {
    capability "Initialize"
		capability "Sensor"
    capability "ContactSensor"
    capability "Switch"
				
    attribute "switch","ENUM",["on","off"]
    attribute "contact","ENUM",["close","open"]
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
  sendEvent(name: "contact", value: "open")
  sendEvent(name: "switch", value: "on")
  if (logEnable) log.info "Virtual Ct/Sw open/on"
  if (AutoOff) {
        runInMillis(settings.OffMilli, off)
    }
}

def off() {
  sendEvent(name: "contact", value: "closed")
  sendEvent(name: "switch", value: "off")
  if (logEnable) log.info "Virtual Ct/Sw closed/off"
}   

def installed() {
}

def initialize() {
}
