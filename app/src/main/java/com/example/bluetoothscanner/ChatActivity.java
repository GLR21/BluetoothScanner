package com.example.bluetoothscanner;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.*;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class ChatActivity extends Activity {
    private static final UUID APP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String KEY_PROMPTED = "prompted";

    private boolean prompted = false;

    private TextView tvChat;
    private EditText etMessage;
    private Button btnSend;

    private ChatService chatService;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_chat);

        tvChat    = findViewById(R.id.tvChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend   = findViewById(R.id.btnSend);

        // Restore whether we already prompted
        if (saved != null) {
            prompted = saved.getBoolean(KEY_PROMPTED, false);
        }

        String mode = getIntent().getStringExtra("mode");
        if ("server".equals(mode)) {
            if (!prompted) {
                promptDiscoverable();
                new ServerThread().start();
                appendLine("Waiting for incoming connection…");
                prompted = true;
            }
        } else {
            String addr = getIntent().getStringExtra("device_address");
            new ClientThread(BluetoothAdapter.getDefaultAdapter()
                    .getRemoteDevice(addr)).start();
        }

        btnSend.setOnClickListener(v -> {
            String m = etMessage.getText().toString().trim();
            if (!m.isEmpty() && chatService != null) {
                chatService.write(m.getBytes());
                appendLine("Me: " + m);
                etMessage.setText("");
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(KEY_PROMPTED, prompted);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // No-op: prevents Activity restart on rotation
    }

    private void promptDiscoverable() {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        if (bt.getScanMode()
                != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(i);
        }
    }

    private void appendLine(String l) {
        runOnUiThread(() -> tvChat.append(l + "\n"));
    }

    // ServerThread & ClientThread unchanged from your previous code
    private class ServerThread extends Thread {
        private BluetoothServerSocket serverSocket;
        ServerThread() {
            try {
                serverSocket = BluetoothAdapter.getDefaultAdapter()
                        .listenUsingRfcommWithServiceRecord("MyChat", APP_UUID);
            } catch (IOException e) { e.printStackTrace(); }
        }
        @Override public void run() {
            try {
                BluetoothSocket sock = serverSocket.accept();
                runOnUiThread(() -> appendLine("Client connected"));
                chatService = new ChatService(sock);
                chatService.start();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try { serverSocket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private class ClientThread extends Thread {
        private final android.bluetooth.BluetoothDevice device;
        ClientThread(android.bluetooth.BluetoothDevice d) { device = d; }
        @Override public void run() {
            try {
                BluetoothSocket socket =
                        device.createRfcommSocketToServiceRecord(APP_UUID);
                if (ActivityCompat.checkSelfPermission(
                        ChatActivity.this,
                        "android.permission.BLUETOOTH_CONNECT")
                        != PackageManager.PERMISSION_GRANTED) {
                    // handle if needed
                }
                socket.connect();
                runOnUiThread(() -> appendLine("Connected to server"));
                chatService = new ChatService(socket);
                chatService.start();
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        appendLine("Connect failed: " + e.getMessage()));
            }
        }
    }

    private class ChatService extends Thread {
        private final BluetoothSocket mmSocket;
        private InputStream in;
        private OutputStream out;
        private boolean running = true;

        ChatService(BluetoothSocket sock) {
            mmSocket = sock;
            try {
                in  = sock.getInputStream();
                out = sock.getOutputStream();
            } catch (IOException e) { e.printStackTrace(); }
        }

        @Override public void run() {
            byte[] buf = new byte[1024];
            int len;
            while (running) {
                try {
                    len = in.read(buf);
                    if (len > 0) {
                        String msg = new String(buf, 0, len);
                        appendLine("Remote: " + msg);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        void write(byte[] b) {
            try { out.write(b); }
            catch (IOException e) { e.printStackTrace(); }
        }

        void cancel() {
            running = false;
            try { mmSocket.close(); }
            catch (IOException e) { e.printStackTrace(); }
        }
    }
}
