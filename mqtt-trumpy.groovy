/**
   * mqtt-trumpy.groovy.
   * Author: Cecil Coupe 
   * Purpose:
   *  Weird and Fun device implemented on a Pi with MQTT connections. 
   *  A playful conversational Buglar alarm. Mycroft is used for NLP
   *  Hubitat is used to coordinate lights, switches, AV activities, starting HSM
   *  via RM rules.
   *  
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
    * Version 1.0.0 - provides Notification and Speech Synthesis
    * Version 2.0.0 
    *      - uses Mycroft for TTS, not amazon
    *     - implements Alarm: Siren and Strobe (matching device might not strobe)
    * Version 2.0.1 - Can turn Alarm off via MQTT message to us or button push
    *        will trigger Rule Machine rules. Only 1 button and only 'pushed'
   */
   
import groovy.json.JsonOutput
import groovy.json.JsonSlurper 

metadata {
  definition (name: "MQTT Trumpy v2", namespace: "ccoupe", author: "Cecil Coupe", importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-trumpy.groovy") {
    capability "Initialize"
    capability "Configuration"
    capability "Refresh"
    capability "SpeechSynthesis"
    capability "Switch"
    capability "Alarm"
    capability "Chime"
    capability "PushableButton"
		
    attribute "switch","ENUM",["on","off"]
    attribute "alarm","ENUM",["strobe", "off", "both", "siren"]
    attribute "status", "ENUM", ["playing","stopped"]
    attribute "soundName", "string"
    attribute "soundEffects", "{1=Doorbell, 2=Siren, 3=Horn, 10=Cops_Arrive, 11=Enjoy}"
    
    command "push"
    
  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicPub", type: "text", title: "Our MQTT device:", 
        description: "Example: 'trumpy_bear'", defaultValue: "trumpy_bear",
        required: false, displayDuringSetup: true
    input name: "cameraName", type: "text", title: "Use MQTT camera:", 
        description: "Example: 'trumpy_cam'", defaultValue: "trumpy_cam",
        required: false, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
    input ("soundName", "enum", title: "Chime Sound",
						require: false,
						options: getSounds(),
            defaultValue: "1 - Default")
    input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
  }
}

def getSounds() {
  return ["1 - Doorbell", "2 - Siren", "3 - Horn", "10 - Cops_Arrive", "11 - Enjoy"]
}

/*
// Build voice list for preferences display.
def getVoices() {
	def voices = getTTSVoices()
	voices.sort{ a, b ->
		a.language <=> b.language ?: a.gender <=> b.gender ?: a.gender <=> b.gender  
	}    
  def list = voices.collect{ ["${it.name}": "${it.name}:${it.gender}:${it.language}"] }
	return list
}
*/

def installed() {
    log.info "installed..."
}

