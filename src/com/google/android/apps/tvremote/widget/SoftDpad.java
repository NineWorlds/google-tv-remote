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
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.widget.ImageView;

/**
 * A widget that imitates a Dpad that would float on top of the UI. Dpad
 * is being simulated as a touch area that recognizes slide gestures or taps
 * for OK. 
 * <p>
 * Make sure you set up a {@link DpadListener} to handle the events.
 * <p>
 * To position the dpad on the screen, use {@code paddingTop} or
 * {@code PaddingBottom}. If you use {@code PaddingBottom}, the widget will be
 * aligned on the bottom of the screen minus the padding.
 *
 */
public final class SoftDpad extends ImageView {

  /**
   * Interface that receives the commands.
   */
  public interface DpadListener {

    /**
     * Called when the Dpad was clicked.
     */
    void onDpadClicked();

    /**
     * Called when the Dpad was moved in a given direction, and with which
     * action (pressed or released).
     *
     * @param direction the direction in which the Dpad was moved
     * @param pressed   {@code true} to represent an event down
     */
    void onDpadMoved(Direction direction, boolean pressed);
  }

  /**
   * Tangent of the angle used to detect a direction.
   * <p>
   * The angle should be less that 45 degree. Pre-calculated for performance.
   */
  private static final double TAN_DIRECTION = Math.tan(Math.PI / 4);

  /**
   * Different directions where the Dpad can be moved.
   */
  public enum Direction {
    /**
     * @hide
     */
    IDLE(false),
    CENTER(false),
    RIGHT(true),
    LEFT(true),
    UP(true),
    DOWN(true);

    final boolean isMove;

    Direction(boolean isMove) {
      this.isMove = isMove;
    }
  }

  /**
   * Coordinates of the center of the Dpad in its initial position.
   */
  private int centerX;
  private int centerY;

  /**
   * Current dpad image offset.
   */
  private int offsetX;
  private int offsetY;

  /**
   * Radius of the Dpad's touchable area.
   */
  private int radiusTouchable;

  /**
   * Radius of the area around touchable area where events get caught and ignored.
   */
  private int radiusIgnore;

  /**
   * Percentage of half of drawable's width that is the radius.
   */
  private float radiusPercent;

  /**
   * OK area expressed as percentage of half of drawable's width.
   */
  private float radiusPercentOk;

  /**
   * Radius of the area handling events, should be &gt;= {@code radiusPercent}
   */
  private float radiusPercentIgnore;

  /**
   * Coordinates of the first touch event on a sequence of movements.
   */
  private int originTouchX;
  private int originTouchY;

  /**
   * Touch bounds.
   */
  private int clickRadiusSqr;

  /**
   * {@code true} if the Dpad is capturing the events.
   */
  private boolean isDpadFocused;

  /**
   * Direction in which the DPad is, or {@code null} if idle.
   */
  private Direction dPadDirection;

  private DpadListener listener;

  /**
   * Vibrator.
   */
  private final Vibrator vibrator;

  public SoftDpad(Context context, AttributeSet attrs) {
    super(context, attrs);
    vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

    TypedArray a = context.obtainStyledAttributes(attrs,
        R.styleable.SoftDpad);

    try {
      radiusPercent = a.getFloat(R.styleable.SoftDpad_radius_percent, 100.0f);
      radiusPercentOk = a.getFloat(R.styleable.SoftDpad_radius_percent_ok,
          20.0f);
      radiusPercentIgnore = a.getFloat(
          R.styleable.SoftDpad_radius_percent_ignore_touch, radiusPercent);

      if (radiusPercentIgnore < radiusPercent) {
        throw new IllegalStateException(
            "Ignored area smaller than touchable area");
      }
    } finally {
      a.recycle();
    }

    initialize();
  }

  private void initialize() {
    isDpadFocused = false;
    setScaleType(ScaleType.CENTER_INSIDE);
    dPadDirection = Direction.IDLE;
  }

  public int getCenterX() {
    return centerX;
  }

  public int getCenterY() {
    return centerY;
  }

  public void setDpadListener(DpadListener listener) {
    this.listener = listener;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    prepare();
  }

