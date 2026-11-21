package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Player
import com.example.data.model.PlayerRoles
import com.example.data.model.Team
import com.example.di.AppContainer
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.SlateCardBg
import com.example.ui.theme.SurfaceDarkNavy
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WicketRed
import com.example.util.ImageModelResolver
import com.example.util.ImagePickerHelper
import kotlinx.coroutines.launch

private val BOWLING_STYLES = listOf(
    "Right-arm Medium",
    "Right-arm Fast",
    "Right-arm Off Spin",
    "Right-arm Leg Spin",
    "Left-arm Fast",
    "Left-arm Orthodox",
    "Left-arm Chinaman",
    "None"
)

private val ROLE_OPTIONS = listOf(
    PlayerRoles.BATSMAN,
    PlayerRoles.BOWLER,
    PlayerRoles.ALL_ROUNDER,
    PlayerRoles.WICKETKEEPER
)

/**
 * Reusable full-detail player form content used in My Players, Team Squad Builder, and Create Match.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullPlayerFormContent(
    fullName: String,
    onFullNameChange: (String) -> Unit,
    nickname: String,
    onNicknameChange: (String) -> Unit,
    jerseyNumber: String,
    onJerseyNumberChange: (String) -> Unit,
    primaryRole: String,
    onPrimaryRoleChange: (String) -> Unit,
    battingStyle: String,
    onBattingStyleChange: (String) -> Unit,
    bowlingStyle: String,
    onBowlingStyleChange: (String) -> Unit,
    contactInfo: String,
    onContactInfoChange: (String) -> Unit,
    photoUrl: String?,
    onPhotoUrlChange: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = ImagePickerHelper.compressUriToBase64Async(context, uri)
                if (encoded != null) {
                    onPhotoUrlChange(encoded)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextWhite,
        unfocusedTextColor = TextWhite,
        focusedBorderColor = EmeraldGreen,
        unfocusedBorderColor = CardBorder,
        focusedLabelColor = EmeraldGreen,
        unfocusedLabelColor = TextLightGrey
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Photo + Full Name Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SlateCardBg)
                    .border(1.5.dp, if (photoUrl != null) EmeraldGreen else CardBorder, CircleShape)
                    .clickable {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("player_photo_picker"),
                contentAlignment = Alignment.Center
            ) {
                if (!photoUrl.isNullOrEmpty()) {
                    val resolvedPhoto = remember(photoUrl) { ImageModelResolver.resolveForCoil(photoUrl) }
                    AsyncImage(
                        model = resolvedPhoto,
                        contentDescription = "Player Photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Add Photo",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Text("+ছবি", color = TextLightGrey, fontSize = 9.sp)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = onFullNameChange,
                    label = { Text("পুরো নাম (Full Name) *") },
                    placeholder = { Text("খেলোয়াড়ের নাম লিখুন") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("player_full_name_input")
                )
                if (!photoUrl.isNullOrEmpty()) {
                    Text(
                        text = "ছবি সরান (Remove Photo)",
                        color = WicketRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { onPhotoUrlChange(null) }
                    )
                }
            }
        }

        // Nickname + Jersey Number Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                label = { Text("ডাকনাম (Nickname)") },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier.weight(1.4f).testTag("player_nickname_input")
            )
            OutlinedTextField(
                value = jerseyNumber,
                onValueChange = { input -> onJerseyNumberChange(input.filter { it.isDigit() }.take(3)) },
                label = { Text("জার্সি নং") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = textFieldColors,
                modifier = Modifier.weight(0.9f).testTag("player_jersey_input")
            )
        }

        // Primary Role
        Text("ভূমিকা (Primary Role):", color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ROLE_OPTIONS.forEach { role ->
                val selected = primaryRole.equals(role, ignoreCase = true) ||
                    (role == PlayerRoles.BATSMAN && primaryRole.equals("Batter", ignoreCase = true)) ||
                    (role == PlayerRoles.WICKETKEEPER && primaryRole.equals("Keeper", ignoreCase = true))
                FilterChip(
                    selected = selected,
                    onClick = { onPrimaryRoleChange(role) },
                    label = {
                        Text(
                            "${PlayerRoles.getRoleIcon(role)} $role",
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldGreen,
                        selectedLabelColor = DeepNavyBg,
                        containerColor = SlateCardBg,
                        labelColor = TextWhite
                    )
                )
            }
        }

        // Batting Style
        Text("ব্যাটিং স্টাইল (Batting Style):", color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Right Hand", "Left Hand").forEach { style ->
                val selected = battingStyle == style
                Button(
                    onClick = { onBattingStyleChange(style) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) EmeraldGreen else SlateCardBg
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (style == "Right Hand") "ডানহাতি (RHB)" else "বামহাতি (LHB)",
                        fontSize = 11.sp,
                        color = if (selected) DeepNavyBg else TextWhite,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Bowling Style
        Text("বোলিং স্টাইল (Bowling Style):", color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BOWLING_STYLES.forEach { style ->
                val selected = bowlingStyle == style
                FilterChip(
                    selected = selected,
                    onClick = { onBowlingStyleChange(style) },
                    label = {
                        Text(
                            style,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldGreen,
                        selectedLabelColor = DeepNavyBg,
                        containerColor = SlateCardBg,
                        labelColor = TextLightGrey
                    )
                )
            }
        }

        // Contact Info
        OutlinedTextField(
            value = contactInfo,
            onValueChange = onContactInfoChange,
            label = { Text("মোবাইল / যোগাযোগ (ঐচ্ছিক)") },
            placeholder = { Text("01XXXXXXXXX") },
            singleLine = true,
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().testTag("player_contact_input")
        )
    }
}

/**
 * Full dialog to create or edit a player with complete details.
 */
