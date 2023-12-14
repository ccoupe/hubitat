/**
   * mqtt-siren.groovy.
   * Author: Cecil Coupe 
   * Purpose:
   *  Use a Computers audio system to play a Siren sound.
   *  The 'device default' volume is not changeable by Hubitat. By Design. It is
   *    overridden for each 'play'
   *  Can set the volume with the buttons and then Configure
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
    *     provides siren settable volume
    *
   */

metadata {
  definition (name: "Mqtt Siren", namespace: "ccoupe", author: "Cecil Coupe", 
        importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-siren.groovy") {
    capability "Initialize"
    capability "Configuration"
    capability "Refresh"
    capability "Alarm"
    capability "AudioVolume"
		
		attribute "status", "ENUM", ["playing","stopped"]
    attribute "alarm", "ENUM", ["strobe", "off", "both", "siren"]
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
    input ("volumeSiren", "text", defaultValue: "Default",
            title: "Siren Volume 0..100",
            required: false,
            displayDuringSetup:true)
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
// Purpose - save the HE driver variables we want to keep
def configure() {
  log.info "Configure.."
  // Save state.tempVol, if it exists
  if (state.tempVol) {
    log.info "confgure() attempting ${state.tempVol}"
    // TODO: not updating GUI widget reload page or not
    settings.volumeSiren  = state.tempVol
  }
}

def refresh() {
  settings.volumeSiren = 'Default'
  state.tempVol = null
  sendEvent([name:'tempVol', value: state.tempVol, displayed:true])
  if (logEnable) log.debug "refresh() called"
}

def stop() {
	def topic = "homie/${settings.topicPub}/siren/state/set"
	if (logEnable) {
		log.debug "publish 'stop' to ${topic}"
	}
	interfaces.mqtt.publish(topic, "off", settings?.QOS.toInteger(), false)
}

// ---- Siren -----
def both() {
  siren()
  strobe()
}

def siren() {
  def tvol = null
  if (state.tempVol) {
    tvol = state.tempVol
    log.debug "siren settings state.tempVol ${tvol}"
  } else if (settings.volumeSiren && settings.volumeSiren != 'Default') {
    tvol = settings.volumeSiren.toInteger()
    log.debug "siren settings.volumeSiren ${tvol}"
  }
  if (tvol) {
    log.debug "temporary setting siren volume to ${tvol}."
    def topic = "homie/${settings.topicPub}/siren/volume/set"
    interfaces.mqtt.publish(topic, tvol.toString(), settings?.QOS.toInteger(), false)
  }

  def topic = "homie/${settings.topicPub}/siren/state/set"
  interfaces.mqtt.publish(topic, "on", settings?.QOS.toInteger(), settings?.retained)
}

def strobe() {
  def topic = "homie/${settings.topicPub}/strobe/state/set"
  interfaces.mqtt.publish(topic, "on", settings?.QOS.toInteger(), settings?.retained)
}

def off() {
  def topic = "homie/${settings.topicPub}/siren/state/set"
  interfaces.mqtt.publish(topic, "off", settings?.QOS.toInteger(), settings?.retained)
  topic = "homie/${settings.topicPub}/strobe/state/set"
  interfaces.mqtt.publish(topic, "off", settings?.QOS.toInteger(), settings?.retained)
}


// ---- Volume stuff ---- Buttons on the page. NOT Preferences. NOT attributes
def setVolume(level) {
  // Displays in 'Current States'
  state.tempVol = level
  log.debug "temporarysetting siren volume to ${state.tempVol}."
  def topic = "homie/${settings.topicPub}/siren/volume/set"
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
