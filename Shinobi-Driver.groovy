/**
 *  Copyright 2020 Brad Sileo
 *
 *  Shinobi NVR
 *
 *  Author: Brad Sileo
 *
 *
 *  version: 0.9.6
 */
metadata {
	definition (name: "Shinobi Monitor", namespace: "bsileo", author: "Brad Sileo")
    {
	    capability "Switch"
        capability "MotionSensor"
        capability "Refresh"
        capability "Configuration"
        attribute "mode", "string"
        attribute "status", "string"
        command"up"
        command "down"
        command "left"
        command "right"
        command "zoomIn"
        command "zoomOut"
        command "enableNV"
        command "disableNV"

        command "trigger", [
            [name: "region", type: "STRING", description: "The name of the region. Example : door"],
            [name: "reason", type: "STRING", description: "The reason for this trigger. Example : motion"],
            [name: "confidence", type: "NUMBER", description: "A number to signify how much confidence this engine has that there is motion. Example : 197.4755859375"],
            ]
        command "addRegionChild", [
            [name: "name*", type: "STRING", description: "Manually add a region child. Use this instead of the Automatic mode with Configure if needed. The name of the region is required and must match the name in Shinobi. Example : door"],
            ]
        command "start", [
            [name:"units*",
                     type: "ENUM",
                     description: "The units for the time or select No Timer to Watch until stopped",
                     constraints: [
                         "0": "no timer",
                         "min" : "minutes",
                         "hr": "hours",
                         "day": "days"
     	             ]
                ],
            [name:"value",
                     type: "NUMBER",
                     description: "The amount of time to stay in Watch mode before stopping",
                ]
            ]
        command "stop"
        command "record", [
                [name:"units*",
                     type: "ENUM",
                     description: "The units for the time or select No Timer to Record until stopped",
                     constraints: [
                         "0": "no timer",
                         "min" : "minutes",
                         "hr": "hours",
                         "day": "days"
     	             ]
                ],
            [name:"value",
                     type: "NUMBER",
                     description: "The amount of time to Record for",
                ]
        ]
	}

    preferences {
         section("General:") {
              input ( name: "motionTimeout",
        	    title: "<h3>Timeout for auto motion stop</h3>",
                description: "The amount of time, in seconds until motion is considered stopped. You can use this instead of the NoMotion detector in Shinobi",
        	    type: "number",
        	    defaultValue: "30"
                )
             input ( name: "enableMotionTimeout",
        	    title: "<h3>Enable automatic motion timeout</h3>",
                description: "Should we automatically inactivate motion on this device and all regions after Motion Timeout",
        	    type: "bool",
        	    defaultValue: true
                )
             input ( name: "enableAutoRegionCreation",
        	    title: "<h3>Enable automatic region creation</h3>",
                description: "If set to true, new regions will automatically be created from the Monitor data. After setting this to True and <strong>saving preferences</strong>, press Configure to update and process the new region children.",
        	    type: "bool",
        	    defaultValue: false
                )
             input ( name: "hasPTZ",
        	    title: "<h3>Does this camera have PTZ capabilities?</h3>",
        	    type: "bool",
        	    defaultValue: false
                )
            input (
        	name: "loggingLevel",
        	title: "<h3>IDE Live Logging Level</h3>",
            description: "Messages with this level and higher will be logged to the IDE. All others are ignored",
        	type: "enum",
        	options: [
        	    "None",
        	    "Error",
        	    "Warning",
        	    "Info",
        	    "Debug",
        	    "Trace"
        	],
        	defaultValue: "Info",
            displayDuringSetup: true,
        	required: false
            )
        }
    }

}

def installed() {
    state.loggingLevel = (settings.loggingLevel) ? settings.loggingLevel : 'Info'
    getHubPlatform()
    state.hasPTZ = settings.hasPTZ
}

def updated() {
    state.loggingLevel = (settings.loggingLevel) ? settings.loggingLevel : 'Info'
    state.hasPTZ = settings.hasPTZ
}

def configure() {
    sendMonitorCommand() { resp ->
        parseConfigure(resp.data)
    }
}

