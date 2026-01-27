package com.btmessenger.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.btmessenger.app.data.entities.Peer
import com.btmessenger.app.ui.ChatScreen
import com.btmessenger.app.ui.GroupChatScreen
import com.btmessenger.app.ui.NearbyPeersScreen
import com.btmessenger.app.ui.theme.BluetoothMessengerTheme
import com.google.gson.Gson
import com.btmessenger.app.permission.PermissionHelper

class MainActivity : ComponentActivity() {
    private val tag = "MainActivity"
    private val REQUEST_BLUETOOTH_PERMS = 1234
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionHelper.hasBluetoothPermissions(this)) {
            Log.d(tag, "Requesting Bluetooth permissions")
            PermissionHelper.requestBluetoothPermissions(this, REQUEST_BLUETOOTH_PERMS)
        }
        setContent {
            BluetoothMessengerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MessengerApp()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMS) {
            if (PermissionHelper.hasBluetoothPermissions(this)) {
                Log.d(tag, "Bluetooth permissions granted")
            } else {
                Log.w(tag, "Bluetooth permissions denied")
                Toast.makeText(this, "Bluetooth permissions are required for nearby features", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun MessengerApp() {
    val navController = rememberNavController()
    val gson = remember { Gson() }
    
    NavHost(navController = navController, startDestination = "peers") {
        composable("peers") {
            NearbyPeersScreen(
                onPeerSelected = { peer ->
                    val peerJson = gson.toJson(peer)
                    navController.navigate("chat/$peerJson")
                },
                onGroupSelected = { gid ->
                    navController.navigate("group/$gid")
                }
            )
        }
        
        composable(
            route = "chat/{peerJson}",
            arguments = listOf(navArgument("peerJson") { type = NavType.StringType })
        ) { backStackEntry ->
            val peerJson = backStackEntry.arguments?.getString("peerJson")
            val peer = gson.fromJson(peerJson, Peer::class.java)
            
            ChatScreen(
                peer = peer,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "group/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupChatScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
