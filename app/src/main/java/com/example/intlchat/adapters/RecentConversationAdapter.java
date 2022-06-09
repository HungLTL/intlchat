package com.example.intlchat.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.RecyclerView;

import com.example.intlchat.ChatApplication;
import com.example.intlchat.databinding.ItemContainerRecentConversationBinding;
import com.example.intlchat.listeners.ConversationListener;
import com.example.intlchat.models.ChatMessage;
import com.example.intlchat.models.User;
import com.example.intlchat.utilities.Constants;
import com.example.intlchat.utilities.PreferenceManager;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.List;

public class RecentConversationAdapter extends RecyclerView.Adapter<RecentConversationAdapter.ConversationViewHolder> {

    private final List<ChatMessage> chatMessages;
    private final ConversationListener conversationListener;
    private final PreferenceManager preferenceManager;

    public RecentConversationAdapter(List<ChatMessage> chatMessages, ConversationListener conversationListener, Context context) {
        this.chatMessages = chatMessages;
        this.conversationListener = conversationListener;
        this.preferenceManager = new PreferenceManager(context);
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversationViewHolder(
                ItemContainerRecentConversationBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(@NonNull RecentConversationAdapter.ConversationViewHolder holder, int position) {
        holder.setData(chatMessages.get(position));
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    class ConversationViewHolder extends RecyclerView.ViewHolder {
        ItemContainerRecentConversationBinding binding;
        private Translator translator;
        private MutableLiveData<Boolean> isTranslatorAvailable;
        
        ConversationViewHolder(ItemContainerRecentConversationBinding itemContainerRecentConversationBinding) {
            super(itemContainerRecentConversationBinding.getRoot());
            binding = itemContainerRecentConversationBinding;

            isTranslatorAvailable = new MutableLiveData<>();
            isTranslatorAvailable.setValue(false);
            isTranslatorAvailable.observeForever(aBoolean -> {
                if (aBoolean)
                    translator.translate(binding.textRecentMessage.getText().toString())
                    .addOnSuccessListener(s -> binding.textRecentMessage.setText(s));
            });
        }

        void setData(ChatMessage chatMsg) {
            binding.imageProfile.setImageBitmap(getConversationImage(chatMsg.conversationImage));
            binding.textName.setText(chatMsg.conversationName);
            binding.textRecentMessage.setText(chatMsg.message);
            binding.getRoot().setOnClickListener(v -> {
                User user = new User();
                user.id = chatMsg.conversation;
                user.name = chatMsg.conversationName;
                user.image = chatMsg.conversationImage;
                conversationListener.onConversationClicked(user);
            });

            if (chatMsg.senderId.equals(preferenceManager.getString(Constants.KEY_USER_ID))) {
                String receivedMessage = chatMsg.message;

                LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
                languageIdentifier.identifyLanguage(receivedMessage)
                        .addOnSuccessListener(languageCode -> {
                                    if (!languageCode.equals("und")
                                            && !languageCode.isEmpty()
                                            && !languageCode.equals(ChatApplication.sysLang)) {
                                        TranslatorOptions options = new TranslatorOptions.Builder()
                                                .setSourceLanguage(languageCode)
                                                .setTargetLanguage(ChatApplication.sysLang)
                                                .build();
                                        translator = Translation.getClient(options);

                                        DownloadConditions conditions = new DownloadConditions.Builder()
                                                .requireWifi()
                                                .build();
                                        translator.downloadModelIfNeeded(conditions)
                                                .addOnSuccessListener(unused -> isTranslatorAvailable.setValue(true));
                                    }
                                }
                        );
            } else binding.textRecentMessage.setText(chatMsg.message);
        }
    }

    private Bitmap getConversationImage(String encodedImage) {
        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
