package com.example.kiit_chatapp.activities;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupChatActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1001;
    private static final int PICK_AUDIO_REQUEST = 1002;
    private static final int REQUEST_PERMISSION_CODE = 42;

    private RecyclerView groupChatRecyclerView;
    private EditText messageEditText;
    private ImageButton sendButton, sendImageButton, recordAudioButton;
    private TextView groupNameTextView;
    private ProgressBar imageUploadProgressBar;

    private MessageAdapter messageAdapter;
    private List<Message> messageList;

    private FirebaseAuth mAuth;
    private DatabaseReference groupMessagesRef;
    private DatabaseReference usersRef;
    private DatabaseReference groupsRef;

    private String currentUserId;
    private String groupId = "default_group_id";
    private String currentUserName;
    private String currentUserEmail;
    private String currentUserRole;

    private ProgressDialog progressDialog;

    // Audio recording
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        // Initialize views
        groupChatRecyclerView = findViewById(R.id.chatRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        sendImageButton = findViewById(R.id.sendImageButton);
        recordAudioButton = findViewById(R.id.recordAudioButton);
        groupNameTextView = findViewById(R.id.groupNameTextView);
        imageUploadProgressBar = findViewById(R.id.imageUploadProgressBar);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Uploading...");

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser().getUid();

        groupId = getIntent().getStringExtra("groupId"); // Pass groupId via intent
        if (groupId == null) groupId = "default_group_id";

        groupMessagesRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("messages");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        groupsRef = FirebaseDatabase.getInstance().getReference("groups");

        // Set group name at the top
        groupsRef.child(groupId).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String groupName = snapshot.getValue(String.class);
                if (groupName != null) {
                    groupNameTextView.setText(groupName);
                } else {
                    groupNameTextView.setText("Group");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Fetch current user's info for sending messages
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
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Setup RecyclerView
        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(this, messageList);
        groupChatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        groupChatRecyclerView.setAdapter(messageAdapter);

        // Load existing messages
        loadMessages();

        // Send text message
        sendButton.setOnClickListener(v -> sendMessage());

        // Send image
        sendImageButton.setOnClickListener(v -> checkAndPickImage());

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
    }

    private void loadMessages() {
        groupMessagesRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Message message = snapshot.getValue(Message.class);
                if (message != null) {
                    messageList.add(message);
                    messageAdapter.notifyItemInserted(messageList.size() - 1);
                    groupChatRecyclerView.scrollToPosition(messageList.size() - 1);
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

        String messageId = groupMessagesRef.push().getKey();
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
                null
        );
        groupMessagesRef.child(messageId).setValue(message)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        messageEditText.setText("");
                    } else {
                        Toast.makeText(GroupChatActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(GroupChatActivity.this, "DB Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void checkAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        REQUEST_PERMISSION_CODE);
            } else {
                pickImage();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_CODE);
            } else {
                pickImage();
            }
        }
    }

    private void checkAndPickAudio() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION_CODE);
        } else {
            pickAudio();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
            recordAudioButton.setImageResource(R.drawable.ic_stop);
        } else {
            Toast.makeText(this, "Permission denied to record audio.", Toast.LENGTH_SHORT).show();
        }
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
                        Toast.makeText(GroupChatActivity.this, "Image upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
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
                        Toast.makeText(GroupChatActivity.this, "Audio upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onReschedule(String requestId, com.cloudinary.android.callback.ErrorInfo error) {
                        imageUploadProgressBar.setVisibility(android.view.View.GONE);
                    }
                })
                .dispatch();
    }

    private void sendImageMessage(String imageUrl) {
        String messageId = groupMessagesRef.push().getKey();
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
                imageUrl
        );
        groupMessagesRef.child(messageId).setValue(message)
                .addOnFailureListener(e -> Toast.makeText(GroupChatActivity.this, "DB Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void sendAudioMessage(String audioUrl) {
        String messageId = groupMessagesRef.push().getKey();
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
                audioUrl
        );
        groupMessagesRef.child(messageId).setValue(message)
                .addOnFailureListener(e -> Toast.makeText(GroupChatActivity.this, "DB Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
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
}