package com.example.studentcodex.ui.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studentcodex.R
import com.example.studentcodex.data.auth.ClassroomAuthorizationOutcome
import com.example.studentcodex.data.auth.SessionInfo
import com.example.studentcodex.data.repo.ClassroomConnectionState
import com.example.studentcodex.data.repo.ClassroomSyncResult
import com.example.studentcodex.di.AppContainer
import kotlinx.coroutines.launch
import com.example.studentcodex.ui.auth.CourseCodesField
import com.example.studentcodex.ui.auth.SchoolDetailsFields
import com.example.studentcodex.ui.auth.SchoolDirectoryState
import com.example.studentcodex.ui.guidance.overlay.GuidanceOverlayMode
import com.example.studentcodex.ui.theme.Spacing

/**
 * The Settings page (the bottom-bar tab). What lives here:
 *
 *  * the Profile page entry - the pushed Profile page shows the identity
 *    itself (its Connections and Preferences sections moved HERE);
 *  * the floating assistant's style - chat head or edge panel (applies
 *    immediately on every other page, and the overlay stays visible over
 *    THIS page too, so switching styles previews it live);
 *  * the account's password and the school details - each behind its own
 *    button, which opens the form in a modal bottom-sheet panel (the same
 *    forms/registry dropdowns the sign-up flow uses);
 *  * the Google Classroom connection card and the deferred Notifications
 *    row (both moved from the Profile page, no category headers here);
 *  * sign out (or "exit demo mode" for guests) - the last row on the page.
 *
 * Guests see the Profile entry, the floating-assistant card, the demo
 * notice, the notifications row, and the exit-demo row: there is no
 * account behind a guest session to manage.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    session: SessionInfo,
    overlayMode: GuidanceOverlayMode,
    onSelectOverlayMode: (GuidanceOverlayMode) -> Unit,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            authRepository = container.authRepository,
            schoolDirectoryRepository = container.schoolDirectoryRepository,
            session = session,
            classroomRepository = container.classroomConnectionRepository,
            classroomSyncRepository = container.classroomSyncRepository,
            classroomAutoSyncStore = container.classroomAutoSyncStore,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val directory by viewModel.schoolDirectory.collectAsStateWithLifecycle()

    // The two account panels start closed; their buttons on the page open
    // them.
    var showPasswordSheet by remember { mutableStateOf(false) }
    var showSchoolSheet by remember { mutableStateOf(false) }

    // Google Classroom connect & sync: the Google consent sheet needs a
    // live activity, so the SCREEN owns the Authorization API handshake
    // and hands the minted access token to the ViewModel, which runs the
    // Edge Function import. Cancelled consent is silent; every other
    // failure surfaces under the connection card.
    val activityContext = LocalContext.current
    val authScope = rememberCoroutineScope()
    val onConnectAndSync: () -> Unit = {
        authScope.launch {
            when (val outcome = container.classroomAuthorizer.fetchAccessToken(activityContext as? Activity)) {
                is ClassroomAuthorizationOutcome.Token ->
                    viewModel.onClassroomAuthorized(outcome.accessToken)
                ClassroomAuthorizationOutcome.Cancelled -> Unit // user closed the sheet
                ClassroomAuthorizationOutcome.Declined ->
                    viewModel.onClassroomAuthError(
                        activityContext.getString(R.string.classroom_auth_declined),
                    )
                is ClassroomAuthorizationOutcome.Rejected ->
                    viewModel.onClassroomAuthError(
                        activityContext.getString(R.string.classroom_auth_rejected, outcome.code ?: 0),
                    )
                is ClassroomAuthorizationOutcome.Error ->
                    viewModel.onClassroomAuthError(
                        activityContext.getString(
                            R.string.classroom_sync_failed,
                            outcome.message ?: "unknown error",
                        ),
                    )
                // Never returned by fetchAccessToken (interactive flow);
                // exhaustive for the silent-only outcome the auto-syncer uses.
                ClassroomAuthorizationOutcome.SilentUnavailable -> Unit
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The profile itself is one tap away: identity and connections live
        // on the pushed Profile page (sign-out moved from there to the
        // bottom of THIS page).
        SettingsActionCard(
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.settings_row_profile),
            note = stringResource(R.string.settings_row_profile_note),
            onClick = onOpenProfile,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )

        Spacer(Modifier.height(Spacing.md))
        FloatingAssistantPreference(
            selectedMode = overlayMode,
            onSelectMode = onSelectOverlayMode,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )

        if (state.isGuest) {
            Spacer(Modifier.height(Spacing.md))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
            ) {
                Text(
                    text = stringResource(R.string.settings_guest_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        } else {
            // No section headers: the row titles ("Change password",
            // "Change school details") carry the grouping on their own.
            Spacer(Modifier.height(Spacing.md))
            SettingsActionCard(
                icon = Icons.Rounded.Password,
                title = stringResource(R.string.settings_row_change_password),
                note = stringResource(R.string.settings_row_change_password_note),
                onClick = { showPasswordSheet = true },
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )

            Spacer(Modifier.height(Spacing.md))
            SettingsActionCard(
                icon = Icons.Rounded.School,
                title = stringResource(R.string.settings_row_change_school),
                note = stringResource(R.string.settings_row_change_school_note),
                onClick = { showSchoolSheet = true },
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )

            // Google Classroom connection (moved from the Profile page):
            // the real registry state, Connect & Sync (Authorization API
            // consent -> Edge Function import) and disconnect. The chain
            // icon mirrors the state: fixed while connected, broken while
            // not.
            Spacer(Modifier.height(Spacing.md))
            ClassroomConnectionCard(
                state = state.classroom,
                disconnecting = state.classroomDisconnecting,
                connecting = state.classroomConnecting,
                syncResult = state.classroomSyncResult,
                syncError = state.classroomSyncError,
                onDisconnect = viewModel::disconnectClassroom,
                onConnect = onConnectAndSync,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )

            // Auto-sync school works lives on its OWN card below: a
            // background-import preference, a different job from managing
            // the Google connection itself. Only a connected account has
            // anything to auto-import.
            if (state.classroom is ClassroomConnectionState.Connected) {
                Spacer(Modifier.height(Spacing.md))
                ClassroomAutoSyncCard(
                    enabled = state.classroomAutoSyncEnabled,
                    onEnabledChange = { enabled ->
                        viewModel.setClassroomAutoSync(enabled)
                        // Turning it on runs one silent attempt right away:
                        // instant proof it works, no consent sheet, and the
                        // sync itself is idempotent.
                        if (enabled) {
                            authScope.launch {
                                container.classroomAutoSyncer.runIfDue(activityContext as? Activity)
                                viewModel.refreshClassroom()
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }

        // Notifications (moved from the Profile page): a deferred
        // capability, so the row states it honestly instead of pretending
        // to work. Shown for guests too - a preference, not an account
        // action.
        Spacer(Modifier.height(Spacing.md))
        SettingsActionCard(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.profile_row_notifications),
            note = stringResource(R.string.profile_deferred_note),
            onClick = null,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )

        // Sign-out sits AFTER every operation button on the page. Guests
        // get the exit-demo variant of the row.
        Spacer(Modifier.height(Spacing.xl))
        SettingsActionCard(
            icon = Icons.Rounded.Logout,
            title = stringResource(
                if (state.isGuest) R.string.settings_row_exit_demo
                else R.string.settings_row_sign_out,
            ),
            note = stringResource(
                if (state.isGuest) R.string.settings_row_sign_out_note_demo
                else R.string.settings_row_sign_out_note,
            ),
            onClick = onSignOut,
            showChevron = false,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )

        Spacer(Modifier.height(Spacing.xxl))
    }

    // The account panels, opened by the buttons above: the same forms that
    // used to sit inline on the page, now shown on demand.
    if (showPasswordSheet) {
        ChangePasswordSheet(
            state = state,
            onPasswordChange = viewModel::onPasswordChange,
            onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
            onSave = viewModel::savePassword,
            onDismiss = { showPasswordSheet = false },
        )
    }
    if (showSchoolSheet) {
        SchoolDetailsSheet(
            state = state,
            directory = directory,
            onProgramChange = viewModel::onProgramChange,
            onYearLevelChange = viewModel::onYearLevelChange,
            onSectionChange = viewModel::onSectionChange,
            onCampusChange = viewModel::onCampusChange,
            onCourseCodeInputChange = viewModel::onCourseCodeInputChange,
            onAddCourseCode = viewModel::addCourseCode,
            onRemoveCourseCode = viewModel::removeCourseCode,
            onRetryDirectory = viewModel::retryDirectory,
            onSave = viewModel::saveSchool,
            onDismiss = { showSchoolSheet = false },
        )
    }
}

/**
 * One row-card on the Settings page: icon, title, note, and - for entries
 * that lead somewhere (a pushed page or an opened panel) - a trailing
 * chevron. Terminal actions (sign out) hide the chevron; informational
 * rows (notifications) pass a null onClick so the card shows no ripple.
 */
