package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Email
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.User
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.WicketRed
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor

@Composable
fun GoogleBrandMark(size: Dp = 20.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = this.size.minDimension * 0.22f
        val inset = strokeWidth / 2f
        val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        // Red top arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -140f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 145f,
            sweepAngle = 75f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue right arc + crossbar
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -5f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(this.size.width * 0.52f, this.size.height * 0.5f),
            end = Offset(this.size.width - inset, this.size.height * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
    }
}

@Composable
fun GoogleSignInActionButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "google_sign_in_btn"
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.2.dp,
                color = adaptiveEmeraldColor()
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Gmail সংযোগ হচ্ছে...",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    GoogleBrandMark(size = 18.dp)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GoogleBrowserSignInButton(
    text: String = "ব্রাউজার দিয়ে Gmail লগইন (বিকল্প পদ্ধতি)",
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "google_browser_sign_in_btn"
) {
    val emerald = adaptiveEmeraldColor()
    OutlinedButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.2.dp, emerald.copy(alpha = 0.75f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = emerald.copy(alpha = 0.08f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = CricUpIcons.OpenInNew,
            contentDescription = null,
            tint = emerald,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = emerald,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GoogleAccountLinkCard(
    user: User?,
    isGoogleSignedIn: Boolean,
    isSigningIn: Boolean,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onSignInWithGoogleBrowser: (() -> Unit)? = null,
    statusMessage: String? = null
) {
    val emerald = adaptiveEmeraldColor()
    val gold = adaptiveGoldColor()
    var showSignOutDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGoogleSignedIn) {
                emerald.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.2.dp,
            if (isGoogleSignedIn) emerald.copy(alpha = 0.5f) else gold.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGoogleSignedIn) emerald.copy(alpha = 0.16f)
                            else gold.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGoogleSignedIn) {
                        Icon(
                            imageVector = CricUpIcons.CloudDone,
                            contentDescription = "Gmail Connected",
                            tint = emerald,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        GoogleBrandMark(size = 22.dp)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isGoogleSignedIn) {
                                "Gmail অ্যাকাউন্ট লিংকড"
                            } else {
                                "Gmail লগইন ও প্রোফাইল লিংক"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isGoogleSignedIn) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = CricUpIcons.VerifiedUser,
                                contentDescription = "Verified",
                                tint = emerald,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    if (isGoogleSignedIn) {
                        val emailText = user?.email?.takeIf { it.isNotBlank() } ?: "Google Account"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = emerald,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = emailText,
                                color = emerald,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "আপনার প্রোফাইল, ম্যাচ ও পরিসংখ্যান এই Gmail-এ সংরক্ষিত ও লিংক করা আছে।",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    } else {
                        Text(
                            text = "Gmail দিয়ে লগইন করলে আপনার বর্তমান প্রোফাইল, ম্যাচ ও টিম স্বয়ংক্রিয়ভাবে ক্লাউডে লিংক হয়ে যাবে।",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isGoogleSignedIn) {
                GoogleSignInActionButton(
                    text = "Continue with Google (Gmail লগইন)",
                    isLoading = isSigningIn,
                    onClick = onSignInWithGoogle,
                    testTag = "google_sign_in_btn"
                )
                if (onSignInWithGoogleBrowser != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    GoogleBrowserSignInButton(
                        isLoading = isSigningIn,
                        onClick = onSignInWithGoogleBrowser,
                        testTag = "google_browser_sign_in_btn"
                    )
                }
                if (!statusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = WicketRed.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, WicketRed.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage,
                            color = WicketRed,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSignInWithGoogle,
                        enabled = !isSigningIn,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("switch_google_account_btn")
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = CricUpIcons.SwapHoriz,
                                contentDescription = "Switch Gmail",
                                tint = emerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "অন্য Gmail",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        enabled = !isSigningIn,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, WicketRed.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WicketRed),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("google_sign_out_btn")
                    ) {
                        Icon(
                            imageVector = CricUpIcons.Logout,
                            contentDescription = "Sign Out",
                            tint = WicketRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "সাইন-আউট",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = {
                Icon(
                    imageVector = CricUpIcons.Link,
                    contentDescription = null,
                    tint = WicketRed
                )
            },
            title = {
                Text(
                    text = "Gmail থেকে সাইন-আউট করবেন?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "সাইন-আউট করলে আপনি ডিভাইস মোডে ফিরে যাবেন। আপনার ক্লাউডে সেভ থাকা প্রোফাইল ও ম্যাচ সুরক্ষিত থাকবে—যেকোনো সময় আবার একই Gmail দিয়ে লগইন করে ফিরে পাবেন।",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WicketRed),
                    modifier = Modifier.testTag("confirm_google_sign_out_btn")
                ) {
                    Text("সাইন-আউট", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
