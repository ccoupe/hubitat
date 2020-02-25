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
    * Version 1.0.0 - provides Notification and Speech Synthesis
    *
   */

metadata {
  definition (name: "MQTT Alarm v1", namespace: "ccoupe", author: "Cecil Coupe", importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-motion3.groovy") {
    capability "Initialize"
    capability "Configuration"
    capability "Refresh"
    capability "SpeechSynthesis"
    capability "Notification"

  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicPub", type: "text", title: "Topic to Publish:", 
        description: "Example: homie/linuxbox  /player/url/set is appended",
        required: false, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
    input ("voice", "enum", title: "Pick a Voice",
            require: false, 
            displayDuringSetup:true,
            options: getVoices(), defaultValue: "Salli")
    input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
  }
}

// Build voice list for preferences display.
def getVoices() {
	def voices = getTTSVoices()
	voices.sort{ a, b ->
		a.language <=> b.language ?: a.gender <=> b.gender ?: a.gender <=> b.gender  
	}    
  def list = voices.collect{ ["${it.name}": "${it.name}:${it.gender}:${it.language}"] }
	return list
}

def installed() {
    log.info "installed..."
}

// Parse incoming device messages to generate events
def parse(String description) {
  msg = interfaces.mqtt.parseMessage(description)
  topic = msg.get('topic')
  payload = msg.get('payload')
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

// Do nothing for now - 
def refresh() {
    if (logEnable) log.debug "nothing to refresh"
}

// Set the voice
def configure() {
  log.info "Configure.."
  if (settings.voice) {
    state.voice = settings.voice
  } else {
    state.voice = "Brian"
  }
  sendEvent([name:'voice', value: state.voice, displayed:true])
  log.info "Chosen Voice: ${state.voice}"
}

def speak(text) {
  speakpub(text, false)
}

def deviceNotification(text) {
  speakpub(text, true)
}

def speakpub(text, wrap) {
  if (wrap) {
    text = "<emphasis level=\"strong\">" + text + "</emphasis>"
  }
  def sound = textToSpeech(text, state.voice)
  def urltmp = "${settings.topicPub}/player/url/set"
  def payload = sound.uri
  if (logEnable) {
    log.debug "speak(${text}) => ${payload}"
    log.debug "publish to: ${urltmp}"
  }
  interfaces.mqtt.publish(urltmp, payload, settings?.QOS.toInteger(), false)
}
