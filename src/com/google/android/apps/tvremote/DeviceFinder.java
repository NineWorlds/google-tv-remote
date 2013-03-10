/*
 * Copyright (C) 2009 Google Inc.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.tvremote;

import com.google.android.apps.tvremote.util.Debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Device discovery with mDNS.
 */
public final class DeviceFinder extends Activity {
  private static final String LOG_TAG = "DeviceFinder";

  /**
   * Request code used by wifi settings activity
   */
  private static final int CODE_WIFI_SETTINGS = 1;

  private ProgressDialog progressDialog;
  private AlertDialog confirmationDialog;
  private RemoteDevice previousRemoteDevice;
  private List<RemoteDevice> recentlyConnectedDevices;

  private InetAddress broadcastAddress;
  private WifiManager wifiManager;
  private boolean active;

  /**
   * Handles used to pass data back to calling activities.
   */
  public static final String EXTRA_REMOTE_DEVICE = "remote_device";
  public static final String EXTRA_RECENTLY_CONNECTED = "recently_connected";

  public DeviceFinder() {
    dataAdapter = new DeviceFinderListAdapter();
    trackedDevices = new TrackedDevices();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.device_finder_layout);

    previousRemoteDevice =
        getIntent().getParcelableExtra(EXTRA_REMOTE_DEVICE);

    recentlyConnectedDevices =
        getIntent().getParcelableArrayListExtra(EXTRA_RECENTLY_CONNECTED);

    broadcastHandler = new BroadcastHandler();
    wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

    stbList = (ListView) findViewById(R.id.stb_list);
    stbList.setOnItemClickListener(selectHandler);
    stbList.setAdapter(dataAdapter);

