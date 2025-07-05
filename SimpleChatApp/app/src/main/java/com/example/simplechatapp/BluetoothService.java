package com.example.simplechatapp;

import android.Manifest;
// import android.app.Activity; // Not directly used, can be removed
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler; // Keep if used by constructor, though MainActivity's handler isn't used by this service for now
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
// import java.util.Set; // Not directly used, can be removed
import java.util.UUID;

/**
 * BluetoothService is responsible for managing Bluetooth connections and data transfer.
 * It handles the lifecycle of a Bluetooth connection (initiating, connecting, managing active connection)
 * for Classic Bluetooth (SPP - Serial Port Profile).
 * <p>
 * This service communicates with the UI (typically {@link MainActivity}) using {@link LocalBroadcastManager}
 * to send updates about connection state, received messages, and errors.
 * <p>
 * Key functionalities:
 * - Establishing an outgoing connection to a paired Bluetooth device.
 * - Managing data transmission (sending and receiving) over an active Bluetooth socket.
 * - Reporting connection status changes and errors.
 * - Operating with two main threads:
 *   - {@link ConnectThread}: For initiating a connection.
 *   - {@link ConnectedThread}: For managing an active connection (reading/writing data).
 */
public class BluetoothService {
    private static final String TAG = "BluetoothService";

    // Standard UUID for Serial Port Profile (SPP)
    // This UUID is well-known and commonly used for serial communication over Bluetooth.
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // --- Broadcast Actions and Extras ---
    // These constants define the actions and extra data keys for intents broadcasted by this service.

    /**
     * Broadcast Action: Indicates a change in the Bluetooth connection state.
     * Associated Extra: {@link #EXTRA_STATE} (int) - The new connection state.
     */
    public static final String ACTION_STATE_CHANGED = "com.example.simplechatapp.ACTION_STATE_CHANGED";
    /** Extra for {@link #ACTION_STATE_CHANGED}: Integer representing the new connection state.
     *  Possible values: {@link #STATE_NONE}, {@link #STATE_CONNECTING}, {@link #STATE_CONNECTED}. */
    public static final String EXTRA_STATE = "com.example.simplechatapp.EXTRA_STATE";

    /**
     * Broadcast Action: Indicates that a message has been received from the connected device.
     * Associated Extra: {@link #EXTRA_MESSAGE} (String) - The content of the received message.
     */
    public static final String ACTION_MESSAGE_RECEIVED = "com.example.simplechatapp.ACTION_MESSAGE_RECEIVED";
    /** Extra for {@link #ACTION_MESSAGE_RECEIVED}: String containing the message data. */
    public static final String EXTRA_MESSAGE = "com.example.simplechatapp.EXTRA_MESSAGE";

    /**
     * Broadcast Action: Sends the name of the connected device to the UI.
     * Associated Extra: {@link #EXTRA_DEVICE_NAME} (String) - The name of the connected Bluetooth device.
     */
    public static final String ACTION_DEVICE_NAME = "com.example.simplechatapp.ACTION_DEVICE_NAME";
    /** Extra for {@link #ACTION_DEVICE_NAME}: String containing the device name. */
    public static final String EXTRA_DEVICE_NAME = "com.example.simplechatapp.EXTRA_DEVICE_NAME";

    /**
     * Broadcast Action: Requests the UI to display a toast message.
     * Associated Extra: {@link #EXTRA_TOAST_MESSAGE} (String) - The message to be displayed in the toast.
     */
    public static final String ACTION_TOAST = "com.example.simplechatapp.ACTION_TOAST";
    /** Extra for {@link #ACTION_TOAST}: String containing the toast message. */
    public static final String EXTRA_TOAST_MESSAGE = "com.example.simplechatapp.EXTRA_TOAST_MESSAGE";


    // --- Connection States ---
    // These constants represent the possible states of the Bluetooth connection.
    /** State: No active connection or connection attempt. */
    public static final int STATE_NONE = 0;
    /** State: Currently attempting to initiate an outgoing connection. */
    public static final int STATE_CONNECTING = 2;
    /** State: Actively connected to a remote Bluetooth device. */
    public static final int STATE_CONNECTED = 3;

