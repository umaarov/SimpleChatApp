package uz.umarov.chatapp

import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import uz.umarov.chatapp.adapters.ChatAdapter
import uz.umarov.chatapp.data.DataCachingManager
import uz.umarov.chatapp.databinding.ActivityChatBinding
import uz.umarov.chatapp.models.ChatMessage

class ChatActivity : AppCompatActivity(), ChatAdapter.ChatViewHolderCallback {

    private lateinit var binding: ActivityChatBinding
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    private lateinit var database: DatabaseReference
    private lateinit var messageRef: DatabaseReference
    private var currentUser: FirebaseUser? = null
    private var selectedMessage: ChatMessage? = null
    private var isMessageListenerAdded = false
    private lateinit var messageListener: ValueEventListener
    private lateinit var dataCachingManager: DataCachingManager
    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        currentUser = FirebaseAuth.getInstance().currentUser

        adapter = ChatAdapter(chatMessages, currentUser?.email ?: "", this)
        binding.recyclerViewChat.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewChat.adapter = adapter

        dataCachingManager = DataCachingManager(this)

        dataCachingManager.fetchCachedChatMessages { cachedChatMessages ->
            if (cachedChatMessages.isNotEmpty()) {
                processChatMessages(cachedChatMessages)
            } else {
                dataCachingManager.fetchChatMessages { firebaseChatMessages ->
                    processChatMessages(firebaseChatMessages)
                }
            }
        }

        binding.profile.setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
        }

        binding.buttonSend.setOnClickListener {
            val message = binding.editTextMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                if (selectedMessage != null) {
                    sendReplyMessage(selectedMessage!!, message)
                    binding.replyUser.visibility = View.GONE
                    binding.replyMessage.visibility = View.GONE
                    binding.arrow.visibility = View.GONE
                    binding.cancelReply.visibility = View.GONE
                    selectedMessage = null
                } else {
                    sendMessage(message)
                }
                binding.editTextMessage.text.clear()
            }
        }

        binding.cancelReply.setOnClickListener {
            binding.replyUser.visibility = View.GONE
            binding.replyMessage.visibility = View.GONE
            binding.arrow.visibility = View.GONE
            binding.cancelReply.visibility = View.GONE
            selectedMessage = null
        }

        currentUser = FirebaseAuth.getInstance().currentUser
        database = FirebaseDatabase.getInstance().reference
        messageRef = database.child("chat_messages")

        messageListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatMessages.clear()
                for (messageSnapshot in snapshot.children) {
                    val chatMessage = messageSnapshot.getValue(ChatMessage::class.java)
                    chatMessage?.let {
                        it.key = messageSnapshot.key.toString()
                        val senderId = it.senderId
                        val userRef =
                            FirebaseDatabase.getInstance().reference.child("user").child(senderId)
                        userRef.child("profileImage")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(profileSnapshot: DataSnapshot) {
                                    val profilePhotoUrl =
                                        profileSnapshot.getValue(String::class.java)
                                    it.profilePhoto = profilePhotoUrl ?: ""
                                    adapter.notifyDataSetChanged()
                                }

                                override fun onCancelled(error: DatabaseError) {}
                            })

                        chatMessages.add(it)
                    }
                }
                adapter.notifyDataSetChanged()
                binding.recyclerViewChat.scrollToPosition(chatMessages.size - 1)

            }

            override fun onCancelled(error: DatabaseError) {}
        }

        if (!isMessageListenerAdded) {
            messageRef.addValueEventListener(messageListener)
            isMessageListenerAdded = true
        }
    }

    override fun onStop() {
        super.onStop()
    }

    private fun processChatMessages(chatMessages: List<ChatMessage>) {
        val processedChatMessages = mutableListOf<ChatMessage>()
        val userIdsToFetch = mutableSetOf<String>()

        for (message in chatMessages) {
            val userId = message.senderId

            dataCachingManager.fetchUserProfilePicture(userId) { imageUrl ->
                val updatedMessage = message.copy(profilePhoto = imageUrl)
                processedChatMessages.add(updatedMessage)
            }

            if (userId !in userIdsToFetch) {
                userIdsToFetch.add(userId)
            }
        }
    }

    override fun onReplyClick(position: Int) {
        val clickedMessage = chatMessages[position]
        selectedMessage = clickedMessage
        binding.cancelReply.visibility = View.VISIBLE
        binding.replyUser.visibility = View.VISIBLE
        binding.replyMessage.visibility = View.VISIBLE
        binding.arrow.visibility = View.VISIBLE
        binding.replyUser.text = clickedMessage.sender
        binding.replyMessage.text = clickedMessage.message
    }

    override fun onLongClick(position: Int) {
        val chatMessage = chatMessages[position]

        if (chatMessage.senderEmail == currentUser?.email) {
            startActionMode(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    mode?.menuInflater?.inflate(R.menu.menu_delete, menu)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    return false
                }

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                    return when (item?.itemId) {
                        R.id.action_delete -> {
                            deleteChatMessage(position)
                            mode?.finish()
                            true
                        }

                        R.id.action_reply -> {
                            onReplyClick(position)
                            mode?.finish()
                            true
                        }

                        else -> false
                    }
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                    actionMode = null
                }
            })
        } else {
            val clickedMessage = chatMessages[position]
            selectedMessage = clickedMessage
            binding.replyUser.visibility = View.VISIBLE
            binding.replyMessage.visibility = View.VISIBLE
            binding.arrow.visibility = View.VISIBLE
            binding.cancelReply.visibility = View.VISIBLE
            binding.replyUser.text = clickedMessage.sender
            binding.replyMessage.text = clickedMessage.message
        }
    }

    private fun deleteChatMessage(position: Int) {
        val chatMessage = chatMessages[position]
        val messageRef = database.child("chat_messages").child(chatMessage.key)
        messageRef.removeValue().addOnCompleteListener { task ->
            if (task.isSuccessful) {
            } else {
            }
        }
    }

    private fun sendMessage(message: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val senderId = currentUser.uid
            val senderEmail = currentUser.email

            val userNameRef =
                FirebaseDatabase.getInstance().reference.child("user").child(senderId).child("name")
            userNameRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val senderName = snapshot.getValue(String::class.java) ?: "Unknown User"
                    val timestamp = System.currentTimeMillis()

                    val chatMessage = ChatMessage(
                        senderName, message, timestamp, senderId, senderEmail!!
                    )
                    messageRef.push().setValue(chatMessage)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun sendReplyMessage(selectedMessage: ChatMessage, replyMessage: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val senderId = currentUser.uid
            val userRef = FirebaseDatabase.getInstance().reference.child("user").child(senderId)
            userRef.child("name").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val senderName = snapshot.getValue(String::class.java) ?: "Unknown User"
                    val replyChatMessage = ChatMessage(
                        sender = senderName,
                        message = replyMessage,
                        timestamp = System.currentTimeMillis(),
                        senderId = senderId,
                        senderEmail = currentUser.email ?: "",
                        isReply = true,
                        replyUser = selectedMessage.sender,
                        replyMessage = selectedMessage.message,
                        profilePhoto = currentUser.photoUrl.toString()
                    )

                    messageRef.push().setValue(replyChatMessage)
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (actionMode != null) {
            actionMode?.finish()
            actionMode = null
        }

        if (isMessageListenerAdded) {
            messageRef.removeEventListener(messageListener)
            isMessageListenerAdded = false
        }
    }
}
