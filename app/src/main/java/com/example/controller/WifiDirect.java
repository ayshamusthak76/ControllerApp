package com.example.controller;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.InetAddresses;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WifiDirect extends AppCompatActivity {
    TextView connectionStatus, messageTextView;
    Button aSwitch, discoverButton, sendButton;
    ListView listView;
    EditText typeMsg;
    WifiP2pManager manager;
    WifiP2pManager.Channel channel;

    BroadcastReceiver receiver;
    IntentFilter intentFilter;

    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    String[] deviceNameArray;
    WifiP2pDevice[] deviceArray;

    Socket socket;

    ServerClass serverClass;
    ClientClass clientClass;
    Boolean isHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_direct);

        initialWork();
        expListener();
    }

    private void expListener() {
        aSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                startActivityForResult(intent, 1);
            }
        });

        discoverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityResultLauncher<Object> requestPermissionLauncher = null;
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                    return;
                }
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Discovery Started");
                    }

                    @Override
                    public void onFailure(int i) {
                        connectionStatus.setText("Discovery not started");
                    }
                });
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final WifiP2pDevice device = deviceArray[i];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityResultLauncher<Object> requestPermissionLauncher = null;
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                    return;
                }
                manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Connected" + device.deviceAddress);
                    }

                    @Override
                    public void onFailure(int i) {
                        connectionStatus.setText("Not connected");
                    }
                });
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                String msg = typeMsg.getText().toString();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if(msg!=null && isHost){
                            serverClass.write(msg.getBytes());
                        }
                        else if(msg!=null && !isHost){
                            clientClass.write(msg.getBytes());
                        }
                    }
                });
            }
        });
    }

    private void initialWork() {
        aSwitch = (Button) findViewById(R.id.onOff);
        discoverButton = (Button) findViewById(R.id.discover);
        sendButton = (Button) findViewById(R.id.sendButton);
        listView = (ListView) findViewById(R.id.peerListView);
        messageTextView = (TextView) findViewById(R.id.readMsg);
        connectionStatus  = (TextView) findViewById(R.id.connectionStatus);
        typeMsg = (EditText) findViewById(R.id.writeMsg);


        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this,getMainLooper(),null);
        receiver = new WifiDirectBroadcastReceiver(manager,channel,this);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

    }

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
            if(!wifiP2pDeviceList.equals(peers)){
                peers.clear();
                peers.addAll(wifiP2pDeviceList.getDeviceList());

                deviceNameArray = new String[wifiP2pDeviceList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[wifiP2pDeviceList.getDeviceList().size()];
                int index = 0;

                for(WifiP2pDevice device : wifiP2pDeviceList.getDeviceList()){
                    deviceNameArray[index]=device.deviceName;
                    deviceArray[index]=device;
                    index++;
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1, deviceNameArray);
                listView.setAdapter(adapter);

                if(peers.size()==0){
                    connectionStatus.setText("No device found");
                    return;
                }
            }
        }
    };

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
            if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner){
                connectionStatus.setText("Host");
                isHost = true;
                serverClass = new ServerClass();
                serverClass.start();
            }
            else if(wifiP2pInfo.groupFormed){
                connectionStatus.setText("Client");
                isHost = false;
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();
            }
        }
    };
    @Override
    protected void onResume(){
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }
    public class ServerClass extends Thread{
        ServerSocket serverSocket;
        private InputStream inputStream;
        private  OutputStream outputStream;

        public  void write(byte[] bytes){
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(8888);
                socket = serverSocket.accept();
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[1024];
                    int bytes;

                    while(socket!=null){
                        try {
                            bytes = inputStream.read(buffer);
                            if(bytes>0){
                                int finalBytes = bytes;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        String tempMsg = new String(buffer,0,finalBytes);
                                        messageTextView.setText(tempMsg);
                                    }
                                });
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }
    public class ClientClass extends Thread{
        String hostAdd;
        private InputStream inputStream;
        private OutputStream outputStream;

        public ClientClass(InetAddress hostAddress) {
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();

        }
        public  void write(byte[] bytes){
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd, 8888), 500);
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[1024];
                    int bytes;

                    while(socket!=null){
                        try {
                            bytes = inputStream.read(buffer);
                            if(bytes>0){
                                int finalBytes = bytes;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        String tempMsg = new String(buffer,0,finalBytes);
                                        messageTextView.setText(tempMsg);
                                    }
                                });
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }
}
//    Button btnOnOff, btnDiscover, btnSend;
//    ListView listView;
//    TextView read_msg_box, connectionStatus;
//    EditText writeMsg;
//
//    WifiManager wifiManager;
//    WifiP2pManager mManager;
//    WifiP2pManager.Channel mChannel;
//
//    BroadcastReceiver mReceiver;
//    IntentFilter mIntentFilter;
//
//    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
//    String[] deviceNameArray;
//    WifiP2pDevice[] deviceArray;
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_wifi_direct);
//        initialWork();
//
//        exqListener();
//    }
//
//    private void exqListener() {
//        btnOnOff.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if (wifiManager.isWifiEnabled()) {
//                    wifiManager.setWifiEnabled(false);
//                    btnOnOff.setText("ON");
//                } else {
//                    wifiManager.setWifiEnabled(true);
//                    btnOnOff.setText("OFF");
//                }
//            }
//        });
//        btnDiscover.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//
//
//                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                    // TODO: Consider calling
//                    //    ActivityCompat#requestPermissions
//                    ActivityResultLauncher<Object> requestPermissionLauncher = null;
//                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
//                    return;
//                }
//                mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
//                    @Override
//                    public void onSuccess() {
//                        connectionStatus.setText("Discovery Started");
//                    }
//
//                    @Override
//                    public void onFailure(int i) {
//                        connectionStatus.setText("Discovery start Failed");
//                    }
//                });
//            }
//        });
//    }
//
//
//
//    private void initialWork(){
//        btnOnOff = (Button) findViewById(R.id.onOff);
//        btnDiscover = (Button) findViewById(R.id.discover);
//        btnSend = (Button) findViewById(R.id.sendButton);
//        listView = (ListView) findViewById(R.id.peerListView);
//        read_msg_box = (TextView) findViewById(R.id.readMsg);
//        connectionStatus  = (TextView) findViewById(R.id.connectionStatus);
//        writeMsg = (EditText) findViewById(R.id.writeMsg);
//
//        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
//
//        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
//        mChannel = mManager.initialize(this,getMainLooper(),null);
//
//        mReceiver = new WifiDirectBroadcastReceiver(mManager,mChannel,this);
//
//        mIntentFilter = new IntentFilter();
//        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
//        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
//        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
//        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
//    }
//
//    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
//        @Override
//        public void onPeersAvailable(WifiP2pDeviceList peerList) {
//            if(!peerList.getDeviceList().equals(peers)){
//                peers.clear();
//                peers.addAll(peerList.getDeviceList());
//
//                deviceNameArray = new String[peerList.getDeviceList().size()];
//                deviceArray = new WifiP2pDevice[peerList.getDeviceList().size()];
//                int index = 0;
//
//                for(WifiP2pDevice device : peerList.getDeviceList()){
//                    deviceNameArray[index]=device.deviceName;
//                    deviceArray[index]=device;
//                    index++;
//                }
//                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1, deviceNameArray);
//                listView.setAdapter(adapter);
//
//                if(peers.size()==0){
//                    Toast.makeText(getApplicationContext(),"No Device found", Toast.LENGTH_SHORT).show();
//                }
//            }
//        }
//    };
//    @Override
//    protected void onResume(){
//        super.onResume();
//        registerReceiver(mReceiver, mIntentFilter);
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        unregisterReceiver(mReceiver);
//    }
//}