  /**
   * Initializes the widget. Must be called after the view has been inflated.
   */
  public void prepare() {
    int w = getWidth() - getPaddingLeft() - getPaddingRight();
    radiusTouchable = (int) (radiusPercent * w / 200);
    radiusIgnore = (int) (radiusPercentIgnore * w / 200);
    centerX = getWidth() / 2;
    centerY = getHeight() / 2;

    clickRadiusSqr = (int) (radiusPercentOk * w / 200);
    clickRadiusSqr *= clickRadiusSqr;

    center();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int x = (int) event.getX();
    int y = (int) event.getY();
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        if (isEventOutsideIgnoredArea(x, y)) {
          return false;
        }
        if (!isEventInsideTouchableArea(x, y)) {
          return true;
        }
        handleActionDown(x, y);
        return true;

      case MotionEvent.ACTION_MOVE:
        if (isDpadFocused) {
          handleActionMove(x, y);
          return true;
        }
        break;

      case MotionEvent.ACTION_UP:
        if (isDpadFocused) {
          handleActionUp(x, y);
        }
        break;
    }
    return false;
  }

  private void handleActionDown(int x, int y) {
    dPadDirection = Direction.IDLE;
    isDpadFocused = true;
    originTouchX = x;
    originTouchY = y;
  }

  private void handleActionMove(int x, int y) {
    int dx = x - originTouchX;
    int dy = y - originTouchY;

    Direction move = getDirection(dx, dy);
    if (move.isMove && !dPadDirection.isMove) {
      sendEvent(move, true, true);
      dPadDirection = move;
    }
  }

  private void handleActionUp(int x, int y) {
    boolean playSound = true;
    handleActionMove(x, y);
    if (dPadDirection.isMove) {
      sendEvent(dPadDirection, false, playSound);
    } else {
      onCenterAction();
    }
    center();
  }

  /**
   * Centers the Dpad.
   */
  private void center() {
    isDpadFocused = false;
    dPadDirection = Direction.IDLE;
  }

  /**
   * Quickly dismiss a touch event if it's not in a square around the idle
   * position of the Dpad.
   * <p>
   * May return {@code false} for an event outside the DPad.
   *
   * @param x x-coordinate form the top left of the screen
   * @param y y-coordinate form the top left of the screen
   * @param r radius of the circle we are testing
   * @return {@code true} if event is outside the Dpad
   */
  private boolean quickDismissEvent(int x, int y, int r) {
    return (x < getCenterX() - r ||
        x > getCenterX() + r ||
        y < getCenterY() - r ||
        y > getCenterY() + r);
  }

  /**
   * Returns {@code true} if the touch event is outside a circle centered on the
   * idle position of the Dpad and of a given radius
   *
   * @param x x-coordinate of the touch event form the top left of the screen
   * @param y y-coordinate of the touch event form the top left of the screen
   * @param r radius of the circle we are testing.
   * @return {@code true} if event is outside designated zone
   */
  private boolean isEventOutside(int x, int y, int r) {
    if (quickDismissEvent(x, y ,r)) {
      return true;
    }
    int dx = (x - getCenterX()) * (x - getCenterX());
    int dy = (y - getCenterY()) * (y - getCenterY());
    return (dx + dy) > r * r;
  }

  /**
   * Returns {@code true} if the touch event is outside the touchable area
   * where the Dpad handles events.
   *
   * @param x x-coordinate form the top left of the screen
   * @param y y-coordinate form the top left of the screen
   * @return {@code true} if outside
   */
  public boolean isEventOutsideIgnoredArea(int x, int y) {
    return isEventOutside(x, y, radiusIgnore);
  }

  /**
   * Returns {@code true} if the touch event is outside the area where the Dpad
   * is when idle, the touchable area.
   *
   * @param x x-coordinate form the top left of the screen
   * @param y y-coordinate form the top left of the screen
   * @return {@code true} if outside
   */
  public boolean isEventInsideTouchableArea(int x, int y) {
    return !isEventOutside(x, y, radiusTouchable);
  }

  /**
   * Returns {@code true} if the dpad has moved enough from its idle position to
   * stop being interpreted as a click.
   *
   * @param dx movement along the x-axis from the idle position
   * @param dy movement along the y-axis from the idle position
   * @return {@code true} if not a click event
   */
  private boolean isClick(int dx, int dy) {
    return (dx * dx + dy * dy) < clickRadiusSqr;
  }

  /**
   * Returns a direction for the movement.
   *
   * @param dx x-coordinate form the idle position of Dpad
   * @param dy y-coordinate form the idle position of Dpad
   * @return a direction, or unknown if the direction is not clear enough
   */
  private Direction getDirection(int dx, int dy) {
    if (isClick(dx, dy)) {
      return Direction.CENTER;
    }
    if (dx == 0) {
      if (dy > 0) {
        return Direction.DOWN;
      } else {
        return Direction.UP;
      }
    }
    if (dy == 0) {
      if (dx > 0) {
        return Direction.RIGHT;
      } else {
        return Direction.LEFT;
      }
    }
    float ratioX = (float) (dy) / (float) (dx);
    float ratioY = (float) (dx) / (float) (dy);
    if (Math.abs(ratioX) < TAN_DIRECTION) {
      if (dx > 0) {
        return Direction.RIGHT;
      } else {
        return Direction.LEFT;
      }
    }
    if (Math.abs(ratioY) < TAN_DIRECTION) {
      if (dy > 0) {
        return Direction.DOWN;
      } else {
        return Direction.UP;
      }
    }
    return Direction.CENTER;
  }

  /**
   * Sends a DPad event if the Dpad is in the right position.
   *
   * @param move the direction in witch the event should be sent.
   * @param pressed {@code true} if touch just begun.
   * @param playSound {@code true} if click sound should be played.
   */
  private void sendEvent(Direction move, boolean pressed,
      boolean playSound) {
    if (listener != null) {
      switch (move) {
        case UP:
        case DOWN:
        case LEFT:
        case RIGHT:
          listener.onDpadMoved(move, pressed);
          if (playSound) {
            if (pressed) {
              vibrator.vibrate(getResources().getInteger(
                  R.integer.dpad_vibrate_time));
            }
            playSound();
          }
      }
    }
  }

  /**
   * Actions performed when the user click on the Dpad.
   */
  private void onCenterAction() {
    if (listener != null) {
      listener.onDpadClicked();
      vibrator.vibrate(getResources().getInteger(
          R.integer.dpad_vibrate_time));
      playSound();
    }
  }

  /**
   * Plays a sound when sending a key.
   */
  private void playSound() {
    playSoundEffect(SoundEffectConstants.CLICK);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.translate(offsetX, offsetY);
    super.onDraw(canvas);
  }
}
