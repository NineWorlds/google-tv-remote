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

import com.google.anymote.Key.Action;
import com.google.anymote.Key.Code;

/**
 * Defines an API to send control commands to a box.
 *
 */
public interface ICommandSender {

  /**
   * Moves the pointer relatively.
   *
   * @param deltaX  an integer representing the amount of horizontal motion
   * @param deltaY  an integer representing the amount of vertical motion
   */
  public void moveRelative(int deltaX, int deltaY);

  /**
   * Issues a key press.
   * <p>
   * This is equivalent to sending and {@link Action#DOWN} followed
   * immediately by an {@link Action#UP}
   *
   * @param key         the pressed key
   */
  public void keyPress(Code key);

  /**
   * A detailed key event.
   *
   * @param keycode the keycode to send
   * @param action  the corresponding action
   */
  public void key(Code keycode, Action action);

  /**
   * Sends a URL for display.
   *
   * @param url     a string representing the target URL
   */
  public void flingUrl(String url);

  /**
   * Issues scrolling command.
   *
   * @param deltaX  an integer representing the amount of horizontal scrolling
   * @param deltaY  an integer representing the amount of vertical scrolling
   */
  public void scroll(int deltaX, int deltaY);

  /**
   * Sends a string.
   *
   * @param text the message to send
   */
  public void string(String text);
}
