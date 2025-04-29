package com.example.bluetoothscanner;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.*;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 2;
    private static final long SCAN_INTERVAL_MS = 500;

    private BluetoothAdapter bluetoothAdapter;
    private DeviceAdapter adapter;
    private final List<Device> devices = new ArrayList<>();
    private final Map<String, Device> deviceMap = new HashMap<>();
    private Handler scanHandler;
    private Runnable scanRunnable;
    private boolean isScanning = false;

    private Button btnHost, btnClient, btnScan;
    private ListView listView;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // UI references
        btnHost   = findViewById(R.id.btnHost);
        btnClient = findViewById(R.id.btnClient);
        btnScan   = findViewById(R.id.btnScan);
        listView  = findViewById(R.id.listDevices);

        // Device list & adapter
        adapter = new DeviceAdapter(this, devices);
        listView.setAdapter(adapter);

        // 1) Host button: start server mode immediately
        btnHost.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, ChatActivity.class);
            i.putExtra("mode", "server");
            startActivity(i);
        });

        // 2) Client button: show scan UI
        btnClient.setOnClickListener(v -> {
            btnHost.setEnabled(false);
            btnClient.setEnabled(false);
            btnScan.setVisibility(View.VISIBLE);
            listView.setVisibility(View.VISIBLE);
            requestPermissionsIfNeeded();
        });

        // Scan button: start/stop discovery
        btnScan.setOnClickListener(v -> {
            if (!isScanning) startScan();
            else stopScan();
        });
        btnScan.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);

        // Click on a device → open client ChatActivity
        listView.setOnItemClickListener((p, v, pos, id) -> {
            Device sel = devices.get(pos);
            Intent i = new Intent(MainActivity.this, ChatActivity.class);
            i.putExtra("mode", "client");
            i.putExtra("device_address", sel.getAddress());
            startActivity(i);
        });

        // Prepare discovery handler
        scanHandler = new Handler(Looper.getMainLooper());
        scanRunnable = () -> {
            if (canScan()) {
                if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
                bluetoothAdapter.startDiscovery();
            }
            scanHandler.postDelayed(scanRunnable, SCAN_INTERVAL_MS);
        };

        // Receiver for found devices
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent it) {
                if (BluetoothDevice.ACTION_FOUND.equals(it.getAction())) {
                    BluetoothDevice bt = it.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    short rssi = it.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                    String name = bt.getName() != null ? bt.getName() : "Desconhecido";
                    Device d = new Device(name, bt.getAddress(), rssi);
                    deviceMap.put(d.getAddress(), d);
                    devices.clear();
                    devices.addAll(deviceMap.values());
                    adapter.notifyDataSetChanged();
                }
            }
        };
        registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    }

    /** Ask for Bluetooth & (on Android ≤11) location permissions */
// 1) REQUEST PERMISSIONS METHOD
    private void requestPermissionsIfNeeded() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            // Android 10 & 11
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    needed.toArray(new String[0]),
                    REQUEST_PERMISSIONS
            );
        } else {
            // All needed permissions already granted
            btnScan.setEnabled(true);
        }
    }

    // 2) HANDLE THE RESULT
    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) return;

        boolean allGranted = true;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            btnScan.setEnabled(true);
        } else {
            // Build a user-friendly message depending on OS version
            String msg;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                msg = "É preciso permitir BLUETOOTH_SCAN e BLUETOOTH_CONNECT para escanear";
            } else {
                msg = "É preciso permitir LOCALIZAÇÃO para escanear no Android ≤11";
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }

    /** Return true only if we have permission to startDiscovery() */
    private boolean canScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void startScan() {
        devices.clear();
        deviceMap.clear();
        adapter.notifyDataSetChanged();
        isScanning = true;
        btnScan.setText("Parar Scan");
        scanHandler.post(scanRunnable);
    }

    private void stopScan() {
        isScanning = false;
        btnScan.setText("Iniciar Scan");
        scanHandler.removeCallbacks(scanRunnable);
        if (canScan() && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        scanHandler.removeCallbacks(scanRunnable);
    }
}
