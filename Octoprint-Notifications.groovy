/*
 *  Octoprint Monitors
 *    Cecil Coupe shamelessly borrowed code from 
 *      Simple State Machines - Copyright 2019 Joel Wetzel
 *      Notification Proxy App - Copyright 2021 Robert Morris

 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 

definition(
  parent: 	"ccoupe:OctoPrint Monitors",
   name: "OctoPrint Notifications",
   namespace: "ccoupe",
   author: "Cecil Coupe",
   description: 'Select one "monitor" device to route its notifications to any number of notification devices',
   iconUrl: "",
   iconX2Url: ""
)

preferences {
	page(name: "mainPage")
}
/*
preferences {
   page(name: "mainPage", install: true, uninstall: true) {  
      section("Choose devices") {
         input "proxyDevice", "device.MqttOctoprintMonitor", 
            title: "Mqtt Octoprint Monitor"		
         input "notificationDevice", "capability.notification",
            title: "Notification Devices", 
            multiple: true
         paragraph "When a notification device is sent to the proxy notification device, it will send the notification to all of the notification devices selected."
         input "debugMode", "bool",
            title: "Enable debug logging"
      }
   }
}
*/

void installed() {
   log.debug "installed()"
   initialize()
}

void updated() {
   log.debug "updated()"
   unsubscribe()
   initialize()
}

void initialize() {
   log.debug "initialize()"	
   subscribe(proxyDevice, "deviceNotification", notificationHandler)
}

void notificationHandler(evt) {
   logDebug "Sending ${evt.value} to ${notificationDevice}"
   String text = "${evt.value}"
   try {
      notificationDevice.deviceNotification(text)
   }
   catch (ex) {
      log.error("Error sending notification to devices: ${ex}")
   }
}

void logDebug(str) {
   if (settings.debugMode != false) log.debug(str)
}

def mainPage() {
	dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        if (!app.label) {
			app.updateLabel(app.name)
		}
		section(getFormat("title", (app?.label ?: app?.name).toString())) {
			input(name:	"oPChildName", type: "string", title: "OctoPrint Notifications Child", multiple: false, required: true, submitOnChange: true)
            
			if (settings.oPChildName) {
				app.updateLabel(settings.oPChildName)
			}
		}
    section () {
       input "proxyDevice", "device.MqttOctoprintMonitor", 
          title: "Mqtt Octoprint Monitor (source) "		
       input "notificationDevice", "capability.notification",
          title: "Notification Devices (destinations)", 
          options: (filterNoteDevices(settings.proxyDevice)),
          multiple: true
       paragraph "A notification from source device is sent to the destination notification device(s)."
    }
		section () {
			input(name:	"enableLogging", type: "bool", title: "Enable Debug Logging?", defaultValue: true,	required: true)
		}
	}
}

def filterNoteDevices(exclude) {
  log.info("filterNoteDevices: called")
}

def getFormat(type, myText="") {
	if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}
