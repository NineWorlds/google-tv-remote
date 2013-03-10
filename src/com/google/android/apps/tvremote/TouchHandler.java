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

import com.google.android.apps.tvremote.backport.ScaleGestureDetector;
import com.google.android.apps.tvremote.backport.ScaleGestureDetectorFactory;
import com.google.android.apps.tvremote.protocol.ICommandSender;
import com.google.android.apps.tvremote.util.Action;

import android.os.CountDownTimer;
import android.view.MotionEvent;
import android.view.View;

/**
 * The touchpad logic.
 *
 */
public final class TouchHandler implements View.OnTouchListener {
  /**
   * Defines the kind of events this handler is supposed to generate.
   */
  private final Mode mode;

  /**
   * Interface to send commands during a touch sequence.
   */
  private final ICommandSender commands;

  /**
   * The current touch sequence.
   */
  private Sequence state;

  /**
   * {@code true} if the touch handler is active.
   */
  private boolean isActive;

  /**
   * Scale gesture detector.
   */
  private final ScaleGestureDetector scaleGestureDetector;

  private final float zoomThreshold;

  /**
   * Max thresholds for a sequence to be considered a click.
   */
  private static final int CLICK_DISTANCE_THRESHOLD_SQUARE = 30 * 30;
  private static final int CLICK_TIME_THRESHOLD = 500;
  private static final float SCROLLING_FACTOR = 0.2f;

  /**
   * Threshold to send a scroll event.
   */
  private static final int SCROLL_THRESHOLD = 2;

  /**
   * Thresholds for multitouch gestures.
   */
  private static final float MT_SCROLL_BEGIN_DIST_THRESHOLD_SQR = 20.0f * 20.0f;
  private static final float MT_SCROLL_BEGIN_THRESHOLD = 1.2f;
  private static final float MT_SCROLL_END_THRESHOLD = 1.4f;
  private static final float MT_ZOOM_SCALE_THRESHOLD = 1.8f;

  /**
   * Describes the way touches should be interpreted.
   */
  public enum Mode {
    POINTER,
    POINTER_MULTITOUCH,
    SCROLL_VERTICAL,
    SCROLL_HORIZONTAL,
    ZOOM_VERTICAL
  }

  public TouchHandler(View view, Mode mode, ICommandSender commands) {
    if (Mode.POINTER_MULTITOUCH.equals(mode)) {
      this.scaleGestureDetector = ScaleGestureDetectorFactory
          .createScaleGestureDetector(view, new MultitouchHandler());
      this.mode = Mode.POINTER;
    } else {
      this.scaleGestureDetector = null;
      this.mode = mode;
    }

    this.commands = commands;
    isActive = true;
    zoomThreshold = view.getResources().getInteger(R.integer.zoom_threshold);
    view.setOnTouchListener(this);
  }

