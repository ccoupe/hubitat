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
   *    Monitors "network" MQTT topics for computer node status.
   *      reports node status attribute
   *      send notification for any thing that is not OK. 
   *      Literally 'not OK'
   *    
   *   
  */

import groovy.json.JsonSlurper 
import java.util.GregorianCalendar

metadata {
  definition (name: "Mqtt Network Monitor", namespace: "ccoupe", 
      author: "Cecil Coupe", 
      importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-network-status.groovy"
    ) {
    capability "Initialize"
    capability "Switch" 
    capability "Notification"
    
    attribute "switch","ENUM",["on","off"]
    attribute "status", "string"
 
 }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", 
        required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", 
        description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:",
        description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicSub", type: "text", title: "Topic to Subscribe:", 
        description: "Example Topic (network/status). Please don't use a #", 
        required: true, displayDuringSetup: true, defaultValue: "network/status"
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
  if (topic == settings?.topicSub) {
    // get a list from the payload. Typically only one list item
    def pr_vals = parser.parseText(payload)
    for (ent in pr_vals) {
      handleNode(ent)
    }
  }
}


def join2(beg, lst) {
  len = lst.size()
  String out = ""
  for (int i = 1; i < len; i++) {
    out = out + lst[i]
    if (i < len-1) out = out + " "
  }
  return out
}

def updateStatus(node, msg) {
  upd = state.status
  if (!upd) upd = [:]
  upd[node] = msg
  state.status = upd
}

def handleNode(ent) {
  def flds = []
  flds = ent.split(" ")
  node = flds[0]
  msg = join2(1, flds)
  log.info("ent: ${node} ${msg}")
  /* moved to child.app
  if (flds[2] == "OK" && flds[1] == "is") {
    log.info("ingore: ${ent}")
  } else {
    log.info("notify: ${node}=>${msg}")
    send_note(ent);
  }
  */
  send_note(ent)
  updateStatus(node, msg)
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
    def topic = settings?.topicSub
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

def on() {
  sendEvent(name: "switch", value: "on")
}

def off() {
  sendEvent(name: "switch", value: "off")
}

def send_note(msg) {
  sendEvent(name: "deviceNotification", value: msg, isStateChange: true)
}
