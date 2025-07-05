import serial
import threading
import time

# --- Configuration ---
# Please change this to the correct serial port for your Arduino
# Examples:
# Windows: "COM3", "COM4", etc.
# Linux: "/dev/ttyUSB0", "/dev/ttyACM0", etc.
# macOS: "/dev/cu.usbmodemXXXXX", "/dev/cu.wchusbserialXXXXX", etc.
SERIAL_PORT = "/dev/ttyUSB0"  # <--- !!! CHECK AND CHANGE THIS !!!
BAUD_RATE = 9600  # Must match the computerSerialBaudRate in the Arduino sketch
# --- End Configuration ---

# Global flag to signal threads to stop
stop_thread = False
# Global variable to hold the serial connection object
arduino_serial = None

def read_from_arduino():
    """
    Reads messages from the Arduino via serial and prints them.
    Runs in a separate thread.
    """
    global stop_thread
    global arduino_serial

    while not stop_thread and arduino_serial:
        try:
            if arduino_serial.in_waiting > 0:
                message = arduino_serial.readline().decode('utf-8', errors='replace').strip()
                if message:
                    print(f"Received from Arduino/Android: {message}")
        except serial.SerialException as e:
            print(f"Serial error: {e}")
            stop_thread = True # Stop if serial port error
            break
        except UnicodeDecodeError as e:
            print(f"Unicode decode error: {e}. Raw data: {arduino_serial.readline()}")
        except Exception as e:
            print(f"Error reading from Arduino: {e}")
            # Decide if this error should stop the thread
            time.sleep(0.1) # Avoid busy-looping on continuous errors
        time.sleep(0.05) # Small delay to prevent high CPU usage

def main():
    global stop_thread
    global arduino_serial

    print("Computer-side Serial Chat Client")
    print("--------------------------------")
    print(f"Attempting to connect to Arduino on port: {SERIAL_PORT} at {BAUD_RATE} baud.")
    print("Type your message and press Enter to send to Android app.")
    print("Type 'exit' or 'quit' to close the application.")
    print("--------------------------------\n")

    try:
        arduino_serial = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=1)
        print(f"Successfully connected to {SERIAL_PORT}.")
        # Give Arduino a moment to reset if DTR causes reset
        time.sleep(2)
        # arduino_serial.flushInput() # Clear input buffer
        # arduino_serial.flushOutput() # Clear output buffer

    except serial.SerialException as e:
        print(f"Error: Could not open serial port {SERIAL_PORT}. {e}")
        print("Please ensure the Arduino is connected and you have selected the correct port.")
        print("Common ports:")
        print("  Windows: COM3, COM4, etc.")
        print("  Linux: /dev/ttyUSB0, /dev/ttyACM0, etc.")
        print("  macOS: /dev/cu.usbmodemXXXX, /dev/cu.wchusbserialXXXX")
        return

    # Start the thread for reading messages from Arduino
    read_thread = threading.Thread(target=read_from_arduino, daemon=True)
    read_thread.start()

    # Main loop for user input
    try:
        while not stop_thread:
            try:
                # Get input from the user (blocking call)
                user_input = input()

                if user_input.lower() in ['exit', 'quit']:
                    print("Exiting application...")
                    stop_thread = True
                    break

                if arduino_serial and arduino_serial.is_open:
                    # Send the message to Arduino (append newline as Arduino sketch might expect it)
                    arduino_serial.write(user_input.encode('utf-8') + b'\n')
                    # print(f"Sent to Arduino: {user_input}") # Optional: local echo
                else:
                    print("Serial port not open. Cannot send message.")
                    stop_thread = True # Stop if serial port somehow closed
                    break

            except EOFError: # Happens if stdin is closed (e.g. piped input)
                print("EOF received, exiting...")
                stop_thread = True
                break
            except KeyboardInterrupt: # Handle Ctrl+C
                print("\nCtrl+C detected. Exiting application...")
                stop_thread = True
                break
            except Exception as e:
                print(f"Error during input/send: {e}")
                stop_thread = True # More general error, stop
                break

    finally:
        stop_thread = True # Ensure thread knows to stop
        if read_thread.is_alive():
            read_thread.join(timeout=1) # Wait for the read thread to finish

        if arduino_serial and arduino_serial.is_open:
            print("Closing serial port.")
            arduino_serial.close()
        print("Application terminated.")

if __name__ == "__main__":
    main()
