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
   
   /* 
    * Version 2 uses json and OLD arduino end device
    * Version 3 - January 26, 2020 - uses homie v3 topic structure, non-json
   */

metadata {
  definition (name: "MQTT Motion v3", namespace: "ccoupe", author: "Cecil Coupe", importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-motion3.groovy") {
    capability "Initialize"
    capability "MotionSensor"
    capability "Configuration"
    //capability "Refresh"


    //command "enable"
    //command "disable"
       
    attribute "motion", "string"
    attribute "motion","ENUM",["active","inactive"]
    attribute "active_hold", "number"
  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicSub", type: "text", title: "Topic to Subscribe:", 
        description: "Example Topic (homie/family_motion1/sensor/motion). Please don't use a #", 
        required: false, displayDuringSetup: true
    input name: "topicPub", type: "text", title: "Topic to Publish:",
        description: "Example Topic (homie/family_motion1/sensor/active_hold)", 
        required: false, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
    input name: "active_hold", type: "number", title: "Active Hold",
        required: false, displayDuringSetup: true, defaultValue: 45,
        description: "Number of seconds to wait for more motion when active"
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
  if (topic.endsWith("motion")) {
    if (payload.startsWith("active")) {
        if (logEnable) log.info "mqtt ${topic} => ${payload}"
        sendEvent(name: "motion", value: "active")
    } else if (payload.startsWith("inactive")){
        if (logEnable) log.info "mqtt ${topic} => ${payload}"
        sendEvent(name: "motion", value: "inactive")
    }
  } else if (topic.endsWith("active_hold")) {
    device.updateSetting("active_hold", [value: payload.toInteger(), type: "number"] )
    sendEvent(name: "active_hold",  value: payload.toInteger(), displayed: true )
    if (logEnable) log.info "remote active_hold is mqtt ${payload}"
  } else {
    log.warn "unknown topic: ${topic}"
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

// Dangerous
def refresh() {
  if (logEnable) log.debug settings?.topicPub + " get configuation"
  interfaces.mqtt.publish(settings?.topicPub, "", settings?.QOS.toInteger(), settings?.retained)
  sendEvent(name: "active_hold", value: settings.active_hold, displayed: true);
}

def configure() {
  log.info "Configure.."
  def actives = settings?.active_hold.toString()
  interfaces.mqtt.publish("${settings?.topicPub}/set", actives, settings?.QOS.toInteger(), true)
  if (logEnable) log.debug "setting active_hold to ${actives}"
  sendEvent(name: "active_hold", value: settings.active_hold, displayed: true);
}

