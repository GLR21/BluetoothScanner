package com.example.bluetoothscanner;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

public class DeviceAdapter extends ArrayAdapter<Device> {
    public DeviceAdapter(Context ctx, List<Device> devices) {
        super(ctx, 0, devices);
    }

    @Override
    public View getView(int pos, View cv, ViewGroup parent) {
        if (cv == null) {
            cv = LayoutInflater.from(getContext())
                    .inflate(R.layout.row_device, parent, false);
        }
        Device d = getItem(pos);
        ((TextView)cv.findViewById(R.id.tvName)).setText(d.getName());
        ((TextView)cv.findViewById(R.id.tvAddress)).setText(d.getAddress());
        ((TextView)cv.findViewById(R.id.tvRssi)).setText(d.getRssi() + " dBm");
        ((ProgressBar)cv.findViewById(R.id.signalBar)).setProgress(d.getQuality());
        return cv;
    }
}
