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

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

/**
 * Widget for displaying text that will become completely transparent.
 *
 */
public final class FadingTextView extends TextView {

  private final AlphaAnimation animation;

  private void initialize() {
    animation.setDuration(
        getContext().getResources().getInteger(R.integer.fading_text_timeout));
    animation.setFillAfter(true);
    animation.setAnimationListener(new Animation.AnimationListener() {
      public void onAnimationStart(Animation animation) {
      }

      public void onAnimationRepeat(Animation animation) {
      }

      public void onAnimationEnd(Animation animation) {
        FadingTextView.this.setText("");
      }
    });
    setAnimation(animation);
  }

  public FadingTextView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    animation = new AlphaAnimation(1.0f, 0.0f);
    initialize();
  }

  public FadingTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    animation = new AlphaAnimation(1.0f, 0.0f);
    initialize();
  }

  public FadingTextView(Context context) {
    super(context);
    animation = new AlphaAnimation(1.0f, 0.0f);
    initialize();
  }

  @Override
  protected void onTextChanged(
      CharSequence text, int start, int before, int after) {
    super.onTextChanged(text, start, before, after);
    if (!TextUtils.isEmpty(text)) {
      startFading();
    }
  }

  private void startFading() {
    animation.reset();
    animation.start();
  }
}
