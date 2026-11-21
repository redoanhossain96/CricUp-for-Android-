package com.example.ui.screens.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.di.AppContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.LightEmeraldText
import com.example.ui.theme.LightGoldAccent
import com.example.ui.theme.LightIntroBackgroundGradient
import com.example.ui.theme.LightTextNavy
import com.example.ui.theme.LightTextSubtle
import com.example.ui.theme.LocalThemeIsDark
import kotlinx.coroutines.delay

import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppIntroScreen(
    container: AppContainer,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by container.authRepository.getCurrentUserFlow().collectAsState()
    val isGoogleSignedIn = currentUser?.isGoogleSignedIn == true || container.authRepository.isGoogleSignedIn()
    var isSigningInGoogle by remember { mutableStateOf(false) }
    var googleAuthError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isGoogleSignedIn) {
        if (isGoogleSignedIn) {
            googleAuthError = null
        }
    }

    // 0: Initial, 1: App Icon Revealed, 2: Developer Photo Revealed, 3: Start Button Ready
    var animationStep by remember { mutableIntStateOf(0) }

    // Start automated sequential animations
    LaunchedEffect(Unit) {
        delay(150)
        animationStep = 1 // Step 1: Icon floats up
        delay(850)
        animationStep = 2 // Step 2: Developer Photo floats up
        delay(950)
        animationStep = 3 // Step 3: Golden Start Button appears
    }

    // Step 1: Icon Animation Values
    val iconScale by animateFloatAsState(
        targetValue = if (animationStep >= 1) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f),
        label = "iconScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (animationStep >= 1) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "iconAlpha"
    )
    val iconOffsetY by animateFloatAsState(
        targetValue = if (animationStep >= 1) 0f else 50f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "iconOffsetY"
    )

    // Step 2: Developer Photo Animation Values
    val devScale by animateFloatAsState(
        targetValue = if (animationStep >= 2) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 280f),
        label = "devScale"
    )
    val devAlpha by animateFloatAsState(
        targetValue = if (animationStep >= 2) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "devAlpha"
    )
    val devOffsetY by animateFloatAsState(
        targetValue = if (animationStep >= 2) 0f else 60f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "devOffsetY"
    )

    // Step 3: Golden Button Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val buttonGlowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buttonGlowScale"
    )

    val isDark = LocalThemeIsDark.current.value
    val backgroundGradient = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF04120B),
                    Color(0xFF072216),
                    Color(0xFF051810),
                    Color(0xFF030D08)
                )
            )
        } else {
            LightIntroBackgroundGradient
        }
    }
    val primaryHeadingColor = if (isDark) Color.White else LightTextNavy
    val subtitleEmeraldColor = if (isDark) EmeraldNeon else LightEmeraldText
    val subtitleGoldColor = if (isDark) GoldLight else LightGoldAccent
    val devRingInnerBg = if (isDark) Color(0xFF061A11) else Color(0xFFF3FAF5)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        // Decorative Stadium Ambient Circles
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(y = (-100).dp)
                .alpha(0.08f)
                .background(EmeraldGreen, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(y = 120.dp)
                .alpha(0.06f)
                .background(GoldYellow, CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 1. APP ICON ANIMATED REVEAL
            // ==========================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    translationY = iconOffsetY.dp.toPx()
                    scaleX = iconScale
                    scaleY = iconScale
                    alpha = iconAlpha
                }
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Soft Halo
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(EmeraldGreen.copy(alpha = 0.18f))
                    )
                    // App Logo
                    Image(
                        painter = painterResource(id = R.drawable.cricup_logo),
                        contentDescription = "CricUp Logo",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(
                                2.dp,
                                Brush.linearGradient(listOf(EmeraldNeon, GoldYellow)),
                                RoundedCornerShape(22.dp)
                            )
                            .shadow(12.dp, RoundedCornerShape(22.dp))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "CricUp",
                    color = primaryHeadingColor,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Cricket Management & Real-Time Live Scoring",
                    color = subtitleEmeraldColor,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 2. DEVELOPER PHOTO ANIMATED REVEAL
            // ==========================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    translationY = devOffsetY.dp.toPx()
                    scaleX = devScale
                    scaleY = devScale
                    alpha = devAlpha
                }
            ) {
                // Developer Photo Frame with Golden Glowing Ring
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // Golden Gradient Glow Ring
                    Box(
                        modifier = Modifier
                            .size(146.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        GoldYellow,
                                        Color(0xFFFFA000),
                                        EmeraldGreen,
                                        GoldYellow
                                    )
                                )
                            )
                    )

                    // Inner Ring Border
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(devRingInnerBg),
                        contentAlignment = Alignment.Center
                    ) {
                        // Developer Photo
                        Image(
                            painter = painterResource(id = R.drawable.developer_photo),
                            contentDescription = "App Developer Redoan Hossain",
                            modifier = Modifier
                                .size(134.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Developer Role Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    GoldYellow,
                                    Color(0xFFFFA000)
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "APP DEVELOPER",
                            color = Color.Black,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Redoan Hossain",
                    color = primaryHeadingColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Lead Software Engineer & Creator of CricUp",
                    color = subtitleGoldColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // ==========================================
            // 3. GOLDEN START BUTTON
            // ==========================================
            AnimatedVisibility(
                visible = animationStep >= 3,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
                    initialOffsetY = { 60 },
                    animationSpec = spring(dampingRatio = 0.7f)
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val goldenGradient = remember {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFFD700),
                                Color(0xFFFFA000),
                                Color(0xFFFFE082),
                                Color(0xFFFFB300)
                            )
                        )
                    }

                    Button(
                        onClick = {
                            // Immediately ensure full access and navigate to Home
                            container.authRepository.initializeFullAccessSession()
                            onStart()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .graphicsLayer {
                                scaleX = buttonGlowScale
                                scaleY = buttonGlowScale
                            }
                            .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = GoldYellow)
                            .testTag("golden_start_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(goldenGradient, RoundedCornerShape(16.dp))
                                .border(1.5.dp, Color(0xFFFFF8E1), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = CricUpIcons.SportsCricket,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "START  •  শুরু করুন",
                                    color = Color.Black,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isGoogleSignedIn && !currentUser?.email.isNullOrBlank()) {
                        androidx.compose.material3.Surface(
                            color = EmeraldGreen.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✓ Gmail সংযুক্ত: ${currentUser?.email}",
                                color = if (isDark) EmeraldNeon else LightEmeraldText,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    } else {
                        com.example.ui.components.GoogleSignInActionButton(
                            text = "Gmail দিয়ে লগইন করুন (Google Sign-In)",
                            isLoading = isSigningInGoogle,
                            onClick = {
                                googleAuthError = null
                                isSigningInGoogle = true
                                com.example.util.GoogleSignInHelper.signInWithGoogle(
                                    context = context,
                                    scope = scope,
                                    authRepository = container.authRepository,
                                    onSuccess = { linkedUser ->
                                        isSigningInGoogle = false
                                        googleAuthError = null
                                        val label = linkedUser.email.takeIf { it.isNotBlank() } ?: linkedUser.displayName
                                        Toast.makeText(
                                            context,
                                            "Gmail ($label) লগইন সফল হয়েছে!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        container.authRepository.initializeFullAccessSession()
                                        onStart()
                                    },
                                    onError = { err ->
                                        isSigningInGoogle = false
                                        googleAuthError = err
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    },
                                    onCancelled = {
                                        isSigningInGoogle = false
                                    }
                                )
                            },
                            testTag = "intro_google_sign_in_btn"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        com.example.ui.components.GoogleBrowserSignInButton(
                            text = "ব্রাউজার দিয়ে Gmail লগইন (বিকল্প পদ্ধতি)",
                            isLoading = isSigningInGoogle,
                            onClick = {
                                googleAuthError = null
                                isSigningInGoogle = true
                                com.example.util.GoogleSignInHelper.signInWithGoogleViaBrowser(
                                    context = context,
                                    scope = scope,
                                    authRepository = container.authRepository,
                                    onSuccess = { linkedUser ->
                                        isSigningInGoogle = false
                                        googleAuthError = null
                                        val label = linkedUser.email.takeIf { it.isNotBlank() } ?: linkedUser.displayName
                                        Toast.makeText(
                                            context,
                                            "Gmail ($label) লগইন সফল হয়েছে!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        container.authRepository.initializeFullAccessSession()
                                        onStart()
                                    },
                                    onError = { err ->
                                        isSigningInGoogle = false
                                        googleAuthError = err
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            testTag = "intro_google_browser_sign_in_btn"
                        )

                        googleAuthError?.let { errMsg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.Surface(
                                color = Color(0xFFD32F2F).copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("intro_google_auth_error")
                            ) {
                                Text(
                                    text = errMsg,
                                    color = if (isDark) Color(0xFFFFCDD2) else Color(0xFFB71C1C),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isGoogleSignedIn) {
                            "ট্যাপ করে পূর্ণ অ্যাক্সেস সহ হোম স্ক্রিনে প্রবেশ করুন"
                        } else {
                            "START চাপলে শুধু স্কোর ও কোড দিয়ে ম্যাচ দেখতে পারবেন • সকল ফিচারের জন্য Gmail দিয়ে লগইন করুন"
                        },
                        color = if (isDark) Color.White.copy(alpha = 0.72f) else LightTextSubtle.copy(alpha = 0.85f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Fallback tap if user taps anywhere before step 3 completes
            if (animationStep < 3) {
                Spacer(modifier = Modifier.height(40.dp))
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}
