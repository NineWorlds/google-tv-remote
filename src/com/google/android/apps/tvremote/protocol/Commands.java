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

import com.google.anymote.Key.Action;
import com.google.anymote.Key.Code;

/**
 * Utility class for building command wrappers.
 *
 */
public class Commands {
  private Commands() {
    // prevent instantiation
    throw new IllegalStateException();
  }

  /**
   * Builds move command.
   *
   * @param deltaX an integer representing the amount of horizontal motion
   * @param deltaY an integer representing the amount of vertical motion
   */
  public static Command buildMoveCommand(final int deltaX, final int deltaY) {
    return new Command() {
      public void execute(ICommandSender sender) {
        sender.moveRelative(deltaX, deltaY);
      }
    };
  }

  /**
   * Builds key press command.
   *
   * @param key the pressed key
   */
  public static Command buildKeyPressCommand(final Code key) {
    return new Command() {
      public void execute(ICommandSender sender) {
        sender.keyPress(key);
      }
    };
  }

  /**
   * Builds detailed key command.
   *
   * @param keycode the keycode to send
   * @param action the corresponding action
   */
  public static Command buildKeyCommand(final Code keycode, final Action action) {
    return new Command() {
      public void execute(ICommandSender sender) {
        sender.key(keycode, action);
      }
    };
  }

  /**
   * Builds fling command.
   *
   * @param url a string representing the target URL
   */
  public static Command buildFlingUrlCommand(final String url) {
    return new Command() {
      public void execute(ICommandSender sender) {
        sender.flingUrl(url);
      }
    };
  }

  /**
   * Builds scroll command.
   *
   * @param deltaX an integer representing the amount of horizontal scrolling
   * @param deltaY an integer representing the amount of vertical scrolling
   */
  public static Command buildScrollCommand(final int deltaX, final int deltaY) {
    return new Command() {
      public void execute(ICommandSender sender) {
        sender.scroll(deltaX, deltaY);
      }
    };
  }

  /**
   * Builds string command.
   *
   * @param text the message to send
   */
  public static Command buildStringCommand(final String text) {
    return new Command() {
      public void execute(ICommandSender sender) {
        sender.string(text);
      }
    };
  }
}
