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

import com.google.android.apps.tvremote.TouchHandler.Mode;
import com.google.android.apps.tvremote.util.Action;
import com.google.android.apps.tvremote.widget.ImeInterceptView;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * Text input activity.
 * <p>
 * Displays a soft keyboard if needed.
 *
 */
public final class KeyboardActivity extends BaseActivity {

  /**
   * Captures text inputs.
   */
  private final TextInputHandler textInputHandler;

  /**
   * The main view.
   */
  private ImeInterceptView view;

  public KeyboardActivity() {
    textInputHandler = new TextInputHandler(this, getCommands());
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.keyboard);

    view = (ImeInterceptView) findViewById(R.id.keyboard);
    view.requestFocus();
    view.setInterceptor(new ImeInterceptView.Interceptor() {
      public boolean onKeyEvent(KeyEvent event) {
        KeyboardActivity.this.onUserInteraction();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
          switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
              finish();
              return true;

            case KeyEvent.KEYCODE_SEARCH:
              Action.NAVBAR.execute(getCommands());
              return true;

            case KeyEvent.KEYCODE_ENTER:
              Action.ENTER.execute(getCommands());
              finish();
              return true;
          }
        }
        return textInputHandler.handleKey(event);
      }

      public boolean onSymbol(char c) {
        KeyboardActivity.this.onUserInteraction();
        textInputHandler.handleChar(c);
        return false;
      }
    });

    textInputHandler.setDisplay(
        (TextView) findViewById(R.id.text_feedback_chars));

    // Attach touch handler to the touch pad.
    new TouchHandler(view, Mode.POINTER_MULTITOUCH, getCommands());
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event){
    if (event.getAction() == MotionEvent.ACTION_DOWN){
      finish();
    }
    return super.onTrackballEvent(event);
  }
}