    private final BluetoothAdapter bluetoothAdapter; // Android's system Bluetooth adapter.
    private ConnectThread connectThread;       // Thread for initiating a connection.
    private ConnectedThread connectedThread;     // Thread for managing an active connection.
    private int currentState;                  // Current connection state.
    // private final Handler handler; // Handler for communication (less used here due to LocalBroadcastManager)
    private final Context context;             // Context, typically the application context or calling Activity.

    /**
     * Constructor for BluetoothService.
     * @param context The context of the caller (e.g., an Activity or Application).
     * @param handler A handler for sending messages back to the UI thread (though LocalBroadcastManager is primary).
     */
    public BluetoothService(Context context, Handler handler) {
        this.context = context;
        // this.handler = handler; // Store if direct Handler messages were to be used more.
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.currentState = STATE_NONE; // Initial state is no connection.
    }

    /**
     * Sets the current Bluetooth connection state and broadcasts the change.
     * This method is synchronized to ensure thread-safe updates to {@code currentState}.
     * @param state The new connection state (e.g., {@link #STATE_NONE}, {@link #STATE_CONNECTING}, {@link #STATE_CONNECTED}).
     */
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + currentState + " -> " + state);
        currentState = state;

        // Broadcast the new state to any listeners (e.g., MainActivity).
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra(EXTRA_STATE, state);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /**
     * Returns the current Bluetooth connection state.
     * This method is synchronized for thread-safe access to {@code currentState}.
     * @return The current connection state.
     */
    public synchronized int getState() {
        return currentState;
    }

    /**
     * Initiates a connection attempt to a specified Bluetooth device.
     * This method is synchronized. It cancels any existing connection attempts or active connections
     * before starting a new {@link ConnectThread}.
     * @param device The {@link BluetoothDevice} to connect to.
     */
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "Attempting to connect to device: " + device.getName() + " [" + device.getAddress() + "]");

        // Cancel any thread attempting to make a connection
        if (currentState == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING); // Update state to reflect connection attempt
    }

    /**
     * Called when a {@link ConnectThread} successfully establishes a connection.
     * This method is synchronized. It cancels the {@link ConnectThread} and starts a
     * {@link ConnectedThread} to manage the now active connection.
     * It also broadcasts the name of the connected device.
     * @param socket The {@link BluetoothSocket} for the established connection.
     * @param device The {@link BluetoothDevice} that has been connected.
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "Successfully connected to device: " + device.getName());

        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection (shouldn't be one, but defensive)
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Intent nameIntent = new Intent(ACTION_DEVICE_NAME);
        // BLUETOOTH_CONNECT permission check before device.getName() is good practice,
        // though it should be available if connection was successful.
        String deviceName = "Unknown Device";
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                deviceName = device.getName();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException getting device name in connected(): " + e.getMessage());
        }
        nameIntent.putExtra(EXTRA_DEVICE_NAME, deviceName);
        LocalBroadcastManager.getInstance(context).sendBroadcast(nameIntent);

        setState(STATE_CONNECTED); // Update state to reflect active connection
    }

    /**
     * Stops all Bluetooth-related threads (ConnectThread and ConnectedThread),
     * effectively terminating any ongoing connection attempt or active connection.
     * Sets the state to {@link #STATE_NONE}. This method is synchronized.
     */
    public synchronized void stop() {
        Log.d(TAG, "Stopping Bluetooth service threads and connection.");
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        setState(STATE_NONE); // No active connection
    }

    /**
     * Writes data to the {@link ConnectedThread} for transmission over Bluetooth.
     * This is an unsynchronized public method that internally synchronizes access
     * to the {@code connectedThread} to ensure thread safety.
     * @param out The byte array containing data to be sent.
     */
    public void write(byte[] out) {
        ConnectedThread r; // Temporary reference to ConnectedThread
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (currentState != STATE_CONNECTED) {
                Log.w(TAG, "Write called but not connected. Message not sent.");
                return; // Cannot write if not connected
            }
            r = connectedThread; // Get the active connection thread
        }
        // Perform the write operation outside the synchronized block to avoid holding lock during I/O
        r.write(out);
    }

    /**
     * Handles the failure of a connection attempt (from {@link ConnectThread}).
     * Sends a toast message indicating failure and stops the service (resets state).
     */
    private void connectionFailed() {
        Log.e(TAG, "Bluetooth connection attempt failed.");
        sendToast("Unable to connect to the device.");
        BluetoothService.this.stop(); // Reset to STATE_NONE
    }

    /**
     * Handles the loss of an active connection (from {@link ConnectedThread}).
     * Sends a toast message indicating connection loss and stops the service (resets state).
     */
    private void connectionLost() {
        Log.e(TAG, "Bluetooth connection was lost.");
        sendToast("Device connection was lost.");
        BluetoothService.this.stop(); // Reset to STATE_NONE
    }

    /**
     * Helper method to send a toast message string to the UI via broadcast.
     * @param message The string message to display in the toast.
     */
    private void sendToast(String message) {
        Intent intent = new Intent(ACTION_TOAST);
        intent.putExtra(EXTRA_TOAST_MESSAGE, message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /**
     * {@link ConnectThread} is responsible for initiating an outgoing Bluetooth connection
     * to a remote device. It attempts to create and connect a {@link BluetoothSocket}.
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket; // Socket for the connection
        private final BluetoothDevice mmDevice; // Device to connect to

        /**
         * Constructor for ConnectThread.
         * @param device The {@link BluetoothDevice} to which a connection attempt will be made.
         */
        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                // BLUETOOTH_CONNECT permission is required to create a socket on Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "BLUETOOTH_CONNECT permission not granted. Cannot create RFCOMM socket in ConnectThread.");
                        sendToast("Bluetooth Connect permission denied. Cannot initiate connection.");
                        // mmSocket will remain null, run() method will handle this.
                        return;
                    }
                }
                // Get a BluetoothSocket to connect with the given BluetoothDevice
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed in ConnectThread", e);
                sendToast("Failed to create connection socket.");
            } catch (SecurityException se) {
                // This can happen if BLUETOOTH_CONNECT is missing on S+
                Log.e(TAG, "SecurityException on socket create(): " + se.getMessage());
                sendToast("Permission error creating socket.");
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN ConnectThread for device: " + mmDevice.getName());
            setName("ConnectThread-" + mmDevice.getAddress()); // Set thread name for debugging

            // Always cancel discovery because it will slow down a connection
            // BLUETOOTH_SCAN permission is required for discovery operations on Android 12+
            try {
                if (bluetoothAdapter.isDiscovering()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                            Log.w(TAG, "BLUETOOTH_SCAN permission not granted. Cannot cancel discovery. Connection might be slow.");
                            sendToast("Scan permission needed to optimize connection. Connection might be slow.");
                            // Proceeding without cancelling discovery if permission is missing.
                        } else {
                            bluetoothAdapter.cancelDiscovery();
                        }
                    } else { // Pre-Android 12, BLUETOOTH_ADMIN was needed
                         if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                            bluetoothAdapter.cancelDiscovery();
                         } else {
                            Log.w(TAG, "BLUETOOTH_ADMIN permission not granted. Cannot cancel discovery. Connection might be slow.");
                         }
                    }
                }
            } catch (SecurityException se) {
                 Log.e(TAG, "SecurityException cancelling discovery: " + se.getMessage());
            }

            if (mmSocket == null) {
                Log.e(TAG, "mmSocket is null in ConnectThread.run(), connection cannot proceed.");
                // This typically means socket creation failed due to permissions or other issues.
                connectionFailed();
                return;
            }

            try {
                // Connect the device through the socket. This is a blocking call.
                // BLUETOOTH_CONNECT permission is required here on Android 12+
                mmSocket.connect();
            } catch (IOException connectException) {
                Log.e(TAG, "Unable to connect socket in ConnectThread for device " + mmDevice.getName(), connectException);
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Unable to close() socket during connection failure", closeException);
                }
                connectionFailed(); // Notify UI about failure
                return;
            } catch (SecurityException se) {
                 Log.e(TAG, "SecurityException on mmSocket.connect(): " + se.getMessage());
                 sendToast("Permission error during connection attempt.");
                 connectionFailed();
                 return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                connectThread = null;
            }

            // Start the ConnectedThread to manage the now active connection
            connected(mmSocket, mmDevice);
        }

        /**
         * Cancels the connection attempt by closing the {@link BluetoothSocket}.
         * This should be called to abort an ongoing connection attempt or when cleaning up.
         */
        public void cancel() {
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to close() connect socket in ConnectThread.cancel()", e);
            }
        }
    }

    /**
     * {@link ConnectedThread} is responsible for maintaining an active Bluetooth connection,
     * managing data transmission (sending and receiving), and monitoring for connection loss.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;   // Socket for the active connection.
        private final InputStream mmInStream;   // Stream for reading incoming data.
        private final OutputStream mmOutStream; // Stream for writing outgoing data.

        /**
         * Constructor for ConnectedThread.
         * @param socket The {@link BluetoothSocket} representing the active connection.
         */
        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "Creating ConnectedThread.");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Temp sockets not created in ConnectedThread constructor", e);
                // This is a critical failure; if streams aren't obtained, the thread is useless.
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN ConnectedThread");
            setName("ConnectedThread"); // Set thread name for debugging
            byte[] buffer = new byte[1024]; // Buffer store for the stream
            int bytes;                      // Bytes returned from read()

            // Keep listening to the InputStream while connected
            while (currentState == STATE_CONNECTED) {
                if (mmInStream == null) {
                    Log.e(TAG, "Input stream is null in ConnectedThread.run(), cannot read. Connection lost.");
                    connectionLost();
                    break;
                }
                try {
                    // Read from the InputStream. This is a blocking call.
                    bytes = mmInStream.read(buffer);
                    // Construct a string from the valid bytes in the buffer
                    String readMessage = new String(buffer, 0, bytes);

                    // Send the obtained bytes/message to the UI Activity via broadcast
                    Intent intent = new Intent(ACTION_MESSAGE_RECEIVED);
                    intent.putExtra(EXTRA_MESSAGE, readMessage);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                    // Log.d(TAG, "Message received: " + readMessage);

                } catch (IOException e) {
                    Log.e(TAG, "IOException during read in ConnectedThread. Connection likely lost.", e);
                    connectionLost(); // Notify UI about connection loss
                    break; // Exit loop as connection is broken
                }
            }
            Log.i(TAG, "END ConnectedThread, state is no longer STATE_CONNECTED or stream error.");
        }

        /**
         * Writes the given byte array to the {@link OutputStream} for transmission.
         * @param buffer The bytes to write.
         */
        public void write(byte[] buffer) {
            if (mmOutStream == null) {
                Log.e(TAG, "Output stream is null in ConnectedThread.write(). Cannot send data.");
                // Optionally, could try to signal connectionLost() here if this state persists.
                return;
            }
            try {
                mmOutStream.write(buffer);
                Log.d(TAG, "Message sent: " + new String(buffer)); // Log sent message
                // Optional: Share the sent message back to the UI Activity if needed for display
                // handler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write in ConnectedThread", e);
                // Consider if this should trigger connectionLost() as well.
                // For robust handling, repeated write failures might indicate a lost connection.
            }
        }

        /**
         * Shuts down the connection by closing the {@link BluetoothSocket}.
         * This should be called to terminate the active connection or when cleaning up.
         */
        public void cancel() {
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to close() socket in ConnectedThread.cancel()", e);
            }
        }
    }
}
