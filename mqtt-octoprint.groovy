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
    input name: "topicNotify", type: "text", title: "Topic to Notify:", 
        description: "Optional text display device", 
        required: false, displayDuringSetup: true
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
  if (logEnable) log.info "${topic} => ${payload}"
  def parser = new JsonSlurper()
  if (topic == "${settings?.topicSub}/progress/printing") {
      def pr_vals = parser.parseText(payload)
      if (pr_vals['progress']) {
        sendEvent(name: "progress", value: pr_vals['progress'], displayed: true)
        if (settings?.topicNotify) {
          // display only handles 7.5 chars [word] width
          def nm = fixlen(settings?.topicSub, 7)
          send_note(nm+" "+pr_vals['progress'].toString()+"%");
        }
      }
  } else if (topic.startsWith("${settings?.topicSub}/event")) {
    evt_name = topic.split('/')[-1]
    sendEvent(name: 'status', value: evt_name, displayed: true)
    if (evt_name == "PrintStarted") {
      def pr_vals = parser.parseText(payload)
      sendEvent(name: "switch", value: "on")
      //log.info "start payload: ${pr_vals}"
      if (pr_vals['path']) {
        path = pr_vals['path']
        //log.info "start name: ${pr_vals['name']} path: ${path}"
        sendEvent(name: "file", value: path, displayed: true)
      }
    } else if (evt_name == "PrintDone" || evt_name == "PrintFailed") {
      def pr_vals = parser.parseText(payload)
      sendEvent(name: "switch", value: "off")
      //log.info "stop payload: ${pr_vals}"
      if (pr_vals['path']) {
        path = pr_vals['path']
        //log.info "stop name: ${pr_vals['name']} path: ${path}"
        sendEvent(name: "file", value: path, displayed: true)
      }
      if (settings?.topicNotify) {
          send_note(fixlen(settings?.topicSub,7)+" "+evt_name.substring(5));
      }
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

def send_note(msg) {
  interfaces.mqtt.publish(settings?.topicNotify, msg, settings?.QOS.toInteger(), false)
}