def parseConfigure(monitor) {
    logger("Doing Configure run with ${monitor}","trace")       
    logger("Doing Configure run with ${monitor.details}","trace")       
    monitor.details.eachWithIndex() { it,index ->
        logger("Details ${index}==${it}","trace")
        def details = new groovy.json.JsonSlurper().parseText(it)
        logger("Detail ${details}","trace")
        details.each { it2 ->     
            def key = it2.key
            if (key == "cords") { 
                def regions = new groovy.json.JsonSlurper().parseText(it2.value)
                logger("A Detail ${regions}","trace")                
                autoAddRegionChildren(regions)
            }
        }
    }  
}

def autoAddRegionChildren(regions) {  
  if (settings.enableAutoRegionCreation) {
      regions.each() {
          logger("Auto creating new child for region ${it.value.name}","debug")
          addRegionChild(it.value.name)
      }
  }   
}


def refresh() {
    sendMonitorCommand() { resp ->
        parseRefresh(resp.data)
    }
}

def parseRefresh(monitor) {
    logger("Parse Shinobi - ${monitor}","debug")
    sendEvent(name: "status", value: monitor.status)
    sendEvent(name: "mode", value: monitor.mode)
}




def triggerMotion(event) {
    logger("Trigger Motion","info")
    logger("Trigger Motion - ${event}","debug")
    def desc = "Motion detected"
    if (event.region) {
        def RC = regionChild(event.region)
        if (RC) {
            RC.sendEvent(name: "motion", value: "active", descriptionText: "Region motion detected in parent")
        } else {
            sendEvent([[name: "motionRegion", value: event.region]])
        }
        desc = desc + " in ${event.region}"
    }
    sendEvent(name: "motion", value: "active", descriptionText: desc)
    if (settings.enableMotionTimeout) {
        logger("Schedule no Motion for ${settings.motionTimeout} seconds","info")
        unschedule()
        runIn(settings.motionTimeout, noMotion)
    }
}

def triggerNoMotion(event) {
    logger("Trigger NO Motion - ${event}","debug")
    sendNoMotion()
}

def sendNoMotion() {
    sendEvent([[name: "motion", value: "inactive"]])
    def child = false
    getChildDevices().each {
        it.sendEvent(name: "motion", value: "inactive", descriptionText: "Region motion cleared in parent")
        child = true
    }
    if (!child) {
        sendEvent([[name: "motionRegion", value: ""]])
    }
}

def noMotion() {
    logger("Auto NO Motion","info")
    sendNoMotion()
}


def regionChild(region) {
    def kid = getChildDevice(regionChildName(region))
    return kid
}

def regionChildName(region) {
     return device.deviceNetworkId + "-" + region
}




def addRegionChild(name) {
    if (regionChild(name)) {
        logger("The region device called '${name}' already exists", "warn")
    } else {
        def d = addChildDevice("hubitat", "Generic Component Motion Sensor", regionChildName(name), [
            "label": "Shinobi Region ${getDataValue("name")}/${name}",
            "completedSetup" : true
		])
        logger("The region device called '${name}' was created", "info")
        return d
    }
}

def timeInterval(time, unit) {
    state.nextTime = time
    state.nextUnit = unit
}

def on() {
     record()
}

def off() {
     stop()
}

def record(units="no timer", time=null) {
    sendMonitorCommand("record",units, time) { resp ->
        logger("Record result->${resp.data}","info")
        refresh()
    }
}

def stop() {
    sendMonitorCommand("stop",null, null) { resp ->
        logger("Stop result->${resp.data}","info")
        refresh()
    }
}

def start(units="no timer", time=null) {
    sendMonitorCommand("start",units, time) { resp ->
        logger("Start result->${resp.data}","info")
        refresh()
    }
}

def center() {
     sendControlCommand("center")
}

def up() {
     sendControlCommand("up")
}

def down() {
     sendControlCommand("down")
}

def left() {
     sendControlCommand("left")
}

def right() {
     sendControlCommand("right") { resp ->
        logger("Right result->${resp.data}","info")
    }
}

def zoomIn() {
     sendControlCommand("zoom_in")
}

def zoomOut() {
     sendControlCommand("zoom_out")
}

def enableNV() {
     sendControlCommand("enable_nv")
}

def disableNV() {
     sendControlCommand("disable_nv")
}

def trigger(region="",reason="motion", confidence=197) {
     sendMotionCommand(device.deviceNetworkId,region,reason,confidence) { resp ->
        logger("Trigger result->${resp.data}","info")
    }
}

def sendMonitorCommand(command=null, units="no timer", time=null, closure) {
     sendCommand("monitor",command, units, time, null, closure)
}

