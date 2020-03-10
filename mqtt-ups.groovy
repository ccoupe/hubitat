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
   *
   *  Changes:
   *
   *  1.0.0 - Initial release
   */

import groovy.json.JsonSlurper 
import java.util.GregorianCalendar

metadata {
  definition (name: "MQTT UPS Monitor", namespace: "ccoupe", 
      author: "Cecil Coupe", 
      importURL: "https://raw.githubusercontent.com/ccoupe/mqtt-camera-motion/master/mqtt-motion-video.groovy"
    ) {
    capability "Initialize"
    capability "PowerSource"
    capability "Battery"
    //capability "Refresh"
           
    attribute "powerSource", "ENUM", ["battery", "dc", "mains", "unknown"]
    attribute "load", "number"
    attribute "runtime", "number"
    attribute "battery", "number"
    attribute "ups_mfr", "string"
    attribute "ups_model", "string"
    attribute "time_max", "string"
 }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", 
        required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", 
        description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:",
        description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicSub", type: "text", title: "Topic to Subscribe:", 
        description: "Example Topic (ups/office/pi4/json). Please don't use a #", 
        required: true, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, 
        defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required:false,
        defaultValue: false, displayDuringSetup: true
    input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
 }
}


def installed() {
    log.info "installed..."
}

// Parse incoming device messages to generate events
def parse(String description) {
  msg = interfaces.mqtt.parseMessage(description)
  topic = msg.get('topic')
  payload = msg.get('payload')
  def parser = new JsonSlurper()
  def rmconf = parser.parseText(payload)
  // get the values out of rmconf into the gui preferences
  if (rmconf['ups.mfr']) {
    sendEvent(name: "ups_mfr", value: rmconf['ups.mfr'], displayed: true);
  }
  if (rmconf['ups.model']) {
    sendEvent(name: "ups_model", value: rmconf['ups.model'], displayed: true);
  }
  if (rmconf['ups.status']) {
    if (rmconf['ups.status'] == "OL")
      sendEvent(name: "powerSource", value: "mains", displayed: true);
    else // "OB"
      sendEvent(name: "powerSource", value: "battery", displayed: true);    
  }
  if (rmconf['ups.load']) {
    sendEvent(name: "load", value: rmconf['ups.load'], displayed: true);
  }
  if (rmconf['battery.runtime']) {
    sendEvent(name: "runtime", value: rmconf['battery.runtime'], displayed: true);
    def c = Calendar.instance
    c.clear()
    c.set(Calendar.SECOND, rmconf['battery.runtime'].toInteger())
    String str = c.format('HH:mm:ss')
    sendEvent(name: "time_max", value: str, displayed: true);
  }
  if (rmconf['battery.charge']) {
    sendEvent(name: "battery", value: rmconf['battery.charge'], displayed: true);
  }
}

def updated() {
  if (logEnable) log.info "Updated..."
  initialize()
}

def uninstalled() {
  if (logEnable) log.info "Disconnecting from mqtt"
  interfaces.mqtt.disconnect()
}

def initialize() {
	if (logEnable) runIn(900,logsOff) // clears debugging after 900 secs 
  if (logEnable) log.info "Initalize..."
	try {
    def mqttInt = interfaces.mqtt
    //open connection
    mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
    mqttInt.connect(mqttbroker, "hubitat_${device}", settings?.username,settings?.password)
    //give it a chance to start
    pauseExecution(1000)
    log.info "Connection established"
		if (logEnable) log.debug "Subscribed to: ${settings?.topicSub}"
    mqttInt.subscribe(settings?.topicSub)
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

