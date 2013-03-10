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

import android.os.Parcel;
import android.os.Parcelable;

import java.net.InetAddress;

/**
 * Container for keeping target server configuration.
 *
 */
public final class RemoteDevice implements Parcelable {
  private final String name;
  private final InetAddress address;
  private final int port;

  public RemoteDevice(String name, InetAddress address, int port) {
    if (address == null) {
      throw new NullPointerException("Address is null");
    }
    this.name = name;
    this.address = address;
    this.port = port;
  }

  /**
   * @return name of the controlled device.
   */
  public String getName() {
    return name;
  }

  /**
   * @return address of the controlled device.
   */
  public InetAddress getAddress() {
    return address;
  }

  /**
   * @return port of the controlled device.
   */
  public int getPort() {
    return port;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof RemoteDevice)) {
      return false;
    }
    RemoteDevice that = (RemoteDevice) obj;
    return equal(this.name, that.name)
        && equal(this.address, that.address)
        && (this.port == that.port);
  }

  @Override
  public int hashCode() {
    int code = 7;
    code = code * 31 + (name != null ? name.hashCode() : 0);
    code = code * 31 + (address != null ? address.hashCode() : 0);
    code = code * 31 + port;
    return code;
  }

  @Override
  public String toString() {
    return String.format("%s [%s:%d]", name, address, port);
  }

  private static <T> boolean equal(T obj1, T obj2) {
    if (obj1 == null) {
      return obj2 == null;
    }
    return obj1.equals(obj2);
  }

  public int describeContents() {
    return 0;
  }

  public void writeToParcel(Parcel parcel, int flags) {
    parcel.writeString(name);
    parcel.writeSerializable(address);
    parcel.writeInt(port);
  }

  public static final Parcelable.Creator<RemoteDevice> CREATOR =
      new Parcelable.Creator<RemoteDevice>() {

    public RemoteDevice createFromParcel(Parcel parcel) {
      return new RemoteDevice(parcel);
    }

    public RemoteDevice[] newArray(int size) {
      return new RemoteDevice[size];
    }
  };

  private RemoteDevice(Parcel parcel) {
    this(parcel.readString(), (InetAddress) parcel.readSerializable(),
        parcel.readInt());
  }
}
