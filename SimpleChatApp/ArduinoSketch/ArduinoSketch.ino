// Arduino Sketch for Bluetooth Chat with Android App

#include <SoftwareSerial.h>

// Configure SoftwareSerial for Bluetooth module (HC-05, HC-06)
// RX pin of Arduino (connects to TX of Bluetooth module)
// TX pin of Arduino (connects to RX of Bluetooth module)
// Make sure to use voltage dividers for Arduino's TX to Bluetooth's RX if Bluetooth module is 3.3V
const int BLUETOOTH_RX_PIN = 10;
const int BLUETOOTH_TX_PIN = 11;

SoftwareSerial bluetooth(BLUETOOTH_RX_PIN, BLUETOOTH_TX_PIN); // RX, TX

// Baud rate for Bluetooth module (common default for HC-05 is 9600 or 38400 in AT mode)
// Ensure this matches your Bluetooth module's configuration.
long bluetoothBaudRate = 9600;

// Baud rate for Serial communication with the computer
long computerSerialBaudRate = 9600;

void setup() {
  // Initialize serial communication with the computer
  Serial.begin(computerSerialBaudRate);
  Serial.println("Arduino Serial Monitor Ready. Initializing Bluetooth...");

  // Initialize serial communication with the Bluetooth module
  bluetooth.begin(bluetoothBaudRate);
  bluetooth.println("Bluetooth Module Initialized by Arduino!"); // Test message to Android if it connects quickly
  Serial.println("Bluetooth Serial Initialized at " + String(bluetoothBaudRate) + " bps.");
  Serial.println("Waiting for messages...");
}

void loop() {
  // Check for data coming from the Android app (via Bluetooth)
  if (bluetooth.available()) {
    String messageFromAndroid = "";
    while(bluetooth.available()) {
      char c = bluetooth.read();
      messageFromAndroid += c;
      delay(2); // Small delay to allow buffer to fill if message is long
    }
    Serial.print("Received from Android: ");
    Serial.println(messageFromAndroid);

    // Optional: Echo back to Android (for testing)
    // bluetooth.print("Arduino Echo: ");
    // bluetooth.println(messageFromAndroid);
  }

  // Check for data coming from the Computer (via Serial Monitor)
  if (Serial.available()) {
    String messageFromComputer = "";
     while(Serial.available()){
      char c = Serial.read();
      messageFromComputer +=c;
      delay(2);
    }
    Serial.print("Sending to Android: ");
    Serial.println(messageFromComputer);
    bluetooth.println(messageFromComputer); // Send to Android app
  }
}

/*
   Common HC-05 Connections:
   HC-05 VCC    -> Arduino 5V
   HC-05 GND    -> Arduino GND
   HC-05 TX     -> Arduino Pin 10 (BLUETOOTH_RX_PIN) - (Note: Bluetooth TX to Arduino RX)
   HC-05 RX     -> Arduino Pin 11 (BLUETOOTH_TX_PIN) through a voltage divider (e.g., 1kOhm -- 2kOhm)
                  (because HC-05 RX is 3.3V tolerant, Arduino TX is 5V)

   If your HC-05 module has a STATE pin, you can connect it to an Arduino pin
   to monitor connection status (usually HIGH when connected).

   Ensure the baud rate (bluetoothBaudRate) matches your HC-05 module's configuration.
   Default is often 9600 or 38400. You might need to use AT commands to set it.
   To enter AT mode for HC-05:
   1. Disconnect VCC.
   2. Press and hold the small button on the HC-05 module.
   3. While holding the button, connect VCC.
   4. The LED should blink slowly (once every 2 seconds). Now it's in AT command mode.
   5. Use Arduino IDE's Serial Monitor (set to 38400 baud, Both NL & CR) to send AT commands.
      - AT (should respond OK)
      - AT+UART? (to check current baud rate, stop bits, parity)
      - AT+UART=9600,0,0 (to set baud rate to 9600, 1 stop bit, no parity)
      - AT+NAME=YourDeviceName (to set the Bluetooth name)
      - AT+PSWD=YourPIN (to set the PIN, e.g., 1234)
*/
