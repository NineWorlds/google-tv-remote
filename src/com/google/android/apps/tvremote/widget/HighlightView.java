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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.NinePatchDrawable;
import android.util.AttributeSet;
import android.view.View;

/**
 * View for displaying single "highlight" layer over buttons.
 *
 */
public final class HighlightView extends View {

  private final NinePatchDrawable highlightDrawable;
  private final Rect highlightRect;
  private Rect buttonRect = null;
  private Rect drawRect = new Rect();

  public HighlightView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    TypedArray array =
        context.obtainStyledAttributes(attrs, R.styleable.HighlightView);
    highlightDrawable =
        (NinePatchDrawable) array.getDrawable(R.styleable.HighlightView_button);
    highlightRect = new Rect();
    if (!highlightDrawable.getPadding(highlightRect)) {
      throw new IllegalStateException("Highlight drawable has to have padding");
    }
    array.recycle();
  }

  public HighlightView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (buttonRect != null) {
      Rect myRect = new Rect();
      if (getGlobalVisibleRect(myRect)) {
        highlightDrawable.setBounds(drawRect);
        highlightDrawable.draw(canvas);
      }
    }
  }

  public void drawButtonHighlight(Rect rect) {
    if (highlightDrawable != null) {
      if (buttonRect != null) {
        invalidate(drawRect);
      }
      buttonRect = rect;
      drawRect = getHighlightRectangle(buttonRect);
      invalidate(drawRect);
    }
  }

  private Rect getHighlightRectangle(Rect globalRect) {
    Rect myRect = new Rect();
    if (!getGlobalVisibleRect(myRect)) {
      throw new IllegalStateException("Highlight view not visible???");
    }
    drawRect.left = buttonRect.left - myRect.left - highlightRect.left;
    drawRect.right = buttonRect.right - myRect.left + highlightRect.right;
    drawRect.top = buttonRect.top - myRect.top - highlightRect.top;
    drawRect.bottom = buttonRect.bottom - myRect.top + highlightRect.bottom;
    return drawRect;
  }

  public void clearButtonHighlight() {
    if (buttonRect != null) {
      buttonRect = null;
      invalidate(drawRect);
    }
  }
}
