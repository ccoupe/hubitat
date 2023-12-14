/**
   * mqtt-chime.groovy.
   * Author: Cecil Coupe 
   * Purpose:
   *  Use a Computers audio system to play a Chime sound.
   *  Chimes use a number to pick the mp3 to play. The device maps it. Not hubitat. 
   *  The 'device default' volume is not changeable by Hubitat. By Design. It is
   *    overridden for each 'play'
   *  Setting device volume with the buttons and then Configure
   *    saves the volume level. That will be used to override the device
   *    each time so it appears to be Sticky.
   *  Save Preference may not save the volume unless Configure button is pressed
   *    first. It will NOT redisplay the GUI widget's value. Refresh page in Brower.
   *  Refresh clears the HE volume setting.
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
    * Version 1.0.0 - July 1, 2020
    *       provides Chime and settable volume.
    *
   */

metadata {
  definition (name: "Mqtt Chime", namespace: "ccoupe", author: "Cecil Coupe", 
        importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-chime.groovy") {
    capability "Initialize"
    capability "Configuration"
    capability "Refresh"
		capability "Chime"
    capability "Tone"
    capability "AudioVolume"
		
		attribute "status", "ENUM", ["playing","stopped"]
		attribute "soundName", "string"
    attribute "soundEffects", "{1=Doorbell, 2=Siren, 3=Horn, 10=Cops_Arrive, 11=Enjoy}"
    attribute "mute", "ENUM", ["unmuted", "muted"]
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
    input ("chimeName", "enum", title: "Pick a Chime",
						require: false,
						options: getSounds(),
            defaultValue: "1 - Doorbell")
    input ("volumeChime", "text", defaultValue: "Default",
            title: "Chime Volume 0..100",
            required: false,
            displayDuringSetup:true)
    input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
  }
}

// TODO: get the list from 'storage'
def getSounds() {
  
  return ["1 - Doorbell", "2 - Siren", "3 - Horn", "10 - Cops_Arrive", "11 - Enjoy"]
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

// Called by Save Prefs Button.
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
    brokerName = "${getLocation().getHub().name}_${device}"
    log.info "connect to ${mqttbroker} as ${brokerName}"
    mqttInt.connect(mqttbroker, brokerName, settings?.username,settings?.password)
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
// Purpose - save the HE driver variables/attributes we want to keep
def configure() {
  log.info "Configure.."
  // Save state.tempVol, if it exists
  if (state.tempVol) {
    log.info "confgure() attempting ${state.tempVol}"
    // TODO: not updating GUI widget reload page or not
    settings.volumeChime  = state.tempVol
  }
}

// refresh Clears tmpVol and settings
def refresh() {
  settings.volumeChime = 'Default'
  state.tempVol = null
  sendEvent([name:'tempVol', value: state.tempVol, displayed:true])
  if (logEnable) log.debug "refresh() called"
}

// -- chime stuff ---

def setVol() {
  def tvol = null
  if (state.tempVol) {
    tvol = state.tempVol
    //log.debug "using state.tempVol ${tvol}"
  } else if (settings?.volumeChime && settings.volumeChime != 'Default') {
    tvol = settings.volumeChime.toInteger()
    //log.debug "using settings.volumeChime ${tvol}"
  }
  if (tvol) {
    //log.debug "temporary setting chime volume to ${tvol}."
    def topic = "homie/${settings.topicPub}/chime/volume/set"
    interfaces.mqtt.publish(topic, tvol.toString(), settings?.QOS.toInteger(), false)
  }
}


def beep() {
  setVol()
  ent = settings?.chimeName
  num = ent.split("-")[0].trim().toInteger()
  playSound(num)
  //log.debug "beep ent: ${num}"
}

def playSound(soundNumber) {
  setVol()
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
	def topic = "homie/${settings?.topicPub}/chime/state/set"
	if (logEnable) {
		log.debug "publish ${sndEntry} to ${topic}"
	}
	interfaces.mqtt.publish(topic, sndEntry, settings?.QOS.toInteger(), false)
}


def stop() {
	def topic = "homie/${settings.topicPub}/chime/state/set"
	if (logEnable) {
		log.debug "publish 'stop' to ${topic}"
	}
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
  stop()
}
