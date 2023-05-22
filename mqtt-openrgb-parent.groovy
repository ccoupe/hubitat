/**
   * mqtt-openrgb-parent.groovy.
   * Author: Cecil Coupe 
   * Purpose:
   *  Manage MQTT connection and child devices for One openrgb machine
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
   * A lot of code was taken from tomw's Switchbot drivers.
   * A key concept is identifing childern and passing around an ID.
   *
   * cDev = addChildDevice("ccoupe", 
   *                "Mqtt OpenRGB Device", 
   *                "/openrgb/games/Patriot_Viper_Steel_RGB/set",
   *                [isComponent: true, name: "Patriot_Viper_Steel_RGB"])
   * cDev.updateDataValue("Id", "/openrgb/games/Patriot_Viper_Steel_RGB/set")
   * 
   * Note that deviceNetworkId is the mqtt topic which is a unique string. 
   * AND it is the same as the "Id" DataValue attached to the child device.
   * 
   * 
   * Version 1.0.0 - provides minimal functionality - proof of concept.
   * Todo:
   *      use a "generic component rgb" device for children? 
   */

metadata {
  definition (name: "Mqtt OpenRGB Parent", namespace: "ccoupe", author: "Cecil Coupe", importURL: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-openrgb-parent.groovy") {
    capability "Actuator"
    capability "Initialize"
    capability "Configuration"
    capability "Refresh"
    capability "ColorControl"
    capability "Bulb"

    command "loadProfile", ["string"]
    command "createChildDevices"
    command "deleteChildDevices"
  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicSub", type: "text", title: "Topic to Monitor:", 
        description: "Enter 'games' for openrgb/games/config",
        required: false, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
    input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
    input ("Profiles", "enum", title: "Pick Profile",
            require: false, 
            displayDuringSetup:true,
            options: getProfiles(), defaultValue: "none")

  }
}



def installed() {
    log.info "installed..."
}
def getProfiles() {
  if (!state.profiles) {
    state.profiles = ["none"]
  }
  return state.profiles
}

def createProfiles(pfList)
{
  log.info("Have profiles: ${pfList}")
  state.profiles = pfList
}

// Parse incoming mqtt messages 
// 
def parse(String description) {
  msg = interfaces.mqtt.parseMessage(description)
  topic = msg.get('topic')
  payload = msg.get('payload')
  log.info("mqtt rcvd: ${topic} => ${payload}")
  if (topic == "openrgb/" + settings?.topicSub + "/config") {
    // is it the parents config topic?
    def jsonSlurper = new groovy.json.JsonSlurper()
    def mapDev = jsonSlurper.parseText(payload)
    def rgbGroupNm = mapDev["name"]
    def devMap = mapDev["devices"]
    createProfiles(mapDev["profiles"])
    
    // check/create child devices and topics for all 
    // Only works on mqtt retained payload from this topic
    // Does not replace existing childDevices. Todo?
    def childList = getChildDevices()
    if (childList.isEmpty()) {
      devMap.each{key, ent -> mqttBuildChildren(ent, rgbGroupNm) }
      /*
      for (it in devMap) {
        mqttBuildChildren(it, rgbGroupNm)
      }
      */
    }
  } else {
    /*  
     * Optional: compare to one of the childrens id's and do something
     * Except we should never recieve anything because the openrgb proxy
     * won't to call us because it is mostly send only. 
     */
     log.info("that topic should not have been used")
  }
}

/*
 *  Assuming this is only called shortly after initialize()
 *  Beware! TODO!
 */
def mqttBuildChildren(devMap, nodeName) {
  //log.info("mqttBuildChildren ${nodeName} ${devMap}")
  def devName = devMap["name"].replaceAll("\\s","_")
  def topic = "openrgb/${nodeName}/${devName}/set"
  //log.info("create child device with Id: ${topic}")
  // create a child
  def child = addChildDevice("ccoupe", 
                   "Mqtt OpenRGB Device", 
                    topic,
                   [isComponent: true, name: devName])
  child.updateDataValue("Id", topic)
  child.updateDataValue("modes", groovy.json.JsonOutput.toJson(devMap["modes"]))
  child.updateDataValue("zones",  groovy.json.JsonOutput.toJson(devMap["zones"]))
  child.initCommands(devMap["modes"],devMap["zones"])
  
  // Could create a mqtt subscription for the child,
  // even though it won't be used. Watch out for topic loops
  // interfaces.mqtt.subscribe(topic)
}

// ToDo: set retain=true - makes the setting sticky?
def childSend(topic, payload) {
  log.info ("parent send: ${topic} => ${payload}")
  interfaces.mqtt.publish(topic, payload, 1, false)
}

def configure() {
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

/*
 *  Initialize runs at hub startup 
 * We connect to the mqtt broker and we have to manage that
 * connection for us and the child devices.
 */
def initialize() {
	if (logEnable) runIn(900,logsOff) // clears debugging after 900 somethings
	try {
    def mqttInt = interfaces.mqtt
    mqttbroker = "tcp://" + settings?.MQTTBroker + ":1883"
    mqttInt.connect(mqttbroker, "hubitat_${device}", settings?.username,settings?.password)
    //give it a chance to start
    pauseExecution(200)
    log.info "Connection established"
    topicTop = "openrgb/" + settings?.topicSub + "/config"
		if (logEnable) log.debug "Subscribed to: ${topicTop}"
    mqttInt.subscribe(topicTop)
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

// Do nothing.
def refresh() {
  if (logEnable) log.debug "nothing to do for refresh"
}

/*
 * Set the color for all the Children. OpenRGB has an API for that.
 */
def setColor(colormap)
{
  def cRGB = hubitat.helper.ColorUtils.hsvToRGB([colormap.hue, colormap.saturation, colormap.level])
  def jstr = groovy.json.JsonOutput.toJson(["color": [r: cRGB[0].toInteger(), 
              g: cRGB[1].toInteger(),
              b: cRGB[2].toInteger()]])
  state.color = jstr
  state.state = "on"
  topic = "openrgb/" + settings?.topicSub + "/cmd/set"
  interfaces.mqtt.publish(topic, jstr)
}

def off() 
{
  def jstr = groovy.json.JsonOutput.toJson("state": "off") 
  state.state = "off"
  topic = "openrgb/" + settings?.topicSub + "/cmd/set"
  interfaces.mqtt.publish(topic, jstr)
}

def on()
{
  jstr = state.color
  if (!jstr) {
    // we can't do on() unless we have a saved value. Make one.
    log.info("on with low levels")
    jstr = groovy.json.JsonOutput.toJson(["color": [r: 30.toInteger(), 
                g: 30.toInteger(),
                b: 30.toInteger()]])
    state.color = jstr
  }
  state.state = "on"
  topic = "openrgb/" + settings?.topicSub + "/cmd/set"
  interfaces.mqtt.publish(topic, jstr)
  
}

def loadProfile(arg) {
  //log.info("loadProfile called with arg = ${arg}")
  jstr = groovy.json.JsonOutput.toJson(["name": arg])
  topic = "openrgb/" + settings?.topicSub + "/profile/set"
  interfaces.mqtt.publish(topic, jstr)
  
}
