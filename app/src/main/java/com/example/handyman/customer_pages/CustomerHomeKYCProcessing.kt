package com.example.handyman.customer_pages

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.database.*
import com.example.handyman.R
import com.example.handyman.utils.SessionManager

@Composable
fun CustomerHomeKYCProcessing(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    var firstName by remember { mutableStateOf("...") }
    var lastName by remember { mutableStateOf("") }

    val currentEmail = SessionManager.getLoggedInEmail(context)

    LaunchedEffect(currentEmail) {
        val userRef = FirebaseDatabase.getInstance().getReference("User")
        val query = userRef.orderByChild("email").equalTo(currentEmail)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    firstName = child.child("firstName").getValue(String::class.java) ?: ""
                    lastName = child.child("lastName").getValue(String::class.java) ?: ""
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error loading user data: ${error.message}")
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Top right logout
        Text(
            "Log out",
            color = Color(0xFF30386D),
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 24.dp)
                .clickable {
                    SessionManager.clearSession(context)
                    navController.navigate("chooseAccountType") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.dp),
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(60.dp)
                        .padding(end = 8.dp)
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hello, $firstName $lastName.", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unverified",
                            color = Color(0xFFE8A317),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(Color(0x1AFEC260), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.bytesize_location),
                            contentDescription = "Location",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Unverified address", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text("KYC Application\nUnder Review", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Your identity verification is being processed.\nWe will let you know when you are ready to use our service.",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(48.dp))

            Image(
                painter = painterResource(id = R.drawable.document_icon_yellow),
                contentDescription = "KYC Document Icon",
                modifier = Modifier.size(100.dp).align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom NavBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.home_icon),
                    contentDescription = "Home",
                    tint = Color(0xFF8A4DFF),
                    modifier = Modifier.size(30.dp)
                )
                Icon(
                    painter = painterResource(id = R.drawable.list_icon),
                    contentDescription = "List",
                    tint = Color.Gray,
                    modifier = Modifier.size(30.dp)
                )
                Icon(
                    painter = painterResource(id = R.drawable.chat_icon),
                    contentDescription = "Chat",
                    tint = Color.Gray,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}
