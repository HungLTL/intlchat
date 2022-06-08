package com.example.intlchat.adapters;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.RecyclerView;

import com.example.intlchat.ChatApplication;
import com.example.intlchat.R;
import com.example.intlchat.databinding.ItemContainerReceivedMessageBinding;
import com.example.intlchat.databinding.ItemContainerSentMessageBinding;
import com.example.intlchat.models.ChatMessage;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.List;

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
        private Translator translator;
        private MutableLiveData<Boolean> isTranslatorAvailable;

        ReceivedMessageViewHolder(ItemContainerReceivedMessageBinding itemContainerReceivedMessageBinding) {
            super(itemContainerReceivedMessageBinding.getRoot());
            binding = itemContainerReceivedMessageBinding;

            isTranslatorAvailable = new MutableLiveData<>();
            isTranslatorAvailable.setValue(false);
            isTranslatorAvailable.observeForever(aBoolean -> {
                if (aBoolean)
                    translator.translate(binding.textMessage.getText().toString())
                            .addOnSuccessListener(binding.textMessage::setText)
                            .addOnFailureListener(e -> Toast.makeText(binding.getRoot().getContext(), R.string.error_translate, Toast.LENGTH_SHORT).show());
            });
        }

        void setData(ChatMessage chatMsg, Bitmap receiverProfileImage) {
            binding.textMessage.setText(chatMsg.message);
            binding.textDateTime.setText(chatMsg.dateTime);
            if (receiverProfileImage != null) {
                binding.imageProfile.setImageBitmap(receiverProfileImage);
            }

            LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
            languageIdentifier.identifyLanguage(chatMsg.message)
                    .addOnSuccessListener(languageCode -> {
                        if (languageCode.equals("und") || languageCode.isEmpty())
                            Toast.makeText(binding.getRoot().getContext(), R.string.error_lang_id, Toast.LENGTH_SHORT).show();
                        else {
                            if (!languageCode.equals(ChatApplication.sysLang)) {
                                TranslatorOptions options = new TranslatorOptions.Builder()
                                        .setSourceLanguage(languageCode)
                                        .setTargetLanguage(ChatApplication.sysLang)
                                        .build();
                                translator = Translation.getClient(options);

                                DownloadConditions conditions = new DownloadConditions.Builder()
                                        .requireWifi()
                                        .build();
                                translator.downloadModelIfNeeded(conditions)
                                        .addOnSuccessListener(unused -> isTranslatorAvailable.setValue(true))
                                        .addOnFailureListener(e -> Toast.makeText(binding.getRoot().getContext(), R.string.error_model_download, Toast.LENGTH_SHORT).show());
                            }
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(binding.getRoot().getContext(), R.string.error_lang_id, Toast.LENGTH_SHORT).show());
        }
    }
}
