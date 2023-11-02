package uz.umarov.chatapp.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import uz.umarov.chatapp.R
import uz.umarov.chatapp.databinding.ItemReceiveMessageBinding
import uz.umarov.chatapp.databinding.ItemSendMessageBinding
import uz.umarov.chatapp.models.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val chatMessages: List<ChatMessage>,
    private val currentUserEmail: String,
    private val callback: ChatViewHolderCallback
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    interface ChatViewHolderCallback {
        fun onReplyClick(position: Int)
        fun onLongClick(position: Int)
    }

    companion object {
        private const val VIEW_TYPE_SEND_MESSAGE = 1
        private const val VIEW_TYPE_RECEIVE_MESSAGE = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == VIEW_TYPE_SEND_MESSAGE) {
            val binding = ItemSendMessageBinding.inflate(inflater, parent, false)
            SendMessageViewHolder(binding, callback)
        } else {
            val binding = ItemReceiveMessageBinding.inflate(inflater, parent, false)
            ReceiveMessageViewHolder(binding, callback)
        }
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chatMessage = chatMessages[position]
        holder.bind(chatMessage)
    }

    override fun getItemCount(): Int {
        return chatMessages.size
    }

    override fun getItemViewType(position: Int): Int {
        val chatMessage = chatMessages[position]
        return if (chatMessage.senderEmail == currentUserEmail) {
            VIEW_TYPE_SEND_MESSAGE
        } else {
            VIEW_TYPE_RECEIVE_MESSAGE
        }
    }

    abstract inner class ChatViewHolder(
        open val binding: ViewBinding, private val callback: ChatViewHolderCallback
    ) : RecyclerView.ViewHolder(binding.root), View.OnLongClickListener {

        init {
            itemView.setOnLongClickListener(this)
        }

        override fun onLongClick(v: View?): Boolean {
            val chatMessage = chatMessages[adapterPosition]

            if (chatMessage.senderEmail == currentUserEmail) {
                callback.onLongClick(adapterPosition)
            } else {
                callback.onReplyClick(adapterPosition)
            }

            return true
        }

        abstract fun bind(chatMessage: ChatMessage)
    }

    inner class SendMessageViewHolder(
        override val binding: ItemSendMessageBinding, callback: ChatViewHolderCallback
    ) : ChatViewHolder(binding, callback) {

        @SuppressLint("SetTextI18n")
        override fun bind(chatMessage: ChatMessage) {

            binding.textViewMessage.text = chatMessage.message
            val timestamp =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(chatMessage.timestamp))
            binding.timestamp.text = timestamp
            val isCurrentUserMessage = chatMessage.senderEmail == currentUserEmail

//            Glide.with(binding.root.context).load(chatMessage.imageURL).into(binding.messageImage)
            if (isCurrentUserMessage) {
                binding.textViewMessage.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.userMessageColor)
                )
            } else {
                binding.textViewMessage.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.black)
                )
            }

            if (chatMessage.isReply) {
                binding.itemReplyUser.visibility = View.VISIBLE
                binding.itemReplyMessage.visibility = View.VISIBLE
                binding.itemArrow.visibility = View.VISIBLE
                binding.itemReplyUser.text = chatMessage.replyUser

                val replyMessage = chatMessage.replyMessage
                if (replyMessage.length > 20) {
                    binding.itemReplyMessage.text = "${replyMessage.substring(0, 25)}..."
                } else {
                    binding.itemReplyMessage.text = replyMessage
                }
            } else {
                binding.itemReplyUser.visibility = View.GONE
                binding.itemReplyMessage.visibility = View.GONE
                binding.itemArrow.visibility = View.GONE
            }
        }
    }

    inner class ReceiveMessageViewHolder(
        override val binding: ItemReceiveMessageBinding, callback: ChatViewHolderCallback
    ) : ChatViewHolder(binding, callback) {

        @SuppressLint("SetTextI18n")
        override fun bind(chatMessage: ChatMessage) {
            Glide.with(binding.root.context).load(chatMessage.profilePhoto)
                .placeholder(R.drawable.ghost).circleCrop().into(binding.profilePhoto)
//            Glide.with(binding.root.context).load(chatMessage.imageURL).into(binding.messageImage)
            binding.textViewUser.text = chatMessage.sender
            binding.textViewMessage.text = chatMessage.message
            val timestamp = SimpleDateFormat(
                "HH:mm", Locale.getDefault()
            ).format(Date(chatMessage.timestamp))
            binding.timestamp.text = timestamp
            val isCurrentUserMessage = chatMessage.senderEmail == currentUserEmail

            if (isCurrentUserMessage) {
                binding.textViewMessage.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.userMessageColor)
                )
                binding.textViewUser.setTextColor(
                    ContextCompat.getColor(
                        binding.root.context, R.color.userMessageColor
                    )
                )
            } else {
                binding.textViewMessage.setTextColor(
                    ContextCompat.getColor(binding.root.context, android.R.color.black)
                )
                binding.textViewUser.setTextColor(
                    ContextCompat.getColor(
                        binding.root.context, android.R.color.black
                    )
                )
            }

            binding.adminVerify.visibility = if (chatMessage.senderEmail == "admin@gmail.com") {
                View.VISIBLE
            } else {
                View.GONE
            }

            if (chatMessage.isReply) {
                binding.itemReplyUser.visibility = View.VISIBLE
                binding.itemReplyMessage.visibility = View.VISIBLE
                binding.itemArrow.visibility = View.VISIBLE
                binding.itemReplyUser.text = chatMessage.replyUser

                val replyMessage = chatMessage.replyMessage
                if (replyMessage.length > 20) {
                    binding.itemReplyMessage.text = "${replyMessage.substring(0, 25)}..."
                } else {
                    binding.itemReplyMessage.text = replyMessage
                }
            } else {
                binding.itemReplyUser.visibility = View.GONE
                binding.itemReplyMessage.visibility = View.GONE
                binding.itemArrow.visibility = View.GONE
            }

        }
    }
}