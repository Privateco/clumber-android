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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import com.privateco.clumber.crypto.EncryptionManager;
import com.privateco.clumber.model.Message;
import com.privateco.clumber.model.MessageEvent;
import com.privateco.clumber.model.User;
import com.privateco.clumber.util.AppUtil;
import com.privateco.clumber.util.ApplicationSocket;
import io.socket.client.Socket;

public class MessageActivity extends AppCompatActivity
        implements MessagesListAdapter.SelectionListener, MessageInput.InputListener {

    private MessagesList messagesList;
    private Menu menu;
    private MessageInput messageInput;

    private MessagesListAdapter<Message> messagesAdapter;
    private int selectionCount;
    private final Socket socket = ApplicationSocket.getInstance().getSocket();

    private final String SELF_SENDER_ID = "0";
    private final int NEW_MESSAGE_NOTIFICATION_ID = 1;
    private String nickname;
    private String chattingWithUserNickName;
    private boolean exited = false;
    private boolean isInFront;


    public static void open(Context context, @NonNull String nickname, @NonNull String chattingWith) {
        Intent intent = new Intent(context, MessageActivity.class);
        intent.putExtra("nickname", nickname);
        intent.putExtra("chattingWith", chattingWith);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        initialize();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (!exited) {
            socket.emit("exit");
            exited = true;
        }
        new ResetEncryptionManagerTask().execute();
        EventBus.getDefault().post(new MessageEvent("message_activity_destroyed"));
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        isInFront = true;

        // clear new message notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NEW_MESSAGE_NOTIFICATION_ID);
    }

    @Override
    public void onPause() {
        super.onPause();
        isInFront = false;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        switch (event.getEvent()) {
            case "socket_receive_message":
                // show notification if activity is not visible
                if (!isInFront) {
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(MessageActivity.this);
                    builder.setAutoCancel(true)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setDefaults(NotificationCompat.DEFAULT_ALL)
                            .setTicker(getString(R.string.notification_new_message))
                            .setContentTitle(getString(R.string.notification_new_message))
                            .setContentText(getString(R.string.notification_new_message_from) + " " + chattingWithUserNickName)
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setContentIntent(PendingIntent.getActivity(
                                    MessageActivity.this, 0, new Intent(MessageActivity.this, MessageActivity.class), 0));

                    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    {
                        String channelId = "1";
                        NotificationChannel channel = new NotificationChannel(
                                channelId,
                                getString(R.string.notification_channel_name),
                                NotificationManager.IMPORTANCE_HIGH);
                        notificationManager.createNotificationChannel(channel);
                        builder.setChannelId(channelId);
                    }

                    notificationManager.notify(NEW_MESSAGE_NOTIFICATION_ID, builder.build());
                }

                Message message = (Message) event.getAttachment();
                messagesAdapter.addToStart(message, true);
                break;
            case "socket_user_exited":
                // exit too
                if (!exited) {
                    socket.emit("exit");
                    exited = true;
                }
                new ResetEncryptionManagerTask().execute();
                new AlertDialog.Builder(MessageActivity.this)
                        .setMessage(R.string.hint_user_exited)
                        .setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {}
                        })
                        .show();
                messageInput.getButton().setVisibility(View.INVISIBLE);  // hide send button
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.chat_actions_menu, menu);
        onSelectionChanged(0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                messagesAdapter.deleteSelectedMessages();
                break;
            case R.id.action_copy:
                messagesAdapter.copySelectedMessagesText(this, getMessageStringFormatter(), true);
                Toast.makeText(this, R.string.hint_copied_message, Toast.LENGTH_LONG).show();
                break;
            case R.id.action_clear:
                new AlertDialog.Builder(MessageActivity.this)
                        .setMessage(R.string.hint_confirm_clear_messages)
                        .setPositiveButton(R.string.action_clear, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
//                                messagesAdapter.delete(messages);
//                                messages.clear();
                                messagesAdapter.clear();
                                messagesAdapter.notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {}
                        })
                        .show();
                break;
            case R.id.action_exit:
                onBackPressed();
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (selectionCount == 0) {
            if (!exited) {
                new AlertDialog.Builder(MessageActivity.this)
                        .setTitle(R.string.hint_title_confirm_exit_chatroom)
                        .setMessage(R.string.hint_description_confirm_exit_chatroom)
                        .setPositiveButton(R.string.action_exit, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                MessageActivity.super.onBackPressed();
                            }
                        })
                        .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                            }
                        })
                        .show();
            } else {
                MessageActivity.super.onBackPressed();
            }
        } else {
            messagesAdapter.unselectAllItems();
        }
    }

    private void initialize() {
        bindView();
        setUpNicknames();
        setUpTitle();
        setUpMessageAdapter();
        setUpMessageInputListener();
        setUpSocketIO();
    }

    private void bindView() {
        messagesList = findViewById(R.id.messagesList);
        messageInput = findViewById(R.id.input);
    }

    private void setUpNicknames() {
        this.nickname = getIntent().getStringExtra("nickname");
        this.chattingWithUserNickName = getIntent().getStringExtra("chattingWith");
    }

    private void setUpTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(chattingWithUserNickName);
        }
    }

    private void setUpMessageAdapter() {
        messagesAdapter = new MessagesListAdapter<>(SELF_SENDER_ID, new ImageLoader() {
            @Override
            public void loadImage(ImageView imageView, String url) {
                Picasso.with(MessageActivity.this).load(url).into(imageView);
            }
        });
        messagesAdapter.enableSelectionMode(this);
//        messagesAdapter.setLoadMoreListener(this);
        this.messagesList.setAdapter(messagesAdapter);
    }

    private void setUpMessageInputListener() {
        messageInput.setInputListener(this);
    }

    private void setUpSocketIO() {

    }

    private void postMessage(String text) {
        Log.i("code", "posting message");
        new PostMessageTask().execute(text);
    }

    @Override
    public void onSelectionChanged(int count) {
        this.selectionCount = count;
        menu.findItem(R.id.action_delete).setVisible(count > 0);
        menu.findItem(R.id.action_copy).setVisible(count > 0);
        menu.findItem(R.id.action_exit).setVisible(count == 0);
    }

    @Override
    public boolean onSubmit(CharSequence input) {
        messagesAdapter.addToStart(
                new Message(AppUtil.getRandomId(), new User(SELF_SENDER_ID, nickname), input.toString()), true);
        postMessage(input.toString());
        return true;
    }

    private MessagesListAdapter.Formatter<Message> getMessageStringFormatter() {
        return new MessagesListAdapter.Formatter<Message>() {
            @Override
            public String format(Message message) {
                String createdAt = new SimpleDateFormat("MMM d, EEE 'at' h:mm a", Locale.getDefault())
                        .format(message.getCreatedAt());

                String text = message.getText();
                if (text == null) text = "[attachment]";

                return String.format(Locale.getDefault(), "%s: %s (%s)",
                        message.getUser().getName(), text, createdAt);
            }
        };
    }

    private class PostMessageTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... texts) {
            if (texts.length != 1) {
                throw new IllegalArgumentException("texts must be of length 1");
            }
            String text = texts[0];
            // encrypt message
            byte[] ciphertext;
            try {
                ciphertext = EncryptionManager.getInstance().encrypt(text);
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
            // wrap message
            JSONObject obj = new JSONObject();
            try {
                obj.put("text", ciphertext);
                obj.put("time", System.currentTimeMillis());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            // send message
            socket.emit("send message", obj);
            return null;
        }
    }

    private class ResetEncryptionManagerTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            EncryptionManager.getInstance().reset(); // reset encryption keys
            return null;
        }
    }

}
