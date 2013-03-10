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

import com.google.android.apps.tvremote.util.Action;

/**
 * Storage class representing a shortcut.
 *
 */
public class Shortcut {

  /**
   * Resource id of the name.
   */
  private final int titleId;

  /**
   * Resource id of the detail string {@code null} if none.
   */
  private final Integer detailId;

  /**
   * Attached action.
   */
  private final Action action;

  /**
   * Color of the first component.
   */
  private final Integer colorId;

  private Shortcut(Action action, int title, Integer detail, Integer colorId) {
    if (action == null) {
      throw new NullPointerException();
    }
    this.titleId = title;
    this.action = action;
    this.detailId = detail;
    this.colorId = colorId;
  }

  public Shortcut(int title, int detail, int colorId, Action action) {
    this(action, title, detail, colorId);
  }

  public Shortcut(int title, int detail, Action action) {
    this(action, title, detail, null);
  }

  public Shortcut(int title, Action action) {
    this(action, title, null, null);
  }

  public int getTitleId() {
    return titleId;
  }

  public int getDetailId() {
    return detailId;
  }

  public boolean hasDetailId() {
    return detailId != null;
  }

  public Action getAction() {
    return action;
  }

  public boolean hasColor() {
    return colorId != null;
  }

  public int getColor() {
    return colorId;
  }
}