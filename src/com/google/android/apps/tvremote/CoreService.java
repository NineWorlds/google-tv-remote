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

import com.google.android.apps.tvremote.protocol.AnymoteSender;
import com.google.android.apps.tvremote.protocol.DummySender;
import com.google.android.apps.tvremote.util.Debug;
import com.google.android.apps.tvremote.util.LimitedLinkedHashMap;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

/**
 * The central point to connect to a remote box and send commands.
 *
 */
public final class CoreService extends Service implements ConnectionManager {

  private static final String LOG_TAG = "TvRemoteCoreService";

  /**
   * Connection status enumeration.
   */
  public enum ConnectionStatus {
    /**
     * Connection successful.
     */
    OK,
    /**
     * Error while creating socket or establishing connection.
     */
    ERROR_CREATE,
    /**
     * Error during SSL handshake.
     */
    ERROR_HANDSHAKE
  }

  private ConnectionListener connectionListener;

  private Socket sendSocket;

  private RemoteDevice target;

  private LimitedLinkedHashMap<InetAddress, RemoteDevice> recentlyConnected;

  /**
   * Key store manager.
   */
  private KeyStoreManager keyStoreManager;

  private Handler handler;

  private ConnectionTask connectionTask;

  private static final Map<State, Set<State>> ALLOWED_TRANSITION
      = allowedTransitions();

  private enum State {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DEVICE_FINDER,
    PAIRING
  }

  /**
   * Various tags used to store the service's configuration.
   */
  private static final String SHARED_PREF_NAME = "CoreServicePrefs";
  private static final String DEVICE_NAME_TAG = "DeviceName";
  private static final String DEVICE_IP_TAG = "DeviceIp";
  private static final String DEVICE_PORT_TAG = "DevicePort";

  /**
   * Notable values for ports, ip addresses and target names.
   */
  private static final int MIN_PORT = 0;
  private static final int MAX_PORT = 0xFFFF;
  private static final int INVALID_PORT = -1;
  private static final String INVALID_IP = "no#ip";
  private static final String INVALID_TARGET = "no#target";

  /**
   * Timeout when creating a socket.
   */
  private static int SOCKET_CREATION_TIMEOUT_MS = 300;

  /**
   * Timeout when reconnecting.
   */
  private static final int RECONNECTION_DELAY_MS = 1000;

  private static final int MAX_CONNECTION_ATTEMPTS = 3;

  /**
   * Sender that uses the Anymote protocol.
   */
  private AnymoteSender anymoteSender;

  public CoreService() {
    target = null;
    sendSocket = null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    handler = new Handler(new ConnectionRequestCallback());

    recentlyConnected = new LimitedLinkedHashMap<InetAddress, RemoteDevice>(
        getResources().getInteger(R.integer.recently_connected_count));

    keyStoreManager = new KeyStoreManager(this);
    loadConfig();
  }

  @Override
  public void onDestroy() {
    storeConfig();
    cleanupSocket();
    if (keyStoreManager != null) {
      keyStoreManager.store();
    }
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return new LocalBinder();
  }

  /**
   * Determines whether a port number is valid.
   *
   * @param     port    an integer representing the port number
   * @return    {@code true} if the number falls within the range of valid ports
   */
  private static boolean isPortValid(int port) {
    return port > MIN_PORT && port < MAX_PORT;
  }

  /**
   * Validates a connection configuration.
   *
   * @param     name    a string representing the name of the target
   * @param     ip      a string representing the ip of the target
   * @param     port    an integer representing the target's remote port
   * @return    {@code true} if the configuration is valid
   */
  private static boolean isConfigValid(String name, String ip, int port) {
    return !INVALID_TARGET.equals(name)
        && !INVALID_IP.equals(ip)
        && isPortValid(port);
  }

  private void cleanupSocket() {
    if (sendSocket == null) {
      return;
    }
    Log.i(LOG_TAG, "Closing connection to " + sendSocket.getInetAddress() +
        ":" + sendSocket.getPort());
    if (anymoteSender != null) {
      anymoteSender.disconnect();
      anymoteSender = null;
    }
    try {
      sendSocket.close();
    } catch (IOException e) {
      Log.e(LOG_TAG, "failed to close socket");
    }
    sendSocket = null;
  }

