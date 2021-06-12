/*
 * Copyright (c) 2021 Privateco and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.privateco.clumber;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Pattern;

import com.privateco.clumber.constants.ApplicationConstants;
import com.privateco.clumber.crypto.EncryptionManager;
import com.privateco.clumber.model.MessageEvent;
import com.privateco.clumber.model.Version;
import com.privateco.clumber.util.AppUtil;
import com.privateco.clumber.util.ApplicationSocket;
import io.socket.client.Socket;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private TextInputLayout nicknameTextInputLayout;
    private TextInputLayout secureCodeTextInputLayout;
    private EditText nicknameEditText;
    private EditText secureCodeEditText;
    private Button connectButton;
    private Menu menu;

    private final Socket socket = ApplicationSocket.getInstance().getSocket();
    private boolean isSocketInitialized = false;

    private boolean isInFront;

    // states, should be cleared when exit from MessageActivity
    private boolean isAwaitingFriends = false;
    private boolean isSelfPublicKeyShared = false;
    private String chattingWith = "";
    private boolean shouldOpenMessageActivity = false; // used when app is in background

    private void resetStates() {
        isAwaitingFriends = false;
        isSelfPublicKeyShared = false;
        chattingWith = "";
        shouldOpenMessageActivity = false;
    }

    // dialog shown when awaiting friends to join in the room, related which "socket_entry_await" and "socket_entry_success" events
    private AlertDialog awaitingFriendsAlertDialog;

    // dialog shown when doing public key exchange
    private AlertDialog awaitingKeyExchangeAlertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialize();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectSocket();
        EventBus.getDefault().unregister(this);
    }
    @Override
    public void onResume() {
        super.onResume();
        isInFront = true;

        if (shouldOpenMessageActivity) {
            shouldOpenMessageActivity = false;
            MessageActivity.open(MainActivity.this, nicknameEditText.getText().toString(), chattingWith);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isInFront = false;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        switch (event.getEvent()) {
            case "socket_connected":
                menu.findItem(R.id.connection_ok).setVisible(true);
                break;
            case "socket_connect_error":
                socket.disconnect();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.error_hint_title_socket_connection))
                        .setMessage(getString(R.string.error_hint_description_socket_connection))
                        .setNegativeButton(R.string.action_retry, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                socket.connect();
                            }
                        })
                        .setPositiveButton(R.string.action_exit, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                MainActivity.this.finish();
                            }
                        })
                        .setCancelable(false)
                        .show();
                break;
            case "socket_entry_name_occupied":
                // re-enable connect button
                connectButton.setEnabled(true);

                // show name occupied hint
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.error_hint_title_entry_name_occupied))
                        .setMessage(getString(R.string.error_hint_description_entry_name_occupied))
                        .setPositiveButton(getString(R.string.action_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {}
                        })
                        .show();
                break;
            case "socket_entry_code_occupied":
                // re-enable connect button
                connectButton.setEnabled(true);

                // show code occupied hint
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.error_hint_title_entry_code_occupied))
                        .setMessage(getString(R.string.error_hint_description_entry_code_occupied))
                        .setPositiveButton(getString(R.string.action_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {}
                        })
                        .show();
                break;
            case "socket_entry_await":
                hideKeyboard();
                awaitingFriendsAlertDialog.show();
                break;
            case "socket_entry_success":
                // re-enable connect button
                connectButton.setEnabled(true);

                // clear secure code edit text
                secureCodeEditText.setText("");

                awaitingFriendsAlertDialog.dismiss();

                // share public key
                new SharePublicKeyTask().execute();

                chattingWith = (String) event.getAttachment();
                break;
            case "socket_entry_key_received":
                onSentOrReceivedPublicKey();
                break;
            case "message_activity_destroyed":
                resetStates();
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.main_actions_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_help:
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.hint_title_clumber_introduction)
                        .setMessage(R.string.hint_description_clumber_introduction)
                        .setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {}
                        })
                        .show();
                break;
            case R.id.connection_ok:
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.hint_connection_status)
                        .setMessage(R.string.hint_successfully_connected_to_server)
                        .setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {}
                        })
                        .show();
                break;
        }
        return true;
    }

    private void initialize() {
        bindViews();
        setUpSocketIO();
        connectSocket();
        setUpOnClickListeners();
        setUpTextInputLayouts();
        setUpAwaitingFriendsAlertDialog();
        setUpAwaitingKeyExchangeAlertDialog();
        new CheckForUpdatesTask().execute();
        new ShowNoticeTask().execute();
    }

    private void bindViews() {
        nicknameTextInputLayout = findViewById(R.id.nickNameTextInputLayout);
        secureCodeTextInputLayout = findViewById(R.id.secureCodeTextInputLayout);
        nicknameEditText = findViewById(R.id.nicknameEditText);
        secureCodeEditText = findViewById(R.id.secureCodeEditText);
        connectButton = findViewById(R.id.connectButton);
    }

    private void setUpSocketIO() {
        isSocketInitialized = true;
    }

    private void connectSocket() {
        if (isSocketInitialized && !socket.connected()) {
            socket.connect();
        }
    }

    private void disconnectSocket() {
        if (isSocketInitialized && socket.connected()) {
            socket.disconnect();
        }
    }

    private void setUpOnClickListeners() {
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String nickname = nicknameEditText.getText().toString();
                String secureCode = secureCodeEditText.getText().toString();

                // check nickname and secure code
                boolean ok = true;
                if (!checkNickname(nickname)) {
                    nicknameTextInputLayout.setErrorEnabled(true);
                    nicknameTextInputLayout.setError(getString(R.string.error_hint_description_invalid_nickname));
                    ok = false;
                }
                if (!checkSecureCode(secureCode)) {
                    secureCodeTextInputLayout.setErrorEnabled(true);
                    secureCodeTextInputLayout.setError(getString(R.string.error_hint_description_invalid_secure_code));
                    ok = false;
                }
                if (!ok) {
                    return;
                }

                // disable connect button to prevent multiple requests
                connectButton.setEnabled(false);

                // transform nickname to lower case and post
                JSONObject obj = new JSONObject();
                try {
                    obj.put("name", nickname.toLowerCase());
                    obj.put("code", secureCode);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                socket.emit("entry", obj);
            }
        });
    }

    private void setUpAwaitingFriendsAlertDialog() {
        awaitingFriendsAlertDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle(getString(R.string.hint_title_entry_awaiting_friends))
                .setMessage(getString(R.string.hint_description_entry_awaiting_friends))
                .setView(getLayoutInflater().inflate(R.layout.simple_progress_bar, null))
                .setNegativeButton(getString(R.string.action_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // re-enable connect button
                        connectButton.setEnabled(true);

                        // emit exit event
                        socket.emit("exit");
                    }
                })
                .setCancelable(false)
                .create();

        awaitingFriendsAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                // update awaiting friends status
                isAwaitingFriends = true;
            }
        });

        awaitingFriendsAlertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                // // update awaiting friends status
                isAwaitingFriends = false;
            }
        });
    }

    private void setUpAwaitingKeyExchangeAlertDialog() {
        awaitingKeyExchangeAlertDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle(getString(R.string.hint_title_entry_awaiting_key_exchange))
                .setMessage(getString(R.string.hint_description_entry_awaiting_key_exchange))
                .setView(getLayoutInflater().inflate(R.layout.simple_progress_bar, null))
                .setCancelable(false)
                .create();
    }

    private boolean checkNickname(String nickname) {
        if (nickname.length() < 2 || !Character.isLetter(nickname.charAt(0))) {
            return false;
        }
        // legal characters are letters, digits, chinese words, - and _
        final Pattern legalPattern = Pattern.compile("^[A-Za-z0-9\\u4E00-\\u9FA5\\-_]*$");
        return legalPattern.matcher(nickname).matches();
    }

    private boolean checkSecureCode(String secureCode) {
        if (secureCode.length() < 4) {
            return false;
        }
        for (char c : secureCode.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private void setUpTextInputLayouts() {
        nicknameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                nicknameTextInputLayout.setError(null);
                nicknameTextInputLayout.setErrorEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        secureCodeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                secureCodeTextInputLayout.setError(null);
                secureCodeTextInputLayout.setErrorEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void hideKeyboard() {
        // Check if no view has focus:
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private class CheckForUpdatesTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(ApplicationConstants.API_CHECK_UPDATE + "/" + AppUtil.getCurrentVersion().getCode())
                    .build();
            try {
                Response response = client.newCall(request).execute();
                if (response.body() == null) {
                    throw new RuntimeException("response body is null");
                }
                return response.body().string();
            } catch (IOException e) {
                // could be a connection error
                // ignore it
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    JSONObject resultObject = new JSONObject(result);
                    boolean isLatest = resultObject.getBoolean("isLatest");
                    if (!isLatest) {
                        JSONObject versionObject = resultObject.getJSONObject("latestVersion");
                        int code = versionObject.getInt("code");
                        String name = versionObject.getString("name");
                        String whatsNewEn = versionObject.getJSONObject("whatsNew").getString("en");
                        String whatsNewZh = versionObject.getJSONObject("whatsNew").getString("zh");

                        Version latestVersion = new Version(code, name, new Version.WhatsNewInfo(whatsNewEn, whatsNewZh));
                        String message = AppUtil.buildNewVersionInformation(getApplicationContext(), latestVersion);
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(getString(R.string.hint_title_new_version))
                                .setMessage(message)
                                .setPositiveButton(R.string.action_update, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        // open browser to get new version
                                        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                                Uri.parse(ApplicationConstants.ANDROID_UPDATE_DOWNLOAD_URL));
                                        startActivity(browserIntent);
                                    }
                                })
                                .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {}
                                })
                                .setCancelable(false)
                                .show();
                    }
                } catch (JSONException e) {
                    // got a malformed json
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private class ShowNoticeTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(ApplicationConstants.API_NOTICE)
                    .build();
            try {
                Response response = client.newCall(request).execute();
                if (response.body() == null) {
                    throw new RuntimeException("response body is null");
                }
                return response.body().string();
            } catch (IOException e) {
                // could be a connection error
                // ignore it
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    JSONObject resultObject = new JSONObject(result);
                    boolean hasNotice = resultObject.getBoolean("hasNotice");
                    if (hasNotice) {
                        JSONObject noticeObject = resultObject.getJSONObject("notice");
                        String title = noticeObject.getString("title");
                        String content = noticeObject.getString("content");
                        String buttonType = noticeObject.getString("buttonType");
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
                                .setTitle(title)
                                .setMessage(content);
                        switch (buttonType) {
                            case "positive": {
                                String positiveButtonText = noticeObject.getString("positiveButtonText");
                                final String positiveButtonUrl = noticeObject.has("positiveButtonUrl")
                                        ? noticeObject.getString("positiveButtonUrl")
                                        : null;
                                builder.setPositiveButton(positiveButtonText, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        if (positiveButtonUrl != null) {
                                            // open browser to the specified url
                                            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                                    Uri.parse(positiveButtonUrl));
                                            startActivity(browserIntent);
                                        }
                                    }
                                }).show();
                                break;
                            }
                            case "positive|negative": {
                                String positiveButtonText = noticeObject.getString("positiveButtonText");
                                final String positiveButtonUrl = noticeObject.has("positiveButtonUrl")
                                        ? noticeObject.getString("positiveButtonUrl")
                                        : null;
                                String negativeButtonText = noticeObject.getString("negativeButtonText");
                                final String negativeButtonUrl = noticeObject.has("negativeButtonUrl")
                                        ? noticeObject.getString("negativeButtonUrl")
                                        : null;
                                builder.setPositiveButton(positiveButtonText, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        if (positiveButtonUrl != null) {
                                            // open browser to the specified url
                                            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                                    Uri.parse(positiveButtonUrl));
                                            startActivity(browserIntent);
                                        }
                                    }
                                }).setNegativeButton(negativeButtonText, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        if (negativeButtonUrl != null) {
                                            // open browser to the specified url
                                            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                                    Uri.parse(negativeButtonUrl));
                                            startActivity(browserIntent);
                                        }
                                    }
                                }).show();
                                break;
                            }
                        }
                    }
                } catch (JSONException e) {
                    // got a malformed json
                    e.printStackTrace();
                }
            }
        }
    }

    private class SharePublicKeyTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            awaitingKeyExchangeAlertDialog.show();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            // share public key with the peer
            byte[] selfPublicKeyJson = EncryptionManager.getInstance().getSelfPublicKeyJson();
            JSONObject keyObject = new JSONObject();
            try {
                keyObject.put("publicKey", selfPublicKeyJson);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            socket.emit("send key", keyObject);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            isSelfPublicKeyShared = true;
            onSentOrReceivedPublicKey();
        }
    }

    private void onSentOrReceivedPublicKey() {
        if (isSelfPublicKeyShared && EncryptionManager.getInstance().hasPeerPublicKey()) {
            if (awaitingKeyExchangeAlertDialog.isShowing()) {
                awaitingKeyExchangeAlertDialog.dismiss();
            }
            if (isInFront) {
                MessageActivity.open(MainActivity.this, nicknameEditText.getText().toString(), chattingWith);
            } else {
                shouldOpenMessageActivity = true;
            }
        }
    }
}