// Track mqtt events on '$state' topic. Manage our response
def parse(String description) {
  msg = interfaces.mqtt.parseMessage(description)
  topic = msg.get('topic')
  payload = msg.get('payload')
  //log.info "TB incoming ${topic} <- ${payload}"
  if (topic == "homie/${settings.topicPub}/\$state") {
    if ((payload == 'busy' || payload == 'running')) {
      log.info "Trumpy Bear is running"
      state.tbmqtt = 'busy'
    } else if ((payload == 'idle' || payload == 'ready')) {
      state.tbmqtt = 'idle'
      log.info "Trumpy Bear turns off its switch"
      sendEvent(name: "switch", value: "off")
    } else if (payload == 'alarm') {
      log.info "Trumpy Bear wants to set the alarms"
      //sendEvent(name: "switch", value: "off")
    } else {
      log.info "TB unhandled ${topic} ${payload}"
    }
  } else if (topic == "homie/${settings.topicPub}/control/cmd") {
    if (payload.contains("on")){
      // Probably from the display panel code
      log.info("MQTT pushes TB_Cancel")
      push(1)
    } 
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
	if (logEnable) runIn(900,logsOff) // clears debugging after 900 seconds
	try {
    def mqttInt = interfaces.mqtt
    mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
    mqttInt.connect(mqttbroker, "hubitat_${device}", settings?.username,settings?.password)
    //give it a chance to start
    pauseExecution(200)
    log.info "Connection established"
    def topic = "homie/${settings.topicPub}/\$state"
		if (logEnable) log.debug "Subscribed to: ${topic}"
    mqttInt.subscribe(topic)
    topic = "homie/${settings.topicPub}/control/cmd"
    mqttInt.subscribe(topic)
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

// Do nothing for now - 
def refresh() {
    if (logEnable) log.debug "nothing to refresh"
}

// Pass along the choice of cameras
def configure() {
  log.info "Configure.."
  sendEvent(name: "numberOfButtons", value: 1, displayed: false)
  // init TrumpBear with camera choice
  def topic = "homie/${settings.topicPub}/control/cmd/set"
  def map = [:]
  map['cmd'] = 'init'
  map['reply'] = settings.cameraName
  def json = JsonOutput.toJson(map)
  interfaces.mqtt.publish(topic, json, settings?.QOS.toInteger(), settings?.retained)
}

def speak(text) {
  def urltmp = "homie/${settings.topicPub}/speech/say/set"
  interfaces.mqtt.publish(urltmp, text, settings?.QOS.toInteger(), false)}


def on() {
  sendEvent(name: "switch", value: "on")
  def topic = "homie/${settings.topicPub}/control/cmd/set"
  def map = [:]
  map['cmd'] = 'begin'
  def json = JsonOutput.toJson(map)
  interfaces.mqtt.publish(topic, json, settings?.QOS.toInteger(), settings?.retained)
  state.tbmqtt = 'idle'  // will be changed shortly
  if (logEnable) log.info "TrumpyBear Triggered"
}

// Off will reset Trumpy Bear and stop the chimes and siren
def off() {
  sendEvent(name: "switch", value: "off")
  def topic = "homie/${settings.topicPub}/control/cmd/set"
  def map = [:]
  map['cmd'] = 'end'
  def json = JsonOutput.toJson(map)
  interfaces.mqtt.publish(topic, json, settings?.QOS.toInteger(), settings?.retained)
  stop()      //chime
  alarmOff()  //siren a strobe
  if (logEnable) log.info "TrumpBear force cycle end"
} 

// ---- Siren -----

def both() {
  siren()
  strobe()
}

def siren() {
  def topic = "homie/${settings.topicPub}/siren/state/set"
  interfaces.mqtt.publish(topic, "on", settings?.QOS.toInteger(), settings?.retained)
}

def strobe() {
  def topic = "homie/${settings.topicPub}/strobe/state/set"
  interfaces.mqtt.publish(topic, "on", settings?.QOS.toInteger(), settings?.retained)
}

def alarmOff() {
  def topic = "homie/${settings.topicPub}/siren/state/set"
  interfaces.mqtt.publish(topic, "off", settings?.QOS.toInteger(), settings?.retained)
  topic = "homie/${settings.topicPub}/strobe/state/set"
  interfaces.mqtt.publish(topic, "off", settings?.QOS.toInteger(), settings?.retained)
}

// -- chime ---
def playSound(soundNumber) {
	def topic = "homie/${settings.topicPub}/chime/state/set"
  // map soundNumber to options string
  def sndList = getSounds()
  sndNum = soundNumber.toString()
  sndEntry = "1 - Doorbell"       // default
  for (snd in sndList) {
    flds = snd.split("-")
    if (flds[0].trim() == sndNum) {
      sndEntry = snd
      break
    }
  }
	if (logEnable) {
		log.debug "publish ${sndEntry} to ${topic}"
	}
	interfaces.mqtt.publish(topic, sndEntry, settings?.QOS.toInteger(), false)
}

// Use the stop button for both Chimes and Siren/Strobe
def stop() {
	def topic = "homie/${settings.topicPub}/chime/state/set"
	if (logEnable) {
		log.debug "publish 'stop' to ${topic}"
	}
	interfaces.mqtt.publish(topic, "off", settings?.QOS.toInteger(), false)
  alarmOff()
}

def push(btn) {
  btn = 1     // yes, I am overriding user preference
  if (logEnable) log.info "TB_Cancel  on"
  // the Event will trigger any Hubitat Rules. isStateChange is required
  // to fire more than once. 
  sendEvent(name: "pushed", value: btn.toInteger(), isStateChange:  true)
  // Tell trumpy_bear to quit what it's doing, if anything
  def topic = "homie/${settings.topicPub}/control/cmd/set"
  def map = [:]
  map['cmd'] = 'end'
  payload = JsonOutput.toJson(map)
  if (logEnable) log.info "${topic} ${payload}"
  interfaces.mqtt.publish(topic, payload, 1, false)
}