  /**
   * Stores the service's configuration to saved preferences.
   *
   * @return      {@code true} if the config was saved
   */
  private boolean storeConfig() {
    SharedPreferences pref
        = getSharedPreferences(SHARED_PREF_NAME, MODE_PRIVATE);
    SharedPreferences.Editor prefEdit = pref.edit();
    prefEdit.clear();

    if (target != null) {
      storeRemoteDevice(prefEdit, "", target);
    }
    int index = 0;
    for (RemoteDevice remoteDevice : recentlyConnected.values()) {
      storeRemoteDevice(prefEdit, "_" + index, remoteDevice);
      ++index;
    }
    if (target != null || index > 0) {
      prefEdit.commit();
      return true;
    }
    return false;
  }

  private void storeRemoteDevice(SharedPreferences.Editor prefEdit,
      String suffix, RemoteDevice remoteDevice) {
    prefEdit.putString(DEVICE_NAME_TAG + suffix, remoteDevice.getName());
    prefEdit.putString(DEVICE_IP_TAG + suffix,
        remoteDevice.getAddress().getHostAddress());
    prefEdit.putInt(DEVICE_PORT_TAG + suffix, remoteDevice.getPort());
  }

  /**
   * Loads an existing configuration, and builds the socket to the target.
   */
  private void loadConfig() {
    SharedPreferences pref
        = getSharedPreferences(SHARED_PREF_NAME, MODE_PRIVATE);

    RemoteDevice restoredTarget = loadRemoteDevice(pref, "");

    for (int i = 0; i < getResources()
        .getInteger(R.integer.recently_connected_count); ++i) {
      RemoteDevice remoteDevice = loadRemoteDevice(pref, "_" + i);
      if (remoteDevice != null) {
        recentlyConnected.put(remoteDevice.getAddress(), remoteDevice);
      }
    }

    if (restoredTarget != null) {
      setTarget(restoredTarget);
    }
  }

  private RemoteDevice loadRemoteDevice(SharedPreferences pref, String suffix) {
    String name = pref.getString(DEVICE_NAME_TAG + suffix, INVALID_TARGET);
    String ip = pref.getString(DEVICE_IP_TAG + suffix, INVALID_IP);
    int port = pref.getInt(DEVICE_PORT_TAG + suffix, INVALID_PORT);
    if (!isConfigValid(name, ip, port)) {
      return null;
    }
    InetAddress address;
    try {
      address = InetAddress.getByName(ip);
    } catch (UnknownHostException e) {
      return null;
    }
    return new RemoteDevice(name, address, port);
  }

  /**
   * Enables in-process access to this service.
   */
  final class LocalBinder extends Binder {
    CoreService getService() {
      return CoreService.this;
    }
  }

  public KeyStoreManager getKeyStoreManager() {
    return keyStoreManager;
  }

  private void addRecentlyConnected(RemoteDevice remoteDevice) {
    recentlyConnected.remove(remoteDevice.getAddress());
    recentlyConnected.put(remoteDevice.getAddress(), remoteDevice);
    storeConfig();
  }

  // CONNECTION MANAGER

  private enum Request {
    CONNECT,
    CONNECTED,
    SET_TARGET,
    DISCONNECT,
    CONNECTION_ERROR,
    SET_KEEP_CONNECTED,
    REQUEST_PAIRING,
    PAIRING_FINISHED,
    REQUEST_DEVICE_FINDER,
    DEVICE_FINDER_FINISHED,
  }

  public void notifyConnectionFailed() {
    sendMessage(Request.CONNECTION_ERROR, null);
  }

  public void connect(ConnectionListener listener) {
    sendMessage(Request.CONNECT, listener);
  }

  public void connected(ConnectionResult result) {
    sendMessage(Request.CONNECTED, result);
  }

  public void disconnect(ConnectionListener listener) {
    sendMessage(Request.DISCONNECT, listener);
  }

  public void setKeepConnected(boolean keepConnected) {
    sendMessage(Request.SET_KEEP_CONNECTED, Boolean.valueOf(keepConnected));
  }

  public void setTarget(RemoteDevice remoteDevice) {
    sendMessage(Request.SET_TARGET, remoteDevice);
  }

  public RemoteDevice getTarget() {
    return target;
  }

  public ArrayList<RemoteDevice> getRecentlyConnected() {
    ArrayList<RemoteDevice> devices = new ArrayList<RemoteDevice>(
        recentlyConnected.values());
    Collections.reverse(devices);
    return devices;
  }

  public void pairingFinished() {
    sendMessage(Request.PAIRING_FINISHED, null);
  }

  public void deviceFinderFinished() {
    sendMessage(Request.DEVICE_FINDER_FINISHED, null);
  }