  public boolean onTouch(View v, MotionEvent event) {
    if (!isActive) {
      return false;
    }

    if (scaleGestureDetector != null) {
      scaleGestureDetector.onTouchEvent(event);
      if (scaleGestureDetector.isInProgress()) {
        if (state != null) {
          state.cancelDownTimer();
          state = null;
        }
        return true;
      }
    }

    int x = (int) event.getX();
    int y = (int) event.getY();
    long timestamp = event.getEventTime();
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        state = new Sequence(x, y, timestamp);
        return true;

      case MotionEvent.ACTION_CANCEL:
        state = null;
        return true;

      case MotionEvent.ACTION_UP:
        boolean handled = state != null && state.handleUp(x, y, timestamp);
        state = null;
        return handled;

      case MotionEvent.ACTION_MOVE:
        return state != null && state.handleMove(x, y, timestamp);

      default:
        return false;
    }
  }

  /**
   * {@code true} activates the touch handler, {@code false} deactivates it.
   */
  public void setActive(boolean active) {
    isActive = active;
  }

  /**
   * Stores parameters of a touch sequence, i.e. down - move(s) - up and handles
   * new touch events.
   */
  private class Sequence {

    /**
     * Location of the sequence's start event.
     */
    private final int refX, refY;

    /**
     * Location of the last touch event.
     */
    private int lastX, lastY;
    private long lastTimestamp;

    /**
     * Delta Y accumulated across several touches.
     */
    private int accuY;

    /**
     * Timer that expires when a click down has to be sent.
     */
    private CountDownTimer clickDownTimer;

    /**
     * {@code true} if a click down has been sent.
     */
    private boolean clickDownSent;

    public Sequence(int x, int y, long timestamp) {
      refX = x;
      refY = y;
      clickDownSent = false;
      setLastTouch(x, y, timestamp);
      if (mode == Mode.POINTER) {
        startClickDownTimer();
      }
    }

    private void setLastTouch(int x, int y, long timestamp) {
      lastX = x;
      lastY = y;
      lastTimestamp = timestamp;
    }

    /**
     * Returns {@code true} if a sequence is a movement.
     */
    private boolean isMove(int x, int y) {
      int distance = ((refX - x) * (refX - x)) + ((refY - y) * (refY - y));
      return distance > CLICK_DISTANCE_THRESHOLD_SQUARE;
    }

    /**
     * Starts a timer that will expire after
     * {@link TouchHandler#CLICK_TIME_THRESHOLD} and start to send a click down
     * event if the touch event cannot be interpreted as a movement.
     */
    private void startClickDownTimer() {
      clickDownTimer = new CountDownTimer(CLICK_TIME_THRESHOLD,
          CLICK_TIME_THRESHOLD) {
        @Override
        public void onTick(long arg0) {
          // Nothing to do.
        }

        @Override
        public void onFinish() {
          clickDown();
        }
      };
      clickDownTimer.start();
    }

    /**
     * Cancels the timer, no-op if there is no timer available.
     *
     * @return {@code true} if there was a timer to cancel
     */
    private boolean cancelDownTimer() {
      if (clickDownTimer != null) {
        clickDownTimer.cancel();
        clickDownTimer = null;
        return true;
      }
      return false;
    }

    /**
     * Sends a click down message.
     */
    private void clickDown() {
      Action.CLICK_DOWN.execute(commands);
      clickDownSent = true;
    }

    /**
     * Handles a touch up.
     *
     * A click will be issued if the initial touch of the sequence is close
     * enough both timewise and distance-wise.
     *
     * @param   x             an integer representing the touch's x coordinate
     * @param   y             an integer representing the touch's y coordinate
     * @param   timestamp     a long representing the touch's time
     * @return  {@code true} if a click was issued
     */
    public boolean handleUp(int x, int y, long timestamp) {
      if (mode != Mode.POINTER) {
        return true;
      }
      // If a click down is waiting, send it.
      if (cancelDownTimer()) {
        clickDown();
      }
      if (clickDownSent) {
        Action.CLICK_UP.execute(commands);
      }
      return true;
    }

    /**
     * Handles a touch move.
     *
     * Depending on the initial touch of the sequence, this will result in a
     * pointer move or in a scrolling action.
     *
     * @param   x             an integer representing the touch's x coordinate
     * @param   y             an integer representing the touch's y coordinate
     * @param   timestamp     a long representing the touch's time
     * @return  {@code true} if any action was taken
     */
    public boolean handleMove(int x, int y, long timestamp) {
      if (mode == Mode.POINTER) {
        if (!isMove(x, y)) {
          // Stand still while it's not a move to avoid a movement when a click
          // is performed.
        } else {
          cancelDownTimer();
        }
      }

      long timeDelta = timestamp - lastTimestamp;
      int deltaX = x - lastX;
      int deltaY = y - lastY;

      switch(mode) {
        case POINTER:
          commands.moveRelative(deltaX, deltaY);
          break;

        case SCROLL_VERTICAL:
          if (shouldTriggerScrollEvent(deltaY)) {
            commands.scroll(0, deltaY);
          }
          break;

        case SCROLL_HORIZONTAL:
          if (shouldTriggerScrollEvent(deltaX)) {
            commands.scroll(deltaX, 0);
          }
          break;

        case ZOOM_VERTICAL:
          accuY += deltaY;
          if (Math.abs(accuY) >= zoomThreshold) {
            if (accuY < 0) {
              Action.ZOOM_IN.execute(commands);
            } else {
              Action.ZOOM_OUT.execute(commands);
            }
            accuY = 0;
          }
          break;
      }
      setLastTouch(x, y, timestamp);
      return true;
    }
  }

  /**
   * Handles multitouch events to capture zoom and scroll events.
   */
  private class MultitouchHandler
      implements ScaleGestureDetector.OnScaleGestureListener {

    private float lastScrollX;
    private float lastScrollY;
    private boolean isScrolling;

    public boolean onScale(ScaleGestureDetector detector) {
      float scaleFactor = detector.getScaleFactor();
      float deltaX = scaleGestureDetector.getFocusX() - lastScrollX;
      float deltaY = scaleGestureDetector.getFocusY() - lastScrollY;

      toggleScrolling(scaleFactor, deltaX, deltaY);
      float absX = Math.abs(deltaX);
      float signX = Math.signum(deltaX);
      float absY = Math.abs(deltaY);
      float signY = Math.signum(deltaY);
      // If both translations are less than 1
      // pick greater one and align to 1
      if ((absX < 1) && (absY < 1)) {
          if (absX > absY) {
              deltaX = signX;
              deltaY = 0;
          } else {
              deltaX = 0;
              deltaY = signY;
          }
      } else {
          if (absX < 1) {
              deltaX = 0;
          } else {
              deltaX = ((absX - 1) * SCROLLING_FACTOR + 1) * signX;
          }
          if (absY < 1) {
              deltaY = 0;
          } else {
              deltaY = ((absY - 1) * SCROLLING_FACTOR + 1) * signY;
          }
      }

      if (isScrolling) {
        if (shouldTriggerScrollEvent(deltaX)
            || shouldTriggerScrollEvent(deltaY)) {
          executeScrollEvent(deltaX, deltaY);
        }
        return false;
      }

      if (!isWithinInvRange(scaleFactor, MT_ZOOM_SCALE_THRESHOLD)) {
        executeZoomEvent(scaleFactor);
        return true;
      }

      return false;
    }

    public boolean onScaleBegin(ScaleGestureDetector detector) {
      resetScroll();
      return true;
    }

    public void onScaleEnd(ScaleGestureDetector detector) {
      // Do nothing
    }

    /**
     * Resets scrolling mode.
     */
    private void resetScroll() {
      isScrolling = false;
      updateScroll();
    }

    /**
     * Updates last scroll positions.
     */
    private void updateScroll() {
      lastScrollX = scaleGestureDetector.getFocusX();
      lastScrollY = scaleGestureDetector.getFocusY();
    }

    /**
     * Sends zoom event.
     *
     * @param scaleFactor scale factor.
     */
    private void executeZoomEvent(float scaleFactor) {
      resetScroll();
      if (scaleFactor > 1.0f) {
        Action.ZOOM_IN.execute(commands);
      } else {
        Action.ZOOM_OUT.execute(commands);
      }
    }

    /**
     * Sends scroll event.
     */
    private void executeScrollEvent(float deltaX, float deltaY) {
      commands.scroll(Math.round(deltaX), Math.round(deltaY));
      updateScroll();
    }

    /**
     * Enables of disables scrolling, depending on the current state,
     * scale factor, and distance from last registered focus position.
     *
     * mode should be enabled / disabled depending on the speed of dragging
     * vs. scale factor.
     */
    private void toggleScrolling(
        float scaleFactor, float deltaX, float deltaY) {
      if (!isScrolling
          && isWithinInvRange(scaleFactor, MT_SCROLL_BEGIN_THRESHOLD)) {
        float dist = deltaX * deltaX + deltaY * deltaY;
        if (dist > MT_SCROLL_BEGIN_DIST_THRESHOLD_SQR) {
          isScrolling = true;
        }
      } else if (isScrolling
          && !isWithinInvRange(scaleFactor, MT_SCROLL_END_THRESHOLD)) {
        // Stop scrolling if zooming occurs.
        isScrolling = false;
      }
    }

    /**
     * Returns {@code true} if {@code (1/upperLimit) &lt; scaleFactor &lt;
     * upperLimit}
     */
    private boolean isWithinInvRange(float scaleFactor, float upperLimit) {
      if (upperLimit < 1.0f) {
        throw new IllegalArgumentException("Upper limit < 1.0f: " + upperLimit);
      }
      return 1.0f / upperLimit < scaleFactor && scaleFactor < upperLimit;
    }
  }

  /**
   * Returns {@code true} if the delta measured when scrolling is enough to
   * trigger a scroll event.
   *
   * @param deltaScroll the amount of scroll wanted
   */
  private static boolean shouldTriggerScrollEvent(float deltaScroll) {
    return Math.abs(deltaScroll) >= SCROLL_THRESHOLD;
  }
}
