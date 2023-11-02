package uz.umarov.chatapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import uz.umarov.chatapp.databinding.ActivityRegisterBinding
import uz.umarov.chatapp.models.User
import uz.umarov.chatapp.utils.FirebaseAuthErrors

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var userList: ArrayList<User>
    private lateinit var mDbRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(binding.root)
        userList = ArrayList()

        firebaseAuth = FirebaseAuth.getInstance()

        if (isUserLoggedIn()) {
            navigateToMainActivity()
        }

        binding.buttonRegister.setOnClickListener {
            val name = binding.editTextName.text.toString().trim()
            val email = binding.email.text.toString().trim()
            val password = binding.password.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                return@setOnClickListener
            }
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = firebaseAuth.currentUser
                        if (user != null) {
                            addUserToDatabase(user.uid, name, email)
                        }

                        Toast.makeText(
                            this, "Muvaffaqiyatli ro'yhatdan o'tildi", Toast.LENGTH_SHORT
                        ).show()

                        navigateToMainActivity(user?.uid)
                    } else {
                        val errorCode = (task.exception as? FirebaseAuthException)?.errorCode
                        if (errorCode == FirebaseAuthErrors.ERROR_EMAIL_ALREADY_IN_USE) {
                            Toast.makeText(
                                this, "Siz allaqachon ro'yhatdan o'tgansiz", Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Log.e(
                                "uz.umarov.neftchipfk.RegisterActivity",
                                "Failed to register: ${task.exception}"
                            )
                        }
                    }
                }
        }

        binding.alreadyRegistered.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun isUserLoggedIn(): Boolean {
        val currentUser = firebaseAuth.currentUser
        return currentUser != null
    }

    private fun navigateToMainActivity(userId: String? = null) {
        val intent = Intent(this, ChatActivity::class.java)
        if (userId != null) {
            intent.putExtra("user_id", userId)
        }
        startActivity(intent)
        finish()
    }

    private fun addUserToDatabase(userId: String, name: String, email: String) {
        mDbRef = FirebaseDatabase.getInstance().reference
        mDbRef.child("user").child(userId).setValue(User(name, email, userId))
    }
}
