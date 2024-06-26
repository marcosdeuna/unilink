package com.marcosdeuna.unilink.ui.user

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.marcosdeuna.unilink.data.model.User
import com.marcosdeuna.unilink.databinding.ItemUserListBinding
import com.marcosdeuna.unilink.ui.auth.AuthViewModel

class UserListAdapter(
    val context: Context,
    val list: ArrayList<User>,
    val lastMessage: Map<String, String>,
    val selectedUsers: ArrayList<String>,
    val b: Boolean,
    val onItemClicked: (Int, User) -> Unit,
    val authViewModel: AuthViewModel
): RecyclerView.Adapter<UserListAdapter.UserViewHolder>() {
    inner class UserViewHolder (val binding: ItemUserListBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        return UserViewHolder(ItemUserListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int {
        return list.size
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {


        val currentUser = list[position]

        if (!b) {
            holder.binding.checkboxInclude.visibility = View.GONE
            lastMessage[currentUser.id]?.let { lastMessage ->
                holder.binding.lastMessage.text = lastMessage
            }

            if(currentUser.status.equals("online")) {
                holder.binding.onlineStatus.visibility = View.VISIBLE
                holder.binding.offlineStatus.visibility = View.GONE
            } else {
                holder.binding.offlineStatus.visibility = View.VISIBLE
                holder.binding.onlineStatus.visibility = View.GONE
            }
        }

        // Set user name and email
        authViewModel.getUserSession {
            if (it != null) {
                if(it.id == currentUser.id) {
                    holder.binding.userFullName.text = "Tú"
                } else {
                    holder.binding.userFullName.text =
                        "${currentUser.firstName} ${currentUser.lastName}"
                }
            }
        }


        Glide.with(context)
            .load(currentUser.profilePicture)
            .into(holder.binding.profilePicture)

        if (selectedUsers.contains(currentUser.id)) {
            holder.binding.checkboxInclude.isChecked = true
        }

        holder.binding.checkboxInclude.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedUsers.add(currentUser.id)
            } else {
                selectedUsers.remove(currentUser.id)
            }
        }

        holder.binding.root.setOnClickListener{
            onItemClicked(position, currentUser)
        }

    }

}