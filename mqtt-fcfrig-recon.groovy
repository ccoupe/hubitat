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
   *  1.0.0 - Initial release - for eMotion Sensor
   *  1.0.1 - Sep 21, 2025 - add code for eMotion Pro 
   */

import groovy.json.JsonSlurper 
//import java.util.GregorianCalendar

metadata {
  definition (name: "MQTT Frigate face-recog", namespace: "ccoupe", 
      author: "Cecil Coupe", 
      importURL: "https://raw.githubusercontent.com/ccoupe/mqtt-fcfrig-recon/master/"
    ) {
    capability "Initialize"
    capability "MotionSensor"
    capability "Configuration"
    capability "Refresh"
	capability "Actuator"     // so it can be 'commanded'
    capability "PresenceSensor"
    //attribute "recognized", "json_object"
    attribute "person", "string"
    attribute "camera", "string"
    attribute "location", "string"
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
    input name: "camera", type: "text", required: true,
        title: "Camera Name",
      	description: "Camera Name or *",  displayDuringSetup: true
    input name: "person", type: "text", required: true,
        title: "Person name[s]",
      	description: "Name[, name] or *",  displayDuringSetup: true
   input name: "threshold", type: "decimal", required: true, 
       title: "Threshold",
       description: "confidence between 0.0 and 1.0", displayDuringSetup: true,
       defaultValue: 0.80
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
  logEnable = true;
  if (logEnable) log.info "rcvd: ${topic} => ${payload}"
  def parser = new JsonSlurper()
  def rmconf = parser.parseText(payload)
  def tstamp = rmconf['timestamp']
  def picId = rmconf['id']			// probably not used
  def Boolean use_cam = false
  if (settings.camera ==  '*') {
    use_cam = true
  } else if (rmconf['camera'] == settings.camera) { 
	use_cam = true 
  }
    
  def Boolean use_persons = false
  def perList = []
  if (settings.person == '*')  {
	  use_persons = true
  } else if (settings.person == rmconf['name']) {
	  use_persons = true
  }
  // If not face, or name is null or score is 0 then reset and skip
  if (rmconf['type'] != "face" || rmconf['score'] < settings.threshold) {
     log.info("unknown at ${rmconf['camera']}")
     return
  }
  
  def locations = [:]
  locations['doorbell_cam'] = 'Front Entry'
  locations['pi5_cam'] = 'Office'
  locations['trumpy_cam'] = 'Living Room'
  locations['guesty_cam'] = 'Guest Room'
    
  //if (logEnable) log.info("use_cam: ${use_cam} use_persons: ${use_persons}")
  if (use_cam && use_persons) {
	if (logEnable) log.info("setting recognized(${perList}) = ${payload}")
	camera = rmconf['camera']
	person = rmconf['name']
	/* Set an attribute via sendEvent */
	sendEvent(name: "camera", value: camera)
	sendEvent(name: "person", value: person)
	sendEvent(name: "location", value: locations[camera])
    sendEvent(name: "presence", value: "present")
    log.info("${camera} sees ${person} at ${locations[camera]}")
	runIn(5, timerFired)
  }
}

def timerFired() {
    sendEvent(name: "presence", value: "not present")
    if (logEnabled) log.info("not present")
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
    brokerName = "${getLocation().getHub().name}_${device}"
    log.info "connect to ${mqttbroker} as ${brokerName}"
    mqttInt.connect(mqttbroker, brokerName, settings?.username,settings?.password)
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

