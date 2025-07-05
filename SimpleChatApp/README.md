# Simple Bluetooth Chat Application (Android - Arduino - Computer)

This project demonstrates a simple chat application system enabling communication between an Android application, an Arduino board (with a Bluetooth module like HC-05), and a computer-side Python script.

**Communication Flow:**
Android App <-> Bluetooth <-> Arduino <-> USB Serial <-> Python Script (Computer)

## Features

*   **Android App:**
    *   User interface to send and receive chat messages.
    *   Connects to a paired Bluetooth device (e.g., HC-05 connected to Arduino).
    *   Handles Bluetooth permissions and enablement.
    *   Displays connection status.
*   **Arduino Sketch:**
    *   Uses `SoftwareSerial` to communicate with a Bluetooth module.
    *   Relays messages received from the Android app (via Bluetooth) to the computer (via USB Serial).
    *   Relays messages received from the computer (via USB Serial) to the Android app (via Bluetooth).
*   **Computer-Side Python Script:**
    *   Connects to the Arduino via a serial port.
    *   Displays messages received from the Android app (forwarded by Arduino).
    *   Allows typing messages in the console to send to the Android app (via Arduino).

## Directory Structure

```
SimpleChatApp/
├── app/                      # Android application module
│   ├── build.gradle
│   ├── libs/
│   └── src/
│       ├── main/
│       │   ├── java/com/example/simplechatapp/
│       │   │   ├── BluetoothService.java
│       │   │   ├── ChatMessageAdapter.java
│       │   │   └── MainActivity.java
│       │   ├── res/
│       │   │   ├── drawable/
│       │   │   ├── layout/
│       │   │   ├── menu/
│       │   │   └── values/
│       │   └── AndroidManifest.xml
│       └── ... (test directories, etc.)
├── ArduinoSketch/            # Arduino sketch files
│   └── ArduinoSketch.ino
├── ComputerSide/             # Python script for computer-side chat
│   └── serial_chat_client.py
├── build.gradle              # Project-level build.gradle
├── settings.gradle
└── README.md                 # This file
```

## Setup and Usage

### 1. Arduino Setup

*   **Hardware:**
    *   Arduino board (e.g., Uno, Nano).
    *   Bluetooth module (e.g., HC-05, HC-06).
    *   Connecting wires.
    *   **Wiring (HC-05 example):**
        *   HC-05 VCC    -> Arduino 5V (or 3.3V if module requires)
        *   HC-05 GND    -> Arduino GND
        *   HC-05 TX     -> Arduino Pin 10 (configurable in `ArduinoSketch.ino` as `BLUETOOTH_RX_PIN`)
        *   HC-05 RX     -> Arduino Pin 11 (configurable as `BLUETOOTH_TX_PIN`) **through a voltage divider** (e.g., 1kΩ & 2kΩ resistors) if your Arduino is 5V and HC-05 is 3.3V.
*   **Software:**
    1.  Open `SimpleChatApp/ArduinoSketch/ArduinoSketch.ino` in the Arduino IDE.
    2.  **Configure Pins:** If you used different pins for `SoftwareSerial` than 10 & 11, update `BLUETOOTH_RX_PIN` and `BLUETOOTH_TX_PIN` in the sketch.
    3.  **Configure Baud Rates:**
        *   `bluetoothBaudRate` (default 9600): Ensure this matches your Bluetooth module's configured baud rate. You might need to use AT commands to set your HC-05's baud rate (see comments in the sketch for AT command mode).
        *   `computerSerialBaudRate` (default 9600): This is for communication with the Python script or Serial Monitor.
    4.  Connect your Arduino to the computer via USB.
    5.  Select the correct board and port in the Arduino IDE.
    6.  Upload the sketch to your Arduino.

### 2. Android Application Setup

*   **Prerequisites:**
    *   Android Studio installed.
    *   An Android device with Bluetooth.
*   **Building and Running:**
    1.  Open the `SimpleChatApp` project in Android Studio.
    2.  Let Gradle sync and build the project.
    3.  **Target Device Name:** In `MainActivity.java`, the variables `TARGET_DEVICE_NAME_1` ("HC-05") and `TARGET_DEVICE_NAME_2` ("ArduinoBT") are used to find your Arduino's Bluetooth module. If your module has a different name, update these constants or modify the connection logic.
    4.  Connect your Android device to your computer (with USB debugging enabled) or use an emulator (Bluetooth functionality might be limited on emulators).
    5.  Run the app on your device.
