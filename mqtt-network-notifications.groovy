/*
 *  Mqtt Network Notifications
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
  parent: 	"ccoupe:MQTT Network Monitors",
   name: "MQTT Network Notifications",
   namespace: "ccoupe",
   author: "Cecil Coupe",
   description: 'Select one "monitor" device to route its notifications to any number of notification devices',
   iconUrl: "",
   iconX2Url: ""
)

preferences {
	page(name: "mainPage")
}


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

/*
def join2(beg, lst) {
  len = lst.size()
  String out = ""
  for (int i = 1; i < len; i++) {
    out = out + lst[i]
    if (i < len-1) out = out + " "
  }
  return out
}
*/

void notificationHandler(evt) {
  String ent = "${evt.value}"
  def flds = []
  flds = ent.split(" ")
  node = flds[0]
  //msg = join2(1, flds)
  try {
    if (flds[1] == 'is' && flds[2] == 'OK') {
      if (settings?.passOK) {
        logDebug "Sending ${evt.value} to ${notificationDevice}"
        notificationDevice.deviceNotification(ent)
      }
    } else {
      logDebug "Sending ${evt.value} to ${notificationDevice}"
      notificationDevice.deviceNotification(ent)
    }
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
			input(name:	"oPChildName", type: "string", title: "MQTT Network Notifications Child", multiple: false, required: true, submitOnChange: true)
            
			if (settings.oPChildName) {
				app.updateLabel(settings.oPChildName)
			}
		}
    section () {
       input "proxyDevice", "device.MqttNetworkMonitor", 
          title: "Mqtt Network Monitor (device) "		
       input "notificationDevice", "capability.notification",
          title: "Notification Devices (destinations)", 
          options: (filterNoteDevices(settings.proxyDevice)),
          multiple: true
       paragraph "A notification from source device is sent to the destination notification device(s)."
    }
		section () {
      input(name: "passOK", type: "bool", title: "Send OK messages?", 
            defaultValue: false,
            required: true)
			input(name:	"enableLogging", type: "bool", title: "Enable Debug Logging?",
            defaultValue: true,
            required: true)
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
