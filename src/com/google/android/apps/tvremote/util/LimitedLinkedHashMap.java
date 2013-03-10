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

import java.util.LinkedHashMap;

/**
 * Linked hash map that limits number of added entries. If exceeded the eldest
 * element is removed.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LimitedLinkedHashMap<K, V> extends LinkedHashMap<K, V> {

  private final int maxSize;

  /**
   * Constructor.
   *
   * @param maxSize maximum number of elements given hash map can hold.
   */
  public LimitedLinkedHashMap(int maxSize) {
    this.maxSize = maxSize;
  }

  @Override
  protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
    return maxSize != 0 && size() > maxSize;
  }
}
