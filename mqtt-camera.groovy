/**
   * Note: I borrowed a lot of code from the net - Thanks. CJC.
   
   *  ****************  MQTT Camera Driver (as Motion Sensor) ****************
   *
   *  importUrl: "https://raw.githubusercontent.com/shomegit/MQTT-Virtual-Switch-Control-Driver/master/MQTT-Virtual-Switch-Control-Driver.groovy"
   *
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
   */

metadata {
  definition (name: "MQTT Camera Driver", namespace: "ccoupe", author: "Cecil Coupe", importURL: "https://raw.githubusercontent.com/shomegit/MQTT-Virtual-Switch-Control-Driver/master/MQTT-Virtual-Switch-Control-Driver.groovy") {
    capability "Initialize"
    capability "MotionSensor"
        
    attribute "motion", "string"
    attribute "motion","ENUM",["active","inactive"]
  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicSub", type: "text", title: "Topic to Subscribe:", description: "Example Topic (topic/device/#)", required: false, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
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
  if (payload.contains("active")){
      if (logEnable) log.info "mqtt ${topic} => ${payload}"
      sendEvent(name: "motion", value: payload)
    }
  else if (payload.contains("inactive")){
      if (logEnable) log.info "mqtt ${topic} => ${payload}"
      sendEvent(name: "motion", value: payload)
     
  } else {
      sendEvent(name: "motion", value: topic.get('payload'))
  }
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
	//if (logEnable) runIn(900,logsOff)
	try {
    def mqttInt = interfaces.mqtt
    //open connection
    mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
    mqttInt.connect(mqttbroker, "hubitat_camera", settings?.username,settings?.password)
    //give it a chance to start
    pauseExecution(1000)
    log.info "Connection established"
		if (logEnable) log.debug "Subscribed to: ${settings?.topicSub}"
      mqttInt.subscribe(settings?.topicSub)
  } catch(e) {
    if (logEnable) log.debug "Initialize error: ${e.message}"
  }
}


def mqttClientStatus(String status){
  if (logEnable) log.debug "MQTTStatus- error: ${message}"
}

def logsOff(){
  log.warn "Debug logging disabled."
  device.updateSetting("logEnable",[value:"false",type:"bool"])
}
