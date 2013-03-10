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

package com.google.android.apps.tvremote.layout;

import com.google.android.apps.tvremote.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * A FixedLayout positions its children in an array of rows of one component,
 * with percentage of total height specified. FixedLayout has to have known
 * dimensions.
 *
 */
public final class FixedLayout extends ViewGroup {

  /**
   * Array of heights of child views.
   */
  private int[] mHeightArray;

  public FixedLayout(Context context) {
    this(context, null);
  }

  public FixedLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public FixedLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  /**
   * Fixed layout paramters.
   */
  public static class FixedLayoutParams extends ViewGroup.LayoutParams {
    public static final float UNSPECIFIED = -1;

    public float percent;

    public FixedLayoutParams(Context context, AttributeSet attrs) {
      super(FILL_PARENT, WRAP_CONTENT);
      TypedArray array =
          context.obtainStyledAttributes(attrs, R.styleable.FixedLayout);
      percent =
          array.getFloat(R.styleable.FixedLayout_layout_percent, UNSPECIFIED);
      array.recycle();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
    int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
    int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
    int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

    if (widthSpecMode == MeasureSpec.UNSPECIFIED
        || heightSpecMode == MeasureSpec.UNSPECIFIED) {
      throw new RuntimeException(
          "FixedLayout cannot have UNSPECIFIED dimensions");
    }

    int width = widthSpecSize - getPaddingLeft() - getPaddingRight();
    int height = heightSpecSize - getPaddingTop() - getPaddingBottom();
    int count = getChildCount();

    mHeightArray = new int[count];

    if (count == 0) {
      return;
    }

    int remainingHeight = height;
    int unspecifiedCount = 0;

    for (int i = 0; i < count; i++) {
      final View child = getChildAt(i);
      final ViewGroup.LayoutParams params = child.getLayoutParams();
      final float currentPercent;

      if (params instanceof FixedLayoutParams) {
        currentPercent = ((FixedLayoutParams) params).percent;
      } else {
        currentPercent = FixedLayoutParams.UNSPECIFIED;
      }

      if (currentPercent > 0.0f) {
        mHeightArray[i] = (int) (height * currentPercent / 100.0f);
        remainingHeight -= mHeightArray[i];
      } else {
        mHeightArray[i] = 0;
      }
      if (mHeightArray[i] < 0) {
        throw new IllegalStateException("Negative height of row: " + i);
      }
      if (mHeightArray[i] == 0) {
        unspecifiedCount++;
      }
    }

    if (remainingHeight < 0) {
      throw new IllegalStateException(
          "Remaining height < 0: " + remainingHeight);
    }

    if (unspecifiedCount > 0) {
      int i = 0;
      for (; unspecifiedCount > 0; unspecifiedCount--) {
        int currentHeight = remainingHeight / unspecifiedCount;
        for (; i < count && unspecifiedCount > 0; ++i) {
          if (mHeightArray[i] == 0) {
            mHeightArray[i] = currentHeight;
            remainingHeight -= currentHeight;
            break;
          }
        }
      }
      if (remainingHeight != 0) {
        throw new IllegalStateException(
            "Remaining height != 0: " + remainingHeight);
      }
    } else if (remainingHeight > 0) {
      mHeightArray[count - 1] += remainingHeight;
    }

    for (int i = 0; i < count; i++) {
      final View child = getChildAt(i);
      int childWidthSpec =
          MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
      int childheightSpec =
          MeasureSpec.makeMeasureSpec(mHeightArray[i], MeasureSpec.EXACTLY);
      child.measure(childWidthSpec, childheightSpec);
    }

    setMeasuredDimension(widthSpecSize, heightSpecSize);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int paddingLeft = getPaddingLeft();
    final int paddingTop = getPaddingTop();
    final int count = getChildCount();

    int childTop = paddingTop;
    for (int i = 0; i < count; i++) {
      View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        int childLeft = paddingLeft;
        child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(),
            childTop + child.getMeasuredHeight());
      }
      childTop += mHeightArray[i];
    }
  }

  @Override
  public LayoutParams generateLayoutParams(AttributeSet attrs) {
    return new FixedLayoutParams(getContext(), attrs);
  }

  @Override
  protected boolean checkLayoutParams(LayoutParams p) {
    return (p instanceof FixedLayoutParams);
  }
}
