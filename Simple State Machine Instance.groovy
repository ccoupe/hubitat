/**
 *  Simple State Machine Instance
 *
 *  Copyright 2019 Joel Wetzel
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
 */

import groovy.json.*
	
definition(
	parent: 	"joelwetzel:Simple State Machines",
    name: 		"Simple State Machine Instance",
    namespace: 	"joelwetzel",
    author: 	"Joel Wetzel",
    description: "Child app that is instantiated by the Simple State Machines app.",
    category: 	"Convenience",
	iconUrl: 	"",
    iconX2Url: 	"",
    iconX3Url: 	"")

preferences {
	page(name: "mainPage")
}


def installed() {
	log.info "Installed with settings: ${settings}"

	initialize()
}


def initialize() {
    atomicState.internalUiState = "default"
    
    atomicState.transitionNames = []
}


def updated() {
	log.info "Updated with settings: ${settings}"

    bindEvents()
	
    atomicState.internalUiState = "default"
}


def bindEvents() {
    // Subscribe to events.  (These are our state machine events, NOT groovy events.)
	unsubscribe()
    def childEvents = enumerateEvents()
    childEvents.each {
        subscribe(it, "pushed", eventHandler)
    }
}


def mainPage() {
	dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        if (!app.label) {
			app.updateLabel(app.name)
		}
		section(getFormat("title", (app?.label ?: app?.name).toString())) {
			input(name:	"stateMachineName", type: "string", title: "State Machine Name", multiple: false, required: true, submitOnChange: true)
            
			if (settings.stateMachineName) {
				app.updateLabel(settings.stateMachineName)
			}
		}
        
    if (app.getInstallationState() == "COMPLETE") {
      section("<b>States</b>", hideable: true, hidden: false) {
        // If they've chosen a dropdown value, delete the state
        if (settings.stateToDeleteId) {
            log.info "Removing State: ${settings.stateToDeleteId}"
            deleteChildDevice(settings.stateToDeleteId)
            app.removeSetting("stateToDeleteId")
            atomicState.internalUiState = "default"
        }

        // List out the existing child states
        enumerateStates().each {
          def currentStateDecorator = ""
          if (it.displayName.toString() == atomicState.currentState) {
              currentStateDecorator = "(ACTIVE)"
          }
          paragraph "${it.displayName.toString()} ${currentStateDecorator}"
        }
          
        if (atomicState.internalUiState == "default") {
          input "btnCreateState", "button", title: "Add State", width: 3, submitOnChange: true
        }
    
        if (atomicState.internalUiState == "creatingState") {
          input(name: "newStateName", type: "text", title: "New State Name", submitOnChange: true)
          if (newStateName) {
            input "btnCreateStateSubmit", "button", title: "Submit", width: 2, submitOnChange: true
          }
          input "btnCreateStateCancel", "button", title: "Cancel", width: 10, submitOnChange: true
        }
          
        if (atomicState.internalUiState == "default" && enumerateStates().size() > 0) {
          input "btnDeleteState", "button", title: "Remove State", width: 9, submitOnChange: true
        }
        
        if (atomicState.internalUiState == "deletingState") {
          // Build a list of the children for use by the dropdown
          def existingStateOptions = []
          enumerateStates().each {
            existingStateOptions << [(it.deviceNetworkId.toString()): it.displayName]
          }
  
          input(name:	"stateToDeleteId",	type: "enum", title: "Remove a state", multiple: false, required: false, submitOnChange: true, options: (existingStateOptions))
          input "btnDeleteStateCancel", "button", title: "Cancel", submitOnChange: true
        }
      } // end section <States>
    }        
        
    if (app.getInstallationState() == "COMPLETE") {
      section("<b>Events</b>", hideable: true, hidden: false) {
        // If they've chosen a dropdown value, delete the event
        if (settings.eventToDeleteId) {
          log.info "Removing Event: ${settings.eventToDeleteId}"
          deleteChildDevice(settings.eventToDeleteId)
          app.removeSetting("eventToDeleteId")
          atomicState.internalUiState = "default"
        }
  
        // List out the existing child events
        enumerateEvents().each {
            paragraph "${it.displayName.toString()}"
        }
          
        if (atomicState.internalUiState == "default") {
            input "btnCreateEvent", "button", title: "Add Event", width: 3, submitOnChange: true
        }
          
        if (atomicState.internalUiState == "creatingEvent") {
          input(name: "newEventName", type: "text", title: "New Event Name", submitOnChange: true)
            
          if (newEventName) {
              input "btnCreateEventSubmit", "button", title: "Submit", width: 2, submitOnChange: true
          }
          input "btnCreateEventCancel", "button", title: "Cancel", width: 10, submitOnChange: true
        }
          
        if (atomicState.internalUiState == "default" && enumerateEvents().size() > 0) {
          input "btnDeleteEvent", "button", title: "Remove Event", width: 9, submitOnChange: true
        }
        
        if (atomicState.internalUiState == "deletingEvent") {
          // Build a list of the children for use by the dropdown
          def existingEventOptions = []
          enumerateEvents().each {
              existingEventOptions << [(it.deviceNetworkId.toString()): it.displayName]
          }
  
          input(name:	"eventToDeleteId",	type: "enum", title: "Remove an event", multiple: false, required: false, submitOnChange: true, options: (existingEventOptions))
          input "btnDeleteEventCancel", "button", title: "Cancel", submitOnChange: true
        }
      } // end section<Events>
    }        
        
    if (app.getInstallationState() == "COMPLETE") {
      section("<b>Transitions</b>", hideable: true, hidden: false) {
        // If they've chosen a dropdown value, delete the transition
        if (settings.transitionToDeleteId) {
            log.info "Removing Transition: ${settings.transitionToDeleteId}"
            removeTransition(settings.transitionToDeleteId)
            app.removeSetting("transitionToDeleteId")
            atomicState.internalUiState = "default"
        }

        // List out the existing transitions
        paragraph generateTransitionTable()
        
        if (atomicState.internalUiState == "default" && enumerateStates().size() >= 2 && enumerateEvents().size() >= 1) {
            input "btnCreateTransition", "button", title: "Add Transition", width: 4, submitOnChange: true
        }
    
        if (atomicState.internalUiState == "creatingTransition") {
            // Build a list of event options to trigger the transition
            def eventOptions = []
            enumerateEvents().each {
                eventOptions << [(it.displayName.toString()): it.displayName]   
            }
            
            // Build a list of state options for the "from" and "to" dropdowns.
            def existingStateOptions = []
            enumerateStates().each {
              existingStateOptions << [(it.displayName.toString()): it.displayName]
            }
            
            input(name:	"triggerEvent",	type: "enum", title: "Trigger Event", multiple: false, required: false, submitOnChange: true, options: (eventOptions))
            input(name:	"fromState",	type: "enum", title: "From State", width: 6, multiple: false, required: false, submitOnChange: true, options: (existingStateOptions))
            input(name:	"toState",	type: "enum", title: "To State", width: 6, multiple: false, required: false, submitOnChange: true, options: (existingStateOptions))
            
            if (triggerEvent && fromState && toState) {
                input "btnCreateTransitionSubmit", "button", title: "Submit", width: 2, submitOnChange: true
            }
            input "btnCreateTransitionCancel", "button", title: "Cancel", width: 10, submitOnChange: true
        }
        
        if (atomicState.internalUiState == "default" && enumerateTransitions().size() > 0) {
            input "btnDeleteTransition", "button", title: "Remove Transition", width: 8, submitOnChange: true
        }
            
        if (atomicState.internalUiState == "deletingTransition") {
            // Build a list of the children for use by the dropdown
            def existingTransitionOptions = []
            enumerateTransitions().each {
              existingTransitionOptions << [(it): it]
            }

            input(name:	"transitionToDeleteId",	type: "enum", title: "Remove a transition", multiple: false, required: false, submitOnChange: true, options: (existingTransitionOptions))
            input "btnDeleteTransitionCancel", "button", title: "Cancel", submitOnChange: true
        }
      } // end section <Transitions>
    }
        
		section () {
			input(name:	"enableLogging", type: "bool", title: "Enable Debug Logging?", defaultValue: true,	required: true)
		}
	}
}


