package com.example.intlchat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.example.intlchat.R;
import com.example.intlchat.adapters.UserAdapter;
import com.example.intlchat.databinding.ActivityUserBinding;
import com.example.intlchat.listeners.UserListener;
import com.example.intlchat.models.User;
import com.example.intlchat.utilities.Constants;
import com.example.intlchat.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UserActivity extends BaseActivity implements UserListener {

    private ActivityUserBinding binding;
    private PreferenceManager preferenceManager;
    private List<User> users, orgUsers;
    private UserAdapter userAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
        getUsers();
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSearch.setOnClickListener(v -> onSearchButtonClicked());
    }

    private void getUsers() {
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS)
                .get()
                .addOnCompleteListener(task -> {
                    loading(false);
                    String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);
                    if (task.isSuccessful() && task.getResult() != null) {
                        users = new ArrayList<>();
                            for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()) {
                                if (currentUserId.equals(queryDocumentSnapshot.getId()))
                                    continue;

                                User user = new User();
                                user.name = queryDocumentSnapshot.getString(Constants.KEY_NAME);
                                user.email = queryDocumentSnapshot.getString(Constants.KEY_EMAIL);
                                user.image = queryDocumentSnapshot.getString(Constants.KEY_IMAGE);
                                user.token = queryDocumentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                                user.id = queryDocumentSnapshot.getId();
                                users.add(user);
                            }
                            if (users.size() > 0) {
                                orgUsers = new ArrayList<>();
                                orgUsers.addAll(users);
                                userAdapter = new UserAdapter(users, this);
                                binding.userRecyclerView.setAdapter(userAdapter);
                                binding.userRecyclerView.setVisibility(View.VISIBLE);
                            } else showErrorMessage();
                    } else showErrorMessage();
                });
    }

    void showErrorMessage() {
        binding.textErrorMessage.setText(String.format("%s", getString(R.string.user_not_available)));
        binding.textErrorMessage.setVisibility(View.VISIBLE);
    }

    private void loading(Boolean isLoading) {
        if (isLoading)
            binding.progressBar.setVisibility(View.VISIBLE);
        else
            binding.progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onUserClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
        finish();
    }

    public void onSearchButtonClicked() {
        String query = binding.inputSearch.getText().toString().toLowerCase();
        if (query.isEmpty())
            Toast.makeText(getApplicationContext(), "You must enter a query!", Toast.LENGTH_SHORT).show();
        else {
            List<User> filteredUsers = new ArrayList<>();
            for (User u : orgUsers) {
                if (u.email.toLowerCase().contains(query) || u.name.toLowerCase().contains(query))
                    filteredUsers.add(u);
            }
            if (filteredUsers.size() > 0) {
                users.clear();
                users.addAll(filteredUsers);
                userAdapter.notifyDataSetChanged();
            } else showErrorMessage();
        }
    }
}