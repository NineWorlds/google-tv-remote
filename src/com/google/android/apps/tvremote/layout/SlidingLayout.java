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

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.Scroller;

/**
 * SlidingLayout that allows swapping between arbitrary number of child views.
 */
public final class SlidingLayout extends ViewGroup {

  private static final int INVALID_SCREEN = -1;

  private int mDefaultScreen;
  private boolean mFirstLayout = true;

  private int mCurrentScreen;
  private int mNextScreen = INVALID_SCREEN;
  private Scroller mScroller;

  private WorkspaceOvershootInterpolator mScrollInterpolator;

  /**
   * Tension interpolator for nice scroll animation.
   */
  private static class WorkspaceOvershootInterpolator implements Interpolator {
    private static final float DEFAULT_TENSION = 1.3f;
    private float mTension;

    public WorkspaceOvershootInterpolator() {
      mTension = DEFAULT_TENSION;
    }

    public void disableSettle() {
      mTension = 0.f;
    }

    public float getInterpolation(float t) {
      t -= 1.0f;
      return t * t * ((mTension + 1) * t + mTension) + 1.0f;
    }
  }

  /**
   * Used to inflate the Workspace from XML.
   *
   * @param context
   *          The application's context.
   * @param attrs
   *          The attribtues set containing the Workspace's customization
   *          values.
   */
  public SlidingLayout(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  /**
   * Used to inflate the Workspace from XML.
   *
   * @param context
   *          The application's context.
   * @param attrs
   *          The attribtues set containing the Workspace's customization
   *          values.
   * @param defStyle
   *          Unused.
   */
  public SlidingLayout(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initSlidingLayout();
  }

  /**
   * Initializes various states for this workspace.
   */
  private void initSlidingLayout() {
    Context context = getContext();
    mScrollInterpolator = new WorkspaceOvershootInterpolator();
    mScroller = new Scroller(context, mScrollInterpolator);
    mCurrentScreen = mDefaultScreen;
  }

  /**
   * Sets the current screen.
   *
   * @param currentScreen
   */
  public void setCurrentScreen(int currentScreen) {
    if (!mScroller.isFinished()) {
      mScroller.abortAnimation();
    }
    mCurrentScreen = Math.max(0, Math.min(currentScreen, getChildCount() - 1));
    scrollTo(mCurrentScreen * getWidth(), 0);
    invalidate();
  }

  @Override
  public void computeScroll() {
    if (mScroller.computeScrollOffset()) {
      scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
    } else if (mNextScreen != INVALID_SCREEN) {
      mCurrentScreen = Math.max(0, Math.min(mNextScreen, getChildCount() - 1));
      mNextScreen = INVALID_SCREEN;
    }
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    boolean restore = false;
    int restoreCount = 0;

    boolean fastDraw = mNextScreen == INVALID_SCREEN;

    if (fastDraw) {
      drawChild(canvas, getChildAt(mCurrentScreen), getDrawingTime());
    } else {
      final long drawingTime = getDrawingTime();
      final float scrollPos = (float) getScrollX() / getWidth();
      final int leftScreen = (int) scrollPos;
      final int rightScreen = leftScreen + 1;
      if (leftScreen >= 0) {
        drawChild(canvas, getChildAt(leftScreen), drawingTime);
      }
      if (scrollPos != leftScreen && rightScreen < getChildCount()) {
        drawChild(canvas, getChildAt(rightScreen), drawingTime);
      }
    }

    if (restore) {
      canvas.restoreToCount(restoreCount);
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    computeScroll();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    final int width = MeasureSpec.getSize(widthMeasureSpec);
    final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    if (widthMode != MeasureSpec.EXACTLY) {
      throw new IllegalStateException(
          "Workspace can only be used in EXACTLY mode.");
    }

    final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    if (heightMode != MeasureSpec.EXACTLY) {
      throw new IllegalStateException(
          "Workspace can only be used in EXACTLY mode.");
    }

    // The children are given the same width and height as the workspace
    final int count = getChildCount();
    for (int i = 0; i < count; i++) {
      getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
    }

    if (mFirstLayout) {
      setHorizontalScrollBarEnabled(false);
      scrollTo(mCurrentScreen * width, 0);
      setHorizontalScrollBarEnabled(true);
      mFirstLayout = false;
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right,
      int bottom) {
    int nextChildLeft = 0;
    final int count = getChildCount();
    for (int i = 0; i < count; i++) {
      final View child = getChildAt(i);
      if (child.getVisibility() != View.GONE) {
        final int childWidth = child.getMeasuredWidth();
        child.layout(nextChildLeft, 0, nextChildLeft + childWidth, child
            .getMeasuredHeight());
        nextChildLeft += childWidth;
      }
    }
  }

  public void snapToScreen(int whichScreen) {
    whichScreen = Math.max(0, Math.min(whichScreen, getChildCount() - 1));
    mNextScreen = whichScreen;

    View focusedChild = getFocusedChild();
    if (focusedChild != null && whichScreen != mCurrentScreen
        && focusedChild == getChildAt(mCurrentScreen)) {
      focusedChild.clearFocus();
    }

    final int screenDelta = Math.max(1, Math.abs(whichScreen - mCurrentScreen));
    final int newX = whichScreen * getWidth();
    final int delta = newX - getScrollX();
    int duration = (screenDelta + 1) * 100;

    if (!mScroller.isFinished()) {
      mScroller.abortAnimation();
    }

    mScrollInterpolator.disableSettle();
    duration += 100;

    mScroller.startScroll(getScrollX(), 0, delta, 0, duration);
    invalidate();
  }
}
