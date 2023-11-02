package uz.umarov.chatapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import org.greenrobot.eventbus.EventBus
import uz.umarov.chatapp.databinding.ActivityAccountBinding
import uz.umarov.chatapp.models.ChatMessage

class AccountActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAccountBinding
    private var currentUser: FirebaseUser? = null
    private var selectedImageUri: Uri? = null
    private var savedImageDownloadUrl: String? = null

    private lateinit var progressBar: ProgressBar
    private val GALLERY_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(binding.root)
        progressBar = binding.progressBar
        currentUser = FirebaseAuth.getInstance().currentUser
        loadProfileImageFromDatabase()
        binding.profilePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, GALLERY_REQUEST_CODE)
        }

        currentUser = FirebaseAuth.getInstance().currentUser

        val userId = currentUser?.uid
        if (userId != null) {
            val userNameRef =
                FirebaseDatabase.getInstance().reference.child("user").child(userId).child("name")
            val emailRef =
                FirebaseDatabase.getInstance().reference.child("user").child(userId).child("email")
            userNameRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userName = snapshot.getValue(String::class.java) ?: "Unknown User"
                    binding.editName.setText(userName)

                    EventBus.getDefault().post(userName)
                }

                override fun onCancelled(error: DatabaseError) {
                }
            })
            emailRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val email = snapshot.getValue(String::class.java) ?: "Unknown User"
                    binding.editEmail.setText(email)

                    EventBus.getDefault().post(email)
                }

                override fun onCancelled(error: DatabaseError) {
                }
            })
        }

        binding.saveButton.setOnClickListener {
            val newName = binding.editName.text.toString().trim()
            if (newName.isNotEmpty()) {
                updateUserName(newName)
                if (selectedImageUri != null) {
                    progressBar.visibility = View.VISIBLE
                    uploadImageToDatabase(selectedImageUri!!)
                } else {
                    savedImageDownloadUrl?.let { imageUrl ->
                        val imageUri = Uri.parse(imageUrl)
                        uploadImageToDatabase(imageUri)
                    }
                }
            } else {
                Toast.makeText(this, "Ism bo'sh bo'lishi mumkin emas!", Toast.LENGTH_SHORT).show()
            }
        }


        binding.logout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()

            val intent = Intent(this, RegisterActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadProfileImageFromDatabase() {
        val userId = currentUser?.uid
        if (userId != null) {
            val userRef = FirebaseDatabase.getInstance().reference.child("user").child(userId)
            val imageRef = userRef.child("profileImage")

            imageRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val imageUrl = snapshot.getValue(String::class.java)
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this@AccountActivity).load(imageUrl).into(binding.profilePhoto)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                }
            })
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK) {
            selectedImageUri = data?.data
            if (selectedImageUri != null) {
                binding.profilePhoto.setImageURI(selectedImageUri)
            }
        }
    }


    private fun uploadImageToDatabase(imageUri: Uri) {
        val userId = currentUser?.uid
        if (userId != null) {
            val userRef = FirebaseDatabase.getInstance().reference.child("user").child(userId)
            val imageRef = userRef.child("profileImage")

            val imageFileName = "profile_image.jpg"
            val storageRef =
                FirebaseStorage.getInstance().reference.child("profile_images").child(userId)
                    .child(imageFileName)
            storageRef.putFile(imageUri).addOnSuccessListener { taskSnapshot ->

                // Get the download URL of the uploaded image
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    // Save the download URL in the Realtime Database
                    imageRef.setValue(downloadUrl.toString()).addOnSuccessListener {
                        progressBar.progress = 0
                        progressBar.visibility = View.GONE
                        Toast.makeText(
                            this, "Rasm saqlandi !", Toast.LENGTH_SHORT
                        ).show()
                        savedImageDownloadUrl = downloadUrl.toString()

                        // Update the profile photo ImageView
                        Glide.with(this).load(savedImageDownloadUrl).into(binding.profilePhoto)
                    }.addOnFailureListener { e ->
                        Toast.makeText(
                            this, "Failed to upload profile image: ${e.message}", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.addOnFailureListener { e ->
                Toast.makeText(
                    this, "Failed to upload profile image: ${e.message}", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun updateUserName(newName: String) {
        val userId = currentUser?.uid
        if (userId != null) {
            val userRef = FirebaseDatabase.getInstance().reference.child("user").child(userId)
            userRef.child("name").setValue(newName).addOnSuccessListener {
                Toast.makeText(this, "Ism saqlandi !", Toast.LENGTH_SHORT).show()
                EventBus.getDefault().post(newName)
                updateMessagesWithNewName(userId, newName)
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update name: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            Toast.makeText(this, "User not signed in.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMessagesWithNewName(userId: String, newName: String) {
        val messagesRef = FirebaseDatabase.getInstance().reference.child("chat_messages")
        messagesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (messageSnapshot in snapshot.children) {
                    val chatMessage = messageSnapshot.getValue(ChatMessage::class.java)
                    if (chatMessage?.senderId == userId) {
                        chatMessage.sender = newName
                        messageSnapshot.ref.setValue(chatMessage)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })
    }
}