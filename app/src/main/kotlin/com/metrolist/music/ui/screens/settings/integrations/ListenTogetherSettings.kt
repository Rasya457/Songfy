/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

import com.metrolist.music.ui.component.SettingsSubScreenScaffold
import com.metrolist.music.ui.component.settingsSwitchColors

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.ListenTogetherAutoApprovalKey
import com.metrolist.music.constants.ListenTogetherAutoApproveSuggestionsKey
import com.metrolist.music.constants.ListenTogetherSyncVolumeKey
import com.metrolist.music.constants.ListenTogetherUsernameKey
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.listentogether.ListenTogetherEvent
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.IntegrationCard
import com.metrolist.music.ui.component.IntegrationCardItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSettings(navController: NavController) {
    val manager = LocalListenTogetherManager.current ?: return
    val context = LocalContext.current
    val cannotEditUsernameInRoomStr = stringResource(R.string.listen_together_cannot_edit_username_in_room)
    val coroutineScope = rememberCoroutineScope()

    val roomState by manager.roomState.collectAsStateWithLifecycle()
    val role by manager.role.collectAsStateWithLifecycle()

    var username by rememberPreference(ListenTogetherUsernameKey, "")
    var autoApprovalJoins by rememberPreference(ListenTogetherAutoApprovalKey, false)
    var autoApproveSuggestions by rememberPreference(ListenTogetherAutoApproveSuggestionsKey, false)
    var syncHostVolume by rememberPreference(ListenTogetherSyncVolumeKey, true)

    var showUsernameDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateRoomDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinRoomDialog by rememberSaveable { mutableStateOf(false) }
    var roomCodeInput by rememberSaveable { mutableStateOf("") }

    // Handle events
    LaunchedEffect(Unit) {
        manager.events.collectLatest { event ->
            when (event) {
                is ListenTogetherEvent.RoomCreated -> {
                    // Room created toast is shown globally by the client
                }

                is ListenTogetherEvent.JoinApproved -> {
                    Toast.makeText(context, "Joined room: ${event.roomCode}", Toast.LENGTH_SHORT).show()
                }

                is ListenTogetherEvent.JoinRejected -> {
                    Toast.makeText(context, "Join rejected: ${event.reason}", Toast.LENGTH_SHORT).show()
                }

                is ListenTogetherEvent.JoinRequestReceived -> {
                    Toast.makeText(context, "${event.username} wants to join", Toast.LENGTH_SHORT).show()
                }

                is ListenTogetherEvent.Kicked -> {
                    Toast.makeText(context, "Kicked: ${event.reason}", Toast.LENGTH_SHORT).show()
                }

                is ListenTogetherEvent.ConnectionError -> {
                    Toast.makeText(context, "Connection error: ${event.error}", Toast.LENGTH_SHORT).show()
                }

                is ListenTogetherEvent.ServerError -> {
                    Toast.makeText(context, "Error: ${event.message}", Toast.LENGTH_SHORT).show()
                }

                else -> {}
            }
        }
    }

    // Dialogs
    if (showUsernameDialog) {
        var tempUsername by rememberSaveable(showUsernameDialog) { mutableStateOf(username) }

        DefaultDialog(
            onDismiss = { showUsernameDialog = false },
            icon = { Icon(painterResource(R.drawable.person), contentDescription = null) },
            title = { Text(stringResource(R.string.listen_together_username)) },
            buttons = {
                TextButton(onClick = {
                    username = ""
                    showUsernameDialog = false
                }) {
                    Text(stringResource(R.string.reset))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    username = tempUsername.trim()
                    showUsernameDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            OutlinedTextField(
                value = tempUsername,
                onValueChange = { tempUsername = it },
                label = { Text(stringResource(R.string.listen_together_username)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.person), contentDescription = null)
                },
                trailingIcon = {
                    if (tempUsername.isNotBlank()) {
                        IconButton(onClick = { tempUsername = "" }, onLongClick = {}) {
                            Icon(painterResource(R.drawable.close), contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showCreateRoomDialog) {
        var createUsername by rememberSaveable(showCreateRoomDialog) { mutableStateOf(username) }

        DefaultDialog(
            onDismiss = { showCreateRoomDialog = false },
            icon = { Icon(painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(stringResource(R.string.listen_together_create_room)) },
            buttons = {
                TextButton(onClick = { showCreateRoomDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val finalUsername = createUsername.trim()
                        if (finalUsername.isNotBlank()) {
                            username = finalUsername
                            manager.createRoom(finalUsername)
                            showCreateRoomDialog = false
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = createUsername.trim().isNotBlank(),
                ) {
                    Text(stringResource(R.string.create))
                }
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.listen_together_create_room_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = createUsername,
                    onValueChange = { createUsername = it },
                    label = { Text(stringResource(R.string.listen_together_username)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.person), contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showJoinRoomDialog) {
        var joinUsername by rememberSaveable(showJoinRoomDialog) { mutableStateOf(username) }

        DefaultDialog(
            onDismiss = { showJoinRoomDialog = false },
            icon = { Icon(painterResource(R.drawable.group_add), contentDescription = null) },
            title = { Text(stringResource(R.string.listen_together_join_room)) },
            buttons = {
                TextButton(onClick = { showJoinRoomDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val finalUsername = joinUsername.trim()
                        if (finalUsername.isNotBlank() && roomCodeInput.length == 8) {
                            username = finalUsername
                            manager.joinRoom(roomCodeInput, finalUsername)
                            showJoinRoomDialog = false
                            roomCodeInput = ""
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = joinUsername.trim().isNotBlank() && roomCodeInput.length == 8,
                ) {
                    Text(stringResource(R.string.join))
                }
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = joinUsername,
                    onValueChange = { joinUsername = it },
                    label = { Text(stringResource(R.string.listen_together_username)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.person), contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = roomCodeInput,
                    onValueChange = { roomCodeInput = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
                    label = { Text(stringResource(R.string.listen_together_room_code)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.key), contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    SettingsSubScreenScaffold(
        title = stringResource(R.string.listen_together),
        navController = navController,
    ) {
        // Settings section using IntegrationCard
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            IntegrationCard(
                title = stringResource(R.string.settings),
                items =
                    listOf(
                        IntegrationCardItem(
                            icon = painterResource(R.drawable.person),
                            title = { Text(stringResource(R.string.listen_together_username)) },
                            description = {
                                Text(username.ifEmpty { stringResource(R.string.not_set) })
                            },
                            onClick =
                                if (roomState == null) {
                                    { showUsernameDialog = true }
                                } else {
                                    {
                                        Toast
                                            .makeText(
                                                context,
                                                cannotEditUsernameInRoomStr,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                        ),
                        IntegrationCardItem(
                            icon = painterResource(R.drawable.done),
                            title = { Text(stringResource(R.string.listen_together_auto_approval_joins)) },
                            description = {
                                Text(stringResource(R.string.listen_together_auto_approval_joins_desc))
                            },
                            trailingContent = {
                                Switch(
                                    checked = autoApprovalJoins,
                                    onCheckedChange = { autoApprovalJoins = it },
                                    // Only disable for guests in a room (hosts can always change)
                                    enabled = roomState == null || role != RoomRole.GUEST,
                                    thumbContent = {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    id = if (autoApprovalJoins) R.drawable.check else R.drawable.close,
                                                ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    },
                                )
                            },
                            // Allow clicking to see disabled state, but only change if enabled
                            onClick = { if (roomState == null || role != RoomRole.GUEST) autoApprovalJoins = !autoApprovalJoins },
                        ),
                        IntegrationCardItem(
                            icon = painterResource(R.drawable.done),
                            title = { Text(stringResource(R.string.listen_together_auto_approval_suggestions)) },
                            description = {
                                Text(stringResource(R.string.listen_together_auto_approval_suggestions_desc))
                            },
                            trailingContent = {
                                Switch(
                                    checked = autoApproveSuggestions,
                                    onCheckedChange = { autoApproveSuggestions = it },
                                    // Only disable for guests in a room (hosts can always change)
                                    enabled = roomState == null || role != RoomRole.GUEST,
                                    thumbContent = {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    id = if (autoApproveSuggestions) R.drawable.check else R.drawable.close,
                                                ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    },
                                )
                            },
                            // Allow clicking to see disabled state, but only change if enabled
                            onClick = { if (roomState == null || role != RoomRole.GUEST) autoApproveSuggestions = !autoApproveSuggestions },
                        ),
                        IntegrationCardItem(
                            icon = painterResource(R.drawable.volume_up),
                            title = { Text(stringResource(R.string.listen_together_sync_volume)) },
                            description = {
                                Text(stringResource(R.string.listen_together_sync_volume_desc))
                            },
                            trailingContent = {
                                Switch(
                                    checked = syncHostVolume,
                                    onCheckedChange = { syncHostVolume = it },
                                    thumbContent = {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    id = if (syncHostVolume) R.drawable.check else R.drawable.close,
                                                ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    },
                                )
                            },
                            onClick = { syncHostVolume = !syncHostVolume },
                        ),
                    ),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
