# Motioneye motion detect for Hubitat

You will need a MQTT server. You can use the linux system with the camera
if you like with a `sudo apt install mosquitto`. Make sure to have your router assign
a static IP to the server. For example, mine is 192.168.1.7

Note that sudo is assumed for many commands - the instructions mention
this too.  The instructions also tell you to use pip2 if your python is
version 2 (Raspberry!)

Note: I'm using a Raspberry and raspbian Buster. MotionEye will work with
webcams on other Linux. If one was really Homebrew savvy and lucky it could
work on OSX.

## MQTT setup
We need to install some MQTT command line tools with 
```sh
sudo apt install mosquitto-clients`
```
Now create the two shell scripts in /usr/local/bin. First `meye-motion-on`
```sh
#!/bin/bash
mosquitto_pub -h 192.168.1.7 -t 'cameras/office/test' -m 'active'
```
and `meye-motion-off`:
```sh
#!/bin/bash
mosquitto_pub -h 192.168.1.7 -t 'cameras/office/test' -m 'inactive'
```
Note: change the script to use your IP address and your topic (-t). You'll need
to remember the IP and the topic for setting up the Hubitat driver and testing

### Hubitat setup
  copy the mqtt-motioneye.groovy code into a new Hubitat driver.
  Create a new device using that driver.
  Set the IP and topic in the preferences and save.

### Test to see if MQTT is working.
On the system with the camera where you have the on off scripts, you can
run them manually 
```sh
$ /usr/local/bin/meye-motion-on
```
and watch the device repsond in Hubitat. If that works
continue to setting up Motioneye.

If you happen to have MQTT Explorer, you can use that to watch the MQTT server and the 
topic. Or, from the command line:
```sh
mosquitto_sub -h 192.168.1.7 -t cameras/office/test
```
and then toggle by calling the scripts.

### Setup Motioneye.

Install MotionEye for your Linux system. [Instructions here](https://github.com/ccrisan/motioneye/wiki/Installation)

NOTE: This is NOT MotionEyeOS! We can't use MotionEyeOS. We can use MotionEye.

After pointing your browser to the right ip and port and logging in as
Admin then setup a camera. On a raspberry pi - you probably want the `mmal`
driver. Set the camera frame rate - I use 10 on my Pi3. Enable `Auto Threshold Tuning`.
`Light Switch Detection` is a good thing to turn on. Minimum Motion Frames: Try 2 and 
work up from there if that's too sensitive. DO NOT set Show Frame Changes. 

For Motion Notificatians, we want to 'Run A Command' and 'Run An End Command'
You have to type the full path to the two shells scripts `/usr/local/bin/meye-motion-on`
and `/usr/local/bin/meye-motion-off`

That's it, Enjoy.

You might want to reboot and make sure that motiond is running even and events are
passing to MQTT if you don't have a browser running.

You might want to adjust the camera and motion settings to your camera and
situation.
