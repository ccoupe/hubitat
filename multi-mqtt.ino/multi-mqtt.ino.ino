/*********
   Sends motion (AM312), temp and humidity (DHT11) to MQTT with json payload.
   Borrowed from
  Rui Santos
  Complete project details at https://randomnerdtutorials.com

  And many others
*********/

#include <WiFi.h>
#include <PubSubClient.h>
#include <Wire.h>
#include <Adafruit_Sensor.h>
#include <DHT.h>

// Replace the next variables with your SSID/Password combination
const char* ssid = "CJCNET";
const char* password = "LostAgain2";

// Add your MQTT Broker IP address, example:
//const char* mqtt_server = "192.168.1.144";
const char* mqtt_server = "192.168.1.7";

WiFiClient espClient;
PubSubClient client(espClient);
long lastMsg = 0;
char msg[50];
int value = 0;


// LED Pin - built-in blue led on my htlegto board
const int led = 2;
// AM312 motion sensor
const int motionSensor = 17;

#define DHTPIN 16     // Digital pin connected to the DHT sensor
#define DHTTYPE    DHT11     // DHT 11
DHT dht(DHTPIN, DHTTYPE);

#define timeSeconds 10      // time after motion before sending OFF
#define timeMinutes  1      // time between temp/humidty checks

// Timer: Auxiliary variables
unsigned long now = millis();
unsigned long lastTrigger = 0;
volatile boolean startTimer = false;
volatile boolean haveMotion = false;
unsigned long readAfter = millis() + (timeMinutes * 60 * 1000);

// Checks if motion was detected, sets LED HIGH and starts a timer
void IRAM_ATTR detectsMovement() {
  //Serial.println("MOTION DETECTED!!!");  // not a good idea to do all this in an ISR
  digitalWrite(led, HIGH);
  haveMotion = true;
  startTimer = true;
  lastTrigger = millis();
}


String readDHTTemperature() {
  // Sensor readings may also be up to 2 seconds 'old' (its a very slow sensor)
  // Read temperature as Celsius (the default)
  //float t = dht.readTemperature();
  // Read temperature as Fahrenheit (isFahrenheit = true)
  float t = dht.readTemperature(true);
  // Check if any reads failed and exit early (to try again).
  if (isnan(t)) {
    Serial.println("Failed to read from DHT sensor!");
    return "--";
  }
  else {
    Serial.println(t);
    return String(t);
  }
}

String readDHTHumidity() {
  // Sensor readings may also be up to 2 seconds 'old' (its a very slow sensor)
  float h = dht.readHumidity();
  if (isnan(h)) {
    Serial.println("Failed to read from DHT sensor!");
    return "--";
  }
  else {
    Serial.println(h);
    return String(h);
  }
}


void setup() {
  Serial.begin(115200);
  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);

  // PIR Motion Sensor mode INPUT_PULLUP
  pinMode(motionSensor, INPUT_PULLUP);
  // Set motionSensor pin as interrupt, assign interrupt function and set RISING mode
  attachInterrupt(digitalPinToInterrupt(motionSensor), detectsMovement, RISING);

  dht.begin();

  pinMode(led, OUTPUT);
  digitalWrite(led, LOW);
}

void setup_wifi() {
  delay(10);
  // We start by connecting to a WiFi network
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
}

void callback(char* topic, byte* message, unsigned int length) {
  Serial.print("Message arrived on topic: ");
  Serial.print(topic);
  Serial.print(". Message: ");
  String messageTemp;

  for (int i = 0; i < length; i++) {
    Serial.print((char)message[i]);
    messageTemp += (char)message[i];
  }
  Serial.println();

  // Feel free to add more if statements to control more GPIOs with MQTT

  // If a message is received on the topic esp32/output, you check if the message is either "on" or "off".
  // Changes the output state according to the message
  if (String(topic) == "esp32/output") {
    Serial.print("Changing output to ");
    if (messageTemp == "on") {
      Serial.println("on");
      digitalWrite(led, HIGH);
    }
    else if (messageTemp == "off") {
      Serial.println("off");
      digitalWrite(led, LOW);
    }
  }
}

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    // Attempt to connect
    if (client.connect("ESP8266Client")) {
      Serial.println("connected");
      // Subscribe
      client.subscribe("esp32/output");
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      // Wait 5 seconds before retrying
      delay(5000);
    }
  }
}
void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();


  if (haveMotion) {       //set by ISR
    Serial.println("Motion detected");
    // publish to MQTT
    client.publish("sensors/office/motion", "ON");
    haveMotion = false;
  }
  // wait for timeout
  now = millis();
  if (startTimer && (now - lastTrigger > (timeSeconds * 1000))) {
    Serial.println("Motion stopped...");
    digitalWrite(led, LOW);
    startTimer = false;
    haveMotion = false;
    // publish to MQTT
    client.publish("sensors/office/motion", "OFF");
  }
  if (now > readAfter) {
    readAfter = millis() + (timeMinutes * 60 * 1000); // set new read time
    client.publish("sensors/office/temperature",readDHTTemperature().c_str() );
    client.publish("sensors/office/humidity", readDHTHumidity().c_str() );
  }
}
