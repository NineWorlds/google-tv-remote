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

import java.net.InetAddress;

/**
 * A wrapper for information in a broadcast advertisement.
 *
 */
public final class BroadcastAdvertisement {

  /**
   * Name of the service.
   */
  private final String mServiceName;
  
  /**
   * Address of the service.
   */
  private final InetAddress mServiceAddress;
  
  /**
   * Port of the service.
   */
  private final int mServicePort;
  
  BroadcastAdvertisement(String name, InetAddress addr, int port) {
    mServiceName = name;
    mServiceAddress = addr;
    mServicePort = port;
  }
  
  public String getServiceName() {
    return mServiceName;
  }
  
  public InetAddress getServiceAddress() {
    return mServiceAddress;
  }
  
  public int getServicePort() {
    return mServicePort;
  }
}
