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

package com.google.android.apps.tvremote.protocol;

import com.google.android.apps.tvremote.CoreService;
import com.google.android.apps.tvremote.protocol.AckManager.Listener;
import com.google.anymote.Key.Action;
import com.google.anymote.Key.Code;
import com.google.anymote.Messages.DataList;
import com.google.anymote.Messages.FlingResult;
import com.google.anymote.common.AnymoteFactory;
import com.google.anymote.common.ConnectInfo;
import com.google.anymote.common.ErrorListener;
import com.google.anymote.device.DeviceAdapter;
import com.google.anymote.device.MessageReceiver;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;

/**
 * An implementation of the ICommandSender interface which uses the Anymote
 * protocol.
 *
 */
public final class AnymoteSender implements ICommandSender {

  private final static String LOG_TAG = "AnymoteSender";

  /**
   * Core service that manages the connection to the server.
   */
  private final CoreService coreService;

  /**
   * Receiver for the Anymote protocol.
   */
  private final MessageReceiver receiver;

  /**
   * Error listener for the Anymote protocol.
   */
  private final ErrorListener errorListener;

  /**
   * Sender for the Anymote protocol.
   */
  private DeviceAdapter deviceAdapter;

  /**
   * The Ack manager.
   */
  private final AckManager ackManager;

  public AnymoteSender(CoreService service) {
    coreService = service;
    ackManager = new AckManager(new Listener() {
      public void onTimeout() {
        onConnectionError();
      }
    }, this);

    receiver = new MessageReceiver() {
      public void onAck() {
        ackManager.onAck();
      }

      public void onData(String type, String data) {
        Log.d(LOG_TAG, "onData: " + type + " / " + data);
      }

      public void onDataList(DataList dataList) {
        Log.d(LOG_TAG, "onDataList: " + dataList.toString());
      }

      public void onFlingResult(
          FlingResult flingResult, Integer sequenceNumber) {
        Log.d(LOG_TAG,
            "onFlingResult: " + flingResult.toString() + " " + sequenceNumber);
      }
    };

    errorListener = new ErrorListener() {
      public void onIoError(String message, Throwable exception) {
        Log.d(LOG_TAG, "IoError: " + message, exception);
        onConnectionError();
      }
    };
  }

  /**
   * Sets the socket the sender will use to communicate with the server.
   *
   * @param socket the socket to the server
   */
  public boolean setSocket(Socket socket) {
    if (socket == null) {
      throw new NullPointerException("null socket");
    }
    return instantiateProtocol(socket);
  }

  public void click(Action action) {
    DeviceAdapter sender = getSender();
    if (sender != null) {
      sender.sendKeyEvent(Code.BTN_MOUSE, action);
    }
  }

  public void flingUrl(String url) {
    DeviceAdapter sender = getSender();
    if (sender != null) {
      sender.sendFling(url, 0);
    }
  }

  public void key(Code keycode, Action action) {
    DeviceAdapter sender = getSender();
    if (sender != null) {
      sender.sendKeyEvent(keycode, action);
    }
  }

  public void keyPress(Code key) {
    DeviceAdapter sender = getSender();
    if (sender != null) {
      sender.sendKeyEvent(key, Action.DOWN);
      sender.sendKeyEvent(key, Action.UP);
    }
  }

  public void moveRelative(int deltaX, int deltaY) {
    DeviceAdapter sender = getSender();
    if (sender != null) {
      sender.sendMouseMove(deltaX, deltaY);
    }
  }

  public void scroll(int deltaX, int deltaY) {
    DeviceAdapter sender = getSender();
    if (sender != null) {
      sender.sendMouseWheel(deltaX, deltaY);
    }
  }

  public void string(String text) {
    DeviceAdapter sender = getSender();
    if (sender != null) {
      sender.sendData(ProtocolConstants.DATA_TYPE_STRING, text);
    }
  }

  public void ping() {
    DeviceAdapter sender = getSender();
    if (sender != null) {
      sender.sendPing();
    }
  }

  private void sendConnect() {
    DeviceAdapter sender = getSender();
    if (sender != null) {
      sender.sendConnect(new ConnectInfo(ProtocolConstants.DEVICE_NAME,
          getVersionCode()));
    }
  }

  /**
   * Called when an error occurs on the transmission.
   */
  private void onConnectionError() {
    if (disconnect()) {
      coreService.notifyConnectionFailed();
    }
  }

  /**
   * Instantiates the protocol.
   */
  private boolean instantiateProtocol(Socket socket) {
    disconnect();
    try {
      deviceAdapter =
          AnymoteFactory.getDeviceAdapter(receiver, socket.getInputStream(),
              socket.getOutputStream(), errorListener);
    } catch (IOException e) {
      Log.d(LOG_TAG, "Unable to create sender", e);
      deviceAdapter = null;
      return false;
    }

    sendConnect();
    ackManager.start();
    return true;
  }

  /**
   * Returns the version number as defined in Android manifest
   * {@code versionCode}
   */
  private int getVersionCode() {
    try {
      PackageInfo info = coreService.getPackageManager().getPackageInfo(
          coreService.getPackageName(),
          0 /* basic info */);
      return info.versionCode;
    } catch (NameNotFoundException e) {
      Log.d(LOG_TAG, "cannot retrieve version number, package name not found");
    }
    return -1;
  }

  public synchronized boolean disconnect() {
    ackManager.cancel();
    if (deviceAdapter != null) {
      deviceAdapter.stop();
      deviceAdapter = null;
      return true;
    }
    return false;
  }

  private DeviceAdapter getSender() {
    return deviceAdapter;
  }
}
