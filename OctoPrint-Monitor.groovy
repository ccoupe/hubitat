/**
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
    name: "OctoPrint Monitors",
    namespace: "ccoupe",
    author: "Cecil Coupe",
    description: "Setup OctoPrint Notification Routes",
    category: "Convenience",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")


preferences {
     page name: "mainPage", title: "", install: true, uninstall: true
}


def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}


def updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}


def initialize() {
    log.info "There are ${childApps.size()} child apps"
    childApps.each { child ->
    	log.info "Child app: ${child.label}"
    }
}


def installCheck() {         
	state.appInstalled = app.getInstallationState()
	
	if (state.appInstalled != 'COMPLETE') {
		section{paragraph "Please hit 'Done' to install '${app.label}' parent app "}
  	}
  	else {
    	log.info "Parent Installed OK"
  	}
}


def getFormat(type, myText=""){
	if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}


def display(){
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#1A77C9;text-align:center'>OctoPrint Monitors - @ccoupe<br><a href='https://github.com/ccoupe/' target='_blank'>Click here for more Hubitat apps/drivers on my GitHub!</a></div>"
	}       
}


def mainPage() {
    dynamicPage(name: "mainPage") {
    	installCheck()
		
		if (state.appInstalled == 'COMPLETE') {
			section(getFormat("title", "${app.label}")) {
				paragraph "*** Always a BETA ***"
			}
  			section("<b>Create a OctoPrint Notifcations Child App:</b>") {
          app(name: "anyOpenApp", appName: "OctoPrint Notifications", namespace: "ccoupe",
          title: "<b>Create a new OctoPrint Notification</b>", multiple: true)
			}
      section("<b>Create a Bambu Notifcations Child App:</b>") {
          app(name: "anyOpenApp", appName: "Bambu Notifications", namespace: "ccoupe",
          title: "<b>Create a new Bambu Notification</b>", multiple: true)
			}
			display()
		}
	}
}



