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

package com.google.android.apps.tvremote.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

/**
 * Allows key events to be intercepted when an IME is showing.
 *
 */
public final class ImeInterceptView extends ImageView {

  /**
   * Will receive intercepted key events.
   */
  public interface Interceptor {

    /**
     * Called on non symbol key events.
     *
     * @param   event   the key event
     * @return  {@code true} if the event was handled
     */
    public boolean onKeyEvent(KeyEvent event);

    /**
     * Called when a symbol is typed.
     *
     * @param   c   the character being typed
     * @return  {@code true} if the event was handled
     */
    public boolean onSymbol(char c);
  }

  private Interceptor interceptor;

  private void initialize() {
    setFocusable(true);
    setFocusableInTouchMode(true);
  }

  public ImeInterceptView(Context context) {
    super(context);
    initialize();
  }

  public ImeInterceptView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public ImeInterceptView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setInterceptor(Interceptor interceptor) {
    this.interceptor = interceptor;
  }

  @Override
  public boolean dispatchKeyEventPreIme(KeyEvent event) {
    if (interceptor != null && interceptor.onKeyEvent(event)) {
      return true;
    }
    return super.dispatchKeyEventPreIme(event);
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    return new InterceptConnection(this, true);
  }

  /**
   * A class that intercepts events from the soft keyboard.
   */
  private final class InterceptConnection extends BaseInputConnection {
    public InterceptConnection(View targetView, boolean fullEditor) {
      super(targetView, fullEditor);
    }

    @Override
    public boolean performEditorAction(int actionCode) {
      interceptor.onKeyEvent(
          new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
      return true;
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
      for (int i = 0; i < text.length(); ++i) {
        interceptor.onSymbol(text.charAt(i));
      }
      return super.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean sendKeyEvent(KeyEvent event) {
      interceptor.onKeyEvent(event);
      return true;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
      for (int i = 0; i < text.length(); ++i) {
        interceptor.onSymbol(text.charAt(i));
      }
      return true;
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasWindowFocus) {
    super.onWindowFocusChanged(hasWindowFocus);
    if (hasWindowFocus) {
      requestFocus();
      showKeyboard();
    } else {
      hideKeyboard();
    }
  }

  private void hideKeyboard() {
    getInputManager().hideSoftInputFromWindow(
        getWindowToken(), 0 /* no flag */);
  }

  private void showKeyboard() {
    InputMethodManager manager = getInputManager();
    manager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
  }

  /**
   * Gets access to the system input manager.
   */
  private InputMethodManager getInputManager() {
    return (InputMethodManager) getContext().getSystemService(
        Context.INPUT_METHOD_SERVICE);
  }
}
