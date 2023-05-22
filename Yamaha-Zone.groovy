/**
 *  Hubitat Driver: Yamaha Zone
 *
 *  Author: Ben Rimmasch
 *  Derived from redloro@gmail.com's ST work for Yamaha Receivers
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
  definition(name: "Yamaha Zone", namespace: "codahq-hubitat", author: "Ben Rimmasch") {

    /**
     * List our capabilties. Doing so adds predefined command(s) which
     * belong to the capability.
     */
    //capability "Music Player"
    capability "Switch"
    capability "Switch Level"
    capability "Refresh"
    capability "Polling"
    capability "Sensor"
    capability "Actuator"
    capability "AudioVolume"

    /**
     * Define all commands, ie, if you have a custom action not
     * covered by a capability, you NEED to define it here or
     * the call will not be made.
     *
     * To call a capability function, just prefix it with the name
     * of the capability, for example, refresh would be "refresh.refresh"
     */
    command "source0"
    command "source1"
    command "source2"
    command "source3"
    command "source4"
    command "source5"
    command "partyModeOn"
    command "partyModeOff"
    command "setDb", [[name: "Set Volume", type: "NUMBER", range: -80..15, description: "Enter dB (default range -80 to 15)"]]
    command "systemConfigReport"

    attribute "dB", "number"
    attribute "volume", "number"
    attribute "source", "string"
    attribute "partyMode", "string"
  }

  /**
   * Define the various tiles and the states that they can be in.
   * The 2nd parameter defines an event which the tile listens to,
   * if received, it tries to map it to a state.
   *
   * You can also use ${currentValue} for the value of the event
   * or ${name} for the name of the event. Just make SURE to use
   * single quotes, otherwise it will only be interpreted at time of
   * launch, instead of every time the event triggers.
   */
  tiles(scale: 2) {
    multiAttributeTile(name: "state", type: "lighting", width: 6, height: 4) {
      tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
        attributeState "on", label: 'On', action: "switch.off", icon: "st.Electronics.electronics16", backgroundColor: "#79b821", nextState: "off"
        attributeState "off", label: 'Off', action: "switch.on", icon: "st.Electronics.electronics16", backgroundColor: "#ffffff", nextState: "on"
      }
      tileAttribute("source", key: "SECONDARY_CONTROL") {
        attributeState "source", label: '${currentValue}'
      }
    }

    // row
    //controlTile("volume", "device.volume", "slider", height: 1, width: 6, range:"(0..100)") {
    controlTile("volume", "device.volume", "slider", height: 1, width: 6, range: "(-80..16)") {
      state "volume", label: "Volume", action: "music Player.setLevel", backgroundColor: "#00a0dc"
    }

    // row
    standardTile("0", "device.source0", decoration: "flat", width: 2, height: 2) {
      state("off", label: "AV1", action: "source0", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-gray.png", backgroundColor: "#ffffff")
      state("on", label: "AV1", action: "source0", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-green.png", backgroundColor: "#ffffff")
    }
    standardTile("1", "device.source1", decoration: "flat", width: 2, height: 2) {
      state("off", label: "AV2", action: "source1", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-gray.png", backgroundColor: "#ffffff")
      state("on", label: "AV2", action: "source1", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-green.png", backgroundColor: "#ffffff")
    }
    standardTile("2", "device.source2", decoration: "flat", width: 2, height: 2) {
      state("off", label: "AV3", action: "source2", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-gray.png", backgroundColor: "#ffffff")
      state("on", label: "AV3", action: "source2", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-green.png", backgroundColor: "#ffffff")
    }
    standardTile("3", "device.source3", decoration: "flat", width: 2, height: 2) {
      state("off", label: "AV4", action: "source3", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-gray.png", backgroundColor: "#ffffff")
      state("on", label: "AV4", action: "source3", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-green.png", backgroundColor: "#ffffff")
    }
    standardTile("4", "device.source4", decoration: "flat", width: 2, height: 2) {
      state("off", label: "AV5", action: "source4", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-gray.png", backgroundColor: "#ffffff")
      state("on", label: "AV5", action: "source4", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-green.png", backgroundColor: "#ffffff")
    }
    standardTile("5", "device.source5", decoration: "flat", width: 2, height: 2) {
      state("off", label: "AV6", action: "source5", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-gray.png", backgroundColor: "#ffffff")
      state("on", label: "AV6", action: "source5", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-green.png", backgroundColor: "#ffffff")
    }

    // row
    standardTile("muted", "device.muted", decoration: "flat", width: 2, height: 2) {
      state("off", label: 'Muted', action: "mutedOn", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-gray.png", backgroundColor: "#ffffff", nextState: "on")
      state("on", label: 'Muted', action: "mutedOff", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-mute.png", backgroundColor: "#ffffff", nextState: "off")
    }
    standardTile("partyMode", "device.partyMode", decoration: "flat", width: 2, height: 2, inactiveLabel: false) {
      state("off", label: 'Party Mode', action: "partyModeOn", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-gray.png", backgroundColor: "#ffffff", nextState: "on")
      state("on", label: 'Party Mode', action: "partyModeOff", icon: "https://raw.githubusercontent.com/redloro/smartthings/master/images/indicator-dot-party.png", backgroundColor: "#ffffff", nextState: "off")
    }
    standardTile("refresh", "device.status", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
      state "default", label: "Refresh", action: "refresh.refresh", icon: "st.secondary.refresh-icon", backgroundColor: "#ffffff"
    }

    // Defines which tile to show in the overview
    main "state"

    // Defines which tile(s) to show when user opens the detailed view
    details([
      "state",
      "volume",
      "0", "1", "2", "3", "4", "5",
      "muted", "partyMode", "refresh"
    ])
  }

  preferences {
    input name: "source0", type: "text", title: "Source 1", defaultValue: "AV1"
    input name: "source1", type: "text", title: "Source 2", defaultValue: "AV2"
    input name: "source2", type: "text", title: "Source 3", defaultValue: "AV3"
    input name: "source3", type: "text", title: "Source 4", defaultValue: "AV4"
    input name: "source4", type: "text", title: "Source 5", defaultValue: "AV5"
    input name: "source5", type: "text", title: "Source 6", defaultValue: "AV6"
    input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
    input name: "maxVolume", type: "number", range: -80..15, title: "Max Volume", description: "Enter the maximum volume in reference decibals that the receiver is allowed", defaultValue: 0
    input name: "minVolume", type: "number", range: -80..15, title: "Min Volume", description: "Enter the minimum volume in reference decibals that the receiver is allowed", defaultValue: -80
    input name: "volumeStep", type: "decimal", range: 0.5..10, title: "Volume Step", description: "Enter the amount the volume up and down commands should adjust the volume", defaultValue: 2.5
  }
}

private logInfo(msg) {
  if (descriptionTextEnable) log.info msg
}

def logDebug(msg) {
  if (logEnable) log.debug msg
}

def logTrace(msg) {
  if (traceLogEnable) log.trace msg
}

def getDEFAULT_MAX_VOL() {
  return 0
}
def getDEFAULT_MIN_VOL() {
  return -80
}

def updated() {
  if (maxVolume == null || maxVolume > 15) {
    log.warn "Max volume out of bounds!  Setting to ${DEFAULT_MAX_VOL}"
    device.updateSetting("maxVolume", DEFAULT_MAX_VOL)
  }
  if (minVolume == null || minVolume < DEFAULT_MIN_VOL) {
    log.warn "Min volume out of bounds!  Setting to ${DEFAULT_MIN_VOL}"
    device.updateSetting("maxVolume", DEFAULT_MIN_VOL)
  }
  def newStep = roundNearestHalf(volumeStep)
  newStep = newStep > 0 && newstep <= 10 ? newStep : 2.5
  if (newStep != volumeStep) {
    log.warn "Volume Step is changing!  Setting to ${newStep}"
    device.updateSetting("volumeStep", newStep)
  }
}

def on() {
  logDebug("on()")
  logInfo("Zone ${getZone()} on")
  sendCommand("<YAMAHA_AV cmd=\"PUT\"><${getZone()}><Power_Control><Power>On</Power></Power_Control></${getZone()}></YAMAHA_AV>")
  sendEvent(name: "switch", value: "on")
}

def off() {
  logDebug("off()")
  logInfo("Zone ${getZone()} off")
  sendCommand("<YAMAHA_AV cmd=\"PUT\"><${getZone()}><Power_Control><Power>Standby</Power></Power_Control></${getZone()}></YAMAHA_AV>")
  sendEvent(name: "switch", value: "off")
}

def setLevel(value) {
  logDebug("setLevel(${value})")
  if (value > 100) value = 100
  if (value < 0) value = 0
  logInfo("Zone ${getZone()} volume set to ${value}")
  def db = calcRelativeValue(value)
  sendVolume(db)
  sendEvent(name: "volume", value: value)
  sendEvent(name: "level", value: value)
}

def setVolume(value) {
  logDebug("setVolume(${value})")
  setLevel(value)
}

def setDb(value) {
  logDebug("setDb(${value})")
  def double dB = value
  if (dB > maxVolume) dB = maxVolume
  if (dB < minVolume) dB = minVolume
  logInfo("Zone ${getZone()} volume set to ${value}")
  sendVolume(value)
  sendEvent(name: "volume", value: calcRelativePercent(value).intValue())
  sendEvent(name: "level", value: calcRelativePercent(value).intValue())
}

def volumeUp() {
  setDb(device.currentValue("dB") + volumeStep)
}

def volumeDown() {
  setDb(device.currentValue("dB") - volumeStep)
}

def sendVolume(double db) {
  logTrace "sendVolume(${db})"
  db = roundNearestHalf(db)
  def strCmd = "<YAMAHA_AV cmd=\"PUT\"><${getZone()}><Volume><Lvl><Val>${(db * 10).intValue()}</Val><Exp>1</Exp><Unit>dB</Unit></Lvl></Volume></${getZone()}></YAMAHA_AV>"
  logTrace groovy.xml.XmlUtil.escapeXml("strCmd ${strCmd}")
  sendCommand(strCmd)
  sendEvent(name: "dB", value: db)
}

def mute() {
  sendCommand("<YAMAHA_AV cmd=\"PUT\"><${getZone()}><Volume><Mute>On</Mute></Volume></${getZone()}></YAMAHA_AV>")
  sendEvent(name: "mute", value: "muted")
}
def unmute() {
  sendCommand("<YAMAHA_AV cmd=\"PUT\"><${getZone()}><Volume><Mute>Off</Mute></Volume></${getZone()}></YAMAHA_AV>")
  sendEvent(name: "mute", value: "unmuted")
}

def systemConfigReport() {
  sendCommand("<YAMAHA_AV cmd=\"GET\"><System><Config>GetParam</Config></System></YAMAHA_AV>")
}

def source0() {
  setSource(0)
}
def source1() {
  setSource(1)
}
def source2() {
  setSource(2)
}
def source3() {
  setSource(3)
}
def source4() {
  setSource(4)
}
def source5() {
  setSource(5)
}

def partyModeOn() {
  sendCommand("<YAMAHA_AV cmd=\"PUT\"><System><Party_Mode><Mode>On</Mode></Party_Mode></System></YAMAHA_AV>")
  sendEvent(name: "partyMode", value: "on")
}
def partyModeOff() {
  sendCommand("<YAMAHA_AV cmd=\"PUT\"><System><Party_Mode><Mode>Off</Mode></Party_Mode></System></YAMAHA_AV>")
  sendEvent(name: "partyMode", value: "off")
}
def refresh() {
  sendCommand("<YAMAHA_AV cmd=\"GET\"><${getZone()}><Basic_Status>GetParam</Basic_Status></${getZone()}></YAMAHA_AV>")
  sendCommand("<YAMAHA_AV cmd=\"GET\"><System><Party_Mode><Mode>GetParam</Mode></Party_Mode></System></YAMAHA_AV>")
}
/**************************************************************************/

/**
 * Called every so often (every 5 minutes actually) to refresh the
 * tiles so the user gets the correct information.
 */
def poll() {
  refresh()
}

def parse(String description) {
  return
}

def setSource(id) {
  logDebug "setSource(${id})"
  logInfo "Setting source to " + getSourceName(id)
  sendCommand("<YAMAHA_AV cmd=\"PUT\"><${getZone()}><Input><Input_Sel>" + getSourceName(id) + "</Input_Sel></Input></${getZone()}></YAMAHA_AV>")
  setSourceTile(getSourceName(id))
}

def getSourceName(id) {
  if (settings) {
    return settings."source${id}"
  }
  else {
    return ['AV1', 'AV2', 'AV3', 'AV4', 'AV5', 'AV6'].get(id)
  }
}

def setSourceTile(name) {
  sendEvent(name: "source", value: "Source: ${name}")
  for (def i = 0; i < 6; i++) {
    if (name == getSourceName(i)) {
      sendEvent(name: "source${i}", value: "on")
    }
    else {
      sendEvent(name: "source${i}", value: "off")
    }
  }
}

def zone(evt) {
  if (evt == null) {
    log.warn "Calling from UI not implemented!"
  }
  //This is even too much logging for trace.  Let's leave it commented for now.
  //logTrace "zone(evt) with evt: ${groovy.xml.XmlUtil.escapeXml(groovy.xml.XmlUtil.serialize(evt))}"

  /*
  * Zone On/Off
  */
  if (evt.Basic_Status.Power_Control.Power.text()) {
    sendEvent(name: "switch", value: (evt.Basic_Status.Power_Control.Power.text() == "On") ? "on" : "off")
  }

  /*
  * Zone Volume
  */
  if (evt.Basic_Status.Volume.Lvl.Val.text()) {
    def int volLevel = evt.Basic_Status.Volume.Lvl.Val.toInteger() ?: -250
    def double dB = volLevel / 10.0
    sendEvent(name: "volume", value: calcRelativePercent(dB).intValue())
    sendEvent(name: "level", value: calcRelativePercent(dB).intValue())
    sendEvent(name: "dB", value: dB)

  }

  /*
  * Zone Muted
  */
  if (evt.Basic_Status.Volume.Mute.text()) {
    sendEvent(name: "mute", value: (evt.Basic_Status.Volume.Mute.text() == "On") ? "muted" : "unmuted")
  }

  /*
  * Zone Source
  */
  if (evt.Basic_Status.Input.Input_Sel.text()) {
    setSourceTile(evt.Basic_Status.Input.Input_Sel.text())
  }

  /*
  * Party Mode
  */
  if (evt.Party_Mode.Mode.text()) {
    sendEvent(name: "partyMode", value: (evt.Party_Mode.Mode.text() == "On") ? "on" : "off")
  }

  if (evt.Config.Name.Input.text()) {
    def supportedSources = evt.Config.Name.Input.children().findAll {
      node ->
        node.text() ?.trim()
    }
    logTrace "supportedSources: ${supportedSources}"
    state.supportedSources = supportedSources.join(", ")
  }

  if (evt.Config.Feature_Existence.text()) {
    def supportedFeatures = evt.Config.Feature_Existence.children().inject([]) {
      acc, node ->   
      if (node ?.text() ?.trim() == "1") {
        acc << node.name()
      }
      acc
    }
    logTrace "supportedFeatures: ${supportedFeatures}"
    state.supportedFeatures = supportedFeatures.join(", ")
  }
}

private sendCommand(body) {
  parent.sendCommand(body)
}

private getZone() {
  return new String(device.deviceNetworkId).tokenize('|')[2]
}

private calcRelativePercent(db) {
  logTrace "calcRelativePercent(${db})"
  def range = maxVolume - minVolume
  def correctedStartValue = db - minVolume
  def percentage = (correctedStartValue * 100) / range
  logTrace "percentage: ${percentage}"
  return percentage
}

private calcRelativeValue(perc) {
  logTrace "calcRelativeValue(${perc})"
  def value = (perc * (maxVolume - minVolume) / 100) + minVolume
  logTrace "value: ${value}"
  return value
}

private roundNearestHalf(value) {
  logTrace "roundNearestHalf(${value})"
  logTrace "result: ${Math.round(value * 2) / 2.0}"
  return Math.round(value * 2) / 2.0
}