def sendControlCommand(command, closure) {
     sendCommand("control", command, null, null, null, closure)
}

def sendMotionCommand(plug, region, reason, confidence, closure) {
    /* From https://shinobi.video/docs/api#content-trigger-a-motion-event
             plug : The name of the plugin. You can put the name of the camera.
             name : The name of the region. Example : door
             reason : The reason for this trigger. Example : motion
             confidence : A number to signify how much confidence this engine has that there is motion. Example : 197.4755859375
    */
    def query = "data={\"plug\":\"${plug}\",\"name\":\"${region}\",\"reason\":\"${reason}\",\"confidence\":\"${confidence}\"}"
    sendCommand("motion",null, null, null, query, closure)
}


private sendCommand(type, command=null, units="no timer", time=null, query=null, closure) {
    logger("Start SendCommand of ${type} with ${units} for ${time} - ${command}","debug")
    def controller = getParent().getControllerParams()
    def body = ""
    def path = "/${controller.APIKey}/${type}/${controller.groupKey}/${device.deviceNetworkId}"
    if (command) { path = path + "/$command" }
    if (units != null && units != "no timer") {
        path = path + "/${time}/${units}"
    }
    if (query) {
        path = path + "?${query}"
    }

    def params = [
        uri: controller.uri,
        path: path,
        requestContentType: "application/json",
        contentType: "application/json",
        body:body
    ]
    logger("Run command with ${params}","debug")
    logger("URL = ${params.uri}${params.path}","debug")
    httpGet(params) { resp ->
        logger("SMC result->${resp.data}","trace")
        closure(resp)
    }
}

// INTERNAL Methods

//*******************************************************
//*  logger()
//*
//*  Wrapper function for all logging.
//*******************************************************

private logger(msg, level = "debug") {

    def lookup = [
        	    "None" : 0,
        	    "Error" : 1,
        	    "Warning" : 2,
        	    "Info" : 3,
        	    "Debug" : 4,
        	    "Trace" : 5]
      def logLevel = lookup[state.loggingLevel ? state.loggingLevel : 'Debug']
     // log.debug("Lookup is now ${logLevel} for ${state.loggingLevel}")

    switch(level) {
        case "error":
            if (logLevel >= 1) log.error msg
            break

        case "warn":
            if (logLevel >= 2) log.warn msg
            break

        case "info":
            if (logLevel >= 3) log.info msg
            break

        case "debug":
            if (logLevel >= 4) log.debug msg
            break

        case "trace":
            if (logLevel >= 5) log.trace msg
            break

        default:
            log.debug msg
            break
    }
}

// **************************************************************************************************************************
// SmartThings/Hubitat Portability Library (SHPL)
// Copyright (c) 2019, Barry A. Burke (storageanarchy@gmail.com)
//
// The following 3 calls are safe to use anywhere within a Device Handler or Application
//  - these can be called (e.g., if (getPlatform() == 'SmartThings'), or referenced (i.e., if (platform == 'Hubitat') )
//  - performance of the non-native platform is horrendous, so it is best to use these only in the metadata{} section of a
//    Device Handler or Application
//
private String  getPlatform() { (physicalgraph?.device?.HubAction ? 'SmartThings' : 'Hubitat') }	// if (platform == 'SmartThings') ...
private Boolean getIsST()     { (physicalgraph?.device?.HubAction ? true : false) }					// if (isST) ...
private Boolean getIsHE()     { (hubitat?.device?.HubAction ? true : false) }						// if (isHE) ...
//
// The following 3 calls are ONLY for use within the Device Handler or Application runtime
//  - they will throw an error at compile time if used within metadata, usually complaining that "state" is not defined
//  - getHubPlatform() ***MUST*** be called from the installed() method, then use "state.hubPlatform" elsewhere
//  - "if (state.isST)" is more efficient than "if (isSTHub)"
//
private String getHubPlatform() {
    if (state?.hubPlatform == null) {
        state.hubPlatform = getPlatform()						// if (hubPlatform == 'Hubitat') ... or if (state.hubPlatform == 'SmartThings')...
        state.isST = state.hubPlatform.startsWith('S')			// if (state.isST) ...
        state.isHE = state.hubPlatform.startsWith('H')			// if (state.isHE) ...
    }
    return state.hubPlatform
}
private Boolean getIsSTHub() { (state.isST) }					// if (isSTHub) ...
private Boolean getIsHEHub() { (state.isHE) }
