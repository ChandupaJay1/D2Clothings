package com.example.d2clothings;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.example.d2clothings.fragments.AdminLoginFragment;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class SigninActivity extends AppCompatActivity {

    private EditText etEmail, etPassword, globalEmail;
    private Button btnSignIn, btnSignUp, btnAdminLogin, btnForgotPassword;
    private FirebaseFirestore db;
    private ProgressDialog progressDialog;
    private RequestQueue requestQueue;

    // Notification related constants
    private static final String CHANNEL_ID = "password_reset_channel";
    private static final int NOTIFICATION_ID = 1;
    private boolean notificationPermissionGranted = false;

    private static final String SERVLET_URL = "https://0a20-2407-c00-6003-f11c-4db9-ada4-9f79-3c50.ngrok-free.app/D2ClothingsFP2/ForgetPassword";

    // Launcher for requesting notification permission
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        // Initialize permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    notificationPermissionGranted = isGranted;
                    if (!isGranted) {
                        Toast.makeText(this, "Notification permission denied. You won't receive password reset notifications.",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );

        // Request notification permission if needed
        requestNotificationPermission();

        // Create notification channel for Android 8.0 and higher
        createNotificationChannel();

        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnSignUp = findViewById(R.id.btnSignUp);
        btnAdminLogin = findViewById(R.id.btnAdminLogin);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Signing In...");

        // Auto Redirect if already logged in
        if (isUserLoggedIn()) {
            startActivity(new Intent(SigninActivity.this, HomeActivity.class));
            finish();
        }

        btnSignIn.setOnClickListener(v -> loginUser());

        // Redirect to SignupActivity when "Sign Up Here" button is clicked
        btnSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(SigninActivity.this, SignupActivity.class);
            startActivity(intent);
        });

        // Open AdminLoginFragment when Admin Login button is clicked
        btnAdminLogin = findViewById(R.id.btnAdminLogin);

        btnAdminLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AdminLoginFragment adminLoginFragment = new AdminLoginFragment();
                adminLoginFragment.show(getSupportFragmentManager(), "adminLoginDialog");
            }
        });

        requestQueue = Volley.newRequestQueue(this);
        btnForgotPassword = findViewById(R.id.btnForgotPassword);
        btnForgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                globalEmail = findViewById(R.id.etEmail);

                String globalemail = globalEmail.getText().toString();

                if (globalemail.isEmpty()) {
                    Toast.makeText(SigninActivity.this, "Email Can Not Be Empty", Toast.LENGTH_SHORT).show();
                } else {
                    // Show progress dialog while processing password reset
                    ProgressDialog resetProgressDialog = new ProgressDialog(SigninActivity.this);
                    resetProgressDialog.setMessage("Processing password reset...");
                    resetProgressDialog.show();

                    fetchUniqueValue(globalemail, resetProgressDialog);
                }
            }
        });

        ImageView bgGif = findViewById(R.id.bgGif);
        Glide.with(this).asGif().load(R.drawable.clothing_bg).into(bgGif);
    }

    // Request notification permission for Android 13+
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {

                // Check if we should show rationale
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.POST_NOTIFICATIONS)) {
                    // Show rationale if needed
                    Toast.makeText(this,
                            "Notification permission is needed to alert you when password is reset",
                            Toast.LENGTH_LONG).show();
                }

                // Request the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Permission already granted
                notificationPermissionGranted = true;
            }
        } else {
            // For versions before Android 13, permission is granted at install time
            notificationPermissionGranted = true;
        }
    }

    // Create notification channel for Android 8.0 and higher
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Password Reset Channel";
            String description = "Channel for password reset notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // Show notification for password reset
    private void showPasswordResetNotification(String email) {
        // Check if we have permission to show notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !notificationPermissionGranted) {
            // If permission is not granted, just show a toast
            Toast.makeText(this, "Password reset successful", Toast.LENGTH_LONG).show();
            return;
        }

        // Create an intent to open the app when notification is tapped
        Intent intent = new Intent(this, SigninActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification) // Make sure you have this icon in your drawable resources
                .setContentTitle("Password Reset Successful")
                .setContentText("Your password for " + email + " has been reset successfully")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Show the notification
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.show();

        db.collection("users").document(email).get().addOnCompleteListener(task -> {
            progressDialog.dismiss();
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    String storedPassword = document.getString("password");

                    if (storedPassword != null && storedPassword.equals(password)) {
                        saveUserEmail(email); // Save email locally
                        Toast.makeText(SigninActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(SigninActivity.this, HomeActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(SigninActivity.this, "Incorrect Password!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(SigninActivity.this, "User not found!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(SigninActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Save email in SharedPreferences
    private void saveUserEmail(String email) {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("userEmail", email);
        editor.apply();
    }

    // Check if user email is saved (already logged in)
    private boolean isUserLoggedIn() {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        return sharedPreferences.contains("userEmail");
    }

    private void fetchUniqueValue(String email, ProgressDialog dialog) {
        // Create POST request
        StringRequest stringRequest = new StringRequest(Request.Method.POST, SERVLET_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        updateNewPassword(response, email, dialog);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dialog.dismiss();
                        Log.i("errorrrr", "Error sending email: " + error.getMessage());
                        Toast.makeText(SigninActivity.this, "Password reset failed. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("email", email);
                return params;
            }
        };

        requestQueue.add(stringRequest);
    }

    private void updateNewPassword(String response, String email, ProgressDialog dialog) {
        db.collection("users")
                .whereEqualTo("email", email)
                .get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        dialog.dismiss();

                        if (!queryDocumentSnapshots.isEmpty()) {
                            DocumentSnapshot documentSnapshot = queryDocumentSnapshots.getDocuments().get(0);
                            HashMap<String, Object> updatedUserData = new HashMap<>();
                            updatedUserData.put("password", response);

                            db.collection("users").document(documentSnapshot.getId()).update(updatedUserData).addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void unused) {
                                    Toast.makeText(SigninActivity.this, "Password Updated", Toast.LENGTH_SHORT).show();

                                    // Show notification when password is reset successfully
                                    showPasswordResetNotification(email);
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Toast.makeText(SigninActivity.this, "Password Updating Failed", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            Toast.makeText(SigninActivity.this, "User Not Found", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        dialog.dismiss();
                        Toast.makeText(SigninActivity.this, "User Finding Failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}