@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    note: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Floating-assistant style picker. Two mutually exclusive options - chat
 * head or edge panel - chosen with segmented buttons; the choice applies
 * immediately everywhere and is remembered across restarts. (The floating
 * overlay is visible over this page, so the switch is its own preview.)
 */
@Composable
private fun FloatingAssistantPreference(
    selectedMode: GuidanceOverlayMode,
    onSelectMode: (GuidanceOverlayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.guidance_overlay_section_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.guidance_overlay_section_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedMode == GuidanceOverlayMode.CHAT_HEAD,
                    onClick = { onSelectMode(GuidanceOverlayMode.CHAT_HEAD) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    PreferenceLabel(
                        icon = {
                            Icon(
                                Icons.Rounded.ChatBubble,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = stringResource(R.string.guidance_overlay_mode_chat_head),
                    )
                }
                SegmentedButton(
                    selected = selectedMode == GuidanceOverlayMode.EDGE_PANEL,
                    onClick = { onSelectMode(GuidanceOverlayMode.EDGE_PANEL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    PreferenceLabel(
                        icon = {
                            Icon(
                                Icons.Rounded.ViewSidebar,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = stringResource(R.string.guidance_overlay_mode_edge_panel),
                    )
                }
            }
        }
    }
}

/** Icon + label centered inside one segmented button. */
@Composable
private fun PreferenceLabel(
    icon: @Composable () -> Unit,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        icon()
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The change-password panel, opened by the Change password button: a
 * modal bottom sheet wrapping the same form that used to sit inline on
 * the Settings page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePasswordSheet(
    state: SettingsUiState,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .imePadding(),
        ) {
            Text(
                text = stringResource(R.string.settings_change_password_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.settings_change_password_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.settings_field_new_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text(stringResource(R.string.settings_field_confirm_new_password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            SaveFeedback(
                error = when (state.error) {
                    SettingsError.PASSWORD_LENGTH ->
                        stringResource(R.string.auth_error_password_length)
                    SettingsError.PASSWORD_MISMATCH ->
                        stringResource(R.string.auth_error_password_mismatch)
                    else -> null
                },
                remoteError = state.remoteError?.takeIf { it.isNotBlank() },
                savedMessage = stringResource(R.string.settings_password_saved)
                    .takeIf { state.passwordSaved },
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Button(
                onClick = onSave,
                enabled = !state.passwordSaving && state.password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
            ) {
                if (state.passwordSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.settings_change_password_title))
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * The school-details panel, opened by the Change school details button:
 * same campus-first registry-dropdowns editor the sign-up form uses, now
 * inside a modal bottom sheet instead of an always-open inline card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchoolDetailsSheet(
    state: SettingsUiState,
    directory: SchoolDirectoryState,
    onProgramChange: (String?) -> Unit,
    onYearLevelChange: (Int?) -> Unit,
    onSectionChange: (String?) -> Unit,
    onCampusChange: (String?) -> Unit,
    onCourseCodeInputChange: (String) -> Unit,
    onAddCourseCode: (String?) -> Unit,
    onRemoveCourseCode: (String) -> Unit,
    onRetryDirectory: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .imePadding(),
        ) {
            Text(
                text = stringResource(R.string.settings_section_school),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.settings_school_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            SchoolDetailsFields(
                directory = directory,
                programId = state.programId,
                yearLevel = state.yearLevel,
                sectionId = state.sectionId,
                campusId = state.campusId,
                onProgramChange = onProgramChange,
                onYearLevelChange = onYearLevelChange,
                onSectionChange = onSectionChange,
                onCampusChange = onCampusChange,
                onRetryDirectory = onRetryDirectory,
                showHeader = false,
            )
            Spacer(Modifier.height(Spacing.md))
            // The course list — the same searchable rolldown as the
            // sign-up form, prefilled with the account's current courses.
            // This is how an account created before the course sign-up
            // existed links its classes (and fixes the empty audience
            // picker in the manual activity editor).
            CourseCodesField(
                codes = state.courseCodes,
                input = state.courseCodeInput,
                inputError = state.courseCodeError,
                directory = directory,
                onInputChange = onCourseCodeInputChange,
                onAdd = onAddCourseCode,
                onRemove = onRemoveCourseCode,
            )
            SaveFeedback(
                error = when (state.error) {
                    SettingsError.SCHOOL -> stringResource(R.string.auth_error_school)
                    SettingsError.COURSES -> stringResource(R.string.auth_error_courses)
                    else -> null
                },
                remoteError = state.remoteError?.takeIf { it.isNotBlank() },
                savedMessage = stringResource(R.string.settings_school_saved)
                    .takeIf { state.schoolSaved },
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Button(
                onClick = onSave,
                enabled = !state.schoolSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
            ) {
                if (state.schoolSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.settings_school_save))
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** One row of inline save feedback: error or success, never both. */
@Composable
private fun SaveFeedback(
    error: String?,
    remoteError: String?,
    savedMessage: String?,
    modifier: Modifier = Modifier,
) {
    val message = error ?: remoteError ?: savedMessage ?: return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = when {
                error != null || remoteError != null -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
        )
    }
}

/**
 * Google Classroom connection card (moved from the Profile page). Shows
 * the REAL state from the google_connections registry: never-connected,
 * connected (+ email, last sync), revoked, expired, or deliberately
 * disconnected. Connect & Sync runs the Authorization API consent sheet
 * and the Edge Function import (Phase 6): one button while never
 * connected/reconnectable, a Sync-now refresh once connected. The chain
 * icon mirrors the connection state: a fixed chain while connected, a
 * broken one otherwise. The result summary lands under the note.
 */
@Composable
private fun ClassroomConnectionCard(
    state: ClassroomConnectionState,
    disconnecting: Boolean,
    connecting: Boolean,
    syncResult: ClassroomSyncResult?,
    syncError: String?,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Icon(
                    imageVector = if (state is ClassroomConnectionState.Connected) {
                        Icons.Rounded.Link
                    } else {
                        Icons.Rounded.LinkOff
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_row_classroom),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = classroomConnectionNote(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (state is ClassroomConnectionState.Connected) {
                    // Connected: re-run an import (new coursework since the
                    // last run arrives here) or break the chain for good.
                    // The broken-chain icon IS the disconnect affordance.
                    OutlinedButton(onClick = onConnect) {
                        Text(stringResource(R.string.classroom_sync_now))
                    }
                    IconButton(
                        onClick = onDisconnect,
                        enabled = !disconnecting,
                    ) {
                        if (disconnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.LinkOff,
                                contentDescription = stringResource(
                                    R.string.classroom_disconnect,
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // Never connected / disconnected / revoked / expired:
                    // one button that authorizes and imports in one tap.
                    // The fixed-chain icon is what tapping builds.
                    OutlinedButton(onClick = onConnect) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(Spacing.xs))
                        Text(stringResource(R.string.classroom_connect))
                    }
                }
            }

            // Last sync outcome, under the note: the summary for a
            // completed run, the error color for failures.
            if (connecting) {
                Text(
                    text = stringResource(R.string.classroom_connecting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            syncResult?.let { result ->
                Text(
                    text = when {
                        result.status == "partial" && result.skippedClasses.isNotEmpty() ->
                            stringResource(
                                R.string.classroom_sync_partial,
                                result.imported,
                                result.updated,
                                result.skippedClasses.joinToString(),
                            )
                        else -> stringResource(
                            R.string.classroom_sync_ok,
                            result.imported,
                            result.updated,
                            result.matchedClasses.size,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            syncError?.let { error ->
                Text(
                    text = stringResource(R.string.classroom_sync_failed, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/**
 * Auto-sync school works: its own card, deliberately separate from the
 * Google Classroom connection card above. The connection card manages
 * the Google link itself; this one only decides whether the app silently
 * re-imports Classroom work on every open while that link exists.
 */
@Composable
private fun ClassroomAutoSyncCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(Spacing.md),
        ) {
            Icon(
                imageVector = Icons.Rounded.Autorenew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.classroom_auto_sync_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.classroom_auto_sync_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

@Composable
private fun classroomConnectionNote(state: ClassroomConnectionState): String =
    when (state) {
        is ClassroomConnectionState.Connected -> {
            val synced = state.lastSyncAt?.let {
                java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(it)
            }
            buildString {
                append(stringResource(R.string.classroom_connected_note))
                state.googleEmail?.let { append(" ($it)") }
                synced?.let { append(" · last sync $it") }
                if (state.lastSyncStatus == "failed") append(" · last sync failed")
            }
        }
        ClassroomConnectionState.NeverConnected ->
            stringResource(R.string.classroom_not_enabled_note)
        ClassroomConnectionState.Revoked ->
            stringResource(R.string.classroom_revoked_note)
        ClassroomConnectionState.Expired ->
            stringResource(R.string.classroom_expired_note)
        ClassroomConnectionState.Disconnected ->
            stringResource(R.string.classroom_disconnected_note)
        ClassroomConnectionState.NotSignedIn ->
            stringResource(R.string.classroom_not_enabled_note)
    }
