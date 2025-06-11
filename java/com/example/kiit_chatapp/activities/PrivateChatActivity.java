package com.example.kiit_chatapp.activities;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.UploadCallback;
import com.example.kiit_chatapp.R;
import com.example.kiit_chatapp.adapters.MessageAdapter;
import com.example.kiit_chatapp.models.Message;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PrivateChatActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1001;
    private static final int PICK_AUDIO_REQUEST = 1002;
    private static final int REQUEST_PERMISSION_CODE = 42;

    private RecyclerView privateChatRecyclerView;
    private EditText messageEditText;
    private ImageButton sendButton, sendImageButton, recordAudioButton;
    private TextView chatUserNameTextView;
    private ProgressBar imageUploadProgressBar;

    private MessageAdapter messageAdapter;
    private List<Message> messageList;

    private FirebaseAuth mAuth;
    private DatabaseReference privateMessagesRef;
    private String currentUserId;
    private String otherUserId;
    private String chatId;

    private DatabaseReference usersRef;
    private String currentUserName;
    private String currentUserEmail;
    private String currentUserRole;

    private ProgressDialog progressDialog;
    private DatabaseReference chatListRef;

    // Audio recording
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_chat);

        privateChatRecyclerView = findViewById(R.id.privateChatRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        sendImageButton = findViewById(R.id.sendImageButton);
        recordAudioButton = findViewById(R.id.recordAudioButton);
        chatUserNameTextView = findViewById(R.id.chatUserNameTextView);
        imageUploadProgressBar = findViewById(R.id.imageUploadProgressBar);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Uploading...");

        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser().getUid();
        otherUserId = getIntent().getStringExtra("otherUserId");

        if (otherUserId == null) {
            Toast.makeText(this, "No chat target!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (currentUserId.compareTo(otherUserId) < 0) {
            chatId = currentUserId + "_" + otherUserId;
        } else {
            chatId = otherUserId + "_" + currentUserId;
        }

        usersRef = FirebaseDatabase.getInstance().getReference("users");

        usersRef.child(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentUserName = snapshot.child("name").getValue(String.class);
                currentUserEmail = snapshot.child("email").getValue(String.class);
                currentUserRole = snapshot.child("role").getValue(String.class);
                if (currentUserName == null) currentUserName = "Unknown";
                if (currentUserEmail == null) currentUserEmail = "";
                if (currentUserRole == null) currentUserRole = "";
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        usersRef.child(otherUserId).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String chatUserName = snapshot.getValue(String.class);
                if (chatUserName != null) {
                    chatUserNameTextView.setText(chatUserName);
                } else {
                    chatUserNameTextView.setText("User");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        privateMessagesRef = FirebaseDatabase.getInstance().getReference("private_chats").child(chatId).child("messages");
        chatListRef = FirebaseDatabase.getInstance().getReference("private_chats_list");

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(this, messageList);
        privateChatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        privateChatRecyclerView.setAdapter(messageAdapter);

        loadMessages();

        sendButton.setOnClickListener(v -> sendMessage());
        sendImageButton.setOnClickListener(v -> pickImage());

        // Record and send audio with mic icon toggle
        recordAudioButton.setOnClickListener(v -> {
            if (!isRecording) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_CODE);
                } else {
                    startRecording();
                    recordAudioButton.setImageResource(R.drawable.ic_stop); // Set your stop icon
                }
            } else {
                stopRecordingAndUpload();
                recordAudioButton.setImageResource(R.drawable.mic_24); // Set your mic icon
            }
        });

        markMessagesAsRead();
    }

    private void loadMessages() {
        privateMessagesRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Message message = snapshot.getValue(Message.class);
                if (message != null) {
                    messageList.add(message);
                    messageAdapter.notifyItemInserted(messageList.size() - 1);
                    privateChatRecyclerView.scrollToPosition(messageList.size() - 1);
                }
            }
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {}
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void sendMessage() {
        String messageText = messageEditText.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) {
            Toast.makeText(this, "Cannot send empty message", Toast.LENGTH_SHORT).show();
            return;
        }
        String messageId = privateMessagesRef.push().getKey();
        if (messageId == null) return;

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        Message message = new Message(
                messageId,
                currentUserId,
                currentUserName,
                currentUserEmail,
                currentUserRole,
                messageText,
                "text",
                timeStamp,
                null,
                false
        );
        privateMessagesRef.child(messageId).setValue(message)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        messageEditText.setText("");
                        updateChatListForBothUsers(messageText, timeStamp);
                        incrementUnreadForRecipient();
                    } else {
                        Toast.makeText(PrivateChatActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateChatListForBothUsers(String lastMessage, String timeStamp) {
        Map<String, Object> chatMeta = new HashMap<>();
        chatMeta.put("lastMessage", lastMessage);
        chatMeta.put("timestamp", System.currentTimeMillis());

        chatListRef.child(currentUserId).child(otherUserId).updateChildren(chatMeta);
        chatListRef.child(otherUserId).child(currentUserId).updateChildren(chatMeta);
    }

    private void incrementUnreadForRecipient() {
        chatListRef.child(otherUserId).child(currentUserId).child("unreadCount").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long current = 0;
                if (snapshot.exists()) {
                    Object val = snapshot.getValue();
                    if (val instanceof Long) current = (Long) val;
                    else if (val instanceof Integer) current = ((Integer) val).longValue();
                }
                chatListRef.child(otherUserId).child(currentUserId).child("unreadCount").setValue(current + 1);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri fileUri = data.getData();
            if (requestCode == PICK_IMAGE_REQUEST) {
                uploadImageToCloudinary(fileUri);
            } else if (requestCode == PICK_AUDIO_REQUEST) {
                uploadAudioToCloudinary(fileUri);
            }
        }
    }

    private void uploadImageToCloudinary(Uri imageUri) {
        imageUploadProgressBar.setVisibility(android.view.View.VISIBLE);

        MediaManager.get().upload(imageUri)
                .unsigned("unsigned_chat_uploads")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        imageUploadProgressBar.setVisibility(android.view.View.VISIBLE);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        imageUploadProgressBar.setVisibility(android.view.View.GONE);
                        String imageUrl = (String) resultData.get("secure_url");
                        sendImageMessage(imageUrl);
                    }

                    @Override
                    public void onError(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                        imageUploadProgressBar.setVisibility(android.view.View.GONE);
                        Toast.makeText(PrivateChatActivity.this, "Image upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                        imageUploadProgressBar.setVisibility(android.view.View.GONE);
                    }
                })
                .dispatch();
    }

    private void uploadAudioToCloudinary(Uri audioUri) {
        imageUploadProgressBar.setVisibility(android.view.View.VISIBLE);

        MediaManager.get().upload(audioUri)
                .option("resource_type", "auto")
                .unsigned("unsigned_chat_uploads")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        imageUploadProgressBar.setVisibility(android.view.View.VISIBLE);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        imageUploadProgressBar.setVisibility(android.view.View.GONE);
                        String audioUrl = (String) resultData.get("secure_url");
                        sendAudioMessage(audioUrl);
                    }

                    @Override
                    public void onError(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                        imageUploadProgressBar.setVisibility(android.view.View.GONE);
                        Toast.makeText(PrivateChatActivity.this, "Audio upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                        imageUploadProgressBar.setVisibility(android.view.View.GONE);
                    }
                })
                .dispatch();
    }

    private void sendImageMessage(String imageUrl) {
        String messageId = privateMessagesRef.push().getKey();
        if (messageId == null) return;

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        Message message = new Message(
                messageId,
                currentUserId,
                currentUserName,
                currentUserEmail,
                currentUserRole,
                null,
                "image",
                timeStamp,
                imageUrl,
                false
        );
        privateMessagesRef.child(messageId).setValue(message)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updateChatListForBothUsers("[Sent a image]", timeStamp);
                        incrementUnreadForRecipient();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(PrivateChatActivity.this, "DB Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void sendAudioMessage(String audioUrl) {
        String messageId = privateMessagesRef.push().getKey();
        if (messageId == null) return;

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        Message message = new Message(
                messageId,
                currentUserId,
                currentUserName,
                currentUserEmail,
                currentUserRole,
                null,
                "audio",
                timeStamp,
                audioUrl,
                false
        );
        privateMessagesRef.child(messageId).setValue(message)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updateChatListForBothUsers("[Sent a audio]", timeStamp);
                        incrementUnreadForRecipient();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(PrivateChatActivity.this, "DB Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // --- Audio Recording Logic ---

    private void startRecording() {
        try {
            audioFilePath = getExternalCacheDir().getAbsolutePath() + "/audio_" + System.currentTimeMillis() + ".m4a";
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecordingAndUpload() {
        try {
            if (mediaRecorder != null && isRecording) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                isRecording = false;
                Toast.makeText(this, "Recording stopped, uploading...", Toast.LENGTH_SHORT).show();
                File audioFile = new File(audioFilePath);
                if (!audioFile.exists() || audioFile.length() == 0) {
                    Toast.makeText(this, "Audio file not found or is empty!", Toast.LENGTH_SHORT).show();
                    return;
                }
                uploadAudioToCloudinary(Uri.fromFile(audioFile));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to stop recording: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void markMessagesAsRead() {
        privateMessagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    Message msg = msgSnap.getValue(Message.class);
                    if (msg != null && !msg.getSenderId().equals(currentUserId) && !msg.isSeen()) {
                        msgSnap.getRef().child("seen").setValue(true);
                    }
                }
                chatListRef.child(currentUserId).child(otherUserId).child("unreadCount").setValue(0);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        markMessagesAsRead();
    }
}