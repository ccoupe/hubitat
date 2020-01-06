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
   */

    metadata {
        definition (name: "MQTT Virtual Multi Driver", namespace: "ccoupe", 
          author: "Cecil Coupe", 
          importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-ups.groovy") {
            capability "Initialize"
            capability "MotionSensor"
            capability "TemperatureMeasurement"
            capability "RelativeHumidityMeasurement"  
            
            attribute "motion", "string"
            attribute "motion","ENUM",["active","inactive"]
            attribute "temperature", "number"
            attribute "humidity", "number"
    	   }

        preferences {
          input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
          input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
          input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
          input name: "topicSub", type: "text", title: "Topic to Subscribe:", description: "Example Topic (topic/device/#)", required: false, displayDuringSetup: true
          input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
        }

    }


    def installed() {
        log.info "installed..."
    }

// Parse incoming device messages to generate events
def parse(String description) {
  topic = interfaces.mqtt.parseMessage(description).topic
  topic = topic.substring(topic.lastIndexOf("/") + 1)
	payload = interfaces.mqtt.parseMessage(description).payload
  
  if (logEnable) log.info "${topic} : ${payload}"
  if (topic == 'motion') {
    if (payload.contains("ON")){
       sendEvent(name: "motion", value: "active")
      }
    if (payload.contains("OFF")){
        sendEvent(name: "motion", value: "inactive")
    }
  } else if (topic == 'humidity') {
    if (payload != "--") {
      sendEvent(name: "humidity", value: payload)
    }
  } else if (topic == 'temperature') {
    if (payload != "--") {
      sendEvent(name: "temperature", value: payload)
    }
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
	if (logEnable) runIn(900,logsOff)
	try {
        def mqttInt = interfaces.mqtt
        //open connection
        mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
        mqttInt.connect(mqttbroker, "hubitat", settings?.username,settings?.password)
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


def off() {
 //   log.debug "off()"
	sendEvent(name: "motion", value: "inactive")
}

def on() {
   // log.debug "on()"
	sendEvent(name: "motion", value: "active")
}