@Composable
fun FullPlayerProfileDialog(
    initialPlayer: Player? = null,
    defaultJerseyNumber: Int = 10,
    existingPlayers: List<Player> = emptyList(),
    creatorId: String,
    attachedTeamIds: List<String> = emptyList(),
    dialogTitle: String = if (initialPlayer == null) "নতুন খেলোয়াড় (পূর্ণ বিবরণসহ)" else "খেলোয়াড় সম্পাদনা (Edit Player)",
    confirmButtonText: String = if (initialPlayer == null) "সংরক্ষণ করুন (Save)" else "আপডেট করুন (Update)",
    onDismiss: () -> Unit,
    onConfirm: (Player) -> Unit
) {
    val context = LocalContext.current
    var fullName by remember(initialPlayer) { mutableStateOf(initialPlayer?.fullName ?: "") }
    var nickname by remember(initialPlayer) { mutableStateOf(initialPlayer?.nickname ?: "") }
    var jerseyNumber by remember(initialPlayer) {
        mutableStateOf((initialPlayer?.jerseyNumber ?: defaultJerseyNumber).toString())
    }
    var primaryRole by remember(initialPlayer) {
        mutableStateOf(initialPlayer?.primaryRole ?: PlayerRoles.ALL_ROUNDER)
    }
    var battingStyle by remember(initialPlayer) {
        mutableStateOf(initialPlayer?.battingStyle ?: "Right Hand")
    }
    var bowlingStyle by remember(initialPlayer) {
        mutableStateOf(initialPlayer?.bowlingStyle ?: "Right-arm Medium")
    }
    var contactInfo by remember(initialPlayer) {
        mutableStateOf(initialPlayer?.contactInfo ?: "")
    }
    var photoUrl by remember(initialPlayer) { mutableStateOf(initialPlayer?.photoUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                FullPlayerFormContent(
                    fullName = fullName,
                    onFullNameChange = { fullName = it },
                    nickname = nickname,
                    onNicknameChange = { nickname = it },
                    jerseyNumber = jerseyNumber,
                    onJerseyNumberChange = { jerseyNumber = it },
                    primaryRole = primaryRole,
                    onPrimaryRoleChange = { primaryRole = it },
                    battingStyle = battingStyle,
                    onBattingStyleChange = { battingStyle = it },
                    bowlingStyle = bowlingStyle,
                    onBowlingStyleChange = { bowlingStyle = it },
                    contactInfo = contactInfo,
                    onContactInfoChange = { contactInfo = it },
                    photoUrl = photoUrl,
                    onPhotoUrlChange = { photoUrl = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanedName = fullName.trim()
                    if (cleanedName.isBlank()) {
                        Toast.makeText(context, "খেলোয়াড়ের নাম লিখুন", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val normalized = cleanedName.lowercase().replace("\\s+".toRegex(), " ")
                    val duplicate = existingPlayers.any {
                        it.id != initialPlayer?.id &&
                            it.fullName.trim().lowercase().replace("\\s+".toRegex(), " ") == normalized
                    }
                    if (duplicate) {
                        Toast.makeText(
                            context,
                            "এই নামের খেলোয়াড় ইতিমধ্যেই ডেটাবেজে রয়েছে! ডেটাবেজ থেকে সিলেক্ট করুন অথবা আলাদা নাম দিন।",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    val mergedTeamIds = ((initialPlayer?.teamIds ?: emptyList()) + attachedTeamIds).distinct()
                    val resultPlayer = (initialPlayer ?: Player(
                        id = java.util.UUID.randomUUID().toString(),
                        creatorId = creatorId
                    )).copy(
                        fullName = cleanedName,
                        nickname = nickname.trim(),
                        photoUrl = photoUrl,
                        jerseyNumber = jerseyNumber.toIntOrNull() ?: defaultJerseyNumber,
                        primaryRole = primaryRole,
                        battingStyle = battingStyle,
                        bowlingStyle = bowlingStyle,
                        contactInfo = contactInfo.trim(),
                        teamIds = mergedTeamIds,
                        creatorId = initialPlayer?.creatorId?.takeIf { it.isNotBlank() } ?: creatorId
                    )
                    onConfirm(resultPlayer)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.testTag("full_player_save_btn")
            ) {
                Text(confirmButtonText, color = DeepNavyBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল (Cancel)", color = TextLightGrey)
            }
        },
        containerColor = SurfaceDarkNavy
    )
}

/**
 * Full Squad Builder Dialog for a Team in My Teams:
 * 1) View/Edit/Remove squad players with complete details
 * 2) Add New Player with full details (photo, nickname, jersey, role, bat/bowl style, contact)
 * 3) Pick existing players from Player Database
 */
@Composable
fun TeamSquadBuilderDialog(
    team: Team,
    allPlayers: List<Player>,
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = container.authRepository.getCurrentUserId()

    // Live squad IDs tracked smoothly
    var squadIds by remember(team.id, team.playerIds, allPlayers) {
        val initial = (team.playerIds + allPlayers.filter { it.teamIds.contains(team.id) }.map { it.id }).distinct()
        mutableStateOf(initial)
    }

    val squadPlayers = remember(squadIds, allPlayers) {
        allPlayers.filter { squadIds.contains(it.id) }.distinctBy { it.id }
    }
    val availableDbPlayers = remember(squadIds, allPlayers) {
        allPlayers.filter { !squadIds.contains(it.id) }.distinctBy { it.fullName.trim().lowercase() }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Squad, 1: New Full Player, 2: From Database
    var editingSquadPlayer by remember { mutableStateOf<Player?>(null) }

    // State for Tab 1 (New Player inline)
    var newName by remember { mutableStateOf("") }
    var newNickname by remember { mutableStateOf("") }
    var newJersey by remember(squadPlayers.size) { mutableStateOf((squadPlayers.size + 1).toString()) }
    var newRole by remember { mutableStateOf(PlayerRoles.ALL_ROUNDER) }
    var newBatStyle by remember { mutableStateOf("Right Hand") }
    var newBowlStyle by remember { mutableStateOf("Right-arm Medium") }
    var newContact by remember { mutableStateOf("") }
    var newPhotoUrl by remember { mutableStateOf<String?>(null) }

    // State for Tab 2 (Database multi-select)
    var dbSearchQuery by remember { mutableStateOf("") }
    val selectedDbIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CricUpAvatar(
                        model = team.logoUrl,
                        size = 36.dp,
                        fallbackText = team.shortName.take(3)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${team.name} — স্কোয়াড গঠন",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${team.shortName} • মোট খেলোয়াড়: ${squadPlayers.size} জন",
                            color = EmeraldGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 460.dp)
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SlateCardBg,
                    contentColor = EmeraldGreen,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = EmeraldGreen
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "স্কোয়াড (${squadPlayers.size})",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) EmeraldGreen else TextLightGrey
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "+ নতুন খেলোয়াড়",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) EmeraldGreen else TextLightGrey
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                "ডেটাবেজ (${availableDbPlayers.size})",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) EmeraldGreen else TextLightGrey
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> {
                        // Current Squad List
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { selectedTab = 1 },
                                border = BorderStroke(1.dp, EmeraldGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("নতুন (পূর্ণ বিবরণ)", color = EmeraldGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { selectedTab = 2 },
                                border = BorderStroke(1.dp, GoldYellow),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Icon(CricUpIcons.Group, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("ডেটাবেজ থেকে", color = GoldYellow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (squadPlayers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(SlateCardBg.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(CricUpIcons.Group, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(38.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("দলে এখনও কোনো খেলোয়াড় নেই", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "উপরে '+ নতুন খেলোয়াড়' ট্যাবে গিয়ে পূর্ণ বিবরণসহ খেলোয়াড় যোগ করুন অথবা 'ডেটাবেজ' থেকে সিলেক্ট করে স্কোয়াড গঠন করুন।",
                                        color = TextLightGrey,
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(squadPlayers, key = { it.id }) { player ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = SlateCardBg),
                                        border = BorderStroke(1.dp, CardBorder),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CricUpAvatar(
                                                model = player.photoUrl,
                                                size = 40.dp,
                                                fallbackText = player.fullName.take(2)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (player.nickname.isNotBlank()) "${player.fullName} (${player.nickname})" else player.fullName,
                                                    color = TextWhite,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${PlayerRoles.getRoleIcon(player.primaryRole)} ${player.primaryRole} • #${player.jerseyNumber}",
                                                    color = GoldYellow,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${player.battingStyle} • ${player.bowlingStyle}" +
                                                        if (player.contactInfo.isNotBlank()) " • ${player.contactInfo}" else "",
                                                    color = TextMuted,
                                                    fontSize = 9.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = { editingSquadPlayer = player },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Player", tint = GoldYellow, modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    val updatedIds = squadIds.filter { it != player.id }
                                                    squadIds = updatedIds
                                                    scope.launch {
                                                        val updatedPlayer = player.copy(teamIds = player.teamIds.filter { it != team.id })
                                                        container.playerRepository.savePlayer(updatedPlayer)
                                                        container.teamRepository.saveTeam(team.copy(playerIds = updatedIds))
                                                        Toast.makeText(context, "${player.fullName} কে স্কোয়াড থেকে সরানো হয়েছে", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove from Squad", tint = WicketRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Add New Player with Full Details directly to this team
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            FullPlayerFormContent(
                                fullName = newName,
                                onFullNameChange = { newName = it },
                                nickname = newNickname,
                                onNicknameChange = { newNickname = it },
                                jerseyNumber = newJersey,
                                onJerseyNumberChange = { newJersey = it },
                                primaryRole = newRole,
                                onPrimaryRoleChange = { newRole = it },
                                battingStyle = newBatStyle,
                                onBattingStyleChange = { newBatStyle = it },
                                bowlingStyle = newBowlStyle,
                                onBowlingStyleChange = { newBowlStyle = it },
                                contactInfo = newContact,
                                onContactInfoChange = { newContact = it },
                                photoUrl = newPhotoUrl,
                                onPhotoUrlChange = { newPhotoUrl = it }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val cleaned = newName.trim()
                                    if (cleaned.isBlank()) {
                                        Toast.makeText(context, "খেলোয়াড়ের নাম লিখুন", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val normalized = cleaned.lowercase().replace("\\s+".toRegex(), " ")
                                    val existing = allPlayers.find {
                                        it.fullName.trim().lowercase().replace("\\s+".toRegex(), " ") == normalized
                                    }
                                    if (existing != null) {
                                        Toast.makeText(
                                            context,
                                            "'${existing.fullName}' ইতিমধ্যেই ডেটাবেজে আছে! 'ডেটাবেজ' ট্যাব থেকে সিলেক্ট করুন।",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@Button
                                    }

                                    val newPlayer = Player(
                                        id = java.util.UUID.randomUUID().toString(),
                                        fullName = cleaned,
                                        nickname = newNickname.trim(),
                                        photoUrl = newPhotoUrl,
                                        jerseyNumber = newJersey.toIntOrNull() ?: (squadPlayers.size + 1),
                                        primaryRole = newRole,
                                        battingStyle = newBatStyle,
                                        bowlingStyle = newBowlStyle,
                                        contactInfo = newContact.trim(),
                                        teamIds = listOf(team.id),
                                        creatorId = currentUserId
                                    )
                                    val updatedIds = (squadIds + newPlayer.id).distinct()
                                    squadIds = updatedIds
                                    scope.launch {
                                        container.playerRepository.savePlayer(newPlayer)
                                        container.teamRepository.saveTeam(team.copy(playerIds = updatedIds))
                                        newName = ""
                                        newNickname = ""
                                        newContact = ""
                                        newPhotoUrl = null
                                        newJersey = (updatedIds.size + 1).toString()
                                        selectedTab = 0
                                        Toast.makeText(context, "${newPlayer.fullName} দলের স্কোয়াডে যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("squad_add_full_player_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = DeepNavyBg)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("তৈরি করে স্কোয়াডে যোগ করুন", color = DeepNavyBg, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    2 -> {
                        // Select from Player Database
                        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            OutlinedTextField(
                                value = dbSearchQuery,
                                onValueChange = { dbSearchQuery = it },
                                label = { Text("খেলোয়াড় খুঁজুন (নাম / ভূমিকা / জার্সি)") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldGreen) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = CardBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val filtered = remember(availableDbPlayers, dbSearchQuery) {
                                val q = dbSearchQuery.trim().lowercase()
                                if (q.isEmpty()) availableDbPlayers
                                else availableDbPlayers.filter {
                                    it.fullName.lowercase().contains(q) ||
                                        it.nickname.lowercase().contains(q) ||
                                        it.primaryRole.lowercase().contains(q) ||
                                        it.jerseyNumber.toString() == q
                                }
                            }

                            if (filtered.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (availableDbPlayers.isEmpty()) "ডেটাবেজের সব খেলোয়াড় ইতিমধ্যেই এই স্কোয়াডে যুক্ত আছে।"
                                        else "খুঁজে পাওয়া যায়নি। '+ নতুন খেলোয়াড়' ট্যাব থেকে যোগ করুন।",
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    items(filtered, key = { it.id }) { player ->
                                        val isChecked = selectedDbIds.contains(player.id)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (isChecked) selectedDbIds.remove(player.id)
                                                    else selectedDbIds.add(player.id)
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isChecked) EmeraldGreen.copy(alpha = 0.14f) else SlateCardBg
                                            ),
                                            border = BorderStroke(1.dp, if (isChecked) EmeraldGreen else CardBorder),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = {
                                                        if (it) {
                                                            if (!selectedDbIds.contains(player.id)) selectedDbIds.add(player.id)
                                                        } else {
                                                            selectedDbIds.remove(player.id)
                                                        }
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = EmeraldGreen,
                                                        checkmarkColor = DeepNavyBg
                                                    )
                                                )
                                                CricUpAvatar(
                                                    model = player.photoUrl,
                                                    size = 36.dp,
                                                    fallbackText = player.fullName.take(2)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(player.fullName, color = TextWhite, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        "${player.primaryRole} • #${player.jerseyNumber} • ${player.battingStyle}",
                                                        color = TextLightGrey,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedDbIds.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val chosen = availableDbPlayers.filter { selectedDbIds.contains(it.id) }
                                        val updatedIds = (squadIds + chosen.map { it.id }).distinct()
                                        squadIds = updatedIds
                                        scope.launch {
                                            chosen.forEach { p ->
                                                container.playerRepository.savePlayer(
                                                    p.copy(teamIds = (p.teamIds + team.id).distinct())
                                                )
                                            }
                                            container.teamRepository.saveTeam(team.copy(playerIds = updatedIds))
                                            selectedDbIds.clear()
                                            selectedTab = 0
                                            Toast.makeText(context, "${chosen.size} জন খেলোয়াড় স্কোয়াডে যোগ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldYellow),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(44.dp)
                                ) {
                                    Text(
                                        "নির্বাচিত ${selectedDbIds.size} জনকে স্কোয়াডে যোগ করুন",
                                        color = DeepNavyBg,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Text("সম্পন্ন (Done)", color = DeepNavyBg, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SurfaceDarkNavy
    )

    editingSquadPlayer?.let { playerToEdit ->
        FullPlayerProfileDialog(
            initialPlayer = playerToEdit,
            existingPlayers = allPlayers,
            creatorId = currentUserId,
            attachedTeamIds = listOf(team.id),
            onDismiss = { editingSquadPlayer = null },
            onConfirm = { updated ->
                scope.launch {
                    container.playerRepository.savePlayer(updated)
                    editingSquadPlayer = null
                    Toast.makeText(context, "${updated.fullName} এর তথ্য আপডেট হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

/**
 * Dialog for Create New Match Step 4 -> Add Player:
 * Supports BOTH:
 * 1) Selecting players from the Player Database (with search + multi-select)
 * 2) Creating a brand-new player with full details
 */
@Composable
fun CreateMatchAddPlayerDialog(
    currentTeam: Team?,
    allPlayers: List<Player>,
    currentSquad: MutableList<Player>,
    currentXI: MutableList<Player>,
    otherSquad: List<Player>,
    otherXI: List<Player>,
    playersPerTeam: Int,
    currentCaptainId: String,
    onCaptainChange: (String) -> Unit,
    container: AppContainer,
    initialTab: Int = 0, // 0 = Database, 1 = New Player
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = container.authRepository.getCurrentUserId()

    var activeTab by remember { mutableIntStateOf(initialTab) }

    // Tab 0: Player Database search & multi-select
    var searchQuery by remember { mutableStateOf("") }
    var filterOnlyUnassigned by remember { mutableStateOf(false) }
    var alsoSaveToTeamDb by remember { mutableStateOf(true) }
    val selectedFromDb = remember { mutableStateListOf<Player>() }

    // Tab 1: New Player full fields
    var pName by remember { mutableStateOf("") }
    var pNickname by remember { mutableStateOf("") }
    var pJersey by remember { mutableStateOf((currentSquad.size + 1).toString()) }
    var pRole by remember { mutableStateOf(PlayerRoles.ALL_ROUNDER) }
    var pBatStyle by remember { mutableStateOf("Right Hand") }
    var pBowlStyle by remember { mutableStateOf("Right-arm Medium") }
    var pContact by remember { mutableStateOf("") }
    var pPhotoUrl by remember { mutableStateOf<String?>(null) }

    val availableDatabasePlayers = remember(allPlayers, currentSquad, otherSquad, otherXI, searchQuery, filterOnlyUnassigned) {
        val q = searchQuery.trim().lowercase()
        allPlayers
            .distinctBy { it.fullName.trim().lowercase() }
            .filter { player ->
                val matchesSearch = q.isEmpty() ||
                    player.fullName.lowercase().contains(q) ||
                    player.nickname.lowercase().contains(q) ||
                    player.primaryRole.lowercase().contains(q) ||
                    player.jerseyNumber.toString() == q
                val matchesFilter = !filterOnlyUnassigned || player.teamIds.isEmpty()
                matchesSearch && matchesFilter
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "${currentTeam?.shortName ?: "Team"} — খেলোয়াড় যোগ করুন",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "নতুন খেলোয়াড় তৈরি করুন অথবা ডেটাবেজ থেকে বেছে নিন",
                    color = TextLightGrey,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp, max = 460.dp)
            ) {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = SlateCardBg,
                    contentColor = EmeraldGreen,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = EmeraldGreen
                        )
                    }
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Text(
                                "ডেটাবেজ থেকে (${allPlayers.size})",
                                fontSize = 11.5.sp,
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 0) EmeraldGreen else TextLightGrey
                            )
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Text(
                                "+ নতুন খেলোয়াড়",
                                fontSize = 11.5.sp,
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 1) EmeraldGreen else TextLightGrey
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (activeTab == 0) {
                    // Database Picker Tab
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("খেলোয়াড় খুঁজুন (নাম, ভূমিকা, জার্সি)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldGreen) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("match_db_player_search")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !filterOnlyUnassigned,
                            onClick = { filterOnlyUnassigned = false },
                            label = { Text("সব খেলোয়াড়", fontSize = 10.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldGreen,
                                selectedLabelColor = DeepNavyBg,
                                containerColor = SlateCardBg,
                                labelColor = TextWhite
                            )
                        )
                        FilterChip(
                            selected = filterOnlyUnassigned,
                            onClick = { filterOnlyUnassigned = true },
                            label = { Text("দলবিহীন / ফ্রি", fontSize = 10.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldYellow,
                                selectedLabelColor = DeepNavyBg,
                                containerColor = SlateCardBg,
                                labelColor = TextWhite
                            )
                        )
                    }

                    if (currentTeam != null && currentTeam.id.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { alsoSaveToTeamDb = !alsoSaveToTeamDb },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = alsoSaveToTeamDb,
                                onCheckedChange = { alsoSaveToTeamDb = it },
                                colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen, checkmarkColor = DeepNavyBg),
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "নির্বাচিতদের ${currentTeam.shortName} দলের স্থায়ী স্কোয়াডেও যুক্ত রাখুন",
                                color = TextLightGrey,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (availableDatabasePlayers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "ডেটাবেজে কোনো মিল পাওয়া যায়নি।",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(onClick = { activeTab = 1 }) {
                                    Text("+ নতুন খেলোয়াড় তৈরি করুন", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            items(availableDatabasePlayers, key = { it.id }) { player ->
                                val alreadyInCurrent = currentSquad.any { it.id == player.id }
                                val alreadyInOther = otherSquad.any { it.id == player.id } || otherXI.any { it.id == player.id }
                                val isSelected = selectedFromDb.any { it.id == player.id }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !alreadyInCurrent && !alreadyInOther) {
                                            if (isSelected) selectedFromDb.removeAll { it.id == player.id }
                                            else selectedFromDb.add(player)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            alreadyInCurrent -> EmeraldGreen.copy(alpha = 0.08f)
                                            isSelected -> EmeraldGreen.copy(alpha = 0.16f)
                                            alreadyInOther -> SlateCardBg.copy(alpha = 0.45f)
                                            else -> SlateCardBg
                                        }
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            isSelected -> EmeraldGreen
                                            alreadyInCurrent -> EmeraldGreen.copy(alpha = 0.5f)
                                            else -> CardBorder
                                        }
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        when {
                                            alreadyInCurrent -> {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Already added",
                                                    tint = EmeraldGreen,
                                                    modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
                                                )
                                            }
                                            alreadyInOther -> {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "In opponent team",
                                                    tint = TextMuted,
                                                    modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
                                                )
                                            }
                                            else -> {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            if (!selectedFromDb.any { it.id == player.id }) selectedFromDb.add(player)
                                                        } else {
                                                            selectedFromDb.removeAll { it.id == player.id }
                                                        }
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen, checkmarkColor = DeepNavyBg)
                                                )
                                            }
                                        }

                                        CricUpAvatar(
                                            model = player.photoUrl,
                                            size = 36.dp,
                                            fallbackText = player.fullName.take(2)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = player.fullName,
                                                color = if (alreadyInOther) TextMuted else TextWhite,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${player.primaryRole} • #${player.jerseyNumber} • ${player.battingStyle}",
                                                color = TextLightGrey,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (alreadyInCurrent) {
                                            Surface(color = EmeraldGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                Text(
                                                    "স্কোয়াডে",
                                                    color = EmeraldGreen,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else if (alreadyInOther) {
                                            Surface(color = Color.Gray.copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)) {
                                                Text(
                                                    "প্রতিপক্ষ",
                                                    color = TextLightGrey,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Create Brand New Player Tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        FullPlayerFormContent(
                            fullName = pName,
                            onFullNameChange = { pName = it },
                            nickname = pNickname,
                            onNicknameChange = { pNickname = it },
                            jerseyNumber = pJersey,
                            onJerseyNumberChange = { pJersey = it },
                            primaryRole = pRole,
                            onPrimaryRoleChange = { pRole = it },
                            battingStyle = pBatStyle,
                            onBattingStyleChange = { pBatStyle = it },
                            bowlingStyle = pBowlStyle,
                            onBowlingStyleChange = { pBowlStyle = it },
                            contactInfo = pContact,
                            onContactInfoChange = { pContact = it },
                            photoUrl = pPhotoUrl,
                            onPhotoUrlChange = { pPhotoUrl = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (activeTab == 0) {
                Button(
                    onClick = {
                        if (selectedFromDb.isEmpty()) {
                            Toast.makeText(context, "অন্তত একজন খেলোয়াড় নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            val updatedPlayers = mutableListOf<Player>()
                            selectedFromDb.forEach { dbPlayer ->
                                val linkedPlayer = if (alsoSaveToTeamDb && currentTeam != null && currentTeam.id.isNotBlank()) {
                                    dbPlayer.copy(teamIds = (dbPlayer.teamIds + currentTeam.id).distinct())
                                } else {
                                    dbPlayer
                                }
                                if (alsoSaveToTeamDb && currentTeam != null && currentTeam.id.isNotBlank()) {
                                    container.playerRepository.savePlayer(linkedPlayer)
                                }
                                updatedPlayers.add(linkedPlayer)
                                if (!currentSquad.any { it.id == linkedPlayer.id }) {
                                    currentSquad.add(linkedPlayer)
                                }
                                if (currentXI.size < playersPerTeam && !currentXI.any { it.id == linkedPlayer.id }) {
                                    currentXI.add(linkedPlayer)
                                }
                            }
                            if (alsoSaveToTeamDb && currentTeam != null && currentTeam.id.isNotBlank()) {
                                val mergedTeamPlayerIds = (currentTeam.playerIds + updatedPlayers.map { it.id }).distinct()
                                container.teamRepository.saveTeam(currentTeam.copy(playerIds = mergedTeamPlayerIds))
                            }
                            if (currentCaptainId.isBlank() && currentXI.isNotEmpty()) {
                                onCaptainChange(currentXI.first().id)
                            }
                            Toast.makeText(
                                context,
                                "${updatedPlayers.size} জন খেলোয়াড় ${currentTeam?.shortName ?: "দলে"} যুক্ত হয়েছে!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                    },
                    enabled = selectedFromDb.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.testTag("confirm_add_db_players_btn")
                ) {
                    Text(
                        text = if (selectedFromDb.isEmpty()) "যোগ করুন" else "${selectedFromDb.size} জন যোগ করুন",
                        color = DeepNavyBg,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = {
                        val trimmedName = pName.trim()
                        if (trimmedName.isBlank()) {
                            Toast.makeText(context, "খেলোয়াড়ের নাম লিখুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (currentSquad.any { it.fullName.trim().equals(trimmedName, ignoreCase = true) }) {
                            Toast.makeText(context, "এই খেলোয়াড় ইতিমধ্যে স্কোয়াডে রয়েছে!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (otherSquad.any { it.fullName.trim().equals(trimmedName, ignoreCase = true) }) {
                            Toast.makeText(context, "এই খেলোয়াড় ইতিমধ্যেই প্রতিপক্ষ দলে রয়েছে!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val existing = allPlayers.find { it.fullName.trim().equals(trimmedName, ignoreCase = true) }
                        val teamIdList = listOfNotNull(currentTeam?.id?.takeIf { it.isNotBlank() })
                        val playerToAdd = existing?.copy(
                            teamIds = (existing.teamIds + teamIdList).distinct()
                        ) ?: Player(
                            id = java.util.UUID.randomUUID().toString(),
                            fullName = trimmedName,
                            nickname = pNickname.trim(),
                            photoUrl = pPhotoUrl,
                            jerseyNumber = pJersey.toIntOrNull() ?: (currentSquad.size + 1),
                            primaryRole = pRole,
                            battingStyle = pBatStyle,
                            bowlingStyle = pBowlStyle,
                            contactInfo = pContact.trim(),
                            teamIds = teamIdList,
                            creatorId = currentUserId
                        )

                        scope.launch {
                            container.playerRepository.savePlayer(playerToAdd)
                            if (currentTeam != null && currentTeam.id.isNotBlank()) {
                                val updatedTeamPlayerIds = (currentTeam.playerIds + playerToAdd.id).distinct()
                                container.teamRepository.saveTeam(currentTeam.copy(playerIds = updatedTeamPlayerIds))
                            }
                            if (!currentSquad.any { it.id == playerToAdd.id }) {
                                currentSquad.add(playerToAdd)
                            }
                            if (currentXI.size < playersPerTeam && !currentXI.any { it.id == playerToAdd.id }) {
                                currentXI.add(playerToAdd)
                            }
                            if (currentCaptainId.isBlank()) {
                                onCaptainChange(playerToAdd.id)
                            }
                            Toast.makeText(context, "${playerToAdd.fullName} দলে যোগ হয়েছে!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    modifier = Modifier.testTag("confirm_add_new_player_btn")
                ) {
                    Text("তৈরি ও যোগ করুন", color = DeepNavyBg, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল (Cancel)", color = TextLightGrey)
            }
        },
        containerColor = SurfaceDarkNavy
    )
}
