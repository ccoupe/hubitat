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

definition(
	name: "Shinobi",
	namespace: "bsileo",
	author: "Brad Sileo",
	description: "Connect your Shinobi NVR to Hubitat",
	iconUrl:   "",
	iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Main")
    page(name: "installPage", title: "Install")
    page(name: "selectMonitors", title: "Select Monitors")
}

def mainPage() {
    if(!state.accessToken) createAccessToken()
    def install = state.monitors != null
    return dynamicPage(name: "home", title: "<h1>Shinobi NVR Integration</h1>", refreshInterval: 0, install: install, uninstall: true) {
        if (state.monitors) {
            section("<h2>Current Installation</h2>") {
                def url = getFullLocalApiServerUrl() + "/motion" + "?access_token=" + state.accessToken + "&amp;mid={{MONITOR_ID}}&amp;region={{REGION_NAME}}&amp;confidence={{CONFIDENCE}}&amp;name={{MONITOR_NAME}}&amp;reason={{REASON}}"
                def urlNo = getFullLocalApiServerUrl() + "/nomotion" + "?access_token=" + state.accessToken+ "&amp;mid={{MONITOR_ID}}"
                paragraph "Use this local URL in the Shinobi Global Detector Settings Webhook:\n\n   <a target=\"_frame\" href=\"${url}"  + "\">${url}</a>"
                paragraph "Use this local URL in the Shinobi No Motion Webhook:\n\n     <a target=\"_frame\" href=\"${urlNo}"  + "\">${urlNo}</a>"
                paragraph "Both WebHooks should be configured to use a PUT request type."

            }
        }
        section("<h2>Install / Setup</h2>") {
            href(name: "installPage", title: "", description: "Tap to setup the Shinobi Server and manage connected devices", required: false, page: "installPage")
        }
        section("<h2>Logging</h2>") {
             input (
                 name: "loggingLevel",
                 title: "Live Logging Level:\nMessages with this level and higher will be logged to the IDE.",
                 type: "enum",
                 options: [
                     "None",
                     "Error",
                     "Warning",
                     "Info",
                     "Debug",
                     "Trace"
                 ],
                 required: false
             )
        }
    }
}


def installPage() {
    return dynamicPage(name: "home", title: "Shinobi NVR Interface", refreshInterval: 0, install: false, uninstall: true, nextPage: selectMonitors) {
        section("Setup NVR Connection") {
            input(name:"controllerIP", type: "text", title: "Shinobi IP Address", required: true, displayDuringSetup: true, defaultValue:"192.168.1.100")
            input(name:"controllerPort", type: "text", title: "Shinobi Port", required: true, displayDuringSetup: true, defaultValue:"8080")
            input(name:"APIKey", type: "text", title: "Shinobi API Key", required: true, displayDuringSetup: true, defaultValue:"")
            input(name:"GroupKey", type: "text", title: "Shinobi Group Key", required: true, displayDuringSetup: true, defaultValue:"")
        }
    }
}


def selectMonitors() {
    unsubscribe()
    atomicState.subscribed = false
    def options = [:]
	def devices = getMonitors()
	devices.each {
        def value = "${it.value.name}"
		options["${it.key}"] = value
	}
    return dynamicPage(name: "selectMonitors", title: "Select the Monitors to Install", refreshInterval: 0, install: true, uninstall: true) {
        section("Monitors", hideable:false, hidden:false) {
            input(name: "selectedMonitors", title:"Select Monitors", type: "enum", required:true, multiple:true, description: "Tap to choose", params: params,
            	  options: options, submitOnChange: true, width: 6)
		}
     }
}

def getMonitors() {
    atomicState.monitors = null
    def body = ""
    def params = [
        uri: "http://${settings.controllerIP}:${controllerPort}",
        path: "/${settings.APIKey}/monitor/${settings.GroupKey}",
        requestContentType: "application/json",
        contentType: "application/json",
        body:body
    ]
    res = [:]
    logger("getMonitors with ${params}","debug")
    httpGet(params) { resp ->
        logger("getMonitor/resp: ${resp.data}","debug")
        //    getMonitor/resp: [ok:false, msg:Not Authorized]
        resp.data.each {
             res[it.mid] = it
        }
        atomicState.monitors = res
    }
    return res
}

def getControllerParams() {
    return [
        uri: "http://${settings.controllerIP}:${controllerPort}",
        APIKey: settings.APIKey,
        groupKey: settings.GroupKey
        ]
}

def installed() {
    state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE : 'Debug'
    getHubPlatform()
    updateChildren()
}

def updated () {
    state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE : 'Debug'
    updateChildren()
}

def uninstalled() {
     removeChildren()
}

def removeChildren() {
    getChildDevices().each {
        deleteChildDevice(it.deviceNetworkID)
    }
}


def refresh() {
    def devices = getMonitors()
	devices.each {
        def child = getChild(it.key)
        child?.parseRefresh(it.value)
	}
}

mappings {
  path("/motion") {
    action: [
      PUT: "motionHandler"
    ]
  }
  path("/nomotion") {
    action: [
      PUT: "noMotionHandler"
    ]
  }
}

def motionHandler(request) {
    def monitorID = params.mid
    def child = getChild(monitorID)
    child?.triggerMotion(params)
}

def noMotionHandler(request) {
    def monitorID = params.mid
    def child = getChild(monitorID)
    child?.triggerNoMotion(params)
}


def getChild(monitorID) {
    def childDNI = monitorID
	return getChildDevice(childDNI)
}

def updateChildren() {
    settings.selectedMonitors.each {
        def child = getChild(it)
        if (child) {
            logger("Update Child ${it}","debug")
        }
        else {
            def monitor = atomicState.monitors[it]
            logger("Create new Monitor called ${monitor.mid}","info")
            createMonitor(monitor)
        }
    }
    getChildDevices().each {
        def selected = false
        settings.selectedMonitors.each { sMon ->
            if (sMon == it.deviceNetworkId) {
                selected = true
            }
        }
        if (! selected) {
            logger("Remove unselected monitor with ID ${it.deviceNetworkId}","warn")
            deleteChildDevice(it.deviceNetworkId)
        }
    }
}

def createMonitor(monitor) {
    logger("Create Monitor for ${monitor}","trace")
    def hub = location.hubs[0]
    d = addChildDevice("bsileo", "Shinobi Monitor", monitor.mid, hub.id, [
        "label": "Shinobi Monitor ${monitor.name}",
        "completedSetup" : true,
		"data": [
            name: monitor.name
				]
		])
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
      def logLevel = lookup[settings.loggingLevel ? settings.loggingLevel : 'Debug']     

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
