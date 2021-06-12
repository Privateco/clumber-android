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

package com.privateco.clumber.util;

import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Date;

import com.privateco.clumber.constants.ApplicationConstants;
import com.privateco.clumber.crypto.EncryptionManager;
import com.privateco.clumber.model.Message;
import com.privateco.clumber.model.MessageEvent;
import com.privateco.clumber.model.User;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class ApplicationSocket {
    private static final ApplicationSocket ourInstance = new ApplicationSocket();

    public static ApplicationSocket getInstance() {
        return ourInstance;
    }

    private final Socket socket;

    private ApplicationSocket() {
        try {
            socket = IO.socket(ApplicationConstants.WEB_SERVICE_URL);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        registerEventListeners();
    }

    private void registerEventListeners() {

        // events for MainActivity
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("code", "socket event: connected");
                EventBus.getDefault().post(new MessageEvent("socket_connected"));
            }
        });
        socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("code", "socket event: connect error");
                EventBus.getDefault().post(new MessageEvent("socket_connect_error"));
            }
        });
        socket.on("entry: name occupied", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("code", "socket event: entry: name occupied");
                EventBus.getDefault().post(new MessageEvent("socket_entry_name_occupied"));
            }
        });
        socket.on("entry: code occupied", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("code", "socket event: entry: code occupied");
                EventBus.getDefault().post(new MessageEvent("socket_entry_code_occupied"));
            }
        });
        socket.on("entry: await", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("code", "socket event: entry: await");
                EventBus.getDefault().post(new MessageEvent("socket_entry_await"));
            }
        });
        socket.on("entry: success", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("code", "socket event: entry: success");

                JSONObject obj = (JSONObject)args[0];
                try {
                    String chattingWith = (String) obj.get("chattingWith");
                    EventBus.getDefault().post(new MessageEvent("socket_entry_success", chattingWith));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

            }
        });

        socket.on("receive key", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("code", "socket event: receive key");
                JSONObject obj = (JSONObject)args[0];
                byte[] peerPublicKeyBytes;
                try {
                    peerPublicKeyBytes = (byte[]) obj.get("publicKey");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                try {
                    EncryptionManager.getInstance().resetPeerPublicKeyHandle(peerPublicKeyBytes);
                } catch (GeneralSecurityException | IOException e) {
                    throw new RuntimeException(e);
                }
                EventBus.getDefault().post(new MessageEvent("socket_entry_key_received"));
            }
        });

        // events for MessageActivity
        socket.on("receive message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("code", "socket event: receive message");
                JSONObject obj = (JSONObject)args[0];
                try {
                    String from = (String) obj.get("from");
                    byte[] ciphertext = (byte[]) obj.get("text");
                    Long time = (Long) obj.get("time");

                    // decrypt message
                    String plaintext = EncryptionManager.getInstance().decrypt(ciphertext);

                    EventBus.getDefault().post(new MessageEvent("socket_receive_message",
                            new Message(AppUtil.getRandomId(), new User(from), plaintext, new Date(time))));
                } catch (JSONException | GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        socket.on("user exited", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("code", "socket event: user exited");
                EventBus.getDefault().post(new MessageEvent("socket_user_exited"));
            }
        });

    }

    public Socket getSocket() {
        return socket;
    }
}
