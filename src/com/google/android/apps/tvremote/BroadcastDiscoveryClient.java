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

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * An implementation of a trivial broadcast discovery protocol.
 * <p>
 * This client sends L3 broadcasts to probe for particular services on the
 * network.
 */
public class BroadcastDiscoveryClient implements Runnable {

  private static final String LOG_TAG = "BroadcastDiscoveryClient";

  /**
   * UDP port to send probe messages to.
   */
  private static final int BROADCAST_SERVER_PORT = 9101;

  /**
   * Frequency of probe messages.
   */
  private static final int PROBE_INTERVAL_MS = 6000;

  /**
   * Command name for a discovery request.
   */
  private static final String COMMAND_DISCOVER = "discover";

  /**
   * Service name to discover.
   */
  private static final String DESIRED_SERVICE = "_anymote._tcp";

  /**
   * Broadcast address of the local device.
   */
  private final InetAddress mBroadcastAddress;

  /**
   * Timer to send probes.
   */
  private final Timer mProbeTimer;

  /**
   * TimerTask to send probes.
   */
  private final TimerTask mProbeTimerTask;

  /**
   * Handle to main thread.
   */
  private final Handler mHandler;

  /**
   * Send/receive socket.
   */
  private final DatagramSocket mSocket;

  /**
   * Constructor
   *
   * @param broadcastAddress  destination address for probes
   * @param handler  update Handler in main thread
   */
  public BroadcastDiscoveryClient(InetAddress broadcastAddress,
      Handler handler) {
    mBroadcastAddress = broadcastAddress;
    mHandler = handler;

    try {
      mSocket = new DatagramSocket(); // binds to random port
      mSocket.setBroadcast(true);
    } catch (SocketException e) {
      Log.e(LOG_TAG, "Could not create broadcast client socket.", e);
      throw new RuntimeException();
    }

    mProbeTimer = new Timer();
    mProbeTimerTask = new TimerTask() {
      @Override
      public void run() {
        BroadcastDiscoveryClient.this.sendProbe();
      }
    };
    Log.i(LOG_TAG, "Starting client on address " + mBroadcastAddress);
  }

  /** {@inheritDoc} */
  public void run() {
    Log.i(LOG_TAG, "Broadcast client thread starting.");
    byte[] buffer = new byte[256];

    mProbeTimer.schedule(mProbeTimerTask, 0, PROBE_INTERVAL_MS);

    while (true) {
      try {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        mSocket.receive(packet);
        handleResponsePacket(packet);
      } catch (InterruptedIOException e) {
        // timeout
      } catch (IOException e) {
        // SocketException - stop() was called
        mProbeTimer.cancel();
        break;
      }
    }
    Log.i(LOG_TAG, "Exiting client loop.");
    mProbeTimer.cancel();
  }

  /**
   * Sends a single broadcast discovery request.
   */
  private void sendProbe() {
    DatagramPacket packet = makeRequestPacket(DESIRED_SERVICE,
        mSocket.getLocalPort());
    try {
      mSocket.send(packet);
    } catch (IOException e) {
      Log.e(LOG_TAG, "Exception sending broadcast probe", e);
      return;
    }
  }

  /**
   * Immediately stops the receiver thread, and cancels the probe timer.
   */
  public void stop() {
    if (mSocket != null) {
      mSocket.close();
    }
  }

  /**
   * Constructs a new probe packet.
   *
   * @param serviceName  the service name to discover
   * @param responsePort  the udp port number for replies
   * @return  a new DatagramPacket
   */
  private DatagramPacket makeRequestPacket(String serviceName,
      int responsePort) {
    String message = COMMAND_DISCOVER + " " + serviceName
        + " " + responsePort + "\n";
    byte[] buf = message.getBytes();
    DatagramPacket packet = new DatagramPacket(buf, buf.length,
        mBroadcastAddress, BROADCAST_SERVER_PORT);
    return packet;
  }

  /**
   * Parse a received packet, and notify the main thread if valid.
   *
   * @param packet  The locally-received DatagramPacket
   */
  private void handleResponsePacket(DatagramPacket packet) {
    String strPacket = new String(packet.getData(), 0, packet.getLength());
    String tokens[] = strPacket.trim().split("\\s+");

    if (tokens.length != 3) {
      Log.w(LOG_TAG, "Malformed response: expected 3 tokens, got "
          + tokens.length);
      return;
    }

    BroadcastAdvertisement advert;
    try {
      String serviceType = tokens[0];
      if (!serviceType.equals(DESIRED_SERVICE)) {
        return;
      }
      String serviceName = tokens[1];
      int port = Integer.parseInt(tokens[2]);
      InetAddress addr = packet.getAddress();
      Log.v(LOG_TAG, "Broadcast response: " + serviceName + ", "
          + addr + ", " + port);
      advert = new BroadcastAdvertisement(serviceName, addr, port);
    } catch (NumberFormatException e) {
      return;
    }

    Message message = mHandler.obtainMessage(DeviceFinder.BROADCAST_RESPONSE,
        advert);
    mHandler.sendMessage(message);
  }

}
