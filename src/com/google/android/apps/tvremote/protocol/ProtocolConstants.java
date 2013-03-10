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

package com.google.android.apps.tvremote.protocol;

/**
 * A utility class that contains the constants used in the anymote protocol.
 *
 */
public final class ProtocolConstants {

  /**
   * Data type used to send a string in a data message.
   */
  public static final String DATA_TYPE_STRING = "com.google.tv.string";

  /**
   * Device name used upon connection.
   */
  public static final String DEVICE_NAME = "android";

  /**
   * No constructor, this class contains only constants.
   */
  private ProtocolConstants() {}
}
