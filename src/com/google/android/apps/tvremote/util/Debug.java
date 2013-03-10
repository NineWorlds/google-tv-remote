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

package com.google.android.apps.tvremote.util;

/**
 * Debug configuration flags.
 *
 */
public class Debug {
  private static final boolean DEBUG_CONNECTION = false;

  private static final boolean DEBUG_DEVICES = false;

  private static final boolean DEBUG_NO_CONNECTION = false;

  /**
   * @return {@code true} if connection debugging is enabled.
   */
  public static boolean isDebugConnection() {
    return DEBUG_CONNECTION;
  }

  /**
   * @return {@code true} if recently connected (usually manually) devices list
   * is enabled.
   */
  public static boolean isDebugDevices() {
    return DEBUG_DEVICES;
  }

  /**
   * @return {@code true} if testing remote without connection is enabled.
   */
  public static boolean isDebugConnectionLess() {
    return DEBUG_NO_CONNECTION;
  }
}
