/**
   *
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
   * Purpose
   *    Monitors  MQTT topics for Bambulabs A1 **AS** presented by
   * 	the HomeAssistant Bambu integration for the A1.
   *    shows % done print progress. Passes status as notifications.
   *  See ha/bambu/automations.yaml
   *   
   *  NOTE:
   *  1. temperature changes are too chatty for Hubitat, IMO. They are
   *    not tracked. Actually most things are not tracked
   *  2. Could be used for X1C if desired and HA was tweaked.
   */

import groovy.json.JsonSlurper 
import java.util.GregorianCalendar

metadata {
  definition (name: "HABambuLabs", namespace: "ccoupe", 
      author: "Cecil Coupe", 
      importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-ha-bambu.groovy"
    ) {
    capability "Initialize"
    capability "Notification"
    capability "Actuator"
    
    attribute "progress", "number"
    attribute "status", "string"
    attribute "file", "string"
  
 }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", 
        required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", 
        description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:",
        description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicSub", type: "text", title: "Topic to Subscribe:", 
        description: "Example Topic: A1", 
        required: true, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, 
        defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required:false,
        defaultValue: false, displayDuringSetup: true
    input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
 }
}

def getPrintLevels() {
  /* can be computed */
  def list = ["Minimal", "10% level", "5% level", "1% level"]
	return list
}


def installed() {
    log.info "installed..."
}

// Parse incoming device messages to generate events
def parse(String description) {
  msg = interfaces.mqtt.parseMessage(description)
  topic = msg.get('topic')
  payload = msg.get('payload')
  
  if (state.starting == true) {
    log.info "Ignoring ${topic} => ${payload}"
    return
  }
  
  if (logEnable) log.info "${topic} => ${payload}"
  if (topic == "${settings?.topicSub}/Progress") {
    def pcent = payload.toInteger()
    def nm = fixlen(settings?.topicSub, 7)
    if (pcent > 0) {
      send_note(nm+" " + pcent.toString() + "%");
      state.pcent = pcent
    }
  } else if (topic == "${settings?.topicSub}/Status") {
    sendEvent(name: 'status', value: payload, displayed: true)
    
    if (payload == "running") {
		log.info "Do something when 'running' appears"
		state.pcent = 0
		state.starting = false
    } else if (payload == 'init') {
		state.pcent = 0
		state.starting = false
    } else if (payload.startsWith("finish") || payload == "failed" ) {
      send_note(fixlen(settings?.topicSub,7)+" "+payload);
      state.pcent = 100
    }
  } 
}

// keep string to maxl characters
def fixlen(str, maxl) {
  def len = str.length()
  if (len > maxl) len = maxl
  return str.substring(0, len)
 
}

def updated() {
  if (logEnable) log.info "Updated..."
  initialize()
}

def uninstalled() {
  if (logEnable) log.info "Disconnecting from mqtt"
  interfaces.mqtt.disconnect()
}

def startup_finished() {
  log.info "CLEARING startup flag"
  state.starting = false
}
  
def initialize() {
	if (logEnable) runIn(900,logsOff) // clears debugging after 900 secs 
  if (logEnable) log.info "Initalize..."
	try {
    // don't process retained messages (for subscribed topics)
    state.starting = true
    def mqttInt = interfaces.mqtt
    //open connection
    mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
    brokerName = "${getLocation().getHub().name}_${device}"
    log.info "connect to ${mqttbroker} as ${brokerName}"
    mqttInt.connect(mqttbroker, brokerName, settings?.username,settings?.password)
    //give it a chance to start
    pauseExecution(1000)
    log.info "Connection established"
    def topic = "${settings?.topicSub}/Progress"
    mqttInt.subscribe(topic)
	if (logEnable) log.debug "Subscribed to: ${topic}"
    topic = "${settings?.topicSub}/Status"
    mqttInt.subscribe(topic)
	if (logEnable) log.debug "Subscribed to: ${topic}"

    // Don't processes the mqtt retained messages.
    // 2 secs from now, allow mqtt message to be processed.
    runIn(4,startup_finished)
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
          log.warn "mqtt reconnect success!"
          break
        }
        i = i + 1
      }
    }
  } else {
    if (logEnable) log.warn "mqtt OK: ${status}"
  }
}

def logsOff(){
  log.warn "Debug logging disabled."
  device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def on() {
  sendEvent(name: "switch", value: "on")
}

def off() {
  sendEvent(name: "switch", value: "off")
}

void deviceNotification(text) {
   if (logEnable) log.debug("Creating notificationevent. Text: ${text}")
   sendEvent(name: "deviceNotification", value: text, isStateChange: true)
}

def send_note(msg) {
  sendEvent(name: "deviceNotification", value: msg, isStateChange: true)
}
