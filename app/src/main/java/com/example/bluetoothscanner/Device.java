package com.example.bluetoothscanner;

public class Device {
    private final String name;
    private final String address;
    private final short rssi;
    private final int quality;

    public Device(String name, String address, short rssi) {
        this.name = name != null ? name : "Desconhecido";
        this.address = address;
        this.rssi = rssi;
        this.quality = calculateQuality(rssi);
    }

    private int calculateQuality(short rssi) {
        if (rssi <= -100) return 0;
        if (rssi >= -50)  return 100;
        return 2 * (rssi + 100);
    }

    public String getName()    { return name; }
    public String getAddress() { return address; }
    public short  getRssi()    { return rssi; }
    public int    getQuality() { return quality; }
}
