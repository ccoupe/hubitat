
README-Octoprint.md

## My 3D Workflow
I use OctoPrint to deal with my 3D printers, an Ender 3 S1 and an Ender 3 V2. 
Each one has a separate Raspberry Pi running Octoprint. 

I use Cura to cura to 'slice' the .stl files into gcode. And then I tell Cura
to send the gcode file to OctoPrint to print. I believe Prusa Slicer has octoprint capabilies
as well as Cura. You need a Cura plugin ( Marketplace -> Plugins -> OctoPrint Connection). You
have to do the API key dance. Then test and use it enough to decide what you
like to don't like. 

Cura doesn't really make it easy to swith between two OctoPrints. Oh, it can be done
but it's not easy and sometimes I think I should restart Cura. Never a good sign when
that is a troubleshooting method. Still, I don't control Cura or the plugins so ignore that 
first world problem. 

I don't like fan noise. Anywhere. That not a positive thing with 3D printers.
So, my Printers are a (audible) distance away from normal activities. Even so
I want to turn them off when not needed. But not too soon, 3D printers need to
cool down somewhat before turning off the power.

I don't like to walk to the printer to turn things on and then walk to the
computer to slice and print. I also don't like having the print finish and
I don't know about it because I'm busy with something else.

I'm going to integrate those things into my Hubitat Home automation system using
MQTT. Why MQTT ? - because it (en)forces operations I believe are good:  It doesn't
tie me to Hubitat. They will work whether or not Hubitat is up and running. The printers
will work if MQTT is down but that's not fun to deal with. Not a problem I worry about.
By tieing into the Home Automation system I can turn the room lights on or off. Or change
the color of bulb based on printer status - like the light outside of a darkroom?

If you spent some time looking at Octoprint plugins you've found some that claim to do 
some of these things. You may have found them wanting. 

Basically, there is a lot of little fiddly things to do and missing a step
can prevent things from working. Oh, you have to google all the instructions
because it is way too much to put any details here. 

## Octoprint
Obviously you need Octoprint, most likely on a Raspberry Pi (I use a couple
of old 3b's) If you want to see pictures in real time (you do), then you need a
compatible camera and camera mount. And lighting.  And Light switches and ...

You'll spend quite a lot more than that $35 computer board. Maybe more than a
free puppy. 

**You want a static IP for your Octoprint puppy.** Go fool with your router.

If you like Dashboards you can put image captures on your Hubitat dashboard. It
is not something I promote because I never use it nor remember what the url is. 
It's not hard to find the Octoprint descriptions.

## MQTT broker. 
MQTT uses a publish/subscribe paradigm. Muliple things can pubish to a topic
and multiple things can be notified if something is published on the topics they
subscribe to. A Broker sits in the middle to notify the 'some things' when the
other things publish. 

I use 'Mosquitto', there are other brokers. I run it on Linux (Raspberry OS is
Linux). I have run Mosquitto on a Pi4 along with some other thigs. It's a very light
load. You could run it on the some machine you have octoprint on - you just need
to get command line access to your octoprint (google it) - not hard to do.

Then `sudo apt install mosquitto-clients`
I do not use login names and passwords with my broker. Too much typing for
false security, IMO. Hint: you don't have to configure MQTT but you do it your
way. 

**You want a static IP for your MQTT Broker machine.** Really. go do it.
Mine is 192.168.1.3 (aka 'stoic.local' in my mdns) 
**Hubitat doesn't like dns names in MQTT**.

Rumor is Mosquitto can be installed on Windows. I'd bet homebrew would
do it on OSX. Don't care. Octoprint needs to run 24x7, a small Pi running a
linux is already in your network. There is no maintenance - I've never had to
look at the logs - really! OK, maybe once when there was a login problem.

## MQTT Explorer

## Power outlet
Since I want to control the power to the printer from Hubitat need an outlet.
There are hundreds of choices. You want a zigbee or zwave. **NOT WIFI**. I use a cheap
Sonoff S31 Lite. 

## An import note you should not ignore
Aside from the static IP's, There is one other **must do** to explain. 
You need a short name for your Printer. How short? 7 characters. 
My Ender 3 V2 is 'E3V2' and my Ender 3 S1 is 'E3S1' - see the pattern?

This name is the first word in the notification message that Hubitat
flings around. I have very small 1" display screens and 7.5 characters
at the largest font is all they can handle on one line. I like to use
a large font so I can read it from a few feet away.  You can use longer
names and regret the time to it takes to redo it my way. 

You could modify the code in the driver and any plugin configurations that need it.
It's a pretty simple fix, but things would break for me so I'm not going to do it.

## Octoprint Plugins
### MQTT
### PSU Control
### PSU Control - MQTT
### PortLister 

## Hubitat
### Octoprint Monitor Driver 
This will be easy to configure. Right? We just need the ip address
of the MQTT broker and the special topic name. 

The Octoprint driver sends Hubitat notifications. It defaults
to sending over 100 per print job. There a driver preferences you
can set. Minimal - is just start and finish events. Or you can get
progress reports every 5% or 10%.  You would not want send a lot events to
your Cell phone. See Octoprint Notification App

### Octoprint Notification App
This one is easy. You pick your Mqtt-octoprint driver instance and
then select other notification devices in Hubitat that you want to
send the notification too.  DO NOT use the Mqtt-octoprint driver
in the second set. - Endless loop. How will you stop it ?