def appButtonHandler(btn) {
    switch (btn) {
        case "btnCreateState":
            app.removeSetting("newStateName")
            atomicState.internalUiState = "creatingState"
            break
        case "btnCreateStateSubmit":
            def nsn = "State;${settings.stateMachineName};${settings.newStateName}" 
            atomicState.internalUiState = "default"
            log.info "Creating state: ${settings.newStateName}"
            def newChildDevice = addChildDevice("joelwetzel", "SSM State", nsn, null, [name: nsn, label: settings.newStateName, completedSetup: true, isComponent: true])
            if (!atomicState.currentState) {
                atomicState.currentState = settings.newStateName
                newChildDevice._on()
            }
            break
        case "btnCreateStateCancel":
            atomicState.internalUiState = "default"
            break
        case "btnDeleteState":
            atomicState.internalUiState = "deletingState"
            break
        case "btnDeleteStateCancel":
            atomicState.internalUiState = "default"
            break        
        
        case "btnCreateEvent":
            app.removeSetting("newEventName")
            atomicState.internalUiState = "creatingEvent"
            break
        case "btnCreateEventSubmit":
            def nen = "Event;${settings.stateMachineName};${settings.newEventName}" 
            atomicState.internalUiState = "default"
            log.info "Creating event: ${settings.newEventName}"
            def newChildDevice = addChildDevice("joelwetzel", "SSM Event", nen, null, [name: nen, label: settings.newEventName, completedSetup: true, isComponent: true])
            bindEvents()
            break
        case "btnCreateEventCancel":
            atomicState.internalUiState = "default"
            break
        case "btnDeleteEvent":
            atomicState.internalUiState = "deletingEvent"
            break
        case "btnDeleteEventCancel":
            atomicState.internalUiState = "default"
            break        
        
        case "btnCreateTransition":
            app.removeSetting("triggerEvent")
            app.removeSetting("fromState")
            app.removeSetting("toState")
            atomicState.internalUiState = "creatingTransition"
            break
        case "btnCreateTransitionSubmit":
            atomicState.internalUiState = "default"
            defineTransition(settings.triggerEvent, settings.fromState, settings.toState)
            break
        case "btnCreateTransitionCancel":
            atomicState.internalUiState = "default"
            break
        case "btnDeleteTransition":
            atomicState.internalUiState = "deletingTransition"
            break
        case "btnDeleteTransitionCancel":
            atomicState.internalUiState = "default"
            break
    }
}


