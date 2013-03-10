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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Abstract activity that handles connection to the {@link CoreService}.
 *
 * The activity connects to service in {@link #onCreate(Bundle)}, and
 * disconnects in {@link #onDestroy()}. Upon successful connection, and before
 * disconnection appropriate callbacks are invoked.
 *
 */
public abstract class CoreServiceActivity extends Activity {
  private static final String LOG_TAG = "CoreServiceActivity";

  /**
   * Used to connect to the background service.
   */
  private ServiceConnection serviceConnection;
  private CoreService coreService;
  private Queue<Runnable> runnableQueue;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    runnableQueue = new LinkedList<Runnable>();
    connectToService();
  }

  @Override
  protected void onDestroy() {
    disconnectFromService();
    super.onDestroy();
  }

  /**
   * Opens the connection to the underlying service.
   */
  private void connectToService() {
    serviceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName name, IBinder service) {
        coreService = ((CoreService.LocalBinder) service).getService();
        runQueuedRunnables();
        onServiceAvailable(coreService);
      }

      public void onServiceDisconnected(ComponentName name) {
        onServiceDisconnecting(coreService);
        coreService = null;
      }
    };
    Intent intent = new Intent(this, CoreService.class);
    bindService(intent, serviceConnection, BIND_AUTO_CREATE);
  }

  /**
   * Closes the connection to the background service.
   */
  private synchronized void disconnectFromService() {
    unbindService(serviceConnection);
    serviceConnection = null;
  }

  private void runQueuedRunnables() {
    Runnable runnable;
    while ((runnable = runnableQueue.poll()) != null) {
      runnable.run();
    }
  }

  /**
   * Callback that is called when the core service become available.
   */
  protected abstract void onServiceAvailable(CoreService coreService);

  /**
   * Callback that is called when the core service is about disconnecting.
   */
  protected abstract void onServiceDisconnecting(CoreService coreService);

  /**
   * Starts an activity based on its class.
   */
  protected void showActivity(Class<?> activityClass) {
    Intent intent = new Intent(this, activityClass);
    startActivity(intent);
  }

  protected ConnectionManager getConnectionManager() {
    return coreService;
  }

  protected KeyStoreManager getKeyStoreManager() {
    if (coreService != null) {
      return coreService.getKeyStoreManager();
    }
    return null;
  }

  protected boolean executeWhenCoreServiceAvailable(Runnable runnable) {
    if (coreService == null) {
      Log.d(LOG_TAG, "Queueing runnable: " + runnable);
      runnableQueue.offer(runnable);
      return false;
    }
    runnable.run();
    return true;
  }
}
