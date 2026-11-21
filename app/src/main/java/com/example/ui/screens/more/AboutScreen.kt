package com.example.ui.screens.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.SurfaceDarkNavy
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val goldAccent = com.example.ui.theme.adaptiveGoldColor()
    val onSurface = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About CricUp", color = onSurface, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Image(
                    painter = painterResource(id = R.drawable.cricup_logo),
                    contentDescription = "CricUp Logo",
                    modifier = Modifier.size(100.dp).clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text("CricUp", color = onSurface, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Cricket Scoring • Public Live Updates • Tournament Hub", color = goldAccent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                Text("Version ${com.example.BuildConfig.VERSION_NAME} (Production Architecture)", color = onSurfaceVariant, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("CRICUP PLATFORM HIGHLIGHTS", color = primaryColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Public Live Scorecards: Instant spectator viewing without mandatory login. Any fan can follow real-time scores, over timeline, and match statistics.", color = onSurfaceVariant, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Offline-First Scoring: Immediate Room Database writes continue uninterrupted even in zero network zones, queuing events safely.", color = onSurfaceVariant, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Real-Time Cloud Sync: Automatic idempotent event queue syncs with Firebase Firestore when internet restores.", color = onSurfaceVariant, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Complete Undo & Edit: Full mathematical recalculation of scorecards, partnerships, bowler and batter figures with audit history.", color = onSurfaceVariant, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Community Moments: All members can share moments, thoughts, photos, and videos right on the Home Page.", color = onSurfaceVariant, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• Reusable Database: Custom teams, players, and umpires are registered once and reused across matches without duplicate entry.", color = onSurfaceVariant, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, goldAccent.copy(alpha = 0.55f))
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.developer_photo),
                            contentDescription = "Redoan Hossain",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .border(2.dp, goldAccent, androidx.compose.foundation.shape.CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GoldYellow)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("APP DEVELOPER", color = androidx.compose.ui.graphics.Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Redoan Hossain", color = onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Lead Software Engineer & Creator", color = onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