def eventHandler(evt) {
    def eventName = evt.getDevice().toString()
    def currentState = atomicState.currentState
    
    log "Event Triggered: ${eventName}.  Current state: ${currentState}"   
    
    def finalState = currentState
    
    atomicState.transitionNames.each {
        // Parse the transitionNames
        def split1 = it.split(";")
        def tEvent = split1[0]
        def split2 = split1[1].split("->")
        def tFrom = split2[0]
        def tTo = split2[1]
        
        //log.debug "***** ${eventName} ${tFrom} ${tTo}"
        
        // Do we need to make this transition?
        if (eventName == tEvent &&
            currentState == tFrom) {
            finalState = tTo
        }
    }
    
    if (finalState != currentState) {
        log.info "Transitioning: '${currentState}' -> '${finalState}'"
        
        getChildDevice("State;${settings.stateMachineName};${currentState}")._off()
        getChildDevice("State;${settings.stateMachineName};${finalState}")._on()
        atomicState.currentState = finalState
    }
}


// ***********************
// Utility Methods
// ***********************

def removeTransition(transitionName) {
    def names = atomicState.transitionNames
    names.remove(transitionName)
    atomicState.transitionNames = names
}

def defineTransition(eventName, fromId, toId) {
    def transitionName = "${eventName};${fromId}->${toId}"
    
    log.info "Creating transition: ${transitionName}"
    
    def names = atomicState.transitionNames
    names << transitionName
    atomicState.transitionNames = names
}


def getChildDevicesInCreationOrder() {
	def unorderedChildDevices = getChildDevices()
	
	def orderedChildDevices = unorderedChildDevices.sort{a,b -> a.device.id <=> b.device.id}
	
	return orderedChildDevices
}


def enumerateStates() {
    def childStates = []
    
    getChildDevicesInCreationOrder().each {
        if (it.deviceNetworkId.startsWith("State;")) {
            childStates << it
        }
    }
            
    return childStates
}


def enumerateEvents() {
    def childEvents = []
    
    getChildDevicesInCreationOrder().each {
        if (it.deviceNetworkId.startsWith("Event;")) {
            childEvents << it
        }
    }
            
    return childEvents
}


def enumerateTransitions() {
    return atomicState.transitionNames
}


def getFormat(type, myText="") {
	if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}


def log(msg) {
	if (enableLogging) {
		log.debug msg
	}
}


def generateTransitionTable() {
    def states = enumerateStates()
    def events = enumerateEvents()
    def transitions = enumerateTransitions()

    def stateCount = states.size()
    def tableSize = stateCount + 1
    
    // Initialize the table data
    def cellValues = new String[tableSize][tableSize]
    for (int i = 0; i < tableSize; i++) {
        for (int j = 0; j < tableSize; j++) {
            cellValues[i][j] = ""
        }
    }
    
    // Add the headings
    for (int i = 0; i < stateCount; i++) {
        cellValues[i + 1][0] = "<b>" + states[i].displayName + "</b>"
        cellValues[0][i + 1] = "<b>" + states[i].displayName + "</b>"
    }
    
    // Create a reverse lookup from state name to index
    def stateIndices = [:]
    for (int i = 0; i < stateCount; i++) {
        stateIndices[states[i].displayName.toString()] = i
    }
    
    // Put each transition in a cell
    for (int i = 0; i < transitions.size(); i++) {
        def it = transitions[i]
        
        // Parse the transitionNames
        def split1 = it.split(";")
        def tEvent = split1[0]
        def split2 = split1[1].split("->")
        def tFrom = split2[0]
        def tTo = split2[1]
        
        def oldCellValue = cellValues[stateIndices[tFrom] + 1][stateIndices[tTo] + 1]
        def newCellValue = tEvent + (oldCellValue ? "<br>" + oldCellValue : "")    // If more than one event causes the same transition, we need to show both in the cell.
        cellValues[stateIndices[tFrom] + 1][stateIndices[tTo] + 1] = newCellValue
    }
    
    // List out the transitions for the left-hand side
    def listStr = ""
    for (int i = 0; i < transitions.size(); i++) {
        listStr += transitions[i] + "<br>"
    }
    
    // Render the table into HTML
    def table = "<table border=1>"
    for (int i = 0; i < tableSize; i++) {
        table += "<tr>"
        
        for (int j = 0; j < tableSize; j++) {
            table += "<td>${cellValues[i][j]}</td>"
        }
        
        table += "</tr>"
    }
    table += "</table>"
    
    def fullHtml = "<table width=100%><tr><td valign=top>${listStr}</td><td>${table}</td></tr></table>"
    
    if (stateCount >= 2) {
        return fullHtml
    }
    else {
        return ""
    }
}






















