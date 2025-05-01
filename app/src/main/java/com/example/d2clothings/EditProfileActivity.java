package com.example.d2clothings;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private EditText fullName, phone, address, city, password, confirmPassword;
    private ImageView profileImage;
    private Button saveBtn, uploadImageBtn;

    private String userEmail;
    private Uri selectedImageUri;
    private String currentImagePath; // Store current image path

    private static final int PICK_IMAGE_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        db = FirebaseFirestore.getInstance();

        fullName = findViewById(R.id.fullName);
        phone = findViewById(R.id.phone);
        address = findViewById(R.id.address);
        city = findViewById(R.id.city);
        password = findViewById(R.id.password);
        confirmPassword = findViewById(R.id.confirmPassword);
        profileImage = findViewById(R.id.profileImage);
        saveBtn = findViewById(R.id.saveBtn);
        uploadImageBtn = findViewById(R.id.uploadImageBtn);

        userEmail = getIntent().getStringExtra("email");

        loadUserProfile();

        uploadImageBtn.setOnClickListener(v -> chooseImage());
        saveBtn.setOnClickListener(v -> saveProfile());
    }

    private void loadUserProfile() {
        db.collection("users").document(userEmail)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        fullName.setText(document.getString("fullName"));
                        phone.setText(document.getString("phone"));
                        address.setText(document.getString("address"));
                        city.setText(document.getString("city"));
                        // Don't load password for security reasons
                        // Leave password fields empty

                        currentImagePath = document.getString("profileImage");
                        if (currentImagePath != null && !currentImagePath.isEmpty()) {
                            File imageFile = new File(currentImagePath);
                            if (imageFile.exists()) {
                                Bitmap bitmap = BitmapFactory.decodeFile(currentImagePath);
                                profileImage.setImageBitmap(bitmap);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("Firestore", "Failed to load user profile", e));
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.getData();

            // Save image locally and update UI
            String newImagePath = saveImageLocally(selectedImageUri);
            if (newImagePath != null) {
                profileImage.setImageURI(Uri.parse(newImagePath));
                currentImagePath = newImagePath; // Update current image path
            }
        }
    }

    private String saveImageLocally(Uri imageUri) {
        try {
            // Delete old image if exists
            if (currentImagePath != null) {
                File oldFile = new File(currentImagePath);
                if (oldFile.exists()) {
                    oldFile.delete();
                }
            }

            File directory = new File(getFilesDir(), "profile_images");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            File imageFile = new File(directory, userEmail + ".jpg");
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            FileOutputStream outputStream = new FileOutputStream(imageFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();

            return imageFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("Image Save", "Error saving image locally", e);
            return null;
        }
    }

    private void saveProfile() {
        String name = fullName.getText().toString();
        String userPhone = phone.getText().toString();
        String userAddress = address.getText().toString();
        String userCity = city.getText().toString();
        String userPassword = password.getText().toString();
        String userConfirmPassword = confirmPassword.getText().toString();

        // Validate password change
        if (!userPassword.isEmpty()) {
            if (!userPassword.equals(userConfirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            // You should implement proper password validation here
            if (userPassword.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("fullName", name);
        userProfile.put("phone", userPhone);
        userProfile.put("address", userAddress);
        userProfile.put("city", userCity);
        userProfile.put("profileImage", currentImagePath); // Save updated image path

        // Only update password if a new one was entered
        if (!userPassword.isEmpty()) {
            userProfile.put("password", userPassword);
        }

        db.collection("users").document(userEmail)
                .set(userProfile, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show();
                    finish(); // Close activity after saving
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore", "Error updating profile", e);
                    Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show();
                });
    }
}