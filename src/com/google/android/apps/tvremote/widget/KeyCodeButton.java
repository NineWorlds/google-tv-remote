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
package com.google.android.apps.tvremote.widget;

import com.google.android.apps.tvremote.R;
import com.google.android.apps.tvremote.MainActivity;
import com.google.anymote.Key.Code;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

/**
 * Button of the remote controller that has remote controller keycode assigned,
 * and supports displaying highlight with the support of {@link HighlightView}.
 *
 */
public class KeyCodeButton extends ImageButton {

  private final Code keyCode;
  private boolean wasPressed;

  /**
   * Key code handler interface.
   */
  public interface KeyCodeHandler {
    /**
     * Invoked when key has became touched.
     *
     * @param keyCode touched key code.
     */
    public void onTouch(Code keyCode);

    /**
     * Invoked when key has been released.
     *
     * @param keyCode released key code.
     */
    public void onRelease(Code keyCode);
  }

  public KeyCodeButton(Context context) {
    super(context);
    keyCode = null;
    initialize();
  }

  public KeyCodeButton(Context context, AttributeSet attrs) {
    super(context, attrs);

    TypedArray a = context.obtainStyledAttributes(attrs,
        R.styleable.RemoteButton);

    try {
      CharSequence s = a.getString(R.styleable.RemoteButton_key_code);
      if (s != null) {
        keyCode = Code.valueOf(s.toString());
        enableKeyCodeAction();
      } else {
        keyCode = null;
      }
    } finally {
      a.recycle();
    }

    initialize();
  }

  private void initialize() {
    setScaleType(ScaleType.CENTER_INSIDE);
  }

  private void enableKeyCodeAction() {
    setOnTouchListener(new View.OnTouchListener() {
      public boolean onTouch(View v, MotionEvent event) {
        Context context = getContext();
        if (context instanceof KeyCodeHandler) {
          KeyCodeHandler handler = (KeyCodeHandler) context;
          switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
              handler.onTouch(keyCode);
              break;
            case MotionEvent.ACTION_UP:
              handler.onRelease(keyCode);
              break;
          }
        }

        return false;
      }
    });
  }

  /**
   * Draws button, and notifies highlight view to draw the glow.
   *
   * @see android.view.View#onDraw(android.graphics.Canvas)
   */
  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    // Notify highlight layer to draw highlight
    Context context = getContext();
    if (context instanceof MainActivity) {
      HighlightView highlightView = ((MainActivity) context).getHighlightView();
      if (this.isPressed()) {
        Rect rect = new Rect();
        if (this.getGlobalVisibleRect(rect)) {
          highlightView.drawButtonHighlight(rect);
          wasPressed = true;
        }
      } else if (wasPressed) {
        wasPressed = false;
        highlightView.clearButtonHighlight();
      }
    }
  }
}
