/**
   * mqtt-frigateP.groovy.
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
    * Version 1 Aug 20, 2021 - the beginning.
   */

metadata {
  definition (name: "Mqtt Frigate Camera", namespace: "ccoupe", author: "Cecil Coupe", importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-frigateP.groovy") {
    capability "Initialize"
    capability "MotionSensor"
    //capability "Configuration"
    //capability "Refresh"
    capability "Actuator"      // needed for custom commands from Rule Machine.
    capability "PresenceSensor"

    command "enable"          // set detect to on/off
    command "disable"
    command "set_Inactive"    // force motion value
    command "set_Active"
    command "arrived"         // force presence value
    command "departed"
    command "clips_on"        // set clips to on/off
    command "clips_off"
    command "snapshots_on"    // set snapshots to on/off
    command "snapshots_off"

       
    attribute "motion", "string"
    attribute "motion","ENUM",["active","inactive"]
    attribute "active_hold", "number"
  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicSub", type: "text", title: "Topic to Subscribe:", 
        description: "Example: frigate/front_door '/person' is supplied",
        required: false, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
    /*
    input name: "detect", type: "bool", title: "Detect Motion",
        required: true, displayDuringSetup: true, defaultValue: true,
        description: "Turn on motion detection?"
    input name: "clips", type: "bool", title: "Record Motion",
        required: true, displayDuringSetup: true, defaultValue: true,
        description: "Record movie clips for motion events?"
    input name: "snapshots", type: "bool", title: "Snapshots",
        required: true, displayDuringSetup: true, defaultValue: true,
        description: "Photograph for motion events?"
    */
    input name: "providePresence", 
      type: "bool",
      title: "Provide Presense",
      required: true, 
      defaultValue: false 
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
  if (logEnable) log.info "${device} ${topic} => ${payload}"
  if (topic.endsWith("person")) {
    if (payload.toInteger() > 0) {
      sendEvent(name: "motion", value: "active")
      arrived()
    } else {
      sendEvent(name: "motion", value: "inactive")
      departed()
    }
  } else {
    log.warn "unknown topic: ${topic}"
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
    def topicTop = "${settings?.topicSub}/person"
    log.info "Connection established"
		if (logEnable) log.debug "Subscribed to: ${topicTop}"
    mqttInt.subscribe(topicTop)
    
    if (settings?.stats) {
      def urltmp = "${settings?.topicSub}/stats"
      if (logEnable) log.debug "Subscribed to: ${urltmp}"
      mqttInt.subscribe(urltmp)
     }
    /*
    if (settings?.detect) {
      def urltmp = "${settings?.topicSub}/control"
      if (logEnable) log.debug "Subscribed to: ${urltmp}"
      mqttInt.subscribe(urltmp)
    }
    */
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
      while (true) {
        // wait for a minute for things to settle out, server to restart, etc...
        pauseExecution(1000*60)
        initialize()
        if (interfaces.mqtt.isConnected()) {
          log.info "mqtt reconnect success!"
          break
        }
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


// unsub/resub to property topic - cause parse to run ?
def refresh() {
  /*
  if (settings?.propertySub) {
    def urltmp = "${settings?.topicSub}/${settings?.propertySub}"
    interfaces.mqtt.unsubscribe(urltmp)
    pauseExecution(100)
    interfaces.mqtt.subscribe(urltmp)
    if (logEnable) log.debug "Refresh: re-subscribed to: ${urltmp}"
  } else {
  */
    if (logEnable) log.debug "nothing to refresh"
  //}
}

// 
def configure() {
  log.info "Configure.."
}

def on() {
}

def off() {
}

def enable() {
    def topic = "${settings?.topicSub}/detect/set"
    if (logEnable) log.debug " ${topic} ON"
    interfaces.mqtt.publish(topic, "ON", settings?.QOS.toInteger(), settings?.retained)
}

def disable() {
    def topic = "${settings?.topicSub}/detect/set"
    if (logEnable) log.debug " ${topic} OFF"
    interfaces.mqtt.publish(topic, "OFF", settings?.QOS.toInteger(), settings?.retained)
}


def set_Active() {
  sendEvent(name: "motion", value: "active")
}

def set_Inactive() {
  sendEvent(name: "motion", value: "inactive")
}

def arrived() {
  if (settings?.providePresence) {
    sendEvent(name: "presence", value: "present", linkText: deviceName, descriptionText: descriptionText)
  }
}

def departed() {
  if (settings?.providePresence) {
    sendEvent(name: "presence", value: "not present", linkText: deviceName, descriptionText: descriptionText)
  }
}

def clips_on() {
    def topic = "${settings?.topicSub}/clips/set"
    if (logEnable) log.debug " ${topic} ON"
    interfaces.mqtt.publish(topic, "ON", settings?.QOS.toInteger(), settings?.retained)
}

def clips_off() {
    def topic = "${settings?.topicSub}/clips/set"
    if (logEnable) log.debug " ${topic} OFF"
    interfaces.mqtt.publish(topic, "OFF", settings?.QOS.toInteger(), settings?.retained)
}

def snapshots_on() {
    def topic = "${settings?.topicSub}/snapshots/set"
    if (logEnable) log.debug " ${topic} ON"
    interfaces.mqtt.publish(topic, "ON", settings?.QOS.toInteger(), settings?.retained)
}

def snapshots_off() {
    def topic = "${settings?.topicSub}/snapshots/set"
    if (logEnable) log.debug " ${topic} OFF"
    interfaces.mqtt.publish(topic, "OFF", settings?.QOS.toInteger(), settings?.retained)
}
