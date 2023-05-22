/**
   * mqtt-tts.groovy.
   * Author: Cecil Coupe 
   * Purpose:
   *  Use a Computers audio system to generate and play a Hubitat tts url.
   *  Setting device volume with the buttons and then Configure
   *    saves the volume level. That will be used to override the device
   *    each time so it appears to be Sticky.
   *  Save Preference may not save the volume unless Configure button is pressed
   *    first. It will NOT redisplay the GUI widget's value. Refresh page in Brower.
   *  Refresh clears the HE volume setting.
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
    * Version 1.0.0 - July 1, 2020 
    *                 Speech Synthesis with settable volume
    *
   */

metadata {
  definition (name: "Mqtt TTS", namespace: "ccoupe", author: "Cecil Coupe", importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-tts.groovy") {
    capability "Initialize"
    capability "Configuration"
    capability "Refresh"
    capability "SpeechSynthesis"
    capability "AudioVolume"
		
		attribute "status", "ENUM", ["playing","stopped"]
    attribute "volume", "NUMBER"
  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicPub", type: "text", title: "Homie device", 
        description: "Just the Device Name",
        required: false, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
    input ("voice", "enum", title: "Pick a Voice",
            require: false, 
            displayDuringSetup:true,
            options: getVoices(), defaultValue: "Salli")
    input ("volumeTTS", "text", defaultValue: "Default",
            title: "TTS Volume 0..100",
            required: false,
            displayDuringSetup:true)
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
  if (topic == "homie/${settings.topicPub}/\$state") {
    if (payload == "busy") {
      sendEvent(name: "status", value: "playing", displayed: true);
    } else if (payload == "ready") {
      sendEvent(name: "status", value: "stopped", displayed: true);
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
	if (logEnable) runIn(900,logsOff) // clears debugging after 900 somethings
	try {
    def mqttInt = interfaces.mqtt
    mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
    mqttInt.connect(mqttbroker, "hubitat_${device}", settings?.username,settings?.password)
    //give it a chance to start
    pauseExecution(200)
    log.info "Connection established"
    topic = "homie/${settings.topicPub}/\$state"
		if (logEnable) log.debug "Subscribed to: ${topic}"
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

// Get here from Save Prefs button OR Configure button
// Purpose - save the HE driver variables we want to keep
def configure() {
  log.info "Configure.."
  if (settings.voice) {
    state.voice = settings.voice
  } else {
    state.voice = "Brian"
  }
  sendEvent([name:'voice', value: state.voice, displayed:true])
  log.info "Chosen Voice: ${state.voice}"
  if (settings.volumeTTS) {
    state.volumeTTS = settings.volumeTTS
  } else {
    state.volumeTTS = null
  }
  sendEvent([name:'volumeTTS', value: state.volumeTTS, displayed:true])
}

// refresh Clears tmpVol and settings
def refresh() {
  settings.volumeTTS = 'Default'
  state.tempVol = null
  sendEvent([name:'tempVol', value: state.tempVol, displayed:true])
  if (logEnable) log.debug "refresh() called"
}

// ---- TTS voice  player----
def speak(text) {
  speakpub(text, false)
}

def speak(text, volume) {
  speakpub(text, false)
}

def speak(text, volume, voice) {
  speakpub(text, false)
}

def deviceNotification(text) {
  speakpub(text, true)
}

def speakpub(text, wrap) {
  def tvol = null
  if (state.tempVol) {
    tvol = state.tempVol
    log.debug "using tts state.tempVol ${tvol}"
  } else if (settings.volumeTTS && settings.volumeTTS != 'Default') {
    tvol = settings.volumeTTS.toInteger()
    log.debug "using settings.volumeTTS ${tvol}"
  }
  if (tvol) {
    log.debug "temporary setting tts volume to ${tvol}."
    def topic = "homie/${settings.topicPub}/player/volume/set"
    interfaces.mqtt.publish(topic, tvol.toString(), settings?.QOS.toInteger(), false)
  }
  if (wrap) {
    text = "<emphasis level=\"strong\">" + text + "</emphasis>"
  }
  def sound = textToSpeech(text, state.voice)
  def urltmp = "homie/${settings.topicPub}/player/url/set"
  def payload = sound.uri
  if (logEnable) {
    log.debug "speak(${text}) => ${payload}"
  }
  interfaces.mqtt.publish(urltmp, payload, settings?.QOS.toInteger(), false)
}

def playerOff() {
  def topic = "homie/${settings.topicPub}/player/url/set"
  interfaces.mqtt.publish(topic, "off", settings?.QOS.toInteger(), false)
}


// ---- Volume stuff ---- Buttons on the page. NOT Preferences Not attributes
def setVolume(level) {
  // Displays in 'Current States'
  state.tempVol = level
  log.debug "temporarysetting chime volume to ${state.tempVol}."
  def topic = "homie/${settings.topicPub}/chime/volume/set"
  interfaces.mqtt.publish(topic, state.tempVol.toString(), settings?.QOS.toInteger(), false)
  sendEvent([name:'tempVol', value: state.tempVol, displayed:true])
}

def mute() {

}

def unmute() {
}

def volumeDown() {
  if (state.tempVol && state.tempVol >= 6) {
    state.tempVol -= 5
    sendEvent([name:'tempVol', value: state.tempVol, displayed:true])
  }
}

def volumeUp() {
  if (state.tempVol && state.tempVol <= 95) {
    state.tempVol += 5
    sendEvent([name:'tempVol', value: state.tempVol, displayed:true])
  }
}

def off() {
  playerOff()
}
