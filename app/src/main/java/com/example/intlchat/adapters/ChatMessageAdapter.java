package com.example.intlchat.adapters;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.intlchat.activities.MainActivity;
import com.example.intlchat.databinding.ItemContainerReceivedMessageBinding;
import com.example.intlchat.databinding.ItemContainerSentMessageBinding;
import com.example.intlchat.models.ChatMessage;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.List;
import java.util.Objects;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> chatMessages;
    public Bitmap receiverProfileImage;
    private final String senderId;

    public static final int VIEW_TYPE_SENT = 1;
    public static final int VIEW_TYPE_RECEIVED = 2;

    public void setReceiverProfileImage(Bitmap bitmap) {
        receiverProfileImage = bitmap;
    }

    public ChatMessageAdapter(List<ChatMessage> chatMessages, Bitmap receiverProfileImage, String senderId) {
        this.chatMessages = chatMessages;
        this.receiverProfileImage = receiverProfileImage;
        this.senderId = senderId;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT)
            return new SentMessageViewHolder(
                    ItemContainerSentMessageBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    ));
        else
            return new ReceivedMessageViewHolder(
                    ItemContainerReceivedMessageBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    ));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_SENT)
            ((SentMessageViewHolder) holder).setData(chatMessages.get(position));
        else
            ((ReceivedMessageViewHolder) holder).setData(chatMessages.get(position), receiverProfileImage);
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (chatMessages.get(position).senderId.equals(senderId))
            return VIEW_TYPE_SENT;
        else
            return VIEW_TYPE_RECEIVED;
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemContainerSentMessageBinding binding;

        SentMessageViewHolder(ItemContainerSentMessageBinding itemContainerSentMessageBinding) {
            super(itemContainerSentMessageBinding.getRoot());
            binding = itemContainerSentMessageBinding;
        }

        void setData(ChatMessage chatMsg) {
            binding.textMessage.setText(chatMsg.message);
            binding.textDateTime.setText(chatMsg.dateTime);
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemContainerReceivedMessageBinding binding;

        ReceivedMessageViewHolder(ItemContainerReceivedMessageBinding itemContainerReceivedMessageBinding) {
            super(itemContainerReceivedMessageBinding.getRoot());
            binding = itemContainerReceivedMessageBinding;
        }

        void setData(ChatMessage chatMsg, Bitmap receiverProfileImage) {
            String message = chatMsg.message;

            LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
            languageIdentifier.identifyLanguage(message).addOnSuccessListener(
                    languageCode -> {
                        if (!languageCode.equals("und")) {

                            TranslatorOptions options = new TranslatorOptions.Builder()
                                    .setSourceLanguage(Objects.requireNonNull(TranslateLanguage.fromLanguageTag(languageCode)))
                                    .setTargetLanguage(Objects.requireNonNull(TranslateLanguage.fromLanguageTag(MainActivity.sysLang)))
                                    .build();
                            final Translator translator = Translation.getClient(options);

                            DownloadConditions conditions = new DownloadConditions.Builder()
                                    .requireWifi()
                                    .build();
                            translator.downloadModelIfNeeded(conditions);
                            translator.translate(message)
                                    .addOnSuccessListener(binding.textMessage::setText);
                        }
                    })
                    .addOnFailureListener(
                            e -> binding.textMessage.setText(message));

            binding.textDateTime.setText(chatMsg.dateTime);
            if (receiverProfileImage != null) {
                binding.imageProfile.setImageBitmap(receiverProfileImage);
            }
        }
    }
}
