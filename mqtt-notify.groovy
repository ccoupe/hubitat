/**
   * mqtt-alarm.groovy.
   * Author: Cecil Coupe 
   * Purpose:
   *  Notification and Speech Synthesis:  text to TTS, sends mp3 to MQTT topic.
   *    where it is played by some device specific listener on the MQTT topic
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
    * Version 1.0.0 - provides Notification (text)
    *         1.0.3 - some settings can be set - not sticky on device. 
    *                 Use the device .json file for sticky.
   */

metadata {
  definition (name: "Mqtt Notify", namespace: "ccoupe", author: "Cecil Coupe", importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-notify.groovy") {
    capability "Initialize"
    capability "Configuration"
    capability "Refresh"
    capability "Notification"
		
  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicPub", type: "text", title: "Topic to Publish:", 
        description: "Example: homie/test_display/display/text/set",
        required: false, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
    input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
    input("stroke", "enum", title: "Text Color", require: false, displayDuringSetup:true, 
          options: ["Red", "White", "Blue", "Green"], defaultValue: "White")
    input("font", "enum", title: "Font Size", require: false, displayDuringSetup:true, 
          options: ["1 - Two Lines", "2 - Three Lines", "3 - Four Lines"], defaultValue: "1 - Two Lines")
    input("blank", "integer", title: "Blank After Minutes", require: false, displayDuringSetup:true, defaultValue: 5)
  }
}

import groovy.json.JsonOutput

def installed() {
    log.info "installed..."
}

// Parse incoming device messages to generate events
// There are none. Output only
def parse(String description) {
  msg = interfaces.mqtt.parseMessage(description)
  topic = msg.get('topic')
  payload = msg.get('payload')
}

def configure() {
    def sendit = false
    def items = [:]
    if (settings?.stroke) {
        if (settings.stroke != state.stroke) {
            state.stroke = settings.stroke
            items["stroke"] = state.stroke
        }
    }
    if (settings?.font) {
      if (settings.font != state.font) {
        flds = settings.font.split(" ")
        def num = 1
        if (flds[0] == "1") num = 1 
        else if (flds[0] == "2") num = 2
        else if (flds[0] == "3") num = 3
        state.font = num
        items["font"] = num
      }
    }
    if (settings?.blank) {
      if (settings.blank != state.blank) {
        def tmo = settings.blank.toInteger()
        if (tmo != state.blank) state.blank = tmo
        items["blank"] = tmo      
      }
    }
    flds = settings.topicPub.split("/")
    topic = "homie/"+flds[1]+"/display/cmd/set" // a hack - assumes many things.
    jstr = JsonOutput.toJson(["settings": items])
    log.info "Configure: ${jstr} --> ${topic}"
    interfaces.mqtt.publish(topic, jstr, settings?.QOS.toInteger(), false) 
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
    log.info "Connection established"
		//if (logEnable) log.debug "Subscribed to: ${topicTop}"
    //mqttInt.subscribe(topicTop)
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

// Do nothing.
def refresh() {
    if (logEnable) log.debug "nothing to do for refresh"
}

def deviceNotification(text) {
  if (logEnable) {
    log.debug "send ${text} => ${settings.topicPub}"
  }
  interfaces.mqtt.publish("${settings.topicPub}", text, settings?.QOS.toInteger(), false)    
}
