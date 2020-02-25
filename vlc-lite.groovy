/**
     *  VLC Lite - modified from VLC Things. No music player stuff. TTS only. 
     *
     *  VLC Things. A SmartThings device handler for the VLC media player.
     *
     *  For more information, please visit
     *  <https://github.com/statusbits/smartthings/tree/master/VlcThing.md/>
     *
     *  --------------------------------------------------------------------------
     *
     *  Copyright © 2014 Statusbits.com
     *
     *  This program is free software: you can redistribute it and/or modify it
     *  under the terms of the GNU General Public License as published by the Free
     *  Software Foundation, either version 3 of the License, or (at your option)
     *  any later version.
     *
     *  This program is distributed in the hope that it will be useful, but
     *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
     *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
     *  for more details.
     *
     *  You should have received a copy of the GNU General Public License along
     *  with this program.  If not, see <http://www.gnu.org/licenses/>.
     *
     *  --------------------------------------------------------------------------
     *
     *  Version 2.0.0 (12/22/2016)
     */

import groovy.json.JsonSlurper

metadata {
    definition (name:"VLC Lite", namespace:"ccoupe", author:"Cecil Coupe") {
        //capability "Actuator"
        //capability "Switch"
        capability "SpeechSynthesis"
        capability "Refresh"
        capability "Polling"
        
        attribute "voice", "string"
        attribute "alarm_url", "string"
        attribute "warn_url", "string"
        attribute "end_url", "string"
        
    }
}

preferences {
    // NOTE: Android client does not accept "defaultValue" attribute!
    input("confIpAddr", "string", title:"VLC IP Address",
        required:false, displayDuringSetup:true)
    input("confTcpPort", "number", title:"VLC TCP Port",
        required:false, displayDuringSetup:true)
    input("confPassword", "password", title:"VLC Password",
        required:false, displayDuringSetup:true)
    input("volLevel", "number", title: "Volume Level",
            require: false, 
            displayDuringSetup:true)
    input("alarm_url", "string", title: "Alert sound url",
            require: false, 
            displayDuringSetup:true)
    input("warn_url", "string", title: "Warning sound url",
            require: false, 
            displayDuringSetup:true)
    input("end_url", "string", title: "Ending sound url",
            require: false, 
            displayDuringSetup:true)
    input ("voice", "enum", title: "Pick a Voice",
             require: false, 
            displayDuringSetup:true,
            options: getVoices(), defaultValue: ["Salli"] )
}


def installed() {
    //log.debug "installed()"
    log.info title()

    // Initialize attributes to default values (Issue #18)
    sendEvent([name:'status', value:'stopped', displayed:false])
    sendEvent([name:'level', value:'0', displayed:false])
    sendEvent([name:'mute', value:'unmuted', displayed:false])
    sendEvent([name:'trackDescription', value:'', displayed:false])
    sendEvent([name:'connection', value:'disconnected', displayed:false])
}

def updated() {
  //log.debug "updated with settings: ${settings}"
    log.info title()

    unschedule()

    if (!settings.confIpAddr) {
      log.warn "IP address is not set!"
        return
    }

    def port = settings.confTcpPort
    if (!port) {
      log.warn "Using default TCP port 8080!"
        port = 8080
    }

    def dni = createDNI(settings.confIpAddr, port)
    device.deviceNetworkId = dni
    state.dni = dni
    state.hostAddress = "${settings.confIpAddr}:${settings.confTcpPort}"
    state.requestTime = 0
    state.responseTime = 0
    state.updatedTime = 0
    state.lastPoll = 0

    if (settings.confPassword) {
        state.userAuth = ":${settings.confPassword}".bytes.encodeBase64() as String
    } else {
        state.userAuth = null
    }
    
    if (settings.volLevel) {
      // TODO
    } else {
      state.level = 50
    }
    
    if (settings.voice) {
      state.voice = settings.voice
    } else {
      state.voice = "Brian"
    }
    sendEvent([name:'voice', value: state.voice, displayed:true])
    log.info "Chosen Voice: ${state.voice}"
    
    if (settings.alarm_url) {
      state.alarm_url = settings.alarm_url
      sendEvent([name:'alarm_url', value: state.alarm_url, displayed:true])
    } 
    if (settings.warn_url) {
      state.warn_url = settings.warn_url
      sendEvent([name:'warn_url', value: state.warn_url, displayed:true])
    } 
    if (settings.end_url) {
      state.alarm_url = settings.alarm_url
      sendEvent([name:'alarm_url', value: state.alarm_url, displayed:true])
    } 
    
  
    startPollingTask()
    //STATE()
}

def getVoices() {
	def voices = getTTSVoices()
	voices.sort{ a, b ->
		a.language <=> b.language ?: a.gender <=> b.gender ?: a.gender <=> b.gender  
	}    
  def list = voices.collect{ ["${it.name}": "${it.name}:${it.gender}:${it.language}"] }
	return list
}

