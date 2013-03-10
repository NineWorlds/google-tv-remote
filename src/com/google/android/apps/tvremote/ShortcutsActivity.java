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

package com.google.android.apps.tvremote;

import com.google.android.apps.tvremote.util.Action;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Simple activity that displays shortcut commands.
 *
 */
public class ShortcutsActivity extends BaseActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.shortcuts);

    ListView list = (ListView) findViewById(R.id.command_list);
    list.setAdapter(new ShortcutAdapter());
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(
          AdapterView<?> parent, View view, int position, long id) {
        Shortcut shortcut =
            ((ShortcutAdapter) parent.getAdapter()).get(position);
        shortcut.getAction().execute(getCommands());
        finish();
      }
    });
  }

  /**
   * Basic adapter around the array of available shortcuts.
   */
  private class ShortcutAdapter extends BaseAdapter {

    public int getCount() {
      return SHORTCUTS.length;
    }

    public Object getItem(int position) {
      return get(position);
    }

    /**
     * Returns the shortcut at a given position.
     */
    Shortcut get(int position) {
      return SHORTCUTS[position];
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      Shortcut item = get(position);

      int layoutId = R.layout.shortcuts_item;
      if (item.hasColor()) {
        layoutId = R.layout.shortcuts_item_color;
      }

      View view = getLayoutInflater().inflate(layoutId, parent,
            false /* don't attach now */);

      TextView titleView = (TextView) view.findViewById(R.id.text);
      if (item.hasColor()) {
        titleView.setTextColor(getResources().getColor(item.getColor()));
      } else {
        titleView.setTextColor(
            getResources().getColor(android.R.color.primary_text_dark));
      }
      titleView.setText(item.getTitleId());
      if (item.hasDetailId()) {
        ((TextView) view.findViewById(R.id.text_detail)).setText(
            item.getDetailId());
      } else {
        ((TextView) view.findViewById(R.id.text_detail)).setText("");
      }
      return view;
    }
  }

  private static final Shortcut[] SHORTCUTS = {
      new Shortcut(R.string.shortcut_detail_tv, R.string.shortcut_power_on_off,
          Action.POWER_TV),
      new Shortcut(R.string.shortcut_detail_tv, R.string.shortcut_input,
          Action.INPUT_TV),
      new Shortcut(R.string.shortcut_detail_avr, R.string.shortcut_power_on_off,
          Action.POWER_AVR),
      new Shortcut(R.string.shortcut_detail_bd, R.string.shortcut_menu,
          Action.BD_MENU),
      new Shortcut(R.string.shortcut_detail_bd, R.string.shortcut_topmenu,
          Action.BD_TOP_MENU),
      new Shortcut(R.string.shortcut_detail_bd, R.string.shortcut_eject,
          Action.EJECT),
      new Shortcut(R.string.shortcut_color_red, R.string.shortcut_detail_button,
          R.color.red, Action.COLOR_RED),
      new Shortcut(R.string.shortcut_color_green,
          R.string.shortcut_detail_button, R.color.green, Action.COLOR_GREEN),
      new Shortcut(R.string.shortcut_color_yellow,
          R.string.shortcut_detail_button, R.color.yellow, Action.COLOR_YELLOW),
      new Shortcut(R.string.shortcut_color_blue,
          R.string.shortcut_detail_button, R.color.blue, Action.COLOR_BLUE),
      new Shortcut(R.string.shortcut_settings, Action.SETTINGS),
  };
}
