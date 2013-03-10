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

package com.google.android.apps.tvremote.protocol;

import com.google.anymote.Key.Action;
import com.google.anymote.Key.Code;

/**
 * Dummy sender.
 *
 */
public class DummySender implements ICommandSender {

  public void moveRelative(int deltaX, int deltaY) {
  }

  public void click(Action action) {
  }

  public void keyPress(Code key) {
  }

  public void key(Code keycode, Action action) {
  }

  public void flingUrl(String url) {
  }

  public void scroll(int deltaX, int deltaY) {
  }

  public void string(String text) {
  }

}
