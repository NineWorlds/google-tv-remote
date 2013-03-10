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

import com.google.android.apps.tvremote.protocol.ICommandSender;
import com.google.android.apps.tvremote.util.Action;
import com.google.anymote.Key.Code;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.TextView;

/**
 * Handles text-related key input.
 *
 * This class also manages a view that displays the last few characters typed.
 *
 */
public final class TextInputHandler {

  /**
   * Interface to send commands during a touch sequence.
   */
  private final ICommandSender commands;

  /**
   * Context.
   */
  private final Context context;

  /**
   * {@code true} if display should be cleared before next output.
   */
  private boolean clearNextTime;

  public TextInputHandler(Context context, ICommandSender commands) {
    this.commands = commands;
    this.context = context;
  }

  /**
   * The view that contains the visual feedback for text input.
   */
  private TextView display;

  /**
   * Handles a character input.
   *
   * @param  c             the character being typed
   * @return {@code true}  if the event was handled
   */
  public boolean handleChar(char c) {
    if (isValidCharacter(c)) {
      String str = String.valueOf(c);
      appendDisplayedText(str);
      commands.string(str);
      return true;
    }
    return false;
  }

  /**
   * Handles a key event.
   *
   * @param     event   the key event to handle
   * @return    {@code true} if the event was handled
   */
  public boolean handleKey(KeyEvent event) {
    if (event.getAction() != KeyEvent.ACTION_DOWN) {
      return false;
    }
    int code = event.getKeyCode();
    if (code == KeyEvent.KEYCODE_ENTER) {
      displaySingleTimeMessage(context.getString(R.string.keyboard_enter));
      Action.ENTER.execute(commands);
      return true;
    }
    if (code == KeyEvent.KEYCODE_DEL) {
      displaySingleTimeMessage(context.getString(R.string.keyboard_del));
      Action.BACKSPACE.execute(commands);
      return true;
    }

    if (code == KeyEvent.KEYCODE_SPACE) {
      appendDisplayedText(" ");
      commands.keyPress(Code.KEYCODE_SPACE);
      return true;
    }

    int c = event.getUnicodeChar();
    return handleChar((char) c);
  }

  private boolean isValidCharacter(int unicode) {
    return unicode > 0 && unicode < 256;
  }

  public void setDisplay(TextView textView) {
    display = textView;
  }

  private void appendDisplayedText(CharSequence seq) {
    if (display != null) {
      if (!clearNextTime) {
        seq = new StringBuffer(display.getText()).append(seq);
      }
      display.setText(seq);
    }
    clearNextTime = false;
  }

  private void displaySingleTimeMessage(CharSequence seq) {
    if (display != null) {
      display.setText(seq);
      clearNextTime = true;
    }
  }
}
