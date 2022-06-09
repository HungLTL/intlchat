package com.example.intlchat.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.example.intlchat.ChatApplication;
import com.example.intlchat.R;
import com.example.intlchat.adapters.ChatMessageAdapter;
import com.example.intlchat.databinding.ActivityChatBinding;
import com.example.intlchat.models.ChatMessage;
import com.example.intlchat.models.User;
import com.example.intlchat.network.ApiClient;
import com.example.intlchat.network.ApiService;
import com.example.intlchat.utilities.Constants;
import com.example.intlchat.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.smartreply.SmartReply;
import com.google.mlkit.nl.smartreply.SmartReplyGenerator;
import com.google.mlkit.nl.smartreply.SmartReplySuggestion;
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult;
import com.google.mlkit.nl.smartreply.TextMessage;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatMessageAdapter chatMessageAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversationId = null;
    private Boolean isReceiverAvailable = false;
    private List<TextMessage> conv;
    private Translator toEnglishTranslator, fromEnglishTranslator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
        init();
        listenMessage();
    }

    private void sendMessage() {

        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constants.KEY_TIMESTAMP, new Date());
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if (conversationId != null)
            updateConversation(binding.inputMessage.getText().toString());
        else {
            HashMap<String, Object> conversation = new HashMap<>();
            conversation.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversation.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversation.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversation.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversation.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversation.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversation.put(Constants.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversation.put(Constants.KEY_TIMESTAMP, new Date());
            addConversation(conversation);
        }
        if (!isReceiverAvailable) {
            try {
                JSONArray tokens = new JSONArray();
                tokens.put(receiverUser.token);

                JSONObject data = new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());

                JSONObject body = new JSONObject();
                body.put(Constants.REMOTE_MSG_DATA, data);
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                sendNotification(body.toString());
            }catch (Exception exception) {
                showToast(exception.getMessage());
            }
        }
        binding.inputMessage.setText(null);
    }

    View.OnFocusChangeListener onInputFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean b) {
            if (conv.size() <= 0)
                return;

            if (b) {
                Collections.sort(conv, (textMessage, t1) -> Long.compare(textMessage.zza(), t1.zza()));
                for (TextMessage msg : conv) {
                    Log.i("MESSAGE", msg.zzb() + " " + msg.zzc() + " " + msg.zza());
                }

                SmartReplyGenerator smartReply = SmartReply.getClient();
                smartReply.suggestReplies(conv)
                        .addOnSuccessListener(result -> {
                            if (result.getStatus() == SmartReplySuggestionResult.STATUS_SUCCESS) {
                                binding.layoutHint.setVisibility(View.VISIBLE);
                                MutableLiveData<Boolean> isTranslatorAvailable = new MutableLiveData<>();
                                isTranslatorAvailable.setValue(false);
                                isTranslatorAvailable.observeForever(aBoolean -> {
                                    if (aBoolean) {
                                        for (SmartReplySuggestion suggestion : result.getSuggestions()) {
                                            fromEnglishTranslator.translate(suggestion.getText())
                                                    .addOnSuccessListener(s -> {
                                                        if (binding.hint1.getText().toString().isEmpty())
                                                            binding.hint1.setText(s);
                                                        else {
                                                            if (binding.hint2.getText().toString().isEmpty())
                                                                binding.hint2.setText(s);
                                                            else
                                                                binding.hint3.setText(s);
                                                        }
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        if (binding.hint1.getText().toString().isEmpty())
                                                            binding.hint1.setText(suggestion.getText());
                                                        else {
                                                            if (binding.hint2.getText().toString().isEmpty())
                                                                binding.hint2.setText(suggestion.getText());
                                                            else
                                                                binding.hint3.setText(suggestion.getText());
                                                        }
                                                    });
                                        }
                                    }
                                });

                                TranslatorOptions options = new TranslatorOptions.Builder()
                                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                                        .setTargetLanguage(ChatApplication.sysLang)
                                        .build();
                                fromEnglishTranslator = Translation.getClient(options);

                                DownloadConditions conditions = new DownloadConditions.Builder()
                                        .requireWifi()
                                        .build();
                                fromEnglishTranslator.downloadModelIfNeeded(conditions)
                                        .addOnSuccessListener(unused -> isTranslatorAvailable.setValue(true))
                                        .addOnFailureListener(e -> {
                                            for (SmartReplySuggestion suggestion : result.getSuggestions()) {
                                                if (binding.hint1.getText().toString().isEmpty())
                                                    binding.hint1.setText(suggestion.getText());
                                                else {
                                                    if (binding.hint2.getText().toString().isEmpty())
                                                        binding.hint2.setText(suggestion.getText());
                                                    else
                                                        binding.hint3.setText(suggestion.getText());
                                                }
                                            }
                                        });
                            }
                        });
            }
            else {
                binding.hint1.setText("");
                binding.hint2.setText("");
                binding.hint3.setText("");
                binding.layoutHint.setVisibility(View.GONE);
            }
        }
    };

    View.OnClickListener setInputToSuggestion = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            TextView textView = (TextView) view;
            binding.inputMessage.setText(textView.getText().toString());
        }
    };

    private void listenMessage() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);

        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
                        for (DocumentSnapshot document : task.getResult().getDocuments()) {
                            String message = document.getString(Constants.KEY_MESSAGE);

                            LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
                            assert message != null;
                            languageIdentifier.identifyLanguage(message)
                                    .addOnSuccessListener(s -> {
                                        if (!s.equals("en")) {
                                            TranslatorOptions options = new TranslatorOptions.Builder()
                                                    .setSourceLanguage(s)
                                                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                                                    .build();
                                            toEnglishTranslator = Translation.getClient(options);
                                            DownloadConditions conditions = new DownloadConditions.Builder()
                                                    .requireWifi()
                                                    .build();
                                            toEnglishTranslator.downloadModelIfNeeded(conditions)
                                                    .addOnSuccessListener(unused -> toEnglishTranslator.translate(message)
                                                            .addOnSuccessListener(s1 -> conv.add(TextMessage.createForLocalUser(s1, document.getDate(Constants.KEY_TIMESTAMP).getTime())))
                                                            .addOnFailureListener(e -> conv.add(TextMessage.createForLocalUser(message, document.getDate(Constants.KEY_TIMESTAMP).getTime()))))
                                                    .addOnFailureListener(e -> conv.add(TextMessage.createForLocalUser(message, document.getDate(Constants.KEY_TIMESTAMP).getTime())));
                                        }
                                        else
                                            conv.add(TextMessage.createForLocalUser(message, document.getDate(Constants.KEY_TIMESTAMP).getTime()));
                                    });
                        }
                    }
                });
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
                        for (DocumentSnapshot document : task.getResult().getDocuments()) {
                            String message = document.getString(Constants.KEY_MESSAGE);

                            LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
                            assert message != null;
                            languageIdentifier.identifyLanguage(message)
                                    .addOnSuccessListener(s -> {
                                        if (!s.equals("en")) {
                                            TranslatorOptions options = new TranslatorOptions.Builder()
                                                    .setSourceLanguage(s)
                                                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                                                    .build();
                                            toEnglishTranslator = Translation.getClient(options);
                                            DownloadConditions conditions = new DownloadConditions.Builder()
                                                    .requireWifi()
                                                    .build();
                                            toEnglishTranslator.downloadModelIfNeeded(conditions)
                                                    .addOnSuccessListener(unused -> toEnglishTranslator.translate(message)
                                                            .addOnSuccessListener(s12 -> conv.add(TextMessage.createForRemoteUser(s12, document.getDate(Constants.KEY_TIMESTAMP).getTime(), Constants.KEY_SENDER_ID)))
                                                            .addOnFailureListener(e -> conv.add(TextMessage.createForRemoteUser(message, document.getDate(Constants.KEY_TIMESTAMP).getTime(), Constants.KEY_SENDER_ID))))
                                                    .addOnFailureListener(e -> conv.add(TextMessage.createForRemoteUser(message, document.getDate(Constants.KEY_TIMESTAMP).getTime(), Constants.KEY_SENDER_ID)));
                                        }
                                        else
                                            conv.add(TextMessage.createForRemoteUser(message, document.getDate(Constants.KEY_TIMESTAMP).getTime(), Constants.KEY_SENDER_ID));
                                    });
                        }
                    }
                });
        List<TextMessage> recentMessages = new ArrayList<>();
        if (conv.size() >= 10) {
            for (int i = conv.size() - 10; i < conv.size(); i++) {
                recentMessages.add(conv.get(i));
            }
            conv = recentMessages;
        }
    }

    private void showToast(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void sendNotification(String msgBody) {
        ApiClient.getClient().create(ApiService.class).sendMessage(
                Constants.getRemoteMsgHeaders(),
                msgBody
                ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    try {
                        if (response.body() != null) {
                            JSONObject responseJson = new JSONObject(response.body());
                            JSONArray results = responseJson.getJSONArray("results");
                            if (responseJson.getInt("failure") == 1) {
                                JSONObject error = (JSONObject) results.get(0);
                                showToast(error.getString("error"));
                                return;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    showToast(getString(R.string.notification_success));
                }else
                    showToast("Error: " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<String> call,@NonNull Throwable t) {
                showToast(t.getMessage());
            }
        });
    }

    private void listenAvailabilityOfReceiver() {
        database.collection(Constants.KEY_COLLECTION_USERS).document(
                receiverUser.id
        ).addSnapshotListener(ChatActivity.this, (value, error) -> {
            if (error != null)
                return;

            if (value != null) {
                if (value.getLong(Constants.KEY_AVAILABILITY) != null) {
                    int availability = Objects.requireNonNull(
                            value.getLong(Constants.KEY_AVAILABILITY)
                    ).intValue();
                    isReceiverAvailable = availability == 1;
                }
                receiverUser.token = value.getString(Constants.KEY_FCM_TOKEN);
                if (receiverUser.image == null) {
                    receiverUser.image = value.getString(Constants.KEY_IMAGE);
                    chatMessageAdapter.setReceiverProfileImage(getBitmapFromEncodedString(receiverUser.image));
                    chatMessageAdapter.notifyItemRangeChanged(0, chatMessages.size());
                }
            }
            if (isReceiverAvailable)
                binding.textAvailability.setVisibility(View.VISIBLE);
            else
                binding.textAvailability.setVisibility(View.GONE);
        });
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null)
            return;

        if (value != null) {
            int count = chatMessages.size();
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    ChatMessage chatMsg = new ChatMessage();
                    chatMsg.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    chatMsg.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    chatMsg.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    chatMsg.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    chatMsg.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                    chatMessages.add(chatMsg);
                }
            }
            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            if (count == 0) {
                chatMessageAdapter.notifyDataSetChanged();
            } else {
                chatMessageAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
        if (conversationId == null)
            checkForConversation();
    };

    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        conv = new ArrayList<>();
        chatMessageAdapter = new ChatMessageAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        binding.chatRecyclerView.setAdapter(chatMessageAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private Bitmap getBitmapFromEncodedString(String encodedImage) {
        if (encodedImage != null) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }
        else
            return null;
    }

    private void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receiverUser.name);
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
        binding.inputMessage.setOnFocusChangeListener(onInputFocus);
        binding.hint1.setOnClickListener(setInputToSuggestion);
        binding.hint2.setOnClickListener(setInputToSuggestion);
        binding.hint3.setOnClickListener(setInputToSuggestion);
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void addConversation(HashMap<String, Object> conversation) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversation)
                .addOnSuccessListener(documentReference -> conversationId = documentReference.getId());
    }

    private void updateConversation(String message) {
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversationId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, new Date()
        );
    }

    private void checkForConversation() {
        if (chatMessages.size() != 0) {
            checkForConversationRemotely(
                preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.id
            );
            checkForConversationRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    private void checkForConversationRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversationOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversationOnCompleteListener = task -> {
        if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversationId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }
}