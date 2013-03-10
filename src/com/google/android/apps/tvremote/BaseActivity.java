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

import com.google.android.apps.tvremote.ConnectionManager.ConnectionListener;
import com.google.android.apps.tvremote.TrackballHandler.Direction;
import com.google.android.apps.tvremote.TrackballHandler.Listener;
import com.google.android.apps.tvremote.TrackballHandler.Mode;
import com.google.android.apps.tvremote.protocol.ICommandSender;
import com.google.android.apps.tvremote.protocol.QueuingSender;
import com.google.android.apps.tvremote.util.Action;
import com.google.android.apps.tvremote.util.Debug;
import com.google.android.apps.tvremote.widget.SoftDpad;
import com.google.android.apps.tvremote.widget.SoftDpad.DpadListener;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.concurrent.TimeUnit;

/**
 * Base for most activities in the app.
 * <p>
 * Automatically connects to the background service on startup.
 *
 */
public class BaseActivity extends CoreServiceActivity
    implements ConnectionListener {

  private static final String LOG_TAG = "BaseActivity";

  /**
   * Request code used by this activity.
   */
  private static final int CODE_SWITCH_BOX = 1;

  /**
   * Request code used by this activity for pairing requests.
   */
  private static final int CODE_PAIRING = 2;

  private static final long MIN_TOAST_PERIOD = TimeUnit.SECONDS.toMillis(3);

  /**
   * User codes defined in activities extending this one should start above
   * this value.
   */
  public static final int FIRST_USER_CODE = 100;

  /**
   * Code for delayed messages to dim the screen.
   */
  private static final int SCREEN_DIM = 1;

  /**
   * Backported brightness level from API level 8 
   */
  private static final float BRIGHTNESS_OVERRIDE_NONE = -1.0f;

  /**
   * Turns trackball events into commands.
   */
  private TrackballHandler trackballHandler;

  private final QueuingSender commands;

  private boolean isConnected;

  private boolean isKeepingConnection;

  private boolean isScreenDimmed;

  private Handler handler;

  /**
   * Constructor.
   */
  BaseActivity() {
    commands = new QueuingSender(new MissingSenderToaster());
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    handler = new Handler(new ScreenDimCallback());
    trackballHandler = createTrackballHandler();
    trackballHandler.setAudioManager(am);
  }

  @Override
  protected void onStart() {
    super.onStart();
    setKeepConnected(true);
  }

  @Override
  protected void onStop() {
    setKeepConnected(false);
    super.onStop();
  }

  @Override
  protected void onResume() {
    super.onResume();
    connect();
    resetScreenDim();
  }

  @Override
  protected void onPause() {
    handler.removeMessages(SCREEN_DIM);
    disconnect();
    super.onPause();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    int code = event.getKeyCode();
    switch (code) {
      case KeyEvent.KEYCODE_VOLUME_DOWN:
        Action.VOLUME_DOWN.execute(getCommands());
        return true;
      case KeyEvent.KEYCODE_VOLUME_UP:
        Action.VOLUME_UP.execute(getCommands());
        return true;
      case KeyEvent.KEYCODE_SEARCH:
        Action.NAVBAR.execute(getCommands());
        showActivity(KeyboardActivity.class);
        return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event) {
    return trackballHandler.onTrackballEvent(event);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = new MenuInflater(this);
    inflater.inflate(R.menu.main, menu);
    return true;
  }

  // MENU HANDLER

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {

      case R.id.menu_switch:
        getConnectionManager().requestDeviceFinder();
        return true;

      case R.id.menu_about:
        showActivity(AboutActivity.class);
        return true;

      default:
        return super.onOptionsItemSelected(item);
    }
  }

  /**
   * Returns an object handling trackball events.
   */
  private TrackballHandler createTrackballHandler() {
    TrackballHandler handler = new TrackballHandler(new Listener() {
      public void onClick() {
        Action.DPAD_CENTER.execute(getCommands());
      }

      public void onDirectionalEvent(Direction direction) {
        switch (direction) {
          case DOWN:
            Action.DPAD_DOWN.execute(getCommands());
            break;

          case LEFT:
            Action.DPAD_LEFT.execute(getCommands());
            break;

          case RIGHT:
            Action.DPAD_RIGHT.execute(getCommands());
            break;

          case UP:
            Action.DPAD_UP.execute(getCommands());
            break;

          default:
            break;
        }
      }

      public void onScrollEvent(int dx, int dy) {
        getCommands().scroll(dx, dy);
      }
    }, this);
    handler.setEnabled(true);
    handler.setMode(Mode.DPAD);
    return handler;
  }

  @Override
  protected void onActivityResult(final int requestCode, final int resultCode,
      final Intent data)  {
    executeWhenCoreServiceAvailable(new Runnable() {
      public void run() {
        if (requestCode == CODE_SWITCH_BOX) {
          if (resultCode == RESULT_OK && data != null) {
            RemoteDevice remoteDevice =
                data.getParcelableExtra(DeviceFinder.EXTRA_REMOTE_DEVICE);
            if (remoteDevice != null) {
              getConnectionManager().setTarget(remoteDevice);
            }
          }
          getConnectionManager().deviceFinderFinished();
          connectOrFinish();
        }
        else if (requestCode == CODE_PAIRING) {
          getConnectionManager().pairingFinished();
          handlePairingResult(resultCode);
        }
      }
    });
  }

  private void showMessage(int resId) {
    Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show();
  }

  private void handlePairingResult(int resultCode) {
    switch (resultCode) {
      case PairingActivity.RESULT_OK:
        showMessage(R.string.pairing_succeeded_toast);
        connect();
        break;
      case PairingActivity.RESULT_CANCELED:
        getConnectionManager().requestDeviceFinder();
        break;
      case PairingActivity.RESULT_CONNECTION_FAILED:
      case PairingActivity.RESULT_PAIRING_FAILED:
        showMessage(R.string.pairing_failed_toast);
        getConnectionManager().requestDeviceFinder();
        break;
      default:
        throw new IllegalStateException("Unsupported pairing activity result: "
            + resultCode);
    }
  }

  /**
   * Returns the interface to send commands to the remote box.
   */
  protected final ICommandSender getCommands() {
    return commands;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
      onKeyboardOpened();
    }
    if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
      onKeyboardClosed();
    }
  }

  /**
   * Called when the physical keyboard is opened.
   * <p>
   * The default behavior is to close the current activity and to start the
   * keyboard activity. Extending classes can override to change behavior.
   */
  protected void onKeyboardOpened() {
    showActivity(KeyboardActivity.class);
    finish();
  }

  /**
   * Called when the physical keyboard is closed.
   * <p>
   * Extending classes can override to change behavior.
   */
  protected void onKeyboardClosed() {
    // default behavior is to do nothing
  }

  /**
   * Returns {@code true} if the activity is in landscape mode.
   */
  protected boolean isLandscape() {
    return (getResources().getConfiguration().orientation ==
        Configuration.ORIENTATION_LANDSCAPE);
  }

  /**
   * Returns a default implementation for the DpadListener.
   */
  protected DpadListener getDefaultDpadListener() {
    return new DpadListener() {

      public void onDpadClicked() {
        if(getCommands() != null) {
          Action.DPAD_CENTER.execute(getCommands());
        }
      }

      public void onDpadMoved(SoftDpad.Direction direction, boolean pressed) {
        Action action = translateDirection(direction, pressed);
        if (action != null) {
          action.execute(getCommands());
        }
      }
    };
  }

  /**
   * Translates a direction and a key pressed in an action.
   *
   * @param direction the direction of the movement
   * @param pressed   {@code true} if the key was pressed
   */
  private static Action translateDirection(SoftDpad.Direction direction,
      boolean pressed) {
    switch (direction) {
      case DOWN:
        return pressed ? Action.DPAD_DOWN_PRESSED
            : Action.DPAD_DOWN_RELEASED;

      case LEFT:
        return pressed ? Action.DPAD_LEFT_PRESSED
            : Action.DPAD_LEFT_RELEASED;

      case RIGHT:
        return pressed ? Action.DPAD_RIGHT_PRESSED
            : Action.DPAD_RIGHT_RELEASED;

      case UP:
        return pressed ? Action.DPAD_UP_PRESSED
            : Action.DPAD_UP_RELEASED;

      default:
        return null;
    }
  }

  private void connect() {
    if (!isConnected) {
      isConnected = true;
      executeWhenCoreServiceAvailable(new Runnable() {
        public void run() {
          getConnectionManager().connect(BaseActivity.this);
        }
      });
    }
  }

  private void disconnect() {
    if (isConnected) {
      commands.setSender(null);
      isConnected = false;
      executeWhenCoreServiceAvailable(new Runnable() {
        public void run() {
          getConnectionManager().disconnect(BaseActivity.this);
        }
      });
    }
  }

  private void setKeepConnected(final boolean keepConnected) {
    if (isKeepingConnection != keepConnected) {
      isKeepingConnection = keepConnected;
      executeWhenCoreServiceAvailable(new Runnable() {
        public void run() {
          logConnectionStatus("Keep Connected: " + keepConnected);
          getConnectionManager().setKeepConnected(keepConnected);
        }
      });
    }
  }

  /**
   * Starts the box selection dialog.
   */
  private final void showSwitchBoxActivity() {
    disconnect();
    startActivityForResult(
        DeviceFinder.createConnectIntent(this, getConnectionManager().getTarget(),
            getConnectionManager().getRecentlyConnected()), CODE_SWITCH_BOX);
  }

  /**
   * If connection failed due to SSL handshake failure, this method will be
   * invoked to start the pairing session with device, and establish secure
   * connection.
   * <p>
   * When pairing finishes, PairingListener's method will be called to
   * differentiate the result.
   */
  private final void showPairingActivity(RemoteDevice target) {
    disconnect();
    if (target != null) {
      startActivityForResult(
          PairingActivity.createIntent(this, new RemoteDevice(
              target.getName(), target.getAddress(), target.getPort() + 1)),
          CODE_PAIRING);
    }
  }

  public void onConnecting() {
    commands.setSender(null);
    logConnectionStatus("Connecting");
  }

  public void onShowDeviceFinder() {
    commands.setSender(null);
    logConnectionStatus("Show device finder");
    showSwitchBoxActivity();
  }

  public void onConnectionSuccessful(ICommandSender sender) {
    logConnectionStatus("Connected");
    commands.setSender(sender);
  }

  public void onNeedsPairing(RemoteDevice remoteDevice) {
    logConnectionStatus("Pairing");
    showPairingActivity(remoteDevice);
  }

  public void onDisconnected() {
    commands.setSender(null);
    logConnectionStatus("Disconnected");
  }

  private class MissingSenderToaster
      implements QueuingSender.MissingSenderListener {
    private long lastToastTime;

    public void onMissingSender() {
      if (System.currentTimeMillis() - lastToastTime > MIN_TOAST_PERIOD) {
        lastToastTime = System.currentTimeMillis();
        showMessage(R.string.sender_missing);
      }
    }
  }

  private void logConnectionStatus(CharSequence sequence) {
    String message = String.format("%s (%s)", sequence,
        getClass().getSimpleName());
    if (Debug.isDebugConnection()) {
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    Log.d(LOG_TAG, "Connection state: " + sequence);
  }

  private void connectOrFinish() {
    if (getConnectionManager() != null) {
      if (getConnectionManager().getTarget() != null) {
        connect();
      } else {
        finish();
      }
    }
  }

  @Override
  protected void onServiceAvailable(CoreService coreService) {
  }

  @Override
  protected void onServiceDisconnecting(CoreService coreService) {
    disconnect();
    setKeepConnected(false);
  }

  // Screen dimming

  @Override
  public void onUserInteraction() {
    super.onUserInteraction();
    resetScreenDim();
  }

  private void screenDim() {
    if (!isScreenDimmed) {
      WindowManager.LayoutParams lp = getWindow().getAttributes();
      lp.screenBrightness = getResources().getInteger(
          R.integer.screen_brightness_dimmed) / 100.0f;
      getWindow().setAttributes(lp);
    }
    isScreenDimmed = true;
  }

  private void resetScreenDim() {
    if (isScreenDimmed) {
      WindowManager.LayoutParams lp = getWindow().getAttributes();
      lp.screenBrightness = BRIGHTNESS_OVERRIDE_NONE;
      getWindow().setAttributes(lp);
    }
    isScreenDimmed = false;
    handler.removeMessages(SCREEN_DIM);
    handler.sendEmptyMessageDelayed(SCREEN_DIM,
        TimeUnit.SECONDS.toMillis(getResources().getInteger(
            R.integer.timeout_screen_dim)));
  }

  private class ScreenDimCallback implements Handler.Callback {

    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case SCREEN_DIM:
          screenDim();
          return true;
      }
      return false;
    }
  }
}