  public void requestDeviceFinder() {
    sendMessage(Request.REQUEST_DEVICE_FINDER, null);
  }

  private void requestPairing() {
    sendMessage(Request.REQUEST_PAIRING, null);
  }

  private void sendMessage(Request request, Object obj) {
    Message msg = handler.obtainMessage(request.ordinal());
    msg.obj = obj;
    handler.dispatchMessage(msg);
  }

  private class ConnectionRequestCallback implements Handler.Callback {
    private int keepConnectedRefcount;
    private State currentState = State.IDLE;
    private boolean pendingNotification;

    private boolean changeState(State newState) {
      return changeState(newState, null);
    }

    private boolean changeState(State newState, Runnable callback) {
      if (isTransitionLegal(currentState, newState)) {
        if (Debug.isDebugConnection()) {
          Log.d(LOG_TAG, "Changing state: " + currentState + " -> " + newState);
        }
        currentState = newState;
        if (callback != null) {
          callback.run();
        }
        sendNotification();
        return true;
      }
      if (Debug.isDebugConnection()) {
        Log.d(LOG_TAG, "Illegal transition: " + currentState + " -> "
            + newState);
      }
      return false;
    }

    public boolean handleMessage(Message msg) {
      Request request = Request.values()[msg.what];
      Log.v(LOG_TAG, "handleMessage:" + request + " (" + msg.obj + ")");
      switch (request) {
        case CONNECT:
          handleConnect((ConnectionListener) msg.obj);
          return true;

        case CONNECTED:
          connectionTask = null;
          handleConnected((ConnectionResult) msg.obj);
          return true;

        case DISCONNECT:
          handleDisconnect((ConnectionListener) msg.obj);
          return true;

        case SET_TARGET:
          handleSetTarget((RemoteDevice) msg.obj);
          return true;

        case CONNECTION_ERROR:
          handleConnectionError();
          return true;

        case SET_KEEP_CONNECTED:
          handleSetKeepConnected((Boolean) msg.obj);
          return true;

        case REQUEST_DEVICE_FINDER:
          handleRequestDeviceFinder();
          return true;

        case REQUEST_PAIRING:
          handleRequestPairing();
          return true;

        case PAIRING_FINISHED:
          changeState(State.IDLE);
          return true;

        case DEVICE_FINDER_FINISHED:
          changeState(State.IDLE);
          return true;
      }
      return false;
    }

    private boolean isConnected() {
      return Debug.isDebugConnectionLess() || sendSocket != null;
    }

    private boolean isConnecting() {
      return State.CONNECTING.equals(currentState);
    }

    private void handleConnectionError() {
      if (changeState(State.DISCONNECTING)) {
        cleanupSocket();
      }
      if (changeState(State.CONNECTING)) {
        connect();
      }
    }

    private void handleConnect(ConnectionListener listener) {
      handleSetKeepConnected(true);

      if (listener != connectionListener) {
        connectionListener = listener;
        if (pendingNotification) {
          sendNotification();
        } else if (isConnecting() || isConnected()) {
          sendNotification();
        }
      }

      if (target != null && changeState(State.CONNECTING)) {
        connect();
      } else if (target == null) {
        changeState(State.DEVICE_FINDER);
      }
    }

    private void handleRequestDeviceFinder() {
      stopConnectionTask();
      disconnect(true);
      changeState(State.DEVICE_FINDER);
    }

    private void handleRequestPairing() {
      stopConnectionTask();
      changeState(State.PAIRING);
    }

    private void handleConnected(final ConnectionResult result) {
      stopConnectionTask();
      if (sendSocket != null) {
        throw new IllegalStateException();
      }
      changeState(State.CONNECTED, new Runnable() {
        public void run() {
          addRecentlyConnected(target);
          anymoteSender = result.sender;
          sendSocket = result.socket;
        }
      });
    }

    private void handleDisconnect(ConnectionListener listener) {
      handleSetKeepConnected(false);
      if (listener == connectionListener) {
        connectionListener = null;
      }
    }

    private void handleSetKeepConnected(boolean keepConnected) {
      keepConnectedRefcount += keepConnected ? 1 : -1;
      if (Debug.isDebugConnection()) {
        Log.d(LOG_TAG, "KeepConnectedRefcount: " + keepConnectedRefcount);
      }
      if (keepConnectedRefcount < 0) {
        throw new IllegalStateException("KeepConnectedRefCount < 0");
      }
      if (connectionListener == null) {
        disconnect(false);
      }
    }

