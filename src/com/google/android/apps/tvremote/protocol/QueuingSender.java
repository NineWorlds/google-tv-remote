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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

/**
 * An implementation of the ICommand sender that lies on top of the core
 * service and sends commands with a separate thread.
 *
 */
public final class QueuingSender implements ICommandSender {

  private static final String LOG_TAG = "QueuingSender";

  /**
   * Buffered command that is will be sent when connected.
   */
  private Command bufferedCommand;

  private final Handler handler;

  /**
   * Action that should be taken when sender is missing.
   */
  private enum MissingAction {
    /**
     * Do nothing.
     */
    IGNORE,
    /**
     * Missing action listener should be notified.
     */
    NOTIFY,
    /**
     * Command should be enqueued for future execution.
     */
    ENQUEUE
  }

  /**
   * Listener that will be notified about attempt of sending an event when no
   * sender is configured.
   */
  public interface MissingSenderListener {
    public void onMissingSender();
  }

  /**
   * The remote service through which commands should be sent.
   */
  private ICommandSender sender;

  private final MissingSenderListener missingSenderListener;

  public QueuingSender(MissingSenderListener listener) {
    missingSenderListener = listener;

    HandlerThread handlerThread = new HandlerThread("Sender looper");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper(), new Handler.Callback() {
      public boolean handleMessage(Message msg) {
        Command command = (Command) msg.obj;
        ICommandSender currentSender = sender;
        if (currentSender != null) {
          command.execute(currentSender);
        } else {
          Log.w(LOG_TAG, "Sender removed before sending command");
        }
        return true;
      }
    });
  }

  public synchronized void setSender(ICommandSender sender) {
    this.sender = sender;
    if (sender != null) {
      flushBufferedEvents();
    }
  }

  private boolean hasSender() {
    return sender != null;
  }

  private synchronized void sendCommand(Command command,
      MissingAction actionIfMissing) {
    if (!hasSender()) {
      switch (actionIfMissing) {
        case IGNORE:
          break;
        case ENQUEUE:
          bufferedCommand = command;
          break;
        case NOTIFY:
          missingSenderListener.onMissingSender();
          break;
        default:
          throw new IllegalStateException("Unsupported action: "
              + actionIfMissing);
      }
      return;
    }
    Message msg = handler.obtainMessage(0, command);
    handler.sendMessage(msg);
  }

  private void sendCommand(Command command) {
    sendCommand(command, MissingAction.NOTIFY);
  }

  public void flingUrl(String url) {
    sendCommand(Commands.buildFlingUrlCommand(url), MissingAction.ENQUEUE);
  }

  public void key(Code keycode, Action action) {
    sendCommand(Commands.buildKeyCommand(keycode, action));
  }

  public void keyPress(Code key) {
    sendCommand(Commands.buildKeyPressCommand(key));
  }

  public void moveRelative(int deltaX, int deltaY) {
    sendCommand(Commands.buildMoveCommand(deltaX, deltaY));
  }

  public void scroll(int deltaX, int deltaY) {
    sendCommand(Commands.buildScrollCommand(deltaX, deltaY));
  }

  public void string(String text) {
    sendCommand(Commands.buildStringCommand(text));
  }

  /**
   * Sends buffered events (currently last flinged URI is buffered).
   */
  private void flushBufferedEvents() {
    if (!hasSender()) {
      throw new IllegalStateException("No sender is set.");
    }
    if (bufferedCommand != null) {
      sendCommand(bufferedCommand, MissingAction.IGNORE);
      bufferedCommand = null;
    }
  }
}
