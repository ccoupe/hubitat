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
   *  1.0.0 - Looks like a switch for dashboard purposes.
   */

import groovy.json.JsonOutput

metadata {
		definition (name: "Mqtt Publish List", namespace: "ccoupe", 
			author: "Cecil Coupe", 
			importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/raw/mqtt-publish-list.groovy") {
				capability "Initialize"
				capability "Switch"
				
		 }

		preferences {
			input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
			input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
			input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
			input name: "topicPub", type: "text", title: "Topic to Publish to:", description: "Example Topic 'test/switch/set", required: false, displayDuringSetup: true
			input name: "onPub", type: "text", title: "Nodes List",
          description: "Nodes to Boot", required: false, 
          displayDuringSetup: true
			input (
	      name: "AutoOff", type: "bool", title: "Enable auto off",  required: false, 
	      displayDuringSetup: false, defaultValue: true
	    )
	    input (
	      name: "offSecs", type: "number", title: "Seconds to Off", required: false,
	      displayDuringSetup: true, defaultValue: 1, description: "Seconds until auto turning off"
	      )
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
  /* we are send only - we don't listen to mqtt
  tur_topic = "homie/${settings?.topicSub}/control"
  if (topic == tur_Topic) {
    if (payload.contains("OK")){
      // device signaled it's ready. Tell hubitat it's done.
      sendEvent(name: "switch", value: "off")
    }
  }
  */
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
        mqttInt.connect(mqttbroker, "hubitat_${device}", settings?.username,settings?.password)
        //give it a chance to start
        pauseExecution(1000)
        log.info "Connection established"
        /*
        topic = settings?.topicPub
        if (logEnable) log.debug "Subscribed to: ${topic}"
        mqttInt.subscribe(topic)
        */
          
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
  log.info "Configure"
}


def logsOff(){
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}


def on() {
  def jmap = [:]
  def nodes = []
  if (settings?.onPub) {
    nodes = settings.onPub.split(',')
  }
  if (nodes.size() > 0) {
    jmap["cmd"] = "reboot"
    jmap["nodes"] = nodes
    jstr = JsonOutput.toJson(jmap)
    log.info "publish ${jstr} to ${settings?.topicPub}"
    interfaces.mqtt.publish(settings?.topicPub, jstr, 0, false)
    if (settings?.AutoOff) {
      runIn(settings?.offSecs.toInteger(), off)
    }
  }
}

def off() {
  /*
  // Do not send anything for off. We just want the switch visual reset.
  offStr = 'off'
  if (settings?.offPub) onStr = settings.offPub
  if (logEnable) log.info "publish 'off' to ${settings?.topicPub}"
  interfaces.mqtt.publish(settings?.topicPub, offStr, 1, false)
  */
}

