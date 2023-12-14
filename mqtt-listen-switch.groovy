/*
   * ------------------------------------------------------------------------------------------------------------------------------
   *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
   *  in compliance with the License. You may obtain a copy of the License at:
   *
   *      http://www.apache.org/licenses/LICENSE-2.0
   *
   *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
   *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
   *  for the specific language governing permissions and limitations under the License.
   *
   * ------------------------------------------------------------------------------------------------------------------------------
   *
   *
   *  Changes:
   *
   *  1.0.0 - Initial release
   */

import groovy.json.JsonOutput

metadata {
		definition (name: "Mqtt Listen Switch", namespace: "ccoupe", 
			author: "Cecil Coupe", 
			importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/raw/mqtt-listen-switch.groovy") {
      capability "Initialize"
      capability "Switch"
      capability "ContactSensor"
      capability "MotionSensor"
				
		 }

		preferences {
			input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
			input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
			input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
			input name: "topicSub", type: "text", title: "Topic to Subscribe:", description: "Example Topic 'path/to/switch/state", required: false, displayDuringSetup: true
      input (name: "switch", type: "bool", required: false, displayDuringSetup: true,
          title: "Switch?", defaultValue: true, description: "Generate On/Off events")
      input (name: "contact", type: "bool", required: false, displayDuringSetup: true,
          title: "Contact?", defaultValue: false, description: "Generate open/close events")
      input (name: "motion", type: "bool", required: false, displayDuringSetup: true,
          title: "Motion?", defaultValue: false, description: "Generate active/inactive events")
			input (name: "AutoOff",type: "bool", title: "Enable auto off", required: false, 
          displayDuringSetup: false, defaultValue: false)
	    input (name: "offSecs", type: "number", title: "Seconds to Off", required: false,
          displayDuringSetup: true, defaultValue: 10,
          description: "Seconds until auto turning off/close/inactive")
      input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
    }
}

def installed() {
   log.info "installed..."
}

// Parse incoming device messages to generate events
def parse(String description) {
  topic = interfaces.mqtt.parseMessage(description).topic
	payload = interfaces.mqtt.parseMessage(description).payload  
  if (logEnable) log.info "on message: ${topic} : ${payload}"
  if (topic == settings?.topicSub) {
    if (payload.compareToIgnoreCase("On") == 0 ) {
      turnedOn()
    } else  if (payload.compareToIgnoreCase("Off") == 0 ) {
      turnedOff()
      sendEvent(name: "switch", value: "off")
    }
  }
}

def turnedOn() 
{
  if (settings?.switch)
    sendEvent(name: "switch", value: "on")
  if (settings?.contact)
    sendEvent(name: "contact", value: "open")
  if (settings?.motion)
    sendEvent(name: "motion", value: "active")
  if (settings?.AutoOff) {
    sec = settings?.offSecs.toInteger()
    runIn(sec,turnedOff)
  }
}

def turnedOff() {
  if (settings?.switch)
    sendEvent(name: "switch", value: "off")
  if (settings?.contact)
    sendEvent(name: "contact", value: "close")
  if (settings?.motion)
    sendEvent(name: "motion", value: "inactive")
}

def updated() {
  if (logEnable) log.info "Updated..."
  if (interfaces.mqtt.isConnected() == false) {
    initialize()
  }
  configure()
}

def uninstalled() {
        if (logEnable) log.info "Disconnecting from mqtt"
        interfaces.mqtt.disconnect()
}


def initialize() {
	if (logEnable) runIn(900,logsOff)
	try {
        def mqttInt = interfaces.mqtt
        //open connection
        mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
        brokerName = "${getLocation().getHub().name}_${device}"
        log.info "connect to ${mqttbroker} as ${brokerName}"
        mqttInt.connect(mqttbroker, brokerName, settings?.username,settings?.password)
        //give it a chance to start
        pauseExecution(1000)
        log.info "Connection established"
        topic = settings?.topicSub
        if (logEnable) log.debug "Subscribed to: ${topic}"
        mqttInt.subscribe(topic)
          
    } catch(e) {
        if (logEnable) log.debug "Initialize error: ${e.message}"
    }
}

def mqttClientStatus(String status) {
  if (status.startsWith("Error")) {
    def restart = false
    if (! interfaces.mqtt.isConnected()) {
      log.warn "mqtt isConnected false"
      restart = true
    }  else if (status.contains("lost")) {
      log.warn "mqtt Connection lost detected"
      restart = true
    } else {
      log.warn "mqtt error: ${status}"
    }
    if (restart) {
      def i = 0
      while (i < 60) {
        // wait for a minute for things to settle out, server to restart, etc...
        pauseExecution(1000*60)
        initialize()
        if (interfaces.mqtt.isConnected()) {
          log.info "mqtt reconnect success!"
          break
        }
        i = i + 1
      }
    }
  } else {
    if (logEnable) log.warn "mqtt OK: ${status}"
  }
}

def configure() {
  log.info "Configure"
}


def logsOff(){
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

// This device can not be set on or off by Hubitat
// rules or Dashboard or.. Only from mqtt
def on() {
  log.warn "On is not supported nor wanted"
}

def off() {
  log.warn "Off is not supported nor wanted"
}
