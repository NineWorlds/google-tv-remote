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

import com.google.polo.exception.PoloException;
import com.google.polo.pairing.ClientPairingSession;
import com.google.polo.pairing.PairingContext;
import com.google.polo.pairing.PairingListener;
import com.google.polo.pairing.PairingSession;
import com.google.polo.pairing.message.EncodingOption;
import com.google.polo.ssl.DummySSLSocketFactory;
import com.google.polo.wire.PoloWireInterface;
import com.google.polo.wire.WireFormat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import java.io.IOException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * An Activity that handles pairing process.
 *
 * Pairing activity establishes and handles Polo pairing session. If needed,
 * it displays dialog to enter secret code.
 *
 */
public class PairingActivity extends CoreServiceActivity {

  private static final String LOG_TAG = "PairingActivity";
  private static final String EXTRA_REMOTE_DEVICE = "remote_device";
  private static final String EXTRA_PAIRING_RESULT = "pairing_result";
  private static final String REMOTE_NAME = Build.MANUFACTURER + " " +
      Build.MODEL;

  /**
   * Result for pairing failure due to connection problem.
   */
  public static final int RESULT_CONNECTION_FAILED = RESULT_FIRST_USER;

  /**
   * Result for pairing failure due to invalid code or protocol error.
   */
  public static final int RESULT_PAIRING_FAILED = RESULT_FIRST_USER + 1;

  /**
   * Enumeration that encapsulates all valid pairing results.
   */
  private enum Result {
    /**
     * Pairing successful.
     */
    SUCCEEDED(Activity.RESULT_OK),
    /**
     * Pairing failed - connection problem.
     */
    FAILED_CONNECTION(PairingActivity.RESULT_CONNECTION_FAILED),
    /**
     * Pairing failed - canceled.
     */
    FAILED_CANCELED(Activity.RESULT_CANCELED),
    /**
     * Pairing failed - invalid secret.
     */
    FAILED_SECRET(PairingActivity.RESULT_PAIRING_FAILED);

    private final int resultCode;

    Result(int resultCode) {
      this.resultCode = resultCode;
    }
  }

  private Handler handler;

  /**
   * Pairing dialog.
   */
  private AlertDialog alertDialog;

  private PairingClientThread pairing;

  private ProgressDialog progressDialog;
  private RemoteDevice remoteDevice;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    handler = new Handler();

    progressDialog = buildProgressDialog();
    progressDialog.show();

