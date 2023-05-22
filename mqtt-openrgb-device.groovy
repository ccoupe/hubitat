/*
 *  openrgb child devices don't do very much. 
 *  You can setColor() from rule machine (or similar)
 *  You can setColor() from the Device page
 *  You can turn if off. Turning on restores the 
 *  last saved (on) value that isn't 0,0,0
 * 
 *  We know the off color is 0,0,0 - so on() can't VISUALLY restore
 *  that, so saving off makes no sense where as not changing does make 
 *  slightly more sense.
 * 
 *  There are no hubitat events to create or respond to.
 * 
 *  We can send json string back to the parent device handler
 *  to send the payload to the correct mqtt topic.
 * 
 *  An awful lot the code was borrowed from 'tomw'. Thanks.
 *  Even more was deleted.
 */

metadata
{
    definition(name: "Mqtt OpenRGB Device", namespace: "ccoupe", author: "Cecil Coupe", importUrl: "https://raw.githubusercontent.com/ccoupe/hubitat/master/mqtt-openrgb-device.groovy")
    {
        capability "Bulb"
        capability "ColorControl"
        capability "ColorMode"
        capability "Initialize"
        capability "Refresh"
        

        
        // This parameter definition isn't correct - no errors in log
        command "modes",[[type: "ENUM", name: "Mode", description: "Pick a Mode",
                          constraints: ["Direct", "Off", "Static", "Breathing", "Flashing",
                          "Spectrum Cycle", "Rainbow", "Chase Fade", "Chase"]]]
    }
}

def initialize()
{    
    refresh()
}

def refresh()
{
    parent?.refreshFromParent(device)
}

def initCommands(modeList, zoneList)
{
    log.info("initCommands: ${modeList}\n\t\t${zoneList}")
    state.modes = modeList
    state.zones = zoneLst
}

def modes(sel)
{
  // The mode in arg 'sel' has to be one of those in state.modes
  if (sel in state.modes) {
    jstr = groovy.json.JsonOutput.toJson(["mode":sel])
    topic = getDataValue("Id")
    parent = getParent()
    parent.childSend(topic, jstr)
    log.info("modes: publish : ${topic} => ${jstr}")
  }
}

def setColor(colormap)
{
    def cRGB = hubitat.helper.ColorUtils.hsvToRGB([colormap.hue, colormap.saturation, colormap.level])
    def jstr = groovy.json.JsonOutput.toJson(["color": [r: cRGB[0].toInteger(), 
              g: cRGB[1].toInteger(),
              b: cRGB[2].toInteger()]])
    topic = getDataValue("Id")
    //log.info ("send: ${topic} => ${jstr}")
    parent = getParent()
    parent.childSend(topic, jstr)
    state.childColor = jstr
}

def on()
{
  topic =  getDataValue("Id")
  jstr = state.childColor
  if (!jstr) {
    // we can't do on() unless we have a saved value. Make one.
    log.info("on with low levels")
    jstr = groovy.json.JsonOutput.toJson( [r: 30.toInteger(), 
                g: 30.toInteger(),
                b: 30.toInteger()] )
    state.childColor = jstr
  }
  parent = getParent()
  parent.childSend(topic, jstr)
  // now change the gui to match the color in state. 
  def jsonSlurper = new groovy.json.JsonSlurper()
  def mapDev = jsonSlurper.parseText(state.childColor)
  def cHSV = hubitat.helper.ColorUtils.rgbToHSV([mapDev["r"].toInteger(), 
                mapDev["g"].toInteger(), mapDev["b"].toInteger()])
  // following line does not change the GUI - even with a browser reload
  setColor(['hue': cHSV[0], 'saturation': cHSV[1], 'level': cHSV[2]])

}

def off()
{
  topic =  getDataValue("Id")
  def jstr = groovy.json.JsonOutput.toJson( "state": "off")
  parent.childSend(topic, jstr)
}