def pollingTask() {
    //log.debug "pollingTask()"

    state.lastPoll = now()

    // Check connection status
    def requestTime = state.requestTime ?: 0
    def responseTime = state.responseTime ?: 0
    if (requestTime && (requestTime - responseTime) > 10000) {
        log.warn "No connection!"
        sendEvent([
            name:           'connection',
            value:          'disconnected',
            isStateChange:  true,
            displayed:      true
        ])
    }

    def updated = state.updatedTime ?: 0
    if ((now() - updated) > 10000) {
        return apiGetStatus()
    }
}

def parse(String message) {
    def msg = stringToMap(message)
    if (msg.containsKey("simulator")) {
        // simulator input
        return parseHttpResponse(msg)
    }

    if (!msg.containsKey("headers")) {
        log.error "No HTTP headers found in '${message}'"
        return null
    }

    // parse HTTP response headers
    def headers = new String(msg.headers.decodeBase64())
    def parsedHeaders = parseHttpHeaders(headers)
   // log.debug "parsedHeaders: ${parsedHeaders}"
    if (parsedHeaders.status != 200) {
        log.error "Server error: ${parsedHeaders.reason}"
        return null
    }

    // parse HTTP response body
    if (!msg.body) {
        log.error "No HTTP body found in '${message}'"
        return null
    }

    def body = new String(msg.body.decodeBase64())
    
    def slurper = new JsonSlurper()
    return parseHttpResponse(slurper.parseText(body))
}



// MusicPlayer.setLevel
def setLevel(number) {
    //log.debug "setLevel(${number})"

    if (device.currentValue('mute') == 'muted') {
        sendEvent(name:'mute', value:'unmuted')
    }

    sendEvent(name:"level", value:number)
    def volume = ((number * 512) / 100) as int
    return apiCommand("command=volume&val=${volume}")
}


// MusicPlayer.unmute
def unmute() {
    //log.debug "unmute()"

    if (device.currentValue('mute') == 'muted') {
        return setLevel(state.savedVolume.toInteger())
    }

    return null
}

// MusicPlayer.playTrack
def playTrack(uri) {
    //log.debug "playTrack(${uri})"
    def command = "command=in_play&input=" + URLEncoder.encode(uri, "UTF-8")
    return apiCommand(command, 500)
}


/*  SpeechSynthesis.speak
  Scan for [alarm] or [warn] at begining of string, [end] at end.
  Get VLC status. (playing track)
  Build cmds
    [pause] if playing
    play warn|alarm url if asked for
    play tts file
    play end url if asked for
    
*/
def speak(text) {
    log.debug "line 292 speak(${text})"
    
    def sound = myTextToSpeech(text)
    
    return playTrack(sound.uri)
}

// polling.poll 
def poll() {
    //log.debug "poll()"
    return refresh()
}

// refresh.refresh
def refresh() {
    log.debug "refresh()"
    //STATE()

    if (!updateDNI()) {
        sendEvent([
            name:           'connection',
            value:          'disconnected',
            isStateChange:  true,
            displayed:      false
        ])

        return null
    }

    // Restart polling task if it's not run for 5 minutes
    def elapsed = (now() - (state?.lastPoll ? state.lastPoll : (now()-301))) / 1000
    if (elapsed > 300) {
        log.warn "Restarting polling task..."
        unschedule()
        startPollingTask()
    }

    return apiGetStatus()
}


def playTextAndResume(text) {
    log.debug "playTextAndResume(${text}, ${volume})"
    def sound = myTextToSpeech(text)
    //log.debug "line 393 sound = ${sound}"
    return playTrackAndResume(sound.uri, (sound.duration as Integer) + 1, volume)
}

def playTextAndRestore(text, volume = null) {
    //log.debug "playTextAndRestore(${text}, ${volume})"
    def sound = myTextToSpeech(text)
    return playTrackAndRestore(sound.uri, (sound.duration as Integer) + 1, volume)
}

def playSoundAndTrack(uri, duration, trackData, volume = null) {
    //log.debug "playSoundAndTrack(${uri}, ${duration}, ${trackData}, ${volume})"

    // FIXME
    return playTrackAndRestore(uri, duration, volume)
}


private startPollingTask() {
    //log.debug "startPollingTask()"

    pollingTask()

    Random rand = new Random(now())
    def seconds = rand.nextInt(60)
    def sched = "${seconds} 0/1 * * * ?"

    //log.debug "Scheduling polling task with \"${sched}\""
    schedule(sched, pollingTask)
}