    private void handleSetTarget(RemoteDevice remoteDevice) {
      disconnect(true);
      target = remoteDevice;
      if (target != null && changeState(State.CONNECTING)) {
        connect();
      }
    }

    private void disconnect(boolean unconditionally) {
      if (unconditionally || keepConnectedRefcount == 0) {
        if (isConnected()) {
          changeState(State.DISCONNECTING);
          cleanupSocket();
          changeState(State.IDLE);
        } else if (isConnecting()) {
          changeState(State.DISCONNECTING);
          stopConnectionTask();
          changeState(State.IDLE);
        }
      }
    }

    private void connect() {
      if (Debug.isDebugConnection()) {
        Log.d(LOG_TAG, "Connecting to: " + target);
      }
      if (sendSocket != null) {
        throw new IllegalStateException("Already connected");
      }
      if (target == null) {
        changeState(State.DEVICE_FINDER);
        return;
      }
      startConnectionTask(target);
    }

    private void sendNotification() {
      if (connectionListener == null) {
        pendingNotification = true;
        if (Debug.isDebugConnection()) {
          Log.d(LOG_TAG, "Pending notification: " + currentState);
        }
        return;
      }
      pendingNotification = false;
      if (Debug.isDebugConnection()) {
        Log.d(LOG_TAG, "Sending notification: " + currentState + " to "
            + connectionListener);
      }
      switch (currentState) {
        case IDLE:
          break;

        case CONNECTING:
          connectionListener.onConnecting();
          break;

        case CONNECTED:
          connectionListener.onConnectionSuccessful(
              Debug.isDebugConnectionLess()
              ? new DummySender() : anymoteSender);
          break;

        case DISCONNECTING:
          connectionListener.onDisconnected();
          break;

        case DEVICE_FINDER:
          connectionListener.onShowDeviceFinder();
          break;

        case PAIRING:
          if (target != null) {
            connectionListener.onNeedsPairing(target);
          } else {
            connectionListener.onShowDeviceFinder();
          }
          break;

        default:
          throw new IllegalStateException("Unsupported state: " + currentState);
      }
    }
  }

  private void startConnectionTask(RemoteDevice remoteDevice) {
    stopConnectionTask();
    connectionTask = new ConnectionTask(this);
    connectionTask.execute(remoteDevice);
  }

  private void stopConnectionTask() {
    if (connectionTask != null) {
      connectionTask.cancel(true);
      connectionTask = null;
    }
  }

  private static class ConnectionResult {
    final ConnectionStatus status;
    final AnymoteSender sender;
    final Socket socket;

    private ConnectionResult(ConnectionStatus status, AnymoteSender sender, Socket socket) {
      this.status = status;
      this.sender = sender;
      this.socket = socket;
    }
  }

  private static class ConnectionTask extends AsyncTask<RemoteDevice, Void, ConnectionResult> {

    private final CoreService coreService;
    private AnymoteSender sender;
    private Socket socket;

    private ConnectionTask(CoreService coreService) {
      this.coreService = coreService;
    }

    @Override
    protected ConnectionResult doInBackground(RemoteDevice... params) {
      if (params.length != 1) {
        throw new IllegalStateException("Expected exactly one remote device");
      }
      for (int i = 0; i <= MAX_CONNECTION_ATTEMPTS; ++i) {
        try {
          Thread.sleep(RECONNECTION_DELAY_MS * i);
        } catch (InterruptedException e) {
          return null;
        }

        sender = null;
        socket = null;

        if (isCancelled()) {
          return null;
        }
        ConnectionStatus status = buildSocket(params[0]);
        if (isCancelled()) {
          return null;
        }
        switch (status) {
          case OK:
            return new ConnectionResult(status, sender, socket);

          case ERROR_HANDSHAKE:
            return new ConnectionResult(status, null, null);

          case ERROR_CREATE:
            // try to reconnect
            break;

          default:
            throw new IllegalStateException("Unsupported status: " + status);
        }
      }
      return new ConnectionResult(ConnectionStatus.ERROR_CREATE, null, null);
    }

