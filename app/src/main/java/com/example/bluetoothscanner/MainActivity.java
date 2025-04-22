package com.example.bluetoothscanner;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ListView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private static final long SCAN_INTERVAL_MS = 5000;

    private BluetoothAdapter bluetoothAdapter;
    private DeviceAdapter adapter;
    private final List<Device> devices = new ArrayList<>();
    private final HashMap<String, Device> deviceMap = new HashMap<>();
    private Handler scanHandler;
    private Runnable scanRunnable;

    private Button btnScan;
    private boolean isScanning = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent it) {
            String action = it.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice bt = it.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short rssi = it.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                    bt.fetchUuidsWithSdp();
                }
                updateDevice(bt, rssi);

            } else if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
                BluetoothDevice bt = it.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String newName = it.getStringExtra(BluetoothDevice.EXTRA_NAME);
                short lastRssi = deviceMap.containsKey(bt.getAddress()) ? deviceMap.get(bt.getAddress()).getRssi() : Short.MIN_VALUE;
                Device updated = new Device(newName, bt.getAddress(), lastRssi);
                deviceMap.put(bt.getAddress(), updated);
                refreshList();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        adapter = new DeviceAdapter(this, devices);
        ListView listView = findViewById(R.id.listDevices);
        listView.setAdapter(adapter);

        btnScan = findViewById(R.id.btnScan);
        btnScan.setEnabled(false);
        btnScan.setOnClickListener(v -> {
            if (!isScanning) startScanCycle();
            else stopScanCycle();
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        registerReceiver(receiver, filter);

        // Inicializa o Handler
        scanHandler = new Handler(Looper.getMainLooper());
        // Define o Runnable após scanHandler estar inicializado
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN)
                        == PackageManager.PERMISSION_GRANTED) {
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                    bluetoothAdapter.startDiscovery();
                }
                scanHandler.postDelayed(this, SCAN_INTERVAL_MS);
            }
        };

        String[] perms = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
        };
        if (!hasAll(perms)) {
            ActivityCompat.requestPermissions(this, perms, REQUEST_PERMISSIONS);
        } else {
            btnScan.setEnabled(true);
        }
    }

    private boolean hasAll(String[] perms) {
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startScanCycle() {
        deviceMap.clear();
        devices.clear();
        adapter.notifyDataSetChanged();
        isScanning = true;
        btnScan.setText("Parar Scan");
        scanHandler.post(scanRunnable);
    }

    private void stopScanCycle() {
        isScanning = false;
        btnScan.setText("Iniciar Scan");
        scanHandler.removeCallbacks(scanRunnable);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    private void updateDevice(BluetoothDevice bt, short rssi) {
        String name;
        try {
            name = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED ? bt.getName() : null;
        } catch (SecurityException e) {
            name = null;
        }
        Device device = new Device(name, bt.getAddress(), rssi);
        deviceMap.put(bt.getAddress(), device);
        refreshList();
    }

    private void refreshList() {
        devices.clear();
        devices.addAll(deviceMap.values());
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == REQUEST_PERMISSIONS) {
            boolean ok = true;
            for (int rr : r) if (rr != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) btnScan.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter != null && scanHandler != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            scanHandler.removeCallbacks(scanRunnable);
        }
        unregisterReceiver(receiver);
    }
}