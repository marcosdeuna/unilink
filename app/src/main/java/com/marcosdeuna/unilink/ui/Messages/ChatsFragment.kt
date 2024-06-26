package com.marcosdeuna.unilink.ui.Messages

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.marcosdeuna.unilink.R
import com.marcosdeuna.unilink.data.model.Message
import com.marcosdeuna.unilink.data.model.User
import com.marcosdeuna.unilink.databinding.FragmentChatsBinding
import com.marcosdeuna.unilink.ui.auth.AuthViewModel
import com.marcosdeuna.unilink.ui.post.ListPostAdapter
import com.marcosdeuna.unilink.ui.post.PostViewModel
import com.marcosdeuna.unilink.ui.user.UserListAdapter
import com.marcosdeuna.unilink.ui.user.UserViewModel
import com.marcosdeuna.unilink.util.UIState
import com.marcosdeuna.unilink.util.toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@AndroidEntryPoint
class ChatsFragment : Fragment() {

    private lateinit var binding: FragmentChatsBinding
    private var userList = arrayListOf<User>()
    private var list = arrayListOf<String>()
    private val messageViewModel: MessageViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()
    val postViewModel: PostViewModel by viewModels()
    private var currentUser: User? = null
    private val userLastMessages = mutableMapOf<String, String>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    val adapter by lazy {
        ListPostAdapter(
            onItemClicked = { position, post ->
                // Acción al hacer clic en un elemento
                findNavController().navigate(R.id.action_postFragment_to_detailPostFragment, Bundle().apply {
                    putParcelable("post", post)
                })
            },
            onEditClicked = { position, post ->
                // Acción al hacer clic en editar
                findNavController().navigate(R.id.action_postFragment_to_createPostFragment, Bundle().apply {
                    putString("operation", "edit")
                    putParcelable("post", post)
                })
            },
            onDeleteClicked = { position, post ->
                // Acción al hacer clic en eliminar
                postViewModel.deletePost(post)
            },
            onSendClicked = { position, post ->
                // Acción al hacer clic en enviar
                findNavController().navigate(R.id.action_postFragment_to_messageFragment, Bundle().apply {
                    putString("receiverId", post.userId)
                    putParcelable("post", post)
                })
            },
            authViewModel = authViewModel,
            coroutineScope = coroutineScope
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentChatsBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.profileModal.visibility = View.GONE
        binding.eliminarCuenta.setOnClickListener {

            authViewModel.getUserSession { user ->
                for (post in adapter.getPosts()) {
                    if(post.userId == user?.id){
                        postViewModel.deletePost(post)
                    }
                }
                user?.let {
                    userViewModel.deleteUser(it)
                }
            }
            authViewModel.logout()
            authViewModel.deleteAccount()
            findNavController().navigate(R.id.action_chatsFragment_to_loginFragment)
        }
        binding.profilePicture.setOnClickListener {
            binding.profileModal.visibility = View.VISIBLE
        }

        binding.cerrarModal.setOnClickListener {
            binding.profileModal.visibility = View.GONE
        }

        // Configurar la acción del botón de cerrar sesión
        binding.logoutButton.setOnClickListener {
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    authViewModel.logout()
                    findNavController().navigate(R.id.action_chatsFragment_to_loginFragment)
                }
            }

        }

