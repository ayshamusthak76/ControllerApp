package com.example.controller.serial;

import java.util.List;


public interface SerialPortCallback {
    void onSerialPortsDetected(List<com.example.controller.serial.UsbSerialDevice> serialPorts);
}
