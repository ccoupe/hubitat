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

import groovy.json.JsonOutput

metadata {
		definition (name: "Mqtt Trumpy Laser", namespace: "ccoupe", 
			author: "Cecil Coupe", 
			importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/trumpy-lasers.groovy") {
				capability "Initialize"
				capability "Switch"
				
				attribute "switch","ENUM",["on","off"]
        command "exec", ["string"]
        command "Laser_on"
        command "Laser off"
        command "Pan", ["integer"]
        command "Tilt", ["integer"]
		 }

		preferences {
			input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
			input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
			input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
			input name: "topicSub", type: "text", title: "Topic to Subscribe:", description: "Example Topic 'turret_back/turret_1", required: false, displayDuringSetup: true
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
	      defaultValue: 10,
	      description: "Seconds until auto turning off"
	      )
      input ("routine", "enum", title: "Pick a routine",
          require: false, 
          displayDuringSetup:true,
          options: getExecs(), defaultValue: "hzig"
        )
      input ("time", "text", defaultValue: "4",
            title: "number of seconds",
            required: false,
            displayDuringSetup:true
        )
      input ("count", "text", defaultValue: "4",
            title: "number of executions",
            required: false,
            displayDuringSetup:true
        )
      input ("lines", "text", defaultValue: "4",
            title: "number of lines (zigs)",
            required: false,
            displayDuringSetup:true
        )
      input ("radius", "text", defaultValue: "10",
            title: "circle radius",
            required: false,
            displayDuringSetup:true
        )
      input ("length", "text", defaultValue: "10",
            title: "length (diamond, crosshairs, random",
            required: false,
            displayDuringSetup:true
        )
      input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
    }
}

def getExecs() {
  return ['square', 'circle', 'diamond', 'crosshairs', 'hzig', 'vzig', 'random', 'TB Tame', 'TB Mean']
}

def installed() {
   log.info "installed..."
}

// Parse incoming device messages to generate events
def parse(String description) {
  topic = interfaces.mqtt.parseMessage(description).topic
	payload = interfaces.mqtt.parseMessage(description).payload  
  if (logEnable) log.info "on message: ${topic} : ${payload}"
  tur_topic = "homie/${settings?.topicSub}/control"
  if (topic == tur_Topic) {
    if (payload.contains("OK")){
      // device signaled it's ready. Tell hubitat it's done.
      sendEvent(name: "switch", value: "off")
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
        mqttInt.connect(mqttbroker, "hubitat_${device}", settings?.username,settings?.password)
        //give it a chance to start
        pauseExecution(1000)
        log.info "Connection established"
        topic = "homie/${settings?.topicSub}/control"
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
  log.info "Configure"
}


def logsOff(){
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}


def on() {
  if (logEnable) log.info "Using ${settings?.topicSub}"
	//sendEvent(name: "switch", value: "on")
  if (AutoOff) {
        runIn(settings.offSecs.toInteger(), off)
    }
  topic = "homie/${settings?.topicSub}/control/set"
  def map = [:]
  map['count'] = settings?.count.toInteger()
  map['time'] = settings?.time.toInteger()
  rt = settings?.routine
  map['exec'] = rt  

  //payload = '{"exec": "vzig", "count": 8, "lines": 4, "time": 4}'
  if (rt == 'hzig' || rt == 'vzig') {
    map['lines'] = settings?.lines.toInteger()
    //payload = '{"exec":${rt}, "count": ${settings?.count}, "lines":, ${settings?.lines}, "time": ${settings?.time}}'
  } else if (rt == 'square') {
    //payload = '{"exec":${rt}, "count": ${settings?.count}, "time": ${settings?.time}}'
  } else if (rt == 'diamond' || rt == 'crosshairs' || rt == 'random') {
    map['lines'] = settings?.lines.toInteger()
    //payload = '{"exec":${rt}, "count": ${settings?.count}, "lines":, ${settings?.lines}, "time": ${settings?.time}}'
  } else if (rt == 'circle') {
    map['radius'] = settings.radius.toInteger()
    //payload = '{"exec":${rt}, "count": ${settings?.count}, "radius":, ${settings?.radius}, "time": ${settings?.time}}'
  } else {
    log.warn "Routine Not selected"
  }
  def payload = JsonOutput.toJson(map)
  if (logEnable) log.info "Execute: ${payload}"
  interfaces.mqtt.publish(topic, payload, 1, false)
}

def off() {
  if (logEnable) log.info "Laser off"
    sendEvent(name: "switch", value: "off")
  topic = "homie/${settings?.topicSub}/control/set"
  payload = 'stop'
  interfaces.mqtt.publish(topic, payload, 1, false)
}

def exec(String cmd) {
  if (logEnable) log.info "Laser exec ${cmd}"
  topic = "homie/${settings?.topicSub}/control/set"
  interfaces.mqtt.publish(topic, cmd, 1, false)
}

def Laser_on() {
  topic = "homie/${settings?.topicSub}/control/set"
  interfaces.mqtt.publish(topic, "{\"power\": 100}", 1, false)
}

def Laser_off() {
  topic = "homie/${settings?.topicSub}/control/set"
  interfaces.mqtt.publish(topic, "{\"power\": 0}", 1, false)
}

def Pan(Integer degrees) {
  topic = "homie/${settings?.topicSub}/control/set"
  interfaces.mqtt.publish(topic, "{\"pan\": $degrees}", 1, false)
}

def Tilt(Integer degrees) {
  topic = "homie/${settings?.topicSub}/control/set"
  interfaces.mqtt.publish(topic, "{\"tilt\": $degrees}", 1, false)
}
