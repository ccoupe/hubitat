/**
   * Note: I borrowed a lot of code from the author below and other examples. CJC.
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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper 

metadata {
  definition (name: "MQTT Motion v2", namespace: "ccoupe", author: "Cecil Coupe", importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-motion2.groovy") {
    capability "Initialize"
    capability "MotionSensor"
    //capability "Configuration"
    capability "Refresh"

    command "enable"
    command "disable"
    //command "delay", ["Number"]
        
    attribute "motion", "string"
    attribute "motion","ENUM",["active","inactive"]
    attribute "active_hold", "number"
  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicSub", type: "text", title: "Topic to Subscribe:", 
        description: "Example Topic (sensors/office/motion1). Please don't use a #", 
        required: false, displayDuringSetup: true
    input name: "topicPub", type: "text", title: "Topic to Publish:",
        description: "Example Topic (sensors/office/motion1_control)", 
        required: false, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
    input name: "active_hold", type: "number", title: "Active Hold",
        required: false, displayDuringSetup: true, defaultValue: 45,
        description: "Number of seconds to keep motion active"
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
  if (payload.startsWith("active")){
      if (logEnable) log.info "mqtt ${topic} => ${payload}"
      sendEvent(name: "motion", value: "active")
  } else if (payload.startsWith("inactive")){
      if (logEnable) log.info "mqtt ${topic} => ${payload}"
      sendEvent(name: "motion", value: "inactive")
  } else if (payload.startsWith("conf=")) {
    jstr = payload[5..-1]
    if (logEnable) log.debug "recv: conf=${jstr}"
    def parser = new JsonSlurper()
    def rmconf = parser.parseText(jstr)
    // get the values out of rmconf into the gui preferences
    if (rmconf['active_hold']) {
      log.debug "ss: ${settings["active_hold"]}"
      settings["active_hold"] = rmconf['active_hold'].toInteger()
      log.debug "ss: ${settings["active_hold"]}"
      device.updateSetting("active_hold", [value: rmconf['active_hold'].toInteger(), type: "number"] )
      sendEvent(name: "active_hold",  value: rmconf['active_hold'].toInteger(), displayed: true )
    }
  }
}


def updated() {
  if (logEnable) log.info "Updated..."
  if (interfaces.mqtt.isConnected() == false)
    initialize()
  else 
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
    //open connection
    mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
    mqttInt.connect(mqttbroker, "hubitat_${device}", settings?.username,settings?.password)
    //give it a chance to start
    pauseExecution(1000)
    log.info "Connection established"
		if (logEnable) log.debug "Subscribed to: ${settings?.topicSub}"
    mqttInt.subscribe(settings?.topicSub)
    refresh()   // get device config
  } catch(e) {
    if (logEnable) log.debug "Initialize error: ${e.message}"
  }
}


def mqttClientStatus(String status){
  if (status.startsWith("Error")) {
    log.warn "MQTTStatus: ${status}"
    if (interfaces.mqtt.isConnected() == false || 
        status.contains("lost") ) {
      initialize()
    }
  } else {
    if (logEnable) log.debug "MQTTStatus- ${status}"
  }
}

def logsOff(){
  log.warn "Debug logging disabled."
  device.updateSetting("logEnable",[value:"false",type:"bool"])
}

// Send commands to device via MQTT
def disable() {
  log.debug settings?.topicSub + " disable sensor"
  interfaces.mqtt.publish(settings?.topicPub, "disable", settings?.QOS.toInteger(), settings?.retained)
}

def enable() {
  log.debug settings?.topicSub + " enable sensor"
  interfaces.mqtt.publish(settings?.topicPub, "enable", settings?.QOS.toInteger(), settings?.retained)
}

/*
def delay(Number s) {
  log.debug settings?.topicSub + " set delay to " + s
  interfaces.mqtt.publish(settings?.topicPub, "delay=${s}", settings?.QOS.toInteger(), settings?.retained)
}
*/

def refresh() {
  if (logEnable) log.debug settings?.topicPub + " get configuation"
  interfaces.mqtt.publish(settings?.topicPub, "conf", settings?.QOS.toInteger(), settings?.retained)
}

def configure() {
  log.info "Configure.."
  def map = [:]
  map['active_hold'] = settings.active_hold.toInteger()
  def json = JsonOutput.toJson(map)
  interfaces.mqtt.publish(settings?.topicPub, "conf=${json}", settings?.QOS.toInteger(), settings?.retained)
  if (logEnable) log.debug "send conf=${json}"
  refresh()
}

