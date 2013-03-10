/*
 * Copyright (C) 2011 Google Inc.  All rights reserved.
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

import android.view.Menu;
import android.view.MenuItem.OnMenuItemClickListener;

/**
 * Singleton that provides configurable functionality.
 *
 */
public class ApplicationVariantConfig {
  private MenuInitializer menuInitializer;

  private static ApplicationVariantConfig INSTANCE;

  private ApplicationVariantConfig() {
  }

  public static synchronized ApplicationVariantConfig getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new ApplicationVariantConfig();
    }
    return INSTANCE;
  }

  public MenuInitializer getMenuInitializer() {
    return menuInitializer != null ? menuInitializer : new MenuInitializer() {
      public OnMenuItemClickListener addMenuItems(Menu menu) {
        return null;
      }
    };
  }

  public void setMenuInitializer(MenuInitializer extender) {
    menuInitializer = extender;
  }
}