    ((Button) findViewById(R.id.button_manual)).setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            buildManualIpDialog().show();
          }
        });
  }

  private void showOtherDevices() {
    broadcastHandler.removeMessages(DELAYED_MESSAGE);
    if (progressDialog.isShowing()) {
      progressDialog.dismiss();
    }
    if (confirmationDialog != null && confirmationDialog.isShowing()) {
      confirmationDialog.dismiss();
    }
    findViewById(R.id.device_finder).setVisibility(View.VISIBLE);
  }

  @Override
  protected void onStart() {
    super.onStart();

    try {
      broadcastAddress = getBroadcastAddress();
    } catch (IOException e) {
      Log.e(LOG_TAG, "Failed to get broadcast address");
      setResult(RESULT_CANCELED, null);
      finish();
    }

    startBroadcast();
  }

  @Override
  protected void onPause() {
    active = false;
    broadcastHandler.removeMessages(DELAYED_MESSAGE);
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    active = true;
  }

  @Override
  protected void onStop() {
    if (null != broadcastClient) {
      broadcastClient.stop();
      broadcastClient = null;
    }

    super.onStop();
  }

  @Override
  protected void onActivityResult(int requestCode,
      int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Log.d(LOG_TAG, "ActivityResult: " + requestCode + ", " + resultCode);
    if (requestCode == CODE_WIFI_SETTINGS) {
      if (!isWifiAvailable()) {
        buildNoWifiDialog().show();
      } else {
        startBroadcast();
      }
    }
  }

  private OnItemClickListener selectHandler = new OnItemClickListener() {
    public void onItemClick(AdapterView<?> parent, View v, int position,
        long id) {
      RemoteDevice remoteDevice = (RemoteDevice) parent.getItemAtPosition(
          position);
      if (remoteDevice != null) {
        connectToEntry(remoteDevice);
      }
    }
  };

  /**
   * Connects to the chosen entry in the list.
   * Finishes the activity and returns the informations on the chosen box.
   *
   * @param remoteDevice the listEntry representing the box you want to connect to
   */
  private void connectToEntry(RemoteDevice remoteDevice) {
    Intent resultIntent = new Intent();
    resultIntent.putExtra(EXTRA_REMOTE_DEVICE, remoteDevice);
    setResult(RESULT_OK, resultIntent);
    finish();
  }

  private void startBroadcast() {
    if (!isWifiAvailable()) {
      buildNoWifiDialog().show();
      return;
    }
    broadcastClient = new BroadcastDiscoveryClient(
        broadcastAddress, broadcastHandler);
    broadcastClientThread = new Thread(broadcastClient);
    broadcastClientThread.start();
    Message message = DelayedMessage.BROADCAST_TIMEOUT
        .obtainMessage(broadcastHandler);
    broadcastHandler.sendMessageDelayed(message,
        getResources().getInteger(R.integer.broadcast_timeout));
    showProgressDialog(buildBroadcastProgressDialog());
  }

  /**
   * Returns an intent that starts this activity.
   */
  public static Intent createConnectIntent(Context ctx,
      RemoteDevice recentlyConnected,
      ArrayList<RemoteDevice> recentlyConnectedList) {
    Intent intent = new Intent(ctx, DeviceFinder.class);
    intent.putExtra(EXTRA_REMOTE_DEVICE, recentlyConnected);
    intent.putParcelableArrayListExtra(EXTRA_RECENTLY_CONNECTED,
        recentlyConnectedList);
    return intent;
  }

  private class DeviceFinderListAdapter extends BaseAdapter {
    public int getCount() {
      return getTotalSize();
    }

    @Override
    public boolean hasStableIds() {
      return false;
    }

    @Override
    public boolean areAllItemsEnabled() {
      return false;
    }

    @Override
    public boolean isEnabled(int position) {
      return position != trackedDevices.size();
    }

    public Object getItem(int position) {
      return getRemoteDevice(position);
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      ListEntryView liv;

      if (position == trackedDevices.size()) {
        return getLayoutInflater().inflate(
            R.layout.device_list_separator_layout, null);
      }

      if (convertView == null || !(convertView instanceof ListEntryView)) {
        liv = (ListEntryView) getLayoutInflater().inflate(
            R.layout.device_list_item_layout, null);
      } else {
        liv = (ListEntryView) convertView;
      }

      liv.setListEntry(getRemoteDevice(position));
      return liv;
    }

    private int getTotalSize() {
      return Debug.isDebugDevices()
          ? trackedDevices.size() + recentlyConnectedDevices.size() + 1
          : trackedDevices.size();
    }

    private RemoteDevice getRemoteDevice(int position) {
      if (position < trackedDevices.size()) {
        return trackedDevices.get(position);
      } else if (Debug.isDebugDevices()) {
        if (position == trackedDevices.size()) {
          return null;
        } else if (position < getTotalSize()) {
          return recentlyConnectedDevices.get(
              position - trackedDevices.size() - 1);
        }
      }
      return null;
    }
  }

  private InetAddress getBroadcastAddress() throws IOException {
    WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    DhcpInfo dhcp = wifi.getDhcpInfo();
    int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
    byte[] quads = new byte[4];
    for (int k = 0; k < 4; k++) {
      quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
    }
    return InetAddress.getByAddress(quads);
  }

  /**
   * Represents an entry in the box list.
   */
  public static class ListEntryView extends LinearLayout {

    public ListEntryView(Context context, AttributeSet attrs) {
      super(context, attrs);
      myContext = context;
    }

    public ListEntryView(Context context) {
      super(context);
      myContext = context;
    }

    @Override
    protected void onFinishInflate() {
      super.onFinishInflate();
      tvName = (TextView) findViewById(R.id.device_list_item_name);
      tvTargetAddr = (TextView) findViewById(R.id.device_list_target_addr);
    }

    private void updateContents() {
      if (null != tvName) {
        String txt = myContext.getString(R.string.unkown_tgt_name);
        if ((null != listEntry) && (null != listEntry.getName())) {
          txt = listEntry.getName();
        }
        tvName.setText(txt);
      }

      if (null != tvTargetAddr) {
        String txt = myContext.getString(R.string.unkown_tgt_addr);
        if ((null != listEntry) && (null != listEntry.getAddress())) {
          txt = listEntry.getAddress().getHostAddress();
        }
        tvTargetAddr.setText(txt);
      }
    }

    public RemoteDevice getListEntry() {
      return listEntry;
    }

    public void setListEntry(RemoteDevice listEntry) {
      this.listEntry = listEntry;
      updateContents();
    }

    private Context myContext = null;
    private RemoteDevice listEntry = null;
    private TextView tvName = null;
    private TextView tvTargetAddr = null;
  }

  private final class BroadcastHandler extends Handler {
    /** {inheritDoc} */
    @Override
    public void handleMessage(Message msg) {
      if (msg.what == DELAYED_MESSAGE) {
        if (!active) {
          return;
        }
        switch ((DelayedMessage) msg.obj) {
          case BROADCAST_TIMEOUT:
            broadcastClient.stop();
            if (progressDialog.isShowing()) {
              progressDialog.dismiss();
            }
            buildBroadcastTimeoutDialog().show();
            break;
  
          case GTV_DEVICE_FOUND:
            // Check if there is previously connected remote and suggest it
            // for connection:
            RemoteDevice toConnect = null;
            if (previousRemoteDevice != null) {
              Log.d(LOG_TAG, "Previous Remote Device: " + previousRemoteDevice);
              toConnect = trackedDevices.findRemoteDevice(previousRemoteDevice);
            }
            if (toConnect == null) {
              Log.d(LOG_TAG, "No previous device found.");
              // No default found - suggest any device
              toConnect = trackedDevices.get(0);
            }
  
            progressDialog.dismiss();
            confirmationDialog = buildConfirmationDialog(toConnect);
            confirmationDialog.show();
            break;
        }
      }

      switch (msg.what) {
        case BROADCAST_RESPONSE:
          BroadcastAdvertisement advert = (BroadcastAdvertisement) msg.obj;
          RemoteDevice remoteDevice = new RemoteDevice(advert.getServiceName(),
              advert.getServiceAddress(), advert.getServicePort());
          handleRemoteDeviceAdd(remoteDevice);
          break;
      }
    }
  }

  private void handleRemoteDeviceAdd(final RemoteDevice remoteDevice) {
    if (trackedDevices.add(remoteDevice)) {
      Log.v(LOG_TAG, "Adding new device: " + remoteDevice);

      // Notify data adapter and update title.
      dataAdapter.notifyDataSetChanged();

      // Show confirmation dialog only for the first STB and only if progress
      // dialog is visible.
      if ((trackedDevices.size() == 1) && progressDialog.isShowing()) {
        broadcastHandler.removeMessages(DELAYED_MESSAGE);
        // delayed automatic adding
        Message message = DelayedMessage.GTV_DEVICE_FOUND
            .obtainMessage(broadcastHandler);
        broadcastHandler.sendMessageDelayed(message,
            getResources().getInteger(R.integer.gtv_finder_reconnect_delay));
      }
    }
  }

  private ProgressDialog buildBroadcastProgressDialog() {
    String message;
    String networkName = getNetworkName();
    if (!TextUtils.isEmpty(networkName)) {
      message = getString(R.string.finder_searching_with_ssid, networkName);
    } else {
      message = getString(R.string.finder_searching);
    }
        
    return buildProgressDialog(message,
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialogInterface, int which) {
        broadcastHandler.removeMessages(DELAYED_MESSAGE);
        showOtherDevices();
      }
    });
  }

  private ProgressDialog buildProgressDialog(String message,
      DialogInterface.OnClickListener cancelListener) {
    ProgressDialog dialog = new ProgressDialog(this);
    dialog.setMessage(message);
    dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
      public boolean onKey(
          DialogInterface dialogInterface, int which, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
          finish();
          return true;
        }
        return false;
      }
    });
    dialog.setButton(getString(R.string.finder_cancel), cancelListener);
    return dialog;
  }

  private AlertDialog buildConfirmationDialog(final RemoteDevice remoteDevice) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View view = LayoutInflater.from(this).inflate(R.layout.device_info, null);
    final TextView ipTextView =
        (TextView) view.findViewById(R.id.device_info_ip_address);

    if (remoteDevice.getName() != null) {
      builder.setMessage(remoteDevice.getName());
    }
    ipTextView.setText(remoteDevice.getAddress().getHostAddress());

    return builder
        .setTitle(R.string.finder_label)
        .setCancelable(false)
        .setPositiveButton(R.string.finder_connect,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialogInterface, int id) {
                connectToEntry(remoteDevice);
              }
            })
        .setNegativeButton(
            R.string.finder_add_other, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialogInterface, int id) {
                showOtherDevices();
              }
            })
        .create();
  }

  private AlertDialog buildManualIpDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View view = LayoutInflater.from(this).inflate(R.layout.manual_ip, null);
    final EditText ipEditText =
        (EditText) view.findViewById(R.id.manual_ip_entry);

    ipEditText.setFilters(new InputFilter[] {
        new NumberKeyListener() {
          @Override
          protected char[] getAcceptedChars() {
            return "0123456789.:".toCharArray();
          }

          public int getInputType() {
            return InputType.TYPE_CLASS_NUMBER;
          }
        }
    });

    builder
        .setPositiveButton(
            R.string.manual_ip_connect, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                RemoteDevice remoteDevice = remoteDeviceFromString(
                    ipEditText.getText().toString());
                if (remoteDevice != null) {
                  connectToEntry(remoteDevice);
                } else {
                  Toast.makeText(DeviceFinder.this,
                      getString(R.string.manual_ip_error_address),
                      Toast.LENGTH_LONG).show();
                }
              }
            })
        .setNegativeButton(
            R.string.manual_ip_cancel, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                // do nothing
              }
            })
        .setCancelable(true)
        .setTitle(R.string.manual_ip_label)
        .setMessage(R.string.manual_ip_entry_label)
        .setView(view);
    return builder.create();
  }

  private AlertDialog buildBroadcastTimeoutDialog() {
    String message;
    String networkName = getNetworkName();
    if (!TextUtils.isEmpty(networkName)) {
      message = getString(R.string.finder_no_devices_with_ssid, networkName);
    } else {
      message = getString(R.string.finder_no_devices);
    }

    return buildTimeoutDialog(message,
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialogInterface, int id) {
        startBroadcast();
      }
    });
  }

  private AlertDialog buildTimeoutDialog(CharSequence message,
      DialogInterface.OnClickListener retryListener) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);

    return builder
        .setMessage(message)
        .setCancelable(false)
        .setPositiveButton(R.string.finder_wait, retryListener)
        .setNegativeButton(
            R.string.finder_cancel, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialogInterface, int id) {
                setResult(RESULT_CANCELED, null);
                finish();
              }
            })
        .create();
  }

  private AlertDialog buildNoWifiDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);

    builder.setMessage(R.string.finder_wifi_not_available);
    builder.setCancelable(false);
    builder.setPositiveButton(R.string.finder_configure,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialogInterface, int id) {
            Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            startActivityForResult(intent, CODE_WIFI_SETTINGS);
          }
      });
    builder.setNegativeButton(
        R.string.finder_cancel, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialogInterface, int id) {
            setResult(RESULT_CANCELED, null);
            finish();
          }
        });
    return builder.create();
  }

  private void showProgressDialog(ProgressDialog newDialog) {
    if ((progressDialog != null) && progressDialog.isShowing()) {
      progressDialog.dismiss();
    }
    progressDialog = newDialog;
    newDialog.show();
  }

  private RemoteDevice remoteDeviceFromString(String text) {
    String[] ipPort = text.split(":");
    int port;
    if (ipPort.length == 1) {
      port = getResources().getInteger(R.integer.manual_default_port);
    } else if (ipPort.length == 2) {
      try {
        port = Integer.parseInt(ipPort[1]);
      } catch (NumberFormatException e) {
        return null;
      }
    } else {
      return null;
    }

    try {
      InetAddress address = InetAddress.getByName(ipPort[0]);
      return new RemoteDevice(
          getString(R.string.manual_ip_default_box_name), address, port);
    } catch (UnknownHostException e) {
    }
    return null;
  }

  private ListView stbList;
  private final DeviceFinderListAdapter dataAdapter;

  private BroadcastHandler broadcastHandler;
  private BroadcastDiscoveryClient broadcastClient;
  private Thread broadcastClientThread;

  private TrackedDevices trackedDevices;

  /**
   * Handler message number for a service update from broadcast client.
   */
  public static final int BROADCAST_RESPONSE = 100;

  /**
   * Handler message number for all delayed messages
   */
  private static final int DELAYED_MESSAGE = 101;

  private enum DelayedMessage {
    BROADCAST_TIMEOUT,
    GTV_DEVICE_FOUND;

    Message obtainMessage(Handler handler) {
      Message message = handler.obtainMessage(DELAYED_MESSAGE);
      message.obj = this;
      return message;
    }
  }

  private static class TrackedDevices implements Iterable<RemoteDevice> {
    private final Map<InetAddress, RemoteDevice> devicesByAddress;
    private final SortedSet<RemoteDevice> devices;
    private RemoteDevice[] deviceArray;

    private static Comparator<RemoteDevice> COMPARATOR =
        new Comparator<RemoteDevice>() {
      public int compare(RemoteDevice remote1, RemoteDevice remote2) {
        int result = remote1.getName().compareToIgnoreCase(remote2.getName());
        if (result != 0) {
          return result;
        }
        return remote1.getAddress().getHostAddress().compareTo(
            remote2.getAddress().getHostAddress());
      }
    };

    TrackedDevices() {
      devicesByAddress = new HashMap<InetAddress, RemoteDevice>();
      devices = new TreeSet<RemoteDevice>(COMPARATOR);
    }

    public boolean add(RemoteDevice remoteDevice) {
      InetAddress address = remoteDevice.getAddress();
      if (!devicesByAddress.containsKey(address)) {
        devicesByAddress.put(address, remoteDevice);
        devices.add(remoteDevice);
        deviceArray = null;
        return true;
      }
      // address?
      return false;
    }

    public int size() {
      return devices.size();
    }

    public RemoteDevice get(int index) {
      return getDeviceArray()[index];
    }

    private RemoteDevice[] getDeviceArray() {
      if (deviceArray == null) {
        deviceArray = devices.toArray(new RemoteDevice[0]);
      }
      return deviceArray;
    }

    public Iterator<RemoteDevice> iterator() {
      return devices.iterator();
    }

    public RemoteDevice findRemoteDevice(RemoteDevice remoteDevice) {
      RemoteDevice byIp = devicesByAddress.get(remoteDevice.getAddress());
      if (byIp != null && byIp.getName().equals(remoteDevice.getName())) {
        return byIp;
      }

      for (RemoteDevice device : devices) {
        Log.d(LOG_TAG, "New device: " + device);
        if (remoteDevice.getName().equals(device.getName())) {
          return device;
        }
      }
      return byIp;
    }
  }

  private boolean isWifiAvailable() {
    if (!wifiManager.isWifiEnabled()) {
      return false;
    }
    WifiInfo info = wifiManager.getConnectionInfo();
    return info != null && info.getIpAddress() != 0;
  }

  private String getNetworkName() {
    if (!isWifiAvailable()) {
      return null;
    }
    WifiInfo info = wifiManager.getConnectionInfo();
    return info != null ? info.getSSID() : null;
  }
}
