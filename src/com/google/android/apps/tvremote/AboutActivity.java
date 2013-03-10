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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * About activity.
 *
 */
public class AboutActivity extends Activity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.about);

    TextView versionTextView = (TextView) findViewById(R.id.version_text);

    String versionString = getString(R.string.unknown_build);
    try {
      PackageInfo info = getPackageManager().getPackageInfo(getPackageName(),
          0 /* basic info */);
      versionString = info.versionName;
    } catch (NameNotFoundException e) {
      // do nothing
    }
    versionTextView.setText(getString(R.string.about_version_title,
        versionString));

    ((Button) findViewById(R.id.button_tos)).setOnClickListener(
        new GoToLinkListener(R.string.tos_link));
    ((Button) findViewById(R.id.button_privacy)).setOnClickListener(
        new GoToLinkListener(R.string.privacy_link));
    ((Button) findViewById(R.id.button_tutorial)).setOnClickListener(
        new OnClickListener() {
          public void onClick(View v) {
            Intent intent = new Intent(AboutActivity.this, TutorialActivity.class);
            startActivity(intent);
          }
        });
  }

  private class GoToLinkListener implements OnClickListener {
    private String link;

    public GoToLinkListener(int linkId) {
      this.link = getString(linkId);
    }

    public void onClick(View view) {
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.setData(Uri.parse(link));
      startActivity(intent);
    }
  }
}
