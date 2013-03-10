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

import android.content.Intent;
import android.os.AsyncTask;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

/**
 * Startup activity that checks if certificates are generated, and if not
 * begins async generation of certificates, and displays remote logo.
 *
 */
public class StartupActivity extends CoreServiceActivity {

  private boolean keystoreAvailable;
  private Button connectButton;

  @Override
  protected void onServiceAvailable(CoreService coreService) {
    // Show UI.
    if (!getKeyStoreManager().hasServerIdentityAlias()) {
      setContentView(R.layout.tutorial);
      connectButton = (Button) findViewById(R.id.tutorial_button);
      connectButton.setEnabled(false);
      connectButton.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) {
          showMainActivity();
        }
      });
      
      new KeystoreInitializerTask(getUniqueId()).execute(getKeyStoreManager());
    } else {
      keystoreAvailable = true;
      showMainActivity();
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    setIntent(intent);
    if (keystoreAvailable) {
      showMainActivity();
    }
  }

  @Override
  protected void onServiceDisconnecting(CoreService coreService) {
    // Do nothing
  }

  private void showMainActivity() {
    Intent intent = new Intent(this, MainActivity.class);
    Intent originalIntent = getIntent();
    if (originalIntent != null) {
      intent.setAction(originalIntent.getAction());
      intent.putExtras(originalIntent);
    }
    startActivity(intent);
  }

  private class KeystoreInitializerTask extends AsyncTask<
      KeyStoreManager, Void, Void> {
    private final String id;

    public KeystoreInitializerTask(String id) {
      this.id = id;
    }

    @Override
    protected Void doInBackground(KeyStoreManager... keyStoreManagers) {
      if (keyStoreManagers.length != 1) {
        throw new IllegalStateException("Only one key store manager expected");
      }
      keyStoreManagers[0].initializeKeyStore(id);
      return null;
    }

    @Override
    protected void onPostExecute(Void result) {
      super.onPostExecute(result);
      keystoreAvailable = true;
      connectButton.setEnabled(true);
    }
  }

  private String getUniqueId() {
    String id = Settings.Secure.getString(getContentResolver(),
        Settings.Secure.ANDROID_ID);
    // null ANDROID_ID is possible on emulator
    return id != null ? id : "emulator";
  }
}
