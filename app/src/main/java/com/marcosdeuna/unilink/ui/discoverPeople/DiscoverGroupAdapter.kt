package com.marcosdeuna.unilink.ui.discoverPeople

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.content.Context
import android.widget.ImageView
import com.marcosdeuna.unilink.data.model.Group
import com.marcosdeuna.unilink.data.model.Post
import com.marcosdeuna.unilink.data.model.User
import com.marcosdeuna.unilink.databinding.DialogUserDetailsBinding
import com.marcosdeuna.unilink.databinding.ItemUserLayoutBinding
import com.marcosdeuna.unilink.ui.auth.AuthViewModel
import com.marcosdeuna.unilink.ui.user.UserListAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch


class DiscoverGroupAdapter(val context: Context, val list: ArrayList<Group>, val authViewModel: AuthViewModel, val onSendClicked: (Int, User) -> Unit, val onItemClicked: (Int, User) -> Unit,
val onEditClicked: (Int, Group) -> Unit, val onDeleteClicked: (Int, Group)-> Unit) : RecyclerView.Adapter<DiscoverGroupAdapter.DiscoverGroupViewHolder>() {

    inner class DiscoverGroupViewHolder(val binding: ItemUserLayoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiscoverGroupViewHolder {
        return DiscoverGroupViewHolder(ItemUserLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int {
        return list.size
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: DiscoverGroupViewHolder, position: Int) {
        val currentGroup = list[position]

        authViewModel.getUserById(currentGroup.admin) { user ->
            authViewModel.getUserSession {
                userSession ->
                user?.let {
                    if (userSession != null) {
                        if (userSession.id == it.id) {
                            holder.binding.btnEdit.visibility = android.view.View.VISIBLE
                            holder.binding.btnDelete.visibility = android.view.View.VISIBLE
                            holder.binding.btnEdit.setOnClickListener {
                                onEditClicked(position, currentGroup)
                            }
                            holder.binding.btnDelete.setOnClickListener {
                                onDeleteClicked(position, currentGroup)
                            }
                            holder.binding.chat.visibility = android.view.View.GONE
                        } else {
                            holder.binding.btnEdit.visibility = android.view.View.GONE
                            holder.binding.btnDelete.visibility = android.view.View.GONE
                        }
                    }
                }
            }
        }

        // Set group name and description
        holder.binding.userName.text = currentGroup.name
        holder.binding.userEmail.text = "Grupo de ${currentGroup.members.size} miembros"

        // Clear previous images in ViewFlipper
        holder.binding.viewFlipperImages.removeAllViews()

        // Load group images into ViewFlipper
        for (imageUrl in currentGroup.images) {
            val imageView = createImageView(imageUrl)
            holder.binding.viewFlipperImages.addView(imageView)
        }

        holder.binding.chat.setOnClickListener {
            authViewModel.getUserById(currentGroup.admin) { user ->
                user?.let {
                    onSendClicked(position, it)
                }
            }
        }

        // Start flipping images
        holder.binding.viewFlipperImages.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    holder.binding.viewFlipperImages.stopFlipping()
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (motionEvent.x < view.width / 2) {
                        holder.binding.viewFlipperImages.showNext()
                    } else {
                        holder.binding.viewFlipperImages.showPrevious()
                    }
                }
            }
            true
        }

        holder.binding.view.setOnClickListener {
            showGroupDetailsDialog(currentGroup)
        }
    }

    private fun createImageView(imageUrl: String): ImageView {
        val imageView = ImageView(context)
        imageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        Glide.with(context)
            .load(imageUrl)
            .into(imageView)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        return imageView
    }

    private fun showGroupDetailsDialog(group: Group) {
        val dialogBinding = DialogUserDetailsBinding.inflate(LayoutInflater.from(context))

        dialogBinding.userFullName.text = group.name
        authViewModel.getUserById(group.admin) { adminUser ->
            authViewModel.getUserSession {
                if (adminUser != null) {
                    if(adminUser.id == it?.id) {
                        dialogBinding.userEmailDetail.text = "Creado por: Mi"
                    } else {
                        dialogBinding.userEmailDetail.text = "Creado por: ${adminUser.firstName} ${adminUser.lastName}"
                    }
                }
            }
        }
        dialogBinding.userDescription.text = group.description
        dialogBinding.userAge.text = "Miembros: ${group.members.size}"
        dialogBinding.userCareer.text = ""
        dialogBinding.userGenre.text = ""

        // Initialize an empty list for members
        val members = arrayListOf<User>()
        var loadedMemberCount = 0

        // Iterate through group members and fetch each user's details
        for (memberId in group.members) {
            authViewModel.getUserById(memberId) { user ->
                user?.let {
                    members.add(it)
                }
                loadedMemberCount++

                // Check if all members are loaded
                if (loadedMemberCount == group.members.size) {
                    // All members are loaded, set up RecyclerView adapter
                    dialogBinding.recyclerViewUsers.adapter = UserListAdapter(context, members, mapOf(), arrayListOf(), false, onItemClicked = { position, user ->
                        onItemClicked(position, user)
                        dialogBinding.closeButton.performClick()
                    }, authViewModel)

                    // Show the dialog only when all data is ready
                    val dialog = AlertDialog.Builder(context)
                        .setView(dialogBinding.root)
                        .create()

                    dialogBinding.closeButton.setOnClickListener {
                        dialog.dismiss()
                    }

                    dialog.show()
                }
            }
        }
    }

}
