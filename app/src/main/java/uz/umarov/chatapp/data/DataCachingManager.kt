package uz.umarov.chatapp.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import uz.umarov.chatapp.models.ChatMessage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class DataCachingManager(private val context: Context) {

    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference
    private val cacheDirectory: File = context.cacheDir

    fun fetchCachedChatMessages(callback: (List<ChatMessage>) -> Unit) {
        val cacheFile = File(cacheDirectory, "cached_chat_messages")
        if (cacheFile.exists()) {
            val cachedChatMessages = readCachedData(cacheFile)
            callback(cachedChatMessages)
        } else {
            callback(emptyList())
        }
    }

    fun fetchChatMessages(callback: (List<ChatMessage>) -> Unit) {
        database.child("chat_messages").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chatMessages = mutableListOf<ChatMessage>()
                for (messageSnapshot in snapshot.children) {
                    val chatMessage = messageSnapshot.getValue(ChatMessage::class.java)
                    chatMessage?.let { chatMessages.add(it) }
                }
                cacheChatMessages(chatMessages)
                callback(chatMessages)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DataCachingManager", "Firebase fetch error: ${error.message}")
                callback(emptyList())
            }
        })
    }

    private fun cacheChatMessages(chatMessages: List<ChatMessage>) {
        val cacheFile = File(cacheDirectory, "cached_chat_messages")
        writeDataToFile(cacheFile, chatMessages.toString())
    }

    private fun readCachedData(cacheFile: File): List<ChatMessage> {
        return try {
            ObjectInputStream(FileInputStream(cacheFile)).use { stream ->
                val cachedData = stream.readObject() as List<ChatMessage>
                cachedData
            }
        } catch (e: Exception) {
            Log.e("DataCachingManager", "Cache read error: ${e.message}")
            emptyList()
        }
    }

    private fun writeDataToFile(cacheFile: File, data: String) {
        try {
            ObjectOutputStream(FileOutputStream(cacheFile)).use { stream ->
                stream.writeObject(data)
            }
        } catch (e: Exception) {
            Log.e("DataCachingManager", "Cache write error: ${e.message}")
        }
    }

    fun fetchUserProfilePicture(userId: String, callback: (String) -> Unit) {
        val cacheFile = File(cacheDirectory, "cached_user_profile_$userId")
        if (cacheFile.exists()) {
            val cachedImageUrl = readCachedData(cacheFile)
            callback(cachedImageUrl.toString())
        } else {
            fetchUserProfilePictureFromFirebase(userId) { imageUrl ->
                callback(imageUrl)
                cacheUserProfilePicture(userId, imageUrl)
            }
        }
    }

    private fun fetchUserProfilePictureFromFirebase(userId: String, callback: (String) -> Unit) {
        database.child("users").child(userId).child("profileImage")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val imageUrl = snapshot.getValue(String::class.java) ?: ""
                    callback(imageUrl)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("DataCachingManager", "Firebase fetch error: ${error.message}")
                    callback("")
                }
            })
    }

    private fun cacheUserProfilePicture(userId: String, imageUrl: String) {
        val cacheFile = File(cacheDirectory, "cached_user_profile_$userId")
        writeDataToFile(cacheFile, imageUrl)
    }
}
