package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Match
import com.example.data.model.SubScorerInfo
import com.example.data.model.User
import com.example.di.AppContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.WicketRed
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor
import com.example.util.MatchShareHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SubScorerManagementDialog(
    match: Match,
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emerald = adaptiveEmeraldColor()
    val gold = adaptiveGoldColor()

    val initialProfiles = remember(match.id, match.authorizedScorers, match.subScorerProfiles) {
        val byUid = match.subScorerProfiles.associateBy { it.uid }.toMutableMap()
        match.authorizedScorers.forEach { uid ->
            if (uid.isNotBlank() && !byUid.containsKey(uid)) {
                byUid[uid] = SubScorerInfo(uid = uid, name = uid)
            }
        }
        byUid.values.toList()
    }

    val currentUserId = remember(container) { container.authRepository.getCurrentUserId() }
    val currentUserEmail = remember(container) {
        container.authRepository.getCurrentUserFlow().value?.email.orEmpty()
    }

    val selectedSubScorers = remember(match.id) {
        mutableStateListOf<SubScorerInfo>().apply { addAll(initialProfiles) }
    }

    var previouslySearchedUsers by remember(match.id, currentUserId) {
        mutableStateOf(
            container.authRepository.getPreviouslySearchedScorerUsers(currentUserId)
                .filter { it.uid.isNotBlank() && it.uid != match.effectiveOwnerUid && it.uid != currentUserId }
        )
    }
    var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var isSearchingUsers by remember { mutableStateOf(false) }
    var isResolvingManual by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var manualIdInput by remember { mutableStateOf("") }
    var manualNameInput by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    fun recordUsersToSuggestions(users: List<User>) {
        if (users.isEmpty()) return
        var latest = previouslySearchedUsers
        users.asReversed().forEach { u ->
            if (u.uid.isNotBlank() && u.uid != match.effectiveOwnerUid && u.uid != currentUserId) {
                latest = container.authRepository.recordSearchedScorerUser(u, currentUserId)
            }
        }
        previouslySearchedUsers = latest.filter {
            it.uid.isNotBlank() && it.uid != match.effectiveOwnerUid && it.uid != currentUserId
        }
    }

    fun executeExplicitSearch() {
        val q = searchQuery.trim()
        if (q.isBlank()) return
        scope.launch {
            isSearchingUsers = true
            val found = container.authRepository.searchUsers(q, excludeUid = match.effectiveOwnerUid)
                .getOrDefault(emptyList())
                .filter { it.uid.isNotBlank() && it.uid != currentUserId }
            searchResults = found
            isSearchingUsers = false
            if (found.isNotEmpty()) {
                recordUsersToSuggestions(found.take(5))
            }
        }
    }

    LaunchedEffect(searchQuery, match.id, currentUserId) {
        val clean = searchQuery.trim()
        if (clean.isBlank()) {
            searchResults = emptyList()
            isSearchingUsers = false
            return@LaunchedEffect
        }
        isSearchingUsers = true
        delay(250)
        val found = container.authRepository.searchUsers(clean, excludeUid = match.effectiveOwnerUid)
            .getOrDefault(emptyList())
            .filter { it.uid.isNotBlank() && it.uid != currentUserId }
        searchResults = found
        isSearchingUsers = false

        val cleanLower = clean.lowercase()
        val exactMatches = found.filter {
            it.uid.equals(clean, ignoreCase = true) ||
                (it.email.isNotBlank() && it.email.equals(cleanLower, ignoreCase = true))
        }
        if (exactMatches.isNotEmpty()) {
            recordUsersToSuggestions(exactMatches)
        }
    }

    val isQueryActive = searchQuery.trim().isNotBlank()
    val filteredDirectory = remember(
        isQueryActive,
        searchResults,
        previouslySearchedUsers,
        selectedSubScorers.size
    ) {
        val chosenIds = selectedSubScorers.map { it.uid }.toSet()
        val source = if (isQueryActive) searchResults else previouslySearchedUsers
        source.filter { user ->
            user.uid.isNotBlank() &&
                user.uid != match.effectiveOwnerUid &&
                user.uid != currentUserId &&
                user.uid !in chosenIds
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CricUpIcons.GroupAdd,
                    contentDescription = null,
                    tint = emerald,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Head & Sub Scorers (স্কোরার)",
                    color = gold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Match Code & Head Scorer Box
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Head Scorer: ${match.ownerName.ifBlank { "Creator" }}",
                                        color = emerald,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Match Code: ${match.matchCode}",
                                        color = gold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = { MatchShareHelper.copyMatchCode(context, match.matchCode) }
                                    ) {
                                        Icon(CricUpIcons.ContentCopy, contentDescription = "Copy Match Code", tint = gold)
                                    }
                                    IconButton(
                                        onClick = { MatchShareHelper.shareMatch(context, match) }
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share Match Code", tint = emerald)
                                    }
                                }
                            }
                            Text(
                                text = "সাব স্কোরাররা ম্যাচ কোড দিয়ে লাইভ ম্যাচটি খুলে সরাসরি বল-বাই-বল স্কোরিং করতে পারবেন।",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Current Assigned Sub Scorers
                item {
                    Text(
                        text = "যুক্ত থাকা Sub Scorers (${selectedSubScorers.size} জন)",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedSubScorers.isEmpty()) {
                    item {
                        Text(
                            text = "এখনো কোনো সাব স্কোরার যুক্ত করা হয়নি। নিচ থেকে একাধিক মেম্বার যোগ করুন।",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                    }
                } else {
                    items(selectedSubScorers, key = { "assigned_${it.uid}" }) { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(emerald.copy(alpha = 0.1f))
                                .border(1.dp, emerald.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("assigned_sub_scorer_${sub.uid}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                CricUpAvatar(
                                    model = null,
                                    size = 34.dp,
                                    fallbackText = sub.name.ifBlank { sub.uid }.take(2),
                                    borderColor = emerald
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sub.name.ifBlank { "Sub Scorer" },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "ID: ${sub.uid}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { selectedSubScorers.removeAll { it.uid == sub.uid } },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("remove_sub_scorer_${sub.uid}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove sub scorer",
                                    tint = WicketRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }

                // Manual User ID or Gmail Add Section
                item {
                    Text(
                        text = "User ID বা Gmail দিয়ে Sub Scorer যোগ করুন",
                        color = gold,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = manualIdInput,
                        onValueChange = { manualIdInput = it },
                        label = { Text("User ID বা Gmail দিন") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sub_scorer_manual_id_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualNameInput,
                            onValueChange = { manualNameInput = it },
                            label = { Text("নাম (ঐচ্ছিক)") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sub_scorer_manual_name_input")
                        )
                        Button(
                            onClick = {
                                val cleanInput = manualIdInput.trim()
                                if (cleanInput.isBlank() || isResolvingManual) return@Button
                                if (cleanInput == match.effectiveOwnerUid ||
                                    (currentUserEmail.isNotBlank() && cleanInput.equals(currentUserEmail, ignoreCase = true))
                                ) {
                                    Toast.makeText(context, "আপনি ইতিমধ্যেই Head Scorer!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isResolvingManual = true
                                scope.launch {
                                    val found = container.authRepository.searchUsers(
                                        query = cleanInput,
                                        excludeUid = match.effectiveOwnerUid
                                    ).getOrDefault(emptyList())
                                        .filter { it.uid != currentUserId }
                                    val exactMatch = found.firstOrNull {
                                        it.uid.equals(cleanInput, ignoreCase = true) ||
                                            (it.email.isNotBlank() && it.email.equals(cleanInput, ignoreCase = true))
                                    }
                                    isResolvingManual = false

                                    if (cleanInput.contains("@") && exactMatch == null) {
                                        Toast.makeText(
                                            context,
                                            "এই Gmail দিয়ে কোনো CricUp অ্যাকাউন্ট পাওয়া যায়নি",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }

                                    val resolvedUid = exactMatch?.uid ?: cleanInput
                                    val resolvedName = manualNameInput.trim().ifBlank {
                                        exactMatch?.displayName?.takeIf { it.isNotBlank() }
                                            ?: "Scorer ${resolvedUid.takeLast(4).uppercase()}"
                                    }
                                    if (resolvedUid == match.effectiveOwnerUid) {
                                        Toast.makeText(context, "আপনি ইতিমধ্যেই Head Scorer!", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    if (selectedSubScorers.none { it.uid == resolvedUid }) {
                                        selectedSubScorers.add(SubScorerInfo(uid = resolvedUid, name = resolvedName))
                                    }
                                    val historyUser = exactMatch?.copy(
                                        displayName = exactMatch.displayName.ifBlank { resolvedName }
                                    ) ?: User(uid = resolvedUid, displayName = resolvedName)
                                    recordUsersToSuggestions(listOf(historyUser))
                                    manualIdInput = ""
                                    manualNameInput = ""
                                }
                            },
                            enabled = manualIdInput.isNotBlank() && !isResolvingManual,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("sub_scorer_manual_add_btn")
                        ) {
                            if (isResolvingManual) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            } else {
                                Icon(CricUpIcons.PersonAdd, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("যোগ", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Search-based CricUp Member Picker (No automatic full directory listing)
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "নাম, ID বা Gmail দিয়ে মেম্বার খুঁজুন",
                        color = emerald,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("নাম, ID বা Gmail দিয়ে খুঁজুন...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { executeExplicitSearch() }),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sub_scorer_search_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isQueryActive) {
                            "সার্চ ফলাফল (${filteredDirectory.size})"
                        } else {
                            "পূর্বে সার্চ করা মেম্বার (${filteredDirectory.size})"
                        },
                        color = if (isQueryActive) gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isSearchingUsers) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = emerald, strokeWidth = 2.dp)
                        }
                    }
                } else if (filteredDirectory.isEmpty()) {
                    item {
                        Text(
                            text = if (isQueryActive) {
                                "এই নাম, ID বা Gmail দিয়ে কোনো মেম্বার পাওয়া যায়নি।"
                            } else {
                                "পূর্বে কোনো মেম্বার সার্চ করা হয়নি। উপরে নাম, ID বা Gmail লিখে খুঁজুন এবং + Add করুন।"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    items(filteredDirectory.take(25), key = { "dir_${it.uid}" }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                CricUpAvatar(
                                    model = user.photoUrl,
                                    size = 34.dp,
                                    fallbackText = user.displayName.ifBlank { user.uid }.take(2),
                                    borderColor = gold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName.ifBlank { "Member" },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "ID: ${user.uid}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (user.email.isNotBlank()) {
                                        Text(
                                            text = user.email,
                                            color = emerald,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isQueryActive) {
                                    IconButton(
                                        onClick = {
                                            previouslySearchedUsers = container.authRepository
                                                .removeSearchedScorerUser(user.uid, currentUserId)
                                                .filter { it.uid != match.effectiveOwnerUid && it.uid != currentUserId }
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("remove_searched_suggestion_${user.uid}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "সাজেশন থেকে সরান",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val displayName = user.displayName.ifBlank {
                                            "Scorer ${user.uid.takeLast(4).uppercase()}"
                                        }
                                        if (selectedSubScorers.none { it.uid == user.uid }) {
                                            selectedSubScorers.add(
                                                SubScorerInfo(
                                                    uid = user.uid,
                                                    name = displayName
                                                )
                                            )
                                        }
                                        recordUsersToSuggestions(listOf(user.copy(displayName = displayName)))
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, emerald),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("add_sub_scorer_${user.uid}")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = emerald, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Add", color = emerald, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        val res = container.matchRepository.updateSubScorers(match.id, selectedSubScorers.toList())
                        isSaving = false
                        res.onSuccess {
                            Toast.makeText(
                                context,
                                "Sub Scorers তালিকা সেভ হয়েছে (${selectedSubScorers.size} জন)",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "সেভ করা যায়নি", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("save_sub_scorers_btn")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text("সংরক্ষণ করুন", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
