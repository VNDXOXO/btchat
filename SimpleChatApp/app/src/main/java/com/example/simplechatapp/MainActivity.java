// MainActivity.java
package com.example.simplechatapp;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
// import android.view.View; // Not explicitly used, can be removed
// import android.widget.Button; // Not explicitly used due to ViewBinding, can be removed
// import android.widget.EditText; // Not explicitly used due to ViewBinding, can be removed
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
// import androidx.recyclerview.widget.RecyclerView; // Not explicitly used due to ViewBinding, can be removed

import com.example.simplechatapp.databinding.ActivityMainBinding;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;

/**
 * MainActivity is the primary UI controller for the Simple Chat Application.
 * It handles:
 * - User interface setup and interactions (displaying messages, sending messages).
 * - Requesting necessary Bluetooth permissions based on Android version.
 * - Managing the Bluetooth connection lifecycle (enabling Bluetooth, connecting to a device).
 * - Communicating with {@link BluetoothService} to send and receive messages.
 * - Updating the UI based on Bluetooth connection state and received data.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity"; // Tag for logging
    private static final int REQUEST_ENABLE_BT = 1; // Request code for enabling Bluetooth intent
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2; // Request code for runtime Bluetooth permissions

    // Target Bluetooth device names to attempt to auto-connect.
    // These should match the name of your Arduino's Bluetooth module (e.g., HC-05).
    private static final String TARGET_DEVICE_NAME_1 = "HC-05";
    private static final String TARGET_DEVICE_NAME_2 = "ArduinoBT"; // Alternative common name

    private ActivityMainBinding binding; // ViewBinding instance for accessing layout views
    private BluetoothService bluetoothService; // Service for handling Bluetooth communication logic
    private BluetoothAdapter bluetoothAdapter; // Android's system Bluetooth adapter
    private ChatMessageAdapter chatAdapter; // Adapter for the RecyclerView that displays chat messages
    private ArrayList<String> chatMessages; // Data source (list of strings) for chat messages

    private String connectedDeviceName = null; // Stores the name of the currently connected Bluetooth device

    /**
     * Handler associated with the main Looper.
     * Note: In this implementation, {@link LocalBroadcastManager} is the primary method
     * for communication from {@link BluetoothService} to this Activity, making this Handler
     * less critical for that specific path but available for other potential uses.
     */
    private final Handler mainActivityHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            // This handler could be used if BluetoothService sent messages directly to it,
            // e.g., using handler.obtainMessage(MESSAGE_TYPE, object).sendToTarget();
            // For now, it's not actively processing messages from BluetoothService.
        }
    };

    /**
     * BroadcastReceiver to handle intents sent by {@link BluetoothService}.
     * This is the primary mechanism for MainActivity to receive asynchronous updates about:
     * - Bluetooth connection state changes (e.g., connected, disconnected, connecting).
     * - Incoming messages received from the connected Bluetooth device.
     * - The name of the connected device (to update UI).
     * - Toast messages that BluetoothService wants to display.
     */
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                Log.w(TAG, "BroadcastReceiver received an intent with no action.");
                return;
            }

            switch (action) {
                case BluetoothService.ACTION_STATE_CHANGED:
                    int state = intent.getIntExtra(BluetoothService.EXTRA_STATE, BluetoothService.STATE_NONE);
                    handleBluetoothStateChange(state);
                    break;
                case BluetoothService.ACTION_MESSAGE_RECEIVED:
                    String message = intent.getStringExtra(BluetoothService.EXTRA_MESSAGE);
                    if (message != null) {
                        addMessageToChat("Device: " + message); // Prefix to identify sender
                    } else {
                        Log.w(TAG, "Received null message string from BluetoothService.");
                    }
                    break;
                case BluetoothService.ACTION_DEVICE_NAME:
                    connectedDeviceName = intent.getStringExtra(BluetoothService.EXTRA_DEVICE_NAME);
                    Toast.makeText(MainActivity.this, "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setSubtitle("Connected to " + connectedDeviceName);
                    }
                    binding.sendButton.setEnabled(true); // Enable send button now that connection is confirmed
                    break;
                case BluetoothService.ACTION_TOAST:
                    String toastMessage = intent.getStringExtra(BluetoothService.EXTRA_TOAST_MESSAGE);
                    if (toastMessage != null) {
                        Toast.makeText(MainActivity.this, toastMessage, Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    Log.w(TAG, "BroadcastReceiver received unknown action: " + action);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate the layout using ViewBinding for type-safe access to views
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Get the default Bluetooth adapter for the device
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(this, "Bluetooth is not available on this device.", Toast.LENGTH_LONG).show();
            finish(); // Close the app as Bluetooth is essential
            return;
        }

        // Initialize the list and adapter for chat messages displayed in RecyclerView
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatMessageAdapter(chatMessages);
        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(this)); // Use a standard linear layout
        binding.chatRecyclerView.setAdapter(chatAdapter);

        // Set up the click listener for the Send button
        binding.sendButton.setOnClickListener(v -> sendMessage());
        binding.sendButton.setEnabled(false); // Send button is initially disabled; enabled upon successful connection

        // Register the BroadcastReceiver to listen for intents from BluetoothService
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothService.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothService.ACTION_MESSAGE_RECEIVED);
        filter.addAction(BluetoothService.ACTION_DEVICE_NAME);
        filter.addAction(BluetoothService.ACTION_TOAST);
        LocalBroadcastManager.getInstance(this).registerReceiver(bluetoothStateReceiver, filter);

        // Initialize our custom BluetoothService (connection is not initiated here)
        bluetoothService = new BluetoothService(this, mainActivityHandler);

        // Set initial subtitle in the ActionBar (e.g., "Not Connected")
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle("Not Connected");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // When the activity (re)starts, it's crucial to:
        // 1. Check and request necessary Bluetooth permissions.
        // 2. If permissions are granted, ensure Bluetooth is enabled.
        if (!checkAndRequestBluetoothPermissions()) {
            // If permissions are not yet granted, the request process has been initiated.
            // Further actions (like enabling BT or connecting) will typically be triggered
            // from onRequestPermissionsResult or onActivityResult callbacks.
            return; // Wait for permission results
        }
        // If permissions are already granted, proceed to ensure Bluetooth is enabled.
        ensureBluetoothEnabled();
    }

    /**
     * Checks if Bluetooth is enabled on the device. If not, it starts an intent
     * to request the user to enable Bluetooth.
     *
     * For Android 12 (API 31) and above, {@link Manifest.permission#BLUETOOTH_CONNECT}
     * permission is required to programmatically enable Bluetooth or check its state.
     */
    private void ensureBluetoothEnabled() {
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            // For Android 12+, BLUETOOTH_CONNECT permission is needed to request enabling Bluetooth.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission is missing. Cannot request to enable Bluetooth.");
                    Toast.makeText(this, "Bluetooth Connect permission is required to enable Bluetooth.", Toast.LENGTH_LONG).show();
                    // The permission request logic is in checkAndRequestBluetoothPermissions.
                    // If it's missing here, it implies a flow issue or prior denial.
                    // Re-requesting might be an option, or guiding the user to settings.
                    checkAndRequestBluetoothPermissions(); // Attempt to re-request
                    return;
                }
            }
            // Create an Intent to request the user to enable Bluetooth.
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            // Bluetooth is already enabled.
            Log.d(TAG, "Bluetooth is already enabled and permissions are granted.");
            // Optionally, if not connected, one could attempt an auto-connection here:
            // if (bluetoothService != null && bluetoothService.getState() == BluetoothService.STATE_NONE) {
            //    connectToDevice(); // Or trigger UI to allow user to connect
            // }
        }
    }

    /**
     * Checks for necessary Bluetooth permissions based on the Android API level.
     * If permissions are not already granted, it requests them from the user.
     *
     * Permissions required:
     * - For Android 12 (API 31) and above:
     *   - {@link Manifest.permission#BLUETOOTH_SCAN}: For discovering devices (even bonded ones).
     *   - {@link Manifest.permission#BLUETOOTH_CONNECT}: For connecting to paired devices, enabling Bluetooth, and other operations.
     *   - {@link Manifest.permission#ACCESS_FINE_LOCATION}: (Potentially) for more robust BLE scanning or if older BT APIs are used implicitly.
     * - For versions below Android 12:
     *   - {@link Manifest.permission#BLUETOOTH}: For basic Bluetooth operations.
     *   - {@link Manifest.permission#BLUETOOTH_ADMIN}: For device discovery and manipulating Bluetooth settings.
     *   - {@link Manifest.permission#ACCESS_FINE_LOCATION}: Often strictly required for Bluetooth device discovery.
     *
     * @return {@code true} if all required permissions are already granted, {@code false} otherwise (in which case, permission requests are initiated).
     */
    private boolean checkAndRequestBluetoothPermissions() {
        ArrayList<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 (API 31) and newer
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            // ACCESS_FINE_LOCATION can be important for discovering BLE devices.
            // For classic Bluetooth connecting to *already paired* devices, it might not be strictly necessary on S+,
            // but including it provides broader compatibility, especially if discovery features are envisioned.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        } else { // Pre-Android 12 (API 30 and older)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH);
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
            // ACCESS_FINE_LOCATION is generally required for Bluetooth device discovery on these older Android versions.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            // Request the missing permissions.
            // The result will be handled in the onRequestPermissionsResult callback.
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
            return false; // Permissions are being requested, so not all are granted yet.
        }
        return true; // All necessary permissions are already granted.
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     *
     * @param requestCode The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either {@link PackageManager#PERMISSION_GRANTED}
     *     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                    // Consider providing more specific feedback if a critical permission is denied.
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Bluetooth permissions granted.", Toast.LENGTH_SHORT).show();
                // All requested permissions have been granted.
                // Now, proceed to ensure Bluetooth is enabled (if it wasn't already).
                ensureBluetoothEnabled();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required for chat features. Please grant them in app settings.", Toast.LENGTH_LONG).show();
                // Handle the case where one or more permissions are denied by the user.
                // The app's Bluetooth functionality will be limited or non-operational.
                // Consider guiding the user to app settings or disabling relevant UI elements.
            }
        }
    }

    /**
     * Callback for the result from starting an activity for a result.
     * This specifically handles the outcome of the Bluetooth enable request initiated by
     * {@code startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)}.
     *
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode The integer result code returned by the child activity
     *                   through its setResult().
     * @param data An Intent, which can return result data to the caller
     *               (various data can be attached to Intent "extras").
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                // User enabled Bluetooth.
                Toast.makeText(this, "Bluetooth has been enabled.", Toast.LENGTH_SHORT).show();
                // Bluetooth is now enabled. If the desired action was to connect,
                // this might be a good place to trigger it, assuming permissions are also in place.
                // For example:
                // if (bluetoothService != null && bluetoothService.getState() == BluetoothService.STATE_NONE) {
                //    connectToDevice();
                // }
            } else {
                // User did not enable Bluetooth or cancelled the request.
                Toast.makeText(this, "Bluetooth was not enabled. Chat features will be unavailable.", Toast.LENGTH_LONG).show();
                // Handle this scenario appropriately (e.g., disable Bluetooth-dependent UI, inform user, or finish activity).
                // finish(); // Example: Close app if Bluetooth is essential and not enabled.
            }
        }
    }

    /**
     * Initiates a connection to a paired Bluetooth device that matches one of the target names
     * ({@code TARGET_DEVICE_NAME_1} or {@code TARGET_DEVICE_NAME_2}).
     *
     * This method requires appropriate Bluetooth permissions to be granted beforehand:
     * - Android 12 (API 31)+: {@link Manifest.permission#BLUETOOTH_CONNECT} and {@link Manifest.permission#BLUETOOTH_SCAN} (for getBondedDevices).
     * - Pre-Android 12: {@link Manifest.permission#BLUETOOTH} and {@link Manifest.permission#BLUETOOTH_ADMIN}.
     * It's crucial that {@link #checkAndRequestBluetoothPermissions()} and {@link #ensureBluetoothEnabled()}
     * are successfully completed before calling this.
     */
    private void connectToDevice() {
        // Perform permission checks again as a safeguard, although they should be handled by onStart/callbacks.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth Scan and Connect permissions are required to find and connect.", Toast.LENGTH_LONG).show();
                checkAndRequestBluetoothPermissions(); // Attempt to re-request if missing.
                return;
            }
        } else { // Pre-Android 12
             if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Legacy Bluetooth permissions are required to find and connect.", Toast.LENGTH_LONG).show();
                checkAndRequestBluetoothPermissions();
                return;
            }
        }

        // Ensure Bluetooth adapter is available and enabled.
        if (bluetoothAdapter == null) { // Should have been caught in onCreate.
            Toast.makeText(this, "Bluetooth adapter is not available.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "connectToDevice called but bluetoothAdapter is null.");
            return;
        }
        if (!bluetoothAdapter.isEnabled()){ // Should be handled by ensureBluetoothEnabled.
            Toast.makeText(this, "Bluetooth is not enabled. Please enable it first.", Toast.LENGTH_SHORT).show();
            ensureBluetoothEnabled(); // Attempt to enable it again.
            return;
        }

        Set<BluetoothDevice> pairedDevices = null;
        try {
            pairedDevices = bluetoothAdapter.getBondedDevices(); // Requires BLUETOOTH_CONNECT on S+ (indirectly via SCAN for names)
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while getting bonded devices. Permissions might be missing or revoked.", e);
            Toast.makeText(this, "Permission error while fetching paired devices.", Toast.LENGTH_LONG).show();
            checkAndRequestBluetoothPermissions(); // Re-check permissions
            return;
        }

        BluetoothDevice targetDevice = null;
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = null;
                try {
                   // device.getName() requires BLUETOOTH_CONNECT permission on API 31+
                   deviceName = device.getName();
                } catch (SecurityException e){
                    Log.e(TAG, "SecurityException when getting device name for " + device.getAddress() + ". BLUETOOTH_CONNECT permission likely missing.", e);
                    // Continue to next device, or handle error (e.g. by showing address instead of name)
                }

                if (deviceName != null && (deviceName.equalsIgnoreCase(TARGET_DEVICE_NAME_1) || deviceName.equalsIgnoreCase(TARGET_DEVICE_NAME_2))) {
                    targetDevice = device;
                    Log.d(TAG, "Target device found in paired list: " + deviceName + " [" + device.getAddress() + "]");
                    break;
                }
            }
        }

        if (targetDevice != null) {
            if (bluetoothService != null) {
                // Delegate the actual connection attempt to BluetoothService.
                bluetoothService.connect(targetDevice);
            } else {
                Log.e(TAG, "BluetoothService is null, cannot connect.");
                Toast.makeText(this, "Bluetooth service not initialized.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Target device (e.g., HC-05 or ArduinoBT) not found in paired devices. Please pair the device in Android Bluetooth settings first.", Toast.LENGTH_LONG).show();
            // Future enhancement: Allow user to pick from a list of all paired devices or scan for new ones.
        }
    }

    /**
     * Retrieves the message text from the input EditText, sends it via {@link BluetoothService},
     * and then adds the message to the local chat display (RecyclerView).
     * The input field is cleared after sending.
     */
    private void sendMessage() {
        String message = binding.messageInput.getText().toString().trim(); // Get text and remove leading/trailing whitespace.
        if (message.isEmpty()) {
            // Do not send empty messages. Optionally, provide feedback to the user.
            Toast.makeText(this, "Cannot send an empty message.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bluetoothService == null || bluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, "Not connected to a device. Cannot send message.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert the message string to bytes using UTF-8 encoding for transmission.
        byte[] send = message.getBytes(StandardCharsets.UTF_8);
        bluetoothService.write(send); // Delegate the actual sending to BluetoothService.

        addMessageToChat("Me: " + message); // Display the sent message in the local chat UI, prefixed with "Me:".
        binding.messageInput.setText(""); // Clear the input field after the message is sent.
    }

    /**
     * Adds a given message string to the chat display (which is a RecyclerView).
     * It updates the underlying data list, notifies the adapter that new data is available,
     * and scrolls the RecyclerView to show the latest message.
     * @param message The message string to add to the chat.
     */
    private void addMessageToChat(String message) {
        chatMessages.add(message);
        // Notify the adapter that an item has been inserted at the end of the list.
        // This triggers the RecyclerView to update and display the new message.
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        // Scroll the RecyclerView to the position of the newly added message, ensuring it's visible.
        binding.chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
    }

    /**
     * Handles UI updates based on changes in the Bluetooth connection state.
     * For example, it updates the ActionBar subtitle to reflect the current status
     * (e.g., "Not Connected", "Connecting...", "Connected to [DeviceName]")
     * and enables/disables the send button accordingly.
     * @param state The new Bluetooth connection state, as defined by constants in {@link BluetoothService}
     *              (e.g., {@code STATE_NONE}, {@code STATE_CONNECTING}, {@code STATE_CONNECTED}).
     */
    private void handleBluetoothStateChange(int state) {
        if (getSupportActionBar() == null) {
            Log.w(TAG, "SupportActionBar is null, cannot update subtitle for Bluetooth state change.");
            return; // Defensive check
        }

        switch (state) {
            case BluetoothService.STATE_NONE:
                getSupportActionBar().setSubtitle("Not Connected");
                binding.sendButton.setEnabled(false);
                // Consider re-enabling the "Connect" menu item if it was dynamically disabled.
                // invalidateOptionsMenu(); // If menu items are changed based on state.
                break;
            case BluetoothService.STATE_CONNECTING:
                getSupportActionBar().setSubtitle("Connecting...");
                binding.sendButton.setEnabled(false); // Cannot send messages while attempting to connect.
                break;
            case BluetoothService.STATE_CONNECTED:
                // The subtitle is typically updated with the specific device name
                // when the ACTION_DEVICE_NAME broadcast is received.
                // Setting a generic "Connected" here can be a fallback.
                // getSupportActionBar().setSubtitle("Connected");
                binding.sendButton.setEnabled(true); // Enable sending messages now that a connection is established.
                break;
        }
    }

    /**
     * Initializes the contents of the Activity's standard options menu.
     * This is where menu items from {@code R.menu.main_menu} are inflated.
     * @param menu The options menu in which items are placed.
     * @return You must return {@code true} for the menu to be displayed;
     *         if you return {@code false} it will not be shown.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu); // Inflate the menu resource.
        return true;
    }

    /**
     * This hook is called whenever an item in your options menu is selected.
     * It handles actions for "Connect" and "Disconnect" menu items.
     * @param item The menu item that was selected.
     * @return boolean Return {@code false} to allow normal menu processing to
     *         proceed, or {@code true} to consume it here.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId(); // Using int for switch, or if/else for R.id constants.

        if (itemId == R.id.menu_connect) {
            if (bluetoothService != null && bluetoothService.getState() == BluetoothService.STATE_NONE) {
                 // Before attempting to connect, ensure all conditions are met:
                 // 1. Permissions are granted.
                 // 2. Bluetooth is enabled.
                if (checkAndRequestBluetoothPermissions()) { // Returns true if permissions are already granted.
                    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()){
                        connectToDevice(); // Attempt to connect.
                    } else {
                        ensureBluetoothEnabled(); // If BT is off, prompt to enable it.
                        Toast.makeText(this, "Please enable Bluetooth first, then try connecting.", Toast.LENGTH_LONG).show();
                    }
                } else {
                     // Permissions not granted; checkAndRequestBluetoothPermissions() would have started the request.
                     Toast.makeText(this, "Bluetooth permissions are required to connect. Please grant them when prompted.", Toast.LENGTH_LONG).show();
                }
            } else if (bluetoothService != null && bluetoothService.getState() == BluetoothService.STATE_CONNECTED) {
                Toast.makeText(this, "Already connected to " + (connectedDeviceName != null ? connectedDeviceName : "a device") + ".", Toast.LENGTH_SHORT).show();
            } else if (bluetoothService != null && bluetoothService.getState() == BluetoothService.STATE_CONNECTING) {
                Toast.makeText(this, "Connection attempt already in progress.", Toast.LENGTH_SHORT).show();
            }
            return true; // Event consumed.
        } else if (itemId == R.id.menu_disconnect) {
            if (bluetoothService != null && bluetoothService.getState() == BluetoothService.STATE_CONNECTED) {
                bluetoothService.stop(); // Tell BluetoothService to close the current connection.
            } else {
                Toast.makeText(this, "Not currently connected to any device.", Toast.LENGTH_SHORT).show();
            }
            return true; // Event consumed.
        }
        return super.onOptionsItemSelected(item); // Default processing for other unhandled menu items.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the BroadcastReceiver to prevent memory leaks when the activity is destroyed.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(bluetoothStateReceiver);

        // Stop the BluetoothService. This is crucial to close any active Bluetooth connections,
        // release system resources, and halt any running threads within the service.
        if (bluetoothService != null) {
            bluetoothService.stop();
        }
        Log.d(TAG, "--- ON DESTROY --- MainActivity destroyed. Bluetooth resources should be released.");
    }
}
