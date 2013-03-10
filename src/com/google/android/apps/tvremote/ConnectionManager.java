/*
 * Copyright (C) 2010 Google Inc.  All rights reserved.
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

import com.google.android.apps.tvremote.protocol.ICommandSender;

import java.util.ArrayList;

/**
 * Connection management interface.
 *
 */
public interface ConnectionManager {

  /**
   * Connection state change listener.
   */
  public interface ConnectionListener {
    /**
     * Called when device finder is being requested.
     */
    public void onShowDeviceFinder();

    /**
     * Called when connection process has started.
     */
    public void onConnecting();

    /**
     * Called when connection succeeds.
     *
     * @param commandSender sender for given connection.
     */
    public void onConnectionSuccessful(ICommandSender commandSender);

    /**
     * Called when remote target needs pairing.
     *
     * @param remoteDevice target to pair with.
     */
    public void onNeedsPairing(RemoteDevice remoteDevice);

    /**
     * Called when remote gets disonnected from GTV.
     */
    public void onDisconnected();
  }

  /**
   * Setting new active target or {@code null} for no target.
   *
   * @param remoteDevice remote device.
   */
  public void setTarget(RemoteDevice remoteDevice);

  /**
   * Connect with given connection listener.
   *
   * @param listener listener to be notified with connection state changes.
   */
  public void connect(ConnectionListener listener);

  /**
   * Disconnect with given connection listener.
   * 
   * @param listener listener to be unregistered.
   */
  public void disconnect(ConnectionListener listener);

  public void setKeepConnected(boolean keepConnected);

  /**
   * Returns current active remote device.
   *
   * @return remote device.
   */
  public RemoteDevice getTarget();

  /**
   * Returns list of recently connected devices.
   *
   * @return recently connected devices.
   */
  public ArrayList<RemoteDevice> getRecentlyConnected();

  /**
   * Notifies connection manager that pairing has finished.
   */
  public void pairingFinished();

  /**
   * Notifies connection manager that device finder has finished.
   */
  public void deviceFinderFinished();

  /**
   * Requests device finder.
   */
  public void requestDeviceFinder();
}