    private ConnectionStatus buildSocket(RemoteDevice target) {
      if (target == null) {
        throw new IllegalStateException();
      }

      // Set up the new connection.
      try {
        socket = getSslSocket(target);
      } catch (SSLException e) {
        Log.e(LOG_TAG, "(SSL) Could not create socket to " + target, e);
        return ConnectionStatus.ERROR_HANDSHAKE;
      } catch (GeneralSecurityException e) {
        Log.e(LOG_TAG, "(GSE) Could not create socket to " + target, e);
        return ConnectionStatus.ERROR_HANDSHAKE;
      } catch (IOException e) {
        // Hack for Froyo which throws IOException for SSL handshake problem:
        if (e.getMessage().startsWith("SSL handshake")) {
          return ConnectionStatus.ERROR_HANDSHAKE;
        }
        Log.e(LOG_TAG, "(IOE) Could not create socket to " + target, e);
        return ConnectionStatus.ERROR_CREATE;
      }
      Log.i(LOG_TAG, "Connected to " + target);
      if (isCancelled()) {
        return ConnectionStatus.ERROR_CREATE;
      }
      sender = new AnymoteSender(coreService);
      if (!sender.setSocket(socket)) {
        Log.e(LOG_TAG, "Initial message failed");
        sender.disconnect();
        try {
          socket.close();
        } catch (IOException e) {
          Log.e(LOG_TAG, "failed to close socket");
        }
        return ConnectionStatus.ERROR_CREATE;
      }

      // Connection successful - we need to reset connection attempts counter,
      // so next time the connection will drop we will try reconnecting.
      return ConnectionStatus.OK;
    }

    /**
     * Generates an SSL-enabled socket.
     *
     * @return the new socket
     * @throws GeneralSecurityException on error building the socket
     * @throws IOException on error loading the KeyStore
     */
    private SSLSocket getSslSocket(RemoteDevice target)
        throws GeneralSecurityException, IOException {
      // Build a new key store based on the key store manager.
      KeyManager[] keyManagers = coreService.getKeyStoreManager()
          .getKeyManagers();
      TrustManager[] trustManagers = coreService.getKeyStoreManager()
          .getTrustManagers();

      if (keyManagers.length == 0) {
        throw new IllegalStateException("No key managers");
      }

      // Create a new SSLContext, using the new KeyManagers and TrustManagers
      // as the sources of keys and trust decisions, respectively.
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(keyManagers, trustManagers, null);

      // Finally, build a new SSLSocketFactory from the SSLContext, and
      // then generate a new SSLSocket from it.
      SSLSocketFactory factory = sslContext.getSocketFactory();
      SSLSocket sock = (SSLSocket) factory.createSocket();
      sock.setNeedClientAuth(true);
      sock.setUseClientMode(true);
      sock.setKeepAlive(true);
      sock.setTcpNoDelay(true);

      InetSocketAddress fullAddr =
          new InetSocketAddress(target.getAddress(), target.getPort());
      sock.connect(fullAddr, SOCKET_CREATION_TIMEOUT_MS);
      sock.startHandshake();

      return sock;
    }

    // Notifications

    @Override
    protected void onCancelled() {
      super.onCancelled();
    }

    @Override
    protected void onPostExecute(ConnectionResult result) {
      super.onPostExecute(result);
      switch (result.status) {
        case OK:
          coreService.connected(result);
          break;
        case ERROR_CREATE:
          coreService.requestDeviceFinder();
          break;
        case ERROR_HANDSHAKE:
          coreService.requestPairing();
          break;
      }
    }
  }

  // State transition management

  private static Map<State, Set<State>> allowedTransitions() {
    Map<State, Set<State>> allowedTransitions = new HashMap<State, Set<State>>();

    allowedTransitions.put(State.IDLE, EnumSet.of(State.IDLE, State.CONNECTING,
        State.DEVICE_FINDER));
    allowedTransitions.put(State.CONNECTING, EnumSet.of(State.CONNECTED,
        State.DEVICE_FINDER, State.PAIRING, State.DISCONNECTING));
    allowedTransitions.put(State.CONNECTED, EnumSet.of(State.DISCONNECTING));
    allowedTransitions.put(State.DEVICE_FINDER, EnumSet.of(State.IDLE));
    allowedTransitions.put(State.PAIRING, EnumSet.of(State.IDLE));
    allowedTransitions.put(State.DISCONNECTING, EnumSet.of(State.IDLE,
        State.CONNECTING));

    for (State state : State.values()) {
      if (!allowedTransitions.containsKey(state)) {
        throw new IllegalStateException("Incomplete transition map");
      }
    }
    return allowedTransitions;
  }

  private static boolean isTransitionLegal(State from, State to) {
    return ALLOWED_TRANSITION.get(from).contains(to);
  }
}
