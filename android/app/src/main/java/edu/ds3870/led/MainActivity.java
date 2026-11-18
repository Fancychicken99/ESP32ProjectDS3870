package edu.ds3870.led;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.UUID;

// Connect checks runtime permissions before starting any Bluetooth operation.
@SuppressLint("MissingPermission")
public class MainActivity extends Activity {
    private static final UUID SERVICE = UUID.fromString("ac4a9f3c-6c4a-4f33-aaae-b650f17d6390");
    private static final UUID LED = UUID.fromString("ac4a9f3c-6c4a-4f33-aaae-b650f17d6391");
    private static final String[] NEARBY_PERMISSIONS = {
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
    };
    private static final String[] LEGACY_PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable connectTimeout = () -> reset("Board did not respond. Try connecting again.");
    private final Runnable updateTimeout = () -> reset("LED update timed out. Reconnect to check its state.");
    private BluetoothLeScanner scanner;
    private ScanCallback scan;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic led;
    private TextView status;
    private Button connect;
    private Button toggle;
    private boolean ledOn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        status = new TextView(this);
        status.setTextSize(22);
        connect = new Button(this);
        toggle = new Button(this);
        layout.addView(status);
        layout.addView(connect);
        layout.addView(toggle);
        setContentView(layout);
        connect.setOnClickListener(view -> {
            if (gatt != null) {
                reset("Disconnected");
            } else {
                connectToBoard();
            }
        });
        toggle.setOnClickListener(view -> writeLed());
        reset("Disconnected");
    }

    private boolean hasPermissions() {
        for (String permission : requiredPermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] requiredPermissions() {
        return Build.VERSION.SDK_INT >= 31 ? NEARBY_PERMISSIONS : LEGACY_PERMISSIONS;
    }

    private void connectToBoard() {
        if (!hasPermissions()) {
            requestPermissions(requiredPermissions(), 1);
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            reset("Turn on Bluetooth, then tap Connect.");
            return;
        }
        if (Build.VERSION.SDK_INT < 31
                && !getSystemService(LocationManager.class).isLocationEnabled()) {
            reset("Turn on Location in Settings, then tap Connect.");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            reset("Bluetooth scanning is unavailable.");
            return;
        }
        status.setText("Looking for DS3870 LED…");
        connect.setEnabled(false);
        scan = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                main.post(() -> {
                    if (scan != this) {
                        return;
                    }
                    stopScan();
                    status.setText("Connecting…");
                    gatt = result.getDevice().connectGatt(MainActivity.this, false, callbacks);
                    if (gatt == null) {
                        reset("Could not start the connection. Try again.");
                    }
                });
            }

            @Override
            public void onScanFailed(int errorCode) {
                main.post(() -> {
                    if (scan == this) {
                        reset("Bluetooth scan failed (" + errorCode + ").");
                    }
                });
            }
        };
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE)).build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        main.postDelayed(connectTimeout, 20000);
        scanner.startScan(Collections.singletonList(filter), settings, scan);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 1) {
            if (hasPermissions()) {
                connectToBoard();
            } else {
                reset(Build.VERSION.SDK_INT >= 31
                        ? "Allow Nearby Devices access in Settings to connect."
                        : "Allow Location access in Settings to scan for the board.");
            }
        }
    }

    private final BluetoothGattCallback callbacks = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt connection, int result, int state) {
            main.post(() -> {
                if (gatt != connection) {
                    return;
                }
                if (result == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                    if (!connection.discoverServices()) {
                        reset("Could not discover the LED service.");
                    }
                } else {
                    reset("Disconnected. Tap Connect to retry.");
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt connection, int result) {
            main.post(() -> {
                if (gatt != connection) {
                    return;
                }
                BluetoothGattService service = connection.getService(SERVICE);
                led = service == null ? null : service.getCharacteristic(LED);
                if (result != BluetoothGatt.GATT_SUCCESS || led == null
                        || !connection.readCharacteristic(led)) {
                    reset("Could not read the LED state.");
                }
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt connection, BluetoothGattCharacteristic characteristic,
                                         byte[] value, int result) {
            handleLedRead(connection, value, result);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onCharacteristicRead(BluetoothGatt connection, BluetoothGattCharacteristic characteristic,
                                         int result) {
            byte[] value = characteristic.getValue();
            handleLedRead(connection, value == null ? null : value.clone(), result);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt connection, BluetoothGattCharacteristic characteristic,
                                          int result) {
            main.post(() -> {
                if (gatt == connection && (result != BluetoothGatt.GATT_SUCCESS
                        || !connection.readCharacteristic(led))) {
                    reset("Could not verify the LED update. Reconnect to check its state.");
                }
            });
        }
    };

    private void handleLedRead(BluetoothGatt connection, byte[] value, int result) {
        main.post(() -> {
            if (gatt != connection) {
                return;
            }
            if (result != BluetoothGatt.GATT_SUCCESS || value == null || value.length != 1
                    || (value[0] != 0 && value[0] != 1)) {
                reset("Could not read the LED state.");
                return;
            }
            main.removeCallbacks(connectTimeout);
            main.removeCallbacks(updateTimeout);
            ledOn = value[0] == 1;
            status.setText(ledOn ? "Connected · LED on" : "Connected · LED off");
            connect.setText("Disconnect");
            connect.setEnabled(true);
            toggle.setText(ledOn ? "Turn LED off" : "Turn LED on");
            toggle.setEnabled(true);
        });
    }

    @SuppressWarnings("deprecation")
    private void writeLed() {
        byte[] value = {(byte) (ledOn ? 0 : 1)};
        status.setText("Updating LED…");
        toggle.setEnabled(false);
        main.postDelayed(updateTimeout, 8000);
        boolean started;
        if (Build.VERSION.SDK_INT >= 33) {
            started = gatt.writeCharacteristic(led, value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0;
        } else {
            led.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            led.setValue(value);
            started = gatt.writeCharacteristic(led);
        }
        if (!started) {
            reset("Could not send the LED update. Try reconnecting.");
        }
    }

    private void stopScan() {
        if (scan != null) {
            ScanCallback activeScan = scan;
            scan = null;
            scanner.stopScan(activeScan);
            scanner = null;
        }
    }

    private void reset(String message) {
        main.removeCallbacks(connectTimeout);
        main.removeCallbacks(updateTimeout);
        stopScan();
        if (gatt != null) {
            BluetoothGatt connection = gatt;
            gatt = null;
            connection.disconnect();
            connection.close();
        }
        led = null;
        status.setText(message);
        connect.setText("Connect");
        connect.setEnabled(true);
        toggle.setText("Turn LED on");
        toggle.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        reset("Disconnected");
        super.onDestroy();
    }
}
