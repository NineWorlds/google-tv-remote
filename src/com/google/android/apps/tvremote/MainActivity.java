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

import com.google.android.apps.tvremote.TouchHandler.Mode;
import com.google.android.apps.tvremote.layout.SlidingLayout;
import com.google.android.apps.tvremote.util.Action;
import com.google.android.apps.tvremote.widget.HighlightView;
import com.google.android.apps.tvremote.widget.KeyCodeButton;
import com.google.android.apps.tvremote.widget.SoftDpad;
import com.google.anymote.Key;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Main screen of the remote controller activity.
 */
public class MainActivity extends BaseActivity
    implements KeyCodeButton.KeyCodeHandler {

  private static final String LOG_TAG = "RemoteActivity";

  private HighlightView surface;

  private final Handler handler;

  /**
   * The enum represents modes of the remote controller with
   * {@link SlidingLayout} screens assignment. In conjunction with
   * {@link ModeSelector} allows sliding between the screens.
   */
  private enum RemoteMode {
    TV(0, R.drawable.icon_04_touchpad_selector),
    TOUCHPAD(1, R.drawable.icon_04_buttons_selector);

    private final int screenId;
    private final int switchButtonId;

    RemoteMode(int screenId, int switchButtonId) {
      this.screenId = screenId;
      this.switchButtonId = switchButtonId;
    }
  }

  /**
   * Mode selector allow sliding across the modes, keeps currently selected mode
   * information, and slides among the modes.
   */
  private static final class ModeSelector {
    private final SlidingLayout slidingLayout;
    private final ImageButton imageButton;
    private RemoteMode mode;

    ModeSelector(
        RemoteMode initialMode, SlidingLayout slidingLayout, ImageButton imageButton) {
      mode = initialMode;

      this.slidingLayout = slidingLayout;
      this.imageButton = imageButton;

      applyMode();
    }

    void slideNext() {
      setMode(RemoteMode.TOUCHPAD.equals(mode) ? RemoteMode.TV : RemoteMode.TOUCHPAD);
    }

    void setMode(RemoteMode newMode) {
      mode = newMode;
      applyMode();
    }

    void applyMode() {
      slidingLayout.snapToScreen(mode.screenId);
      imageButton.setImageResource(mode.switchButtonId);
    }
  }

  public MainActivity() {
    handler = new Handler();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_touchpad_top);

    surface = (HighlightView) findViewById(R.id.HighlightView);

    LayoutInflater inflater = LayoutInflater.from(getBaseContext());

    SlidingLayout slidingLayout = (SlidingLayout) findViewById(R.id.slider);
    slidingLayout.addView(
        inflater.inflate(R.layout.subview_playcontrol_tv, null), 0);
    slidingLayout.addView(
        inflater.inflate(R.layout.subview_touchpad, null), 1);
    slidingLayout.setCurrentScreen(0);

    ImageButton nextButton = (ImageButton) findViewById(R.id.button_next_page);
    ImageButton keyboardButton =
        (ImageButton) findViewById(R.id.button_keyboard);
    ImageButton voiceButton = (ImageButton) findViewById(R.id.button_voice);
    ImageButton searchButton = (ImageButton) findViewById(R.id.button_search);
    ImageButton shortcutsButton =
        (ImageButton) findViewById(R.id.button_shortcuts);
    ImageButton liveTvButton = (ImageButton) findViewById(R.id.button_livetv);

    final ModeSelector current =
        new ModeSelector(RemoteMode.TV, slidingLayout, nextButton);

    nextButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        current.slideNext();
      }
    });

    liveTvButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        current.setMode(RemoteMode.TV);
      }
    });

    keyboardButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View view) {
        showActivity(KeyboardActivity.class);
      }
    });

    voiceButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View view) {
        showVoiceSearchActivity();
      }
    });

    searchButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View view) {
        Action.NAVBAR.execute(getCommands());
        showActivity(KeyboardActivity.class);
      }
    });

    shortcutsButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View arg0) {
        showActivity(ShortcutsActivity.class);
      }
    });

    SoftDpad softDpad = (SoftDpad) findViewById(R.id.SoftDpad);
    softDpad.setDpadListener(getDefaultDpadListener());

    // Attach touch handler to the touchpad
    new TouchHandler(
        findViewById(R.id.touch_pad), Mode.POINTER_MULTITOUCH, getCommands());

    flingIntent(getIntent());
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  public HighlightView getHighlightView() {
    return surface;
  }

  // KeyCode handler implementation.
  public void onRelease(Key.Code keyCode) {
    getCommands().key(keyCode, Key.Action.UP);
  }

  public void onTouch(Key.Code keyCode) {
    playClick();
    getCommands().key(keyCode, Key.Action.DOWN);
  }

  private void playClick() {
    ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).playSoundEffect(
        AudioManager.FX_KEY_CLICK);
  }

  private void flingIntent(Intent intent) {
    if (intent != null) {
      if (Intent.ACTION_SEND.equals(intent.getAction())) {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text != null) {
          Uri uri = Uri.parse(text);
          if (uri != null && ("http".equals(uri.getScheme())
              || "https".equals(uri.getScheme()))) {
            getCommands().flingUrl(text);
          } else {
            Toast.makeText(
                this, R.string.error_could_not_send_url, Toast.LENGTH_SHORT)
                .show();
          }
        } else {
          Log.w(LOG_TAG, "No URI to fling");
        }
      }
    }
  }

  @Override
  protected void onKeyboardOpened() {
    showActivity(KeyboardActivity.class);
  }

  // SUBACTIVITIES

  /**
   * The activities that can be launched from the main screen.
   * <p>
   * These codes should not conflict with the request codes defined in
   * {@link BaseActivity}.
   */
  private enum SubActivity {
    VOICE_SEARCH,
    UNKNOWN;

    public int code() {
      return BaseActivity.FIRST_USER_CODE + ordinal();
    }

    public static SubActivity fromCode(int code) {
      for (SubActivity activity : values()) {
        if (code == activity.code()) {
          return activity;
        }
      }
      return UNKNOWN;
    }
  }

  @Override
  protected void onActivityResult(
      int requestCode, int resultCode, Intent data) {
    SubActivity activity = SubActivity.fromCode(requestCode);
    switch (activity) {
      case VOICE_SEARCH:
        onVoiceSearchResult(resultCode, data);
        break;

      default:
        super.onActivityResult(requestCode, resultCode, data);
        break;
    }
  }

  // VOICE SEARCH

  private void showVoiceSearchActivity() {
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    startActivityForResult(intent, SubActivity.VOICE_SEARCH.code());
  }

  private void onVoiceSearchResult(int resultCode, Intent data) {
    String searchQuery;

    if ((resultCode == RESULT_CANCELED) || (data == null)) {
      return;
    }

    ArrayList<String> queryResults =
        data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
    if ((queryResults == null) || (queryResults.isEmpty())) {
      Log.d(LOG_TAG, "No results from VoiceSearch server.");
      return;
    } else {
      searchQuery = queryResults.get(0);
      if (TextUtils.isEmpty(searchQuery)) {
        Log.d(LOG_TAG, "Empty result from VoiceSearch server.");
        return;
      }
    }

    showVoiceSearchDialog(searchQuery);
  }

  private void showVoiceSearchDialog(final String query) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder
        .setNeutralButton(
            R.string.voice_send, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                getCommands().string(query);
              }
            })
        .setPositiveButton(
            R.string.voice_search_send, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                getCommands().keyPress(Key.Code.KEYCODE_SEARCH);
                // Send query delayed
                handler.postDelayed(new Runnable() {
                  public void run() {
                    getCommands().string(query);
                  }
                }, getResources().getInteger(R.integer.search_query_delay));
              }
            })
        .setNegativeButton(
            R.string.pairing_cancel, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
              }
            })
        .setCancelable(true)
        .setTitle(R.string.voice_dialog_label)
        .setMessage(query);
    builder.create().show();
  }
}