def apiGet(String path) {
    //log.debug "apiGet(${path})"

    if (!updateDNI()) {
        return null
    }

    state.requestTime = now()
    state.responseTime = 0

    def headers = [
        HOST:       state.hostAddress,
        Accept:     "*/*"
    ]
    
    if (state.userAuth != null) {
        headers['Authorization'] = "Basic ${state.userAuth}"
    }

    def httpRequest = [
        method:     'GET',
        path:       path,
        headers:    headers
    ]

    //log.debug "httpRequest: ${httpRequest}"
    return new hubitat.device.HubAction(httpRequest)
}

private def delayHubAction(ms) {
    return new hubitat.device.HubAction("delay ${ms}")
}

private apiGetStatus() {
    return apiGet("/requests/status.json")
}

private apiCommand(command, refresh = 0) {
    //log.debug "apiCommand(${command})"

    def actions = [
        apiGet("/requests/status.json?${command}")
    ]

    if (refresh) {
        actions << delayHubAction(refresh)
        actions << apiGetStatus()
    }

    return actions
}

private def apiGetPlaylists() {
    //log.debug "getPlaylists()"
    return apiGet("/requests/playlist.json")
}

private parseHttpHeaders(String headers) {
    def lines = headers.readLines()
    def status = lines.remove(0).split()

    def result = [
        protocol:   status[0],
        status:     status[1].toInteger(),
        reason:     status[2]
    ]

    return result
}

private def parseHttpResponse(Map data) {
    //log.debug "parseHttpResponse(${data})"

    state.updatedTime = now()
    if (!state.responseTime) {
        state.responseTime = now()
    }

    def events = []

    if (data.containsKey('state')) {
        def vlcState = data.state
        //log.debug "VLC state: ${vlcState})"
        events << createEvent(name:"status", value:vlcState)
        if (vlcState == 'stopped') {
            events << createEvent([name:'trackDescription', value:''])
        }
    }

    if (data.containsKey('volume')) {
        //log.debug "VLC volume: ${data.volume})"
        def volume = ((data.volume.toInteger() * 100) / 512) as int
        events << createEvent(name:'level', value:volume)
    }

    if (data.containsKey('information')) {
        parseTrackInfo(events, data.information)
    }

    events << createEvent([
        name:           'connection',
        value:          'connected',
        isStateChange:  true,
        displayed:      false
    ])

    //log.debug "events: ${events}"
    return events
}

private def parseTrackInfo(events, Map info) {
    //log.debug "parseTrackInfo(${events}, ${info})"

    if (info.containsKey('category') && info.category.containsKey('meta')) {
        def meta = info.category.meta
        //log.debug "Track info: ${meta})"
        if (meta.containsKey('filename')) {
            if (meta.filename.contains("//s3.amazonaws.com/smartapp-")) {
                log.trace "Skipping event generation for sound file ${meta.filename}"
                return
            }
        }

        def track = ""
        if (meta.containsKey('artist')) {
            track = "${meta.artist} - "
        }
        if (meta.containsKey('title')) {
            track += meta.title
        } else if (meta.containsKey('filename')) {
            def parts = meta.filename.tokenize('/');
            track += parts.last()
        } else {
            track += '<untitled>'
        }

        if (track != device.currentState('trackDescription')) {
            meta.station = track
            events << createEvent(name:'trackDescription', value:track, displayed:false)
           // events << createEvent(name:'trackData', value:meta.encodeAsJSON(), displayed:false)
        }
    }
}


private def myTextToSpeech(text) {
   
    def sound = textToSpeech(text, state.voice)
    log.debug "${text}"
    log.debug "${sound.uri}"
    sound.uri = sound.uri.replace('https:', 'http:')
    return sound
}

private String createDNI(ipaddr, port) {
    //log.debug "createDNI(${ipaddr}, ${port})"

    def hexIp = ipaddr.tokenize('.').collect {
        String.format('%02X', it.toInteger())
    }.join()

    def hexPort = String.format('%04X', port.toInteger())
 
    return "${hexIp}:${hexPort}"
}

private updateDNI() {
    if (!state.dni) {
      log.warn "DNI is not set! Please enter IP address and port in settings."
        return false
    }
 
    if (state.dni != device.deviceNetworkId) {
      log.warn "Invalid DNI: ${device.deviceNetworkId}!"
        device.deviceNetworkId = state.dni
    }

    return true
}

private def title() {
    return "VLC Thing. Version 2.0.0 (12/22/2016). Copyright © 2014 Statusbits.com"
}

private def STATE() {
    log.trace "state: ${state}"
    log.trace "deviceNetworkId: ${device.deviceNetworkId}"
    log.trace "status: ${device.currentValue('status')}"
    log.trace "level: ${device.currentValue('level')}"
    log.trace "mute: ${device.currentValue('mute')}"
    log.trace "trackDescription: ${device.currentValue('trackDescription')}"
    log.trace "connection: ${device.currentValue("connection")}"
}