    remoteDevice = getIntent().getParcelableExtra(EXTRA_REMOTE_DEVICE);
    if (remoteDevice == null) {
      throw new IllegalStateException();
    }
  }

  @Override
  protected void onPause() {
    if (pairing != null) {
      pairing.cancel();
      pairing = null;
    }
    hideKeyboard();
    super.onPause();
  }

  public static Intent createIntent(Context context, RemoteDevice remoteDevice) {
    Intent intent = new Intent(context, PairingActivity.class);
    intent.putExtra(EXTRA_REMOTE_DEVICE, remoteDevice);
    return intent;
  }

  private void startPairing() {
    if (pairing != null) {
      Log.v(LOG_TAG, "Already pairing - cancel first.");
      return;
    }
    Log.v(LOG_TAG, "Starting pairing with " + remoteDevice);
    pairing = new PairingClientThread();
    new Thread(pairing).start();
  }

  private AlertDialog createPairingDialog(final PairingClientThread client) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View view =
        LayoutInflater.from(this).inflate(R.layout.pairing, null);
    final EditText pinEditText =
        (EditText) view.findViewById(R.id.pairing_pin_entry);

    builder
        .setPositiveButton(
            R.string.pairing_ok, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                alertDialog = null;
                client.setSecret(pinEditText.getText().toString());
              }
            })
        .setNegativeButton(
            R.string.pairing_cancel, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                alertDialog = null;
                client.cancel();
              }
            })
        .setCancelable(false)
        .setTitle(R.string.pairing_label)
        .setMessage(remoteDevice.getName())
        .setView(view);
    return builder.create();
  }

  private void finishedPairing(Result result) {
    Intent resultIntent = new Intent();
    resultIntent.putExtra(EXTRA_PAIRING_RESULT, result);
    setResult(result.resultCode);
    finish();
  }

  /**
   * Pairing client thread, that handles pairing logic.
   *
   */
  private final class PairingClientThread extends Thread {
    private String secret;
    private boolean isCancelling;

    public synchronized void setSecret(String secretEntered) {
      if (secret != null) {
        throw new IllegalStateException("Secret already set: " + secret);
      }
      secret = secretEntered;
      notify();
    }

    public void cancel() {
      synchronized (this) {
        Log.d(LOG_TAG, "Cancelling: " + this);
        isCancelling = true;
        notify();
      }
      try {
        join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    private synchronized String getSecret() {
      if (isCancelling) {
        return null;
      }
      if (secret != null) {
        return secret;
      }
      try {
        wait();
      } catch (InterruptedException e) {
        Log.d(LOG_TAG, "Exception occurred", e);
        return null;
      }
      return secret;
    }

    @Override
    public void run() {
      Result result = Result.FAILED_CONNECTION;
      try {
        SSLSocketFactory socketFactory;
        try {
          socketFactory = DummySSLSocketFactory.fromKeyManagers(
              getKeyStoreManager().getKeyManagers());
        } catch (GeneralSecurityException e) {
          throw new IllegalStateException("Cannot build socket factory", e);
        }

        SSLSocket socket;
        try {
          socket = (SSLSocket) socketFactory.createSocket(
              remoteDevice.getAddress(), remoteDevice.getPort());
        } catch (UnknownHostException e) {
          return;
        } catch (IOException e) {
          return;
        }

        PairingContext context;
        try {
          context = PairingContext.fromSslSocket(socket, false);
        } catch (PoloException e) {
          return;
        } catch (IOException e) {
          return;
        }

        PoloWireInterface protocol =
            WireFormat.PROTOCOL_BUFFERS.getWireInterface(context);
        ClientPairingSession pairingSession =
            new ClientPairingSession(protocol, context, "AnyMote",
                REMOTE_NAME);

        EncodingOption hexEnc =
            new EncodingOption(
                EncodingOption.EncodingType.ENCODING_HEXADECIMAL, 4);
        pairingSession.addInputEncoding(hexEnc);
        pairingSession.addOutputEncoding(hexEnc);

        PairingListener listener = new PairingListener() {
          public void onSessionEnded(PairingSession session) {
            Log.d(LOG_TAG, "onSessionEnded: " + session);
          }

          public void onSessionCreated(PairingSession session) {
            Log.d(LOG_TAG, "onSessionCreated: " + session);
          }

          public void onPerformOutputDeviceRole(PairingSession session,
              byte[] gamma) {
            Log.d(LOG_TAG, "onPerformOutputDeviceRole: " + session + ", "
                + session.getEncoder().encodeToString(gamma));
          }

          public void onPerformInputDeviceRole(PairingSession session) {
            showPairingDialog(PairingClientThread.this);

            Log.d(LOG_TAG, "onPerformInputDeviceRole: " + session);
            String secret = getSecret();
            Log.d(LOG_TAG, "Got: " + secret + " " + isCancelling);
            if (!isCancelling && secret != null) {
              try {
                byte[] secretBytes = session.getEncoder().decodeToBytes(secret);
                session.setSecret(secretBytes);
              } catch (IllegalArgumentException exception) {
                Log.d(LOG_TAG, "Exception while decoding secret: ", exception);
                session.teardown();
              } catch (IllegalStateException exception) {
                // ISE may be thrown when session is currently terminating
                Log.d(LOG_TAG, "Exception while setting secret: ", exception);
                session.teardown();
              }
            } else {
              session.teardown();
            }
          }

          public void onLogMessage(LogLevel level, String message) {
            Log.d(LOG_TAG, "Log: " + message + " (" + level + ")");
          }
        };

        boolean ret = pairingSession.doPair(listener);
        if (ret) {
          Log.d(LOG_TAG, "Success");
          getKeyStoreManager().storeCertificate(context.getServerCertificate());
          result = Result.SUCCEEDED;
        } else if (isCancelling) {
          result = Result.FAILED_CANCELED;
        } else {
          result = Result.FAILED_SECRET;
        }
      } finally {
        sendPairingResult(result);
      }
    }
  }

  private void showPairingDialog(final PairingClientThread client) {
    handler.post(new Runnable() {
      public void run() {
        dismissProgressDialog();
        if (pairing == null) {
          return;
        }
        alertDialog = createPairingDialog(client);
        alertDialog.show();

        // Focus and show keyboard
        View pinView = alertDialog.findViewById(R.id.pairing_pin_entry);
        pinView.requestFocus();
        showKeyboard();
      }
    });
  }

  private void sendPairingResult(final Result pairingResult) {
    handler.post(new Runnable() {
      public void run() {
        if (alertDialog != null) {
          hideKeyboard();
          alertDialog.dismiss();
        }
        finishedPairing(pairingResult);
      }
    });
  }

  private ProgressDialog buildProgressDialog() {
    ProgressDialog dialog = new ProgressDialog(this);
    dialog.setMessage(getString(R.string.pairing_waiting));
    dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
      public boolean onKey(
          DialogInterface dialogInterface, int which, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
          cancelPairing();
          return true;
        }
        return false;
      }
    });
    dialog.setButton(getString(R.string.pairing_cancel),
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialogInterface, int which) {
            cancelPairing();
          }
        });
    return dialog;
  }

  @Override
  protected void onServiceAvailable(CoreService coreService) {
    startPairing();
  }

  @Override
  protected void onServiceDisconnecting(CoreService coreService) {
    cancelPairing();
  }

  private void cancelPairing() {
    if (pairing != null) {
      pairing.cancel();
      pairing = null;
    }
    dismissProgressDialog();
    finishedPairing(Result.FAILED_CANCELED);
  }

  private void dismissProgressDialog() {
    if (progressDialog != null) {
      progressDialog.dismiss();
      progressDialog = null;
    }
  }

  private void hideKeyboard() {
    InputMethodManager manager = (InputMethodManager) getSystemService(
        Context.INPUT_METHOD_SERVICE);
    manager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
  }

  private void showKeyboard() {
    InputMethodManager manager = (InputMethodManager) getSystemService(
        Context.INPUT_METHOD_SERVICE);
    manager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
  }
}
