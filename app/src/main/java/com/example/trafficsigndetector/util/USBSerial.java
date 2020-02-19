package com.example.trafficsigndetector.util;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class USBSerial {
    private String TAG = "USBSerial";

    private UsbManager usbManager;
    private UsbSerialPort serialPort;
    private List<UsbSerialDriver> drivers;
    private SerialInputOutputManager serialIoManager;
    private SerialInputOutputManager.Listener serialListener;
    private ExecutorService executorService;
    private Context context;

    public USBSerial(Context context, SerialInputOutputManager.Listener serialListener) {
        this.context = context;
        this.serialListener = serialListener;
    }

    public void initUsbSerial() {

        // 1.查找设备
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (drivers.size() <= 0) {
            Toast.makeText(context, "无串口设备", Toast.LENGTH_SHORT).show();
            return;
        } else {
            Toast.makeText(context, "找到 " + drivers.size() + " 个串口设备", Toast.LENGTH_SHORT).show();
        }

        UsbDevice device = drivers.get(0).getDevice();

        if (usbManager.hasPermission(device)) {
            permissionAllow(device);
        } else {
            Log.e("TAG", "没有权限");
            UsbPermissionActionReceiver mUsbPermissionActionReceiver = new UsbPermissionActionReceiver();
            Intent intent = new Intent(ACTION_USB_PERMISSION);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
            IntentFilter intentFilter = new IntentFilter(ACTION_USB_PERMISSION);
            context.registerReceiver(mUsbPermissionActionReceiver, intentFilter);
            usbManager.requestPermission(device, pendingIntent);
        }

    }

    private void permissionAllow(UsbDevice device) {

        List<UsbSerialPort> result = new ArrayList<>();

        for (final UsbSerialDriver driver : drivers) {
            final List<UsbSerialPort> ports = driver.getPorts();
            result.addAll(ports);
        }

        UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(device);

        try {
            serialPort = result.get(0);
            serialPort.open(usbDeviceConnection);
            serialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            serialIoManager = new SerialInputOutputManager(serialPort, serialListener);
            executorService = Executors.newSingleThreadExecutor();
            executorService.submit(serialIoManager);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final String ACTION_USB_PERMISSION = "android.hardware.usb.action.USB_DEVICE_ATTACHED";

    private class UsbPermissionActionReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // user choose YES for your previously popup window asking for grant perssion for this usb device
                        if (null != usbDevice) {
                            permissionAllow(usbDevice);
                        }
                    } else {
                        //user choose NO for your previously popup window asking for grant perssion for this usb device
                        Toast.makeText(context, "Permission denied for device" + usbDevice, Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    public void close() throws IOException {
        if (serialPort != null) {
            serialPort.close();
        }

        if (serialIoManager != null) {
            serialIoManager.stop();
        }

        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public void sendMsg(String msg) {
        try {
            serialPort.write(msg.getBytes(), 150);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnect() {
        return serialPort != null;
    }

}
