package uz.umarov.chatapp.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

class FadeOutAnimator : DefaultItemAnimator() {

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        dispatchRemoveStarting(holder)
        val animator = holder.itemView.animate()
        animator.alpha(0f).setDuration(removeDuration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    dispatchRemoveFinished(holder)
                    holder.itemView.alpha = 1f // Reset the alpha value for potential reuse
                }
            })
        animator.start()
        return false // We are not handling the remove animation, let the DefaultItemAnimator do it.
    }
}