        binding.seeProfileButton.setOnClickListener {
            findNavController().navigate(R.id.action_chatsFragment_to_detailUserFragment)
        }
        binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Acción para Home
                    findNavController().navigate(R.id.action_chatsFragment_to_postFragment)
                    true
                }
                R.id.navigation_discover_people -> {
                    // Acción para descubrir personas
                    findNavController().navigate(R.id.action_chatsFragment_to_discoverPeopleFragment)
                    true
                }
                R.id.navigation_create_post -> {
                    findNavController().navigate(R.id.action_chatsFragment_to_createPostFragment, Bundle().apply {
                        putString("operation", "create")
                    })
                    true
                }
                R.id.navigation_discover_places -> {
                    // Acción para descubrir lugares
                    if (hasLocationPermission()) {
                        findNavController().navigate(R.id.action_chatsFragment_to_discoverPlacesFragment)
                    } else {
                        requestLocationPermission()
                        toast("Para acceder necesitas permitir el acceso a tu ubicación.")
                    }
                    false
                }
                R.id.navigation_chats -> {
                    // Acción para chats
                    true
                }
                else -> false
            }
        }

        authViewModel.getUserSession {
            currentUser = it
            messageViewModel.getMessages("", "")
        }

        messageViewModel.messages.observe(viewLifecycleOwner) { result ->
            when (result) {
                is UIState.Loading -> {
                    // Show loading
                }
                is UIState.Success -> {
                    list.clear()

                    for(message in result.data){
                        if(message.senderId == currentUser?.id){
                            list.add(message.receiverId)
                        }

                        if(message.receiverId == currentUser?.id){
                            list.add(message.senderId)
                        }
                    }

                    readChats()

                }
                is UIState.Error -> {
                    toast(result.exception)
                }

                UIState.Empty -> TODO()
                is UIState.Error -> TODO()
                UIState.Loading -> TODO()
                is UIState.Success -> TODO()
            }
        }
        userViewModel.observeUsers()
        userViewModel.users.observe(viewLifecycleOwner) { result ->
            when (result) {
                is UIState.Loading -> {}
                is UIState.Success -> {
                    userList.clear()
                    val userIds = HashSet<String>()
                    for (user in result.data) {
                        for (id in list) {
                            if (user.id == id) {
                                userIds.add(user.id)
                            }
                        }
                    }
                    for (user in result.data) {
                        if (userIds.contains(user.id)) {
                            userList.add(user)
                        }
                    }

                    val adapter = UserListAdapter(requireContext(), userList, userLastMessages, arrayListOf<String>(), false, onItemClicked = { position, user ->
                        findNavController().navigate(R.id.action_chatsFragment_to_messageFragment, Bundle().apply {
                            putString("receiverId", user.id)
                        })
                    }, authViewModel)
                    binding.chatsRecyclerView.adapter = adapter
                    if (userList.isEmpty()) {
                        binding.noChatsText.visibility = View.VISIBLE
                    } else {
                        binding.noChatsText.visibility = View.GONE
                    }
                }
                is UIState.Error -> toast(result.exception)
                UIState.Empty -> {}
            }
        }

        binding.searchBox.addTextChangedListener(afterTextChanged = {
            val search = it.toString()
            if (search.isNotEmpty()) {
                val filteredList = userList.filter {
                    val completename = "${it.firstName} ${it.lastName}"
                    completename.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT), ignoreCase = true)
                }
                binding.chatsRecyclerView.adapter = UserListAdapter(requireContext(), filteredList as ArrayList<User>, userLastMessages, arrayListOf<String>(), false, onItemClicked = { position, user ->
                    findNavController().navigate(R.id.action_chatsFragment_to_messageFragment, Bundle().apply {
                        putString("receiverId", user.id)
                    })
                }, authViewModel)
            } else {
                binding.chatsRecyclerView.adapter = UserListAdapter(requireContext(), userList, userLastMessages, arrayListOf<String>(), false, onItemClicked = { position, user ->
                    findNavController().navigate(R.id.action_chatsFragment_to_messageFragment, Bundle().apply {
                        putString("receiverId", user.id)
                    })
                }, authViewModel)
            }
        })



    }

    override fun onStart() {
        super.onStart()
        binding.bottomNavigation.selectedItemId = R.id.navigation_chats
        binding.searchBox.setText("")
        authViewModel.getUserSession { user ->
            user?.let {
                binding.profileName.setText(it.userName)
                val profileImageUrl = it.profilePicture
                if (profileImageUrl.isNotEmpty()) {
                    coroutineScope.launch {
                        val bitmap = downloadImage(profileImageUrl)
                        bitmap?.let {
                            binding.profilePicture.setImageBitmap(it)
                            binding.profilePictureLarge.setImageBitmap(it)
                        }
                    }
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1005
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso de ubicación concedido, navega al fragmento Discover Places
                findNavController().navigate(R.id.action_postFragment_to_discoverPlacesFragment)
            } else {
                // Permiso de ubicación denegado, mostrar mensaje al usuario
                toast("Para acceder a Discover Places, necesitas permitir el acceso a tu ubicación.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    private suspend fun downloadImage(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val imageUrl = URL(url)
            val connection: HttpURLConnection = imageUrl.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val inputStream = connection.inputStream
            return@withContext BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun readChats(){
        userViewModel.getUsers()
        userViewModel.users.observe(viewLifecycleOwner) { result ->
            when (result) {
                is UIState.Loading -> {
                    // Show loading
                }
                is UIState.Success -> {
                    userList.clear()
                    val userIds = HashSet<String>()

                    for (user in result.data) {
                        for (id in list) {
                            if (user.id == id) {
                                userIds.add(user.id)
                            }
                        }
                    }

                    for (user in result.data) {
                        if (userIds.contains(user.id)) {
                            userList.add(user)
                            val lastMessage = getLastMessageForUser(user.id)
                            lastMessage?.let {
                                val messageText = if (it.senderId == currentUser?.id) {
                                    "Tú: ${it.message}"
                                } else {
                                    it.message
                                }
                                userLastMessages[user.id] = messageText
                            }
                        }
                    }

                    val adapter = UserListAdapter(requireContext(), userList, userLastMessages, arrayListOf(), false, onItemClicked = { position, user ->
                        findNavController().navigate(R.id.action_chatsFragment_to_messageFragment, Bundle().apply {
                            putString("receiverId", user.id)
                        })
                    }, authViewModel)
                    binding.chatsRecyclerView.adapter = adapter
                }
                is UIState.Error -> {
                    toast(result.exception)
                }

                UIState.Empty -> {
                    // Handle empty state
                }
            }
        }
    }

    private fun status(status: String) {
        currentUser?.let { userViewModel.updateUserInfo(it.copy(status = status)) }
    }

    override fun onResume() {
        super.onResume()
        status("online")
    }

    override fun onPause() {
        super.onPause()
        status("offline")
    }

    private fun getLastMessageForUser(userId: String): Message? {
        return messageViewModel.messages.value?.let { result ->
            if (result is UIState.Success) {
                result.data.filter {
                    (it.senderId == currentUser?.id && it.receiverId == userId) ||
                            (it.senderId == userId && it.receiverId == currentUser?.id)
                }.maxByOrNull { it.timestamp!! }
            } else {
                null
            }
        }
    }



}