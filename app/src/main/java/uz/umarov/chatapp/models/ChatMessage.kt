package uz.umarov.chatapp.models

import com.google.firebase.database.Exclude
import java.io.Serializable

data class ChatMessage(
    var sender: String = "",
    val message: String = "",
    val timestamp: Long = 0,
    val senderId: String = "",
    val senderEmail: String = "",
    @get:Exclude var key: String = "",
    var date: String = "",
    var profilePhoto: String = "",
    var isReply: Boolean = false,
    var replyUser: String = "",
    var replyMessage: String = "",
    var viewCount: Int = 0
//    var image: String? = null
) : Serializable