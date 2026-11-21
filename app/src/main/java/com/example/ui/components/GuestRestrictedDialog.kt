package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.di.AppContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.WicketRed
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor
import com.example.util.GoogleSignInHelper

/**
 * Dialog shown when an unregistered (non-Gmail) viewer attempts a restricted action
 * such as scoring, posting in Latest Cricket Feed, or adding players/teams/tournaments.
 */
@Composable
fun GuestRestrictedDialog(
    actionName: String = "এই ফিচারটি",
    onDismiss: () -> Unit,
    onNavigateToAuth: () -> Unit = {},
    container: AppContainer? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val goldColor = adaptiveGoldColor()
    val emeraldColor = adaptiveEmeraldColor()
    var isSigningIn by remember { mutableStateOf(false) }
    var googleAuthError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSigningIn) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(WicketRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = WicketRed, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = "Gmail লগইন প্রয়োজন",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$actionName ব্যবহার করতে অনুগ্রহ করে আপনার Gmail অ্যাকাউন্ট দিয়ে লগইন (রেজিস্টার) করুন।",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "✓ লগইন ছাড়া: শুধু ম্যাচের লাইভ ও পূর্ববর্তী স্কোর এবং কোড সার্চ করে স্কোর দেখতে পারবেন।",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "🔒 Gmail লগইনে আনলক: স্কোরিং, Latest Cricket Feed-এ পোস্ট, Player/Team/Tournament যোগ করা ও প্রোফাইল অংশগ্রহণ।",
                            fontSize = 11.5.sp,
                            color = goldColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (container != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    GoogleBrowserSignInButton(
                        text = "ব্রাউজার দিয়ে Gmail লগইন (বিকল্প)",
                        isLoading = isSigningIn,
                        onClick = {
                            googleAuthError = null
                            isSigningIn = true
                            GoogleSignInHelper.signInWithGoogleViaBrowser(
                                context = context,
                                scope = scope,
                                authRepository = container.authRepository,
                                onSuccess = { linkedUser ->
                                    isSigningIn = false
                                    googleAuthError = null
                                    val label = linkedUser.email.takeIf { it.isNotBlank() } ?: linkedUser.displayName
                                    Toast.makeText(
                                        context,
                                        "Gmail ($label) সফলভাবে লগইন হয়েছে! সব ফিচার আনলক করা হয়েছে।",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onDismiss()
                                },
                                onError = { err ->
                                    isSigningIn = false
                                    googleAuthError = err
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        testTag = "dialog_google_browser_sign_in_btn"
                    )
                }
                googleAuthError?.let { errMsg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errMsg,
                        color = WicketRed,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (container != null) {
                        googleAuthError = null
                        isSigningIn = true
                        GoogleSignInHelper.signInWithGoogle(
                            context = context,
                            scope = scope,
                            authRepository = container.authRepository,
                            onSuccess = { linkedUser ->
                                isSigningIn = false
                                googleAuthError = null
                                val label = linkedUser.email.takeIf { it.isNotBlank() } ?: linkedUser.displayName
                                Toast.makeText(
                                    context,
                                    "Gmail ($label) সফলভাবে লগইন হয়েছে! সব ফিচার আনলক করা হয়েছে।",
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            },
                            onError = { err ->
                                isSigningIn = false
                                googleAuthError = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            },
                            onCancelled = {
                                isSigningIn = false
                            }
                        )
                    } else {
                        onDismiss()
                        onNavigateToAuth()
                    }
                },
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("dialog_google_sign_in_btn")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("লগইন হচ্ছে...", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color.White)
                } else {
                    if (container != null) {
                        GoogleBrandMark(size = 16.dp)
                    } else {
                        Icon(imageVector = CricUpIcons.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (container != null) "Gmail দিয়ে লগইন" else "লগইন করুন / Sign In",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSigningIn,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("শুধু স্কোর দেখুন", color = emeraldColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Top banner displayed when user is not signed in with Gmail (view-only + match code search access).
 */
@Composable
fun GuestVisitorBanner(
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier,
    isSigningIn: Boolean = false
) {
    val goldColor = adaptiveGoldColor()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("viewer_only_banner"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, goldColor.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = CricUpIcons.Visibility, contentDescription = null, tint = goldColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ভিউয়ার মোড (View & Search Score Only)",
                        color = goldColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "স্কোরিং, ফিড পোস্ট ও দল/খেলোয়াড়/টুর্নামেন্ট যুক্ত করতে Gmail লগইন করুন",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 10.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onNavigateToAuth,
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("viewer_banner_login_btn")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Gmail লগইন", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp)
                }
            }
        }
    }
}
