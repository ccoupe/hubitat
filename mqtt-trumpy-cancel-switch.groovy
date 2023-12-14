/*
   * ------------------------------------------------------------------------------------------------------------------------------
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
   *  1.0.0 - Initial release
   *  listens on [homie/trumpy_bear/control/cmd for 'on' (begin cancel)
   *      If this was a pushbutton we could put all the code in mqtt_trumpy.groovy.
   *      Hint. Hint.
   */
import groovy.json.JsonOutput
import groovy.json.JsonSlurper 

metadata {
		definition (name: "Mqtt Trumpy Cancel Switch", namespace: "ccoupe", 
			author: "Cecil Coupe", 
			importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/trumpy-cancel-switch.groovy") {
				capability "Initialize"
				capability "Switch"
				
				attribute "switch","ENUM",["on","off"]
		 }

		preferences {
			input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
			input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
			input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
			input name: "topicSub", type: "text",
        title: "Topic to Subscribe:",
        description: "Example Topic (homie/trumpy_bear)",
        required: false, 
        displayDuringSetup: true,
        defaultValue: 'homie/trumpy_bear'
			input (
	      name: "AutoOff",
	      type: "bool",
	      title: "Enable auto off", 
	      required: false, 
	      displayDuringSetup: false, 
	      defaultValue: false
	    )
	    input (
	      name: "offSecs",
	      type: "number",
	      title: "Seconds to Off",
	      required: false,
	      displayDuringSetup: true,
	      defaultValue: 30,
	      description: "Seconds until auto turning off"
	      )
      input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
    }
}


def installed() {
   log.info "installed..."
}

// Parse incoming device messages to generate events
def parse(String description) {
  topic = interfaces.mqtt.parseMessage(description).topic
	payload = interfaces.mqtt.parseMessage(description).payload
  
  if (logEnable) log.info "on message: ${topic} : ${payload}"
  subTopic = "${settings?.topicSub}/control/cmd"
  if (topic == subTopic) {
    if (payload.contains("on")){
      log.info("MQTT sets TB_Cancel_Switch")
      on()
    } else if (payload.contains('off')){
      off()
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
	if (logEnable) runIn(900,logsOff)
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
        topic = "${settings?.topicSub}/control/cmd"
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

def configure() {
  log.info "Configure.."

}


def logsOff(){
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}


def on() {
  if (logEnable) log.info "TB_Cancel  on"
  // the Event will trigger any Hubitat Rules.
  sendEvent(name: "switch", value: "on")
  // Tell trumpy_bear to quit what it's doing, if anything
  def topic = "${settings.topicSub}/control/cmd/set"
  def map = [:]
  map['cmd'] = 'end'
  payload = JsonOutput.toJson(map)
  if (logEnable) log.info "${topic} ${payload}"
  interfaces.mqtt.publish(topic, payload, 1, false)
}

def off() {
  if (logEnable) log.info "TB_Cancel off"
  sendEvent(name: "switch", value: "off")
}
