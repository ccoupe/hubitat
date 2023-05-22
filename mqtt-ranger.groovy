/**
   * mqtt-ranger.groovy.
   * Author: Cecil Coupe - derived from sample codes from many others.
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
   */
   
   /* 
    * Uses json for configuration messages
    */

metadata {
  definition (name: "Mqtt Ranger", namespace: "ccoupe", author: "Cecil Coupe", importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-ranger.groovy") {
    capability "Initialize"
    capability "Configuration"
    capability "Refresh"
    capability "PresenceSensor"

    command "Arrived"
    command "Departed"
  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicSub", type: "text", title: "Topic to Subscribe:", 
        description: "Example: homie/garage/ranger/distance",
        required: true, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
    input name: "delayFor", type: "number", title: "Check every",
        required: true, displayDuringSetup: true, defaultValue: 60,
        description: "Number of seconds to wait before checking. 65K max"
    input name: "presenceDist", type: "number", title: "Distance",
        required: true, displayDuringSetup: true, defaultValue: 120,
        description: "Distance for 'Present' in cm"
    input name: "tolerance", type: "number", title: "Tolerance",
        required: true, displayDuringSetup: true, defaultValue: 2,
        description: "plus or minus this value (cm)"
   input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
  }
}


def installed() {
    log.info "installed..."
}

// Parse incoming device messages (distance) generate events
def parse(String description) {
  msg = interfaces.mqtt.parseMessage(description)
  topic = msg.get('topic')
  payload = msg.get('payload')
  if (topic == settings?.topicSub && payload != "") {
    def dist = payload.toInteger()
    if (dist < 50) {
        // less than 50cm is a device error? 
        log.info "${device} ingnoring ${dist}"
    } else if ((dist > (settings?.presenceDist - settings?.tolerance)) &&
        (dist < (settings?.presenceDist + settings?.tolerance))) {
      log.info "${device}: is present ${dist}"
      arrived()
    } else {
      log.info "${device}: is not present ${dist}"
      departed()
    }
  } else {
    log.info "ranger: unknown message: ${topic} => ${payload}"
  }
  
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
	if (logEnable) runIn(900,logsOff) // clears debugging after 900 somethings
	try {
    def mqttInt = interfaces.mqtt
    mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
    mqttInt.connect(mqttbroker, "hubitat_${device}", settings?.username,settings?.password)
    //give it a chance to start
    pauseExecution(200)
    def topicTop = "${settings?.topicSub}"
    log.info "Connection established"
		if (logEnable) log.debug "Subscribed to: ${topicTop}"
    mqttInt.subscribe(topicTop)
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
def logsOff(){
  log.warn "Debug logging disabled."
  device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def arrived() {
  sendEvent(name: "presence", value: "present", linkText: deviceName, descriptionText: descriptionText)
}

def departed() {
  sendEvent(name: "presence", value: "not present", linkText: deviceName, descriptionText: descriptionText)
}

def refresh() {
}

// send the delayFor value to the ESP32 (EEPROM)
def configure() {
  log.info "Configure.."
  if (settings?.topicSub) {
    def urltmp = "${settings?.topicSub}/set"
    def jstr = "{\"period\": ${settings?.delayFor.toInteger()}}"
    interfaces.mqtt.publish(urltmp, jstr, settings?.QOS.toInteger(), false)
    if (logEnable) log.debug "setting ${urltmp} to ${jstr}"
    //sendEvent(name: "delayFor", value: settings?.delayFor, displayed: true);
  }
}


def enable() {
  if (settings.isCamera) {
    def topic = "${settings?.topicSub}/control/set"
    if (logEnable) log.debug " ${topic} ${det}"
    interfaces.mqtt.publish(topic, "enable", settings?.QOS.toInteger(), settings?.retained)
  }
}

def disable() {
  if (settings.isCamera) {
    def topic = "${settings?.topicSub}/control/set"
    if (logEnable) log.debug " ${topic} ${det}"
    interfaces.mqtt.publish(topic, "disable", settings?.QOS.toInteger(), settings?.retained)
  }
}