*   **Pairing:**
    1.  On your Android device, go to Bluetooth settings.
    2.  Scan for devices. Your Arduino's Bluetooth module (e.g., "HC-05") should appear.
    3.  Pair with the module. The default PIN for HC-05 is often "1234" or "0000".

### 3. Computer-Side Python Script Setup

*   **Prerequisites:**
    *   Python 3 installed.
    *   `pyserial` library installed:
        ```bash
        pip install pyserial
        ```
*   **Configuration:**
    1.  Open `SimpleChatApp/ComputerSide/serial_chat_client.py` in a text editor.
    2.  **Modify `SERIAL_PORT`:** Change the value of `SERIAL_PORT` to the correct serial port your Arduino is connected to (e.g., "COM3" on Windows, "/dev/ttyUSB0" or "/dev/ttyACM0" on Linux, "/dev/cu.usbmodemXXXX" on macOS).
    3.  Ensure `BAUD_RATE` (default 9600) matches `computerSerialBaudRate` in the Arduino sketch.
*   **Running:**
    1.  Open a terminal or command prompt.
    2.  Navigate to the `SimpleChatApp/ComputerSide/` directory.
    3.  Run the script:
        ```bash
        python serial_chat_client.py
        ```
    4.  If the Arduino IDE's Serial Monitor is open on the same port, close it before running the Python script.

## Usage Workflow

1.  Ensure the Arduino is powered on and the sketch is running.
2.  (Optional) Start the Python script on your computer. It should connect to the Arduino.
3.  Open the Android app.
4.  Grant Bluetooth permissions if prompted.
5.  Enable Bluetooth if prompted.
6.  In the Android app, tap the "Connect" option in the menu (top-right). The app will attempt to connect to the paired Bluetooth module (e.g., "HC-05").
    *   The app's subtitle should change to "Connecting..." and then "Connected to [DeviceName]".
    *   The "Send" button will be enabled.
7.  **Chatting:**
    *   **Android to Computer:** Type a message in the Android app's input field and tap "Send". The message should appear in the app's chat display and also in the Python script's console (prefixed with "Received from Arduino/Android:").
    *   **Computer to Android:** Type a message in the Python script's console and press Enter. The message should appear in the Android app's chat display (prefixed with "Device:").

## Troubleshooting

*   **Cannot Connect (Android App):**
    *   Ensure the Bluetooth module is paired with your Android device.
    *   Verify `TARGET_DEVICE_NAME_1`/`_2` in `MainActivity.java` matches your module's broadcasted name.
    *   Check HC-05 wiring and power. Its LED should indicate its status (e.g., blinking means waiting for connection, often a different blink rate or solid when connected).
    *   Ensure the `bluetoothBaudRate` in the Arduino sketch matches the HC-05's actual configuration.
*   **Python Script Cannot Connect:**
    *   Verify `SERIAL_PORT` in `serial_chat_client.py` is correct.
    *   Make sure no other program (like Arduino IDE's Serial Monitor) is using the serial port.
    *   Check Arduino is connected to USB and powered.
*   **Garbled Messages:**
    *   Likely a baud rate mismatch. Ensure:
        *   `bluetoothBaudRate` in Arduino sketch matches HC-05 module's rate.
        *   `computerSerialBaudRate` in Arduino sketch matches `BAUD_RATE` in the Python script.
*   **Android Permission Issues:**
    *   For Android 6.0 (API 23) and above, runtime permissions are required. The app requests these.
    *   For Android 12 (API 31) and above, new Bluetooth permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) are needed. Ensure they are in `AndroidManifest.xml` and handled. `ACCESS_FINE_LOCATION` might also be needed for discovery.
*   **HC-05 RX Pin Voltage:** Remember the HC-05 RX pin is typically 3.3V tolerant. If your Arduino outputs 5V on its TX pin, use a voltage divider before connecting to the HC-05's RX pin to prevent damage.

This README should provide a good starting point for users.
```
