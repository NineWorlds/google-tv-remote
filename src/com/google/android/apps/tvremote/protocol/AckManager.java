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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class manages the requests for acknowledgments that are sent to the
 * server to monitor the connection and the replies from it.
 *
 */
public final class AckManager {

  private static final String LOG_TAG = "AckManager";
  private static final boolean DEBUG = false;

  /**
   * Duration between two ack requests.
   */
  private static final long PONG_PERIOD = 3 * 1000;

  /**
   * Timeout before the connection is declared lost.
   */
  private static final long PING_TIMEOUT = 500;

  /**
   * Interface used when the connection is lost.
   */
  public interface Listener {
    /**
     * Called when the connection is considered lost, if no acknowledgment
     * message has been received after {@link #PING_TIMEOUT}.
     */
    public void onTimeout();
  }

  private final Listener connectionListener;

  private final AckHandler handler;

  private final AnymoteSender sender;

  public AckManager(Listener listener, AnymoteSender sender) {
    HandlerThread handlerThread = new HandlerThread("AckHandlerThread");
    handlerThread.start();
    handler = new AckHandler(handlerThread.getLooper());
    connectionListener = listener;
    this.sender = sender;
  }

  /**
   * Notifies the AckManager that a acknowledgment message has been received.
   */
  public void onAck() {
    handler.sendEmptyMessageAndIncrement(Action.ACK);
  }

  public void start() {
    handler.sendEmptyMessageAndIncrement(Action.START);
  }

  public void cancel() {
    handler.getLooper().quit();
  }

  /**
   * Notifies the listener that the connection has been lost.
   */
  private void connectionTimeout() {
    connectionListener.onTimeout();
  }

  private enum Action {
    START,
    PING,
    ACK,
    TIMEOUT,
  }

  /**
   * Inner class that handles start / stop / pong / ack / timeout messages
   * from multiple threads, and serializes their execution.
   */
  private final class AckHandler extends Handler {
    /**
     * The current sequence number.
     */
    private final AtomicInteger sequence;

    AckHandler(Looper looper) {
      super(looper);
      sequence = new AtomicInteger();
    }

    @Override
    public void handleMessage(Message msg) {
      Action action = actionValueOf(msg.what);
      if (DEBUG) {
        Log.d(LOG_TAG,
            "action=" + action + " : msg=" + msg + " : seq=" + sequence.get()
                + " @ " + System.currentTimeMillis());
      }
      switch (action) {
        case START:
          handleStart();
          break;

        case PING:
          handlePing();
          break;

        case ACK:
          handleAck();
          break;

        case TIMEOUT:
          handleTimeout(msg.arg1);
          break;
      }
    }

    private void handlePing() {
      int token = sequence.incrementAndGet();
      removeMessages(Action.ACK, Action.TIMEOUT);
      sender.ping();
      sendMessageDelayed(obtainMessage(Action.TIMEOUT, token), PING_TIMEOUT);
    }

    private void handleStart() {
      sequence.incrementAndGet();
      removeMessages(Action.TIMEOUT, Action.PING, Action.ACK);
      handlePing();
    }

    private void handleAck() {
      sequence.incrementAndGet();
      removeMessages(Action.TIMEOUT);
      sendMessageDelayed(obtainMessage(Action.PING), PONG_PERIOD);
    }

    private void handleTimeout(int token) {
      removeMessages(Action.PING, Action.ACK);
      if (sequence.compareAndSet(token, token + 1)) {
        connectionTimeout();
      }
      sequence.incrementAndGet();
    }

    private void removeMessages(Action... actions) {
      for (Action action : actions) {
        removeMessages(action.ordinal());
      }
    }

    private Message obtainMessage(Action action) {
      return obtainMessage(action.ordinal());
    }

    private Message obtainMessage(Action action, int arg1) {
      return obtainMessage(action.ordinal(), arg1, 0);
    }

    private Action actionValueOf(int what) {
      return Action.values()[what];
    }

    void sendEmptyMessageAndIncrement(Action action) {
      sequence.incrementAndGet();
      sendEmptyMessage(action.ordinal());
    }
  }
}
