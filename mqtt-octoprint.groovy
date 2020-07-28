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
   *    Monitors Octoprints MQTT topics for 3D printer status.
   *    Sets a switch for Started, Stopped (or Failed) for use in Rules. 
   *    shows % done print progress.
   *    Note: monitoring temperatures may send Hubitat too much, too fast.
   *      so I don't subscribe to that.
   *  
   */

import groovy.json.JsonSlurper 
import java.util.GregorianCalendar

metadata {
  definition (name: "Mqtt Octoprint Monitor", namespace: "ccoupe", 
      author: "Cecil Coupe", 
      importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-octoprint.groovy"
    ) {
    capability "Initialize"
    capability "Switch" 
           
    attribute "switch","ENUM",["on","off"]
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
        description: "Example Topic (octoprint). Please don't use a #", 
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
  if (topic == "${settings?.topicSub}/progress/printing") {
      def pr_vals = parser.parseText(payload)
      if (pr_vals['progress']) {
        sendEvent(name: "progress", value: pr_vals['progress'], displayed: true)
      }
  } else if (topic.startsWith("${settings?.topicSub}/event")) {
    evt_name = topic.split('/')[-1]
    sendEvent(name: 'status', value: evt_name, displayed: true)
    def pr_vals = parser.parseText(payload)
    if (evt_name == "PrintStarted") {
      sendEvent(name: "switch", value: "on")
      if pr_vals['path'] {
        sendEvent(name: "file", values: pr_vals['path'])
      }
    } else if (evt_name == "PrintDone" || evt_name == "PrintFailed") {
      sendEvent(name: "switch", value: "off")
      if pr_vals['path'] {
        sendEvent(name: "file", values: pr_vals['path'])
      }
    }
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
    def topic = "${settings?.topicSub}/event/#"
    mqttInt.subscribe(topic)
		if (logEnable) log.debug "Subscribed to: ${topic}"
    topic = "${settings?.topicSub}/progress/printing"
    mqttInt.subscribe(topic)
		if (logEnable) log.debug "Subscribed to: ${topic}"
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

// We do NOT control the printer via mqtt. These methods are for
// Hubitat GUI state and Rule Machine
def on() {
 sendEvent(name: "switch", value: "on")
 sendEvent(name: "progress", value: "0", displayed: true)
}

def off() {
 sendEvent(name: "switch", value: "off")
}
