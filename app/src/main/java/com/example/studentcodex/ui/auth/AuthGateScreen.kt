@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.studentcodex.ui.auth

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.studentcodex.R
import com.example.studentcodex.data.school.looksLikeCourseCode
import com.example.studentcodex.ui.components.ClassroomImportSheet
import com.example.studentcodex.ui.components.ClassroomImportUiState
import com.example.studentcodex.ui.theme.Spacing

/**
 * The gate shown before the main app: real Supabase sign-in / sign-up
 * (with first/last name, which the database turns into the username, plus
 * program / year level / section / campus chosen from registry dropdowns),
 * Google Sign-In, plus the explicit "explore without an account" bypass
 * for quick testing.
 */
@Composable
fun AuthGateScreen(
    state: AuthUiState.SignedOut,
    directory: SchoolDirectoryState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSchoolProgramChange: (String?) -> Unit,
    onSchoolYearLevelChange: (Int?) -> Unit,
    onSchoolSectionChange: (String?) -> Unit,
    onSchoolCampusChange: (String?) -> Unit,
    onCourseCodeInputChange: (String) -> Unit,
    onAddCourseCode: (String?) -> Unit,
    onRemoveCourseCode: (String) -> Unit,
    onRetryDirectory: () -> Unit,
    onSwitchMode: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onGoogleSignIn: (Activity?) -> Unit,
    onDismissGoogleSetup: () -> Unit,
    onSubmit: () -> Unit,
    classroomImport: ClassroomImportUiState,
    onImportStart: () -> Unit,
    onImportCourseToggled: (String) -> Unit,
    onImportConfirmed: () -> Unit,
    onImportDismissed: () -> Unit,
    onExploreAsGuest: () -> Unit,
) {
    val isSignUp = state.mode == AuthMode.SIGN_UP
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    // Live activity for the Google account picker; read at click time so it
    // never goes stale across configuration changes.
    val activity = LocalActivity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxl))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.auth_app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        Spacer(Modifier.height(Spacing.xxl))

        Text(
            text = stringResource(
                if (isSignUp) R.string.auth_title_sign_up else R.string.auth_title_sign_in,
            ),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.lg))

        if (isSignUp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = state.firstName,
                    onValueChange = onFirstNameChange,
                    label = { Text(stringResource(R.string.auth_field_first_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.lastName,
                    onValueChange = onLastNameChange,
                    label = { Text(stringResource(R.string.auth_field_last_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(Spacing.md))
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.auth_field_email)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.md))

        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.auth_field_password)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            visualTransformation =
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (isSignUp) ImeAction.Next else ImeAction.Go,
            ),
            keyboardActions = if (isSignUp) {
                KeyboardActions.Default
            } else {
                KeyboardActions(onGo = { onSubmit() })
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.auth_hide_password_cd else R.string.auth_show_password_cd,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (isSignUp) {
            Spacer(Modifier.height(Spacing.md))

            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text(stringResource(R.string.auth_field_confirm_password)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation =
                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.lg))

            // Registry-backed dropdowns: program, year level, section and
            // campus are chosen from the database, never typed by hand.
            SchoolDetailsFields(
                directory = directory,
                programId = state.programId,
                yearLevel = state.yearLevel,
                sectionId = state.sectionId,
                campusId = state.campusId,
                onProgramChange = onSchoolProgramChange,
                onYearLevelChange = onSchoolYearLevelChange,
                onSectionChange = onSchoolSectionChange,
                onCampusChange = onSchoolCampusChange,
                onRetryDirectory = onRetryDirectory,
            )

            Spacer(Modifier.height(Spacing.lg))

            // The student's subjects: pick each course from the rolldown
            // (or type a code the catalog does not know yet — the server
            // creates that course at save time). These become the classes
            // the manual activity editor picks from, so at least one is
            // required.
            CourseCodesField(
                codes = state.courseCodes,
                input = state.courseCodeInput,
                inputError = state.courseCodeError,
                directory = directory,
                onInputChange = onCourseCodeInputChange,
                onAdd = onAddCourseCode,
                onRemove = onRemoveCourseCode,
                classroomImport = classroomImport,
                onImportStart = onImportStart,
                onImportCourseToggled = onImportCourseToggled,
                onImportConfirmed = onImportConfirmed,
                onImportDismissed = onImportDismissed,
            )
        }

        val errorText = errorText(state.error)
        if (errorText != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.confirmationRequired) {
            Spacer(Modifier.height(Spacing.md))
            ConfirmationNotice(onDismiss = onDismissConfirmation)
        }

        Spacer(Modifier.height(Spacing.lg))

        Button(
            onClick = onSubmit,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = stringResource(
                        if (isSignUp) R.string.auth_submit_sign_up else R.string.auth_submit_sign_in,
                    ),
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // Google Sign-In sits directly below the sign-in button. Supabase
        // creates the account on first sign-in (then asks for the name once),
        // so there is no separate "sign up with Google".
        OutlinedButton(
            onClick = { onGoogleSignIn(activity) },
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_google_logo),
                contentDescription = stringResource(R.string.auth_google_cd),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.auth_google_cta))
        }

        Spacer(Modifier.height(Spacing.md))

        TextButton(
            onClick = onSwitchMode,
            enabled = !state.isLoading,
            modifier = Modifier.padding(top = Spacing.xs),
        ) {
            Text(
                stringResource(
                    if (isSignUp) R.string.auth_switch_to_sign_in else R.string.auth_switch_to_sign_up,
                ),
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.auth_or_divider),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(Spacing.md))

        OutlinedButton(
            onClick = onExploreAsGuest,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(stringResource(R.string.auth_guest_cta))
        }
        Text(
            text = stringResource(R.string.auth_guest_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        if (state.showGoogleSetup) {
            GoogleSetupDialog(onDismiss = onDismissGoogleSetup)
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

/**
 * First Google sign-in: the account exists and a session is live, but the
 * user has not entered a name yet. One form collects first/last name (the
 * database derives the username from them), the school details (registry
 * dropdowns, same as email sign-up) and — for Google-only accounts — a
 * password, so email sign-in works alongside Google afterwards.
 */
@Composable
fun ProfileCompletionScreen(
    state: AuthUiState.CompletingProfile,
    directory: SchoolDirectoryState,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSchoolProgramChange: (String?) -> Unit,
    onSchoolYearLevelChange: (Int?) -> Unit,
    onSchoolSectionChange: (String?) -> Unit,
    onSchoolCampusChange: (String?) -> Unit,
    onCourseCodeInputChange: (String) -> Unit,
    onAddCourseCode: (String?) -> Unit,
    onRemoveCourseCode: (String) -> Unit,
    onRetryDirectory: () -> Unit,
    onSubmit: () -> Unit,
    classroomImport: ClassroomImportUiState,
    onImportStart: () -> Unit,
    onImportCourseToggled: (String) -> Unit,
    onImportConfirmed: () -> Unit,
    onImportDismissed: () -> Unit,
    onCancel: () -> Unit,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val needsPassword = !state.hasPasswordCredential

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxl))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xxl))

        Text(
            text = stringResource(R.string.auth_completion_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        state.email?.let { email ->
            Text(
                text = stringResource(R.string.auth_completion_signed_in_as, email),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = state.firstName,
                onValueChange = onFirstNameChange,
                label = { Text(stringResource(R.string.auth_field_first_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.lastName,
                onValueChange = onLastNameChange,
                label = { Text(stringResource(R.string.auth_field_last_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        SchoolDetailsFields(
            directory = directory,
            programId = state.programId,
            yearLevel = state.yearLevel,
            sectionId = state.sectionId,
            campusId = state.campusId,
            onProgramChange = onSchoolProgramChange,
            onYearLevelChange = onSchoolYearLevelChange,
            onSectionChange = onSchoolSectionChange,
            onCampusChange = onSchoolCampusChange,
            onRetryDirectory = onRetryDirectory,
        )

        Spacer(Modifier.height(Spacing.lg))

        // Same searchable course picker as the email sign-up form: Google
        // accounts pick their subjects here on first sign-in.
        CourseCodesField(
            codes = state.courseCodes,
            input = state.courseCodeInput,
            inputError = state.courseCodeError,
            directory = directory,
            onInputChange = onCourseCodeInputChange,
            onAdd = onAddCourseCode,
            onRemove = onRemoveCourseCode,
            classroomImport = classroomImport,
            onImportStart = onImportStart,
            onImportCourseToggled = onImportCourseToggled,
            onImportConfirmed = onImportConfirmed,
            onImportDismissed = onImportDismissed,
        )

        if (needsPassword) {
            Spacer(Modifier.height(Spacing.md))

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.auth_field_password)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation =
                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(
                                if (passwordVisible) R.string.auth_hide_password_cd else R.string.auth_show_password_cd,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.md))

            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text(stringResource(R.string.auth_field_confirm_password)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation =
                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.auth_completion_password_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
            )
        }

        val errorText = errorText(state.error)
        if (errorText != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Button(
            onClick = onSubmit,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.auth_completion_submit))
            }
        }

        TextButton(
            onClick = onCancel,
            enabled = !state.isLoading,
            modifier = Modifier.padding(top = Spacing.xs),
        ) {
            Text(stringResource(R.string.auth_completion_cancel))
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

/**
 * The four school dropdowns shared by the sign-up form, the Google
 * completion form and the Settings editor. Campus comes FIRST: once one is
 * chosen the Program dropdown lists only that campus offerings; before
 * that it lists the programs of all campuses (duplicates disambiguated by
 * the campus name). Values only ever come from the database registry —
 * while it loads the fields sit disabled, and a fetch failure offers an
 * inline retry instead of silently empty lists.
 */
@Composable
internal fun SchoolDetailsFields(
    directory: SchoolDirectoryState,
    programId: String?,
    yearLevel: Int?,
    sectionId: String?,
    campusId: String?,
    onProgramChange: (String?) -> Unit,
    onYearLevelChange: (Int?) -> Unit,
    onSectionChange: (String?) -> Unit,
    onCampusChange: (String?) -> Unit,
    onRetryDirectory: () -> Unit,
    showHeader: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showHeader) {
            Text(
                text = stringResource(R.string.auth_school_details_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
        }

        when (directory) {
            SchoolDirectoryState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.auth_directory_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.md),
                    )
                }
            }
            SchoolDirectoryState.Failed -> {
                Text(
                    text = stringResource(R.string.auth_directory_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(
                    onClick = onRetryDirectory,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 0.dp, end = 0.dp, top = 0.dp, bottom = 0.dp,
                    ),
                ) {
                    Text(stringResource(R.string.auth_directory_retry))
                }
            }
            is SchoolDirectoryState.Ready -> {
                val registry = directory.directory
                // Campus FIRST: picking one narrows the Program list below
                // to that campus offerings.
                SchoolDropdown(
                    label = stringResource(R.string.auth_field_campus),
                    options = registry.campuses,
                    selected = registry.campuses.firstOrNull { it.id == campusId },
                    optionLabel = { it.name },
                    onOptionSelected = { onCampusChange(it.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val campusPrograms = registry.programsForCampus(campusId)
                SchoolDropdown(
                    label = stringResource(R.string.auth_field_program),
                    options = campusPrograms,
                    selected = campusPrograms.firstOrNull { it.id == programId },
                    optionLabel = { registry.programLabel(it, campusId) },
                    onOptionSelected = { onProgramChange(it.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                ) {
                    SchoolDropdown(
                        label = stringResource(R.string.auth_field_year_level),
                        options = registry.yearLevels,
                        selected = registry.yearLevels.firstOrNull { it.level == yearLevel },
                        optionLabel = { it.label },
                        onOptionSelected = { onYearLevelChange(it.level) },
                        modifier = Modifier.weight(1f),
                    )
                    SchoolDropdown(
                        label = stringResource(R.string.auth_field_section),
                        options = registry.yearSections,
                        selected = registry.yearSections.firstOrNull { it.id == sectionId },
                        optionLabel = { it.name },
                        onOptionSelected = { onSectionChange(it.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * The student's course (subject) entry — shared by the email sign-up
 * form, the Google completion form and the Settings school sheet.
 *
 * Selected courses sit as removable chips; the picker below them is the
 * same rolldown mechanic the program question uses: tap the field, the
 * course list drops open, and typing searches it by code or title. A
 * code the catalog does not know yet is offered as an "Add … as a new
 * course" row — the server creates the course from the code at save
 * time, so the catalog keeps growing with real schedules.
 */
@Composable
internal fun CourseCodesField(
    codes: List<String>,
    input: String,
    inputError: Boolean,
    directory: SchoolDirectoryState,
    onInputChange: (String) -> Unit,
    onAdd: (String?) -> Unit,
    onRemove: (String) -> Unit,
    classroomImport: ClassroomImportUiState = ClassroomImportUiState.Idle,
    onImportStart: (() -> Unit)? = null,
    onImportCourseToggled: (String) -> Unit = {},
    onImportConfirmed: () -> Unit = {},
    onImportDismissed: () -> Unit = {},
) {
    val query = input.trim()
    val registry = (directory as? SchoolDirectoryState.Ready)?.directory
    val typedShape = registry?.normalizeCourseCode(query)
        ?: query.replace(Regex("\\s+"), " ").takeIf { looksLikeCourseCode(it) }
    // Live shape check: junk that cannot be a code at all hints inline,
    // before any add is attempted.
    val junk = query.isNotEmpty() && typedShape == null

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.auth_courses_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.auth_courses_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.md),
        )

        if (codes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(bottom = Spacing.md),
            ) {
                codes.forEach { code ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(code) },
                        label = { Text(code) },
                        trailingIcon = {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(
                                    R.string.auth_courses_remove_cd, code,
                                ),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }

        // The easy path: pull the subjects straight from Google Classroom.
        // Manual picking/typing below stays exactly as it was.
        if (onImportStart != null) {
            OutlinedButton(
                onClick = onImportStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.classroom_import_button))
            }
            Text(
                // The Google account is chosen AFTER the tap: the system
                // chooser lists every Google account on the device.
                text = stringResource(R.string.classroom_import_choose_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            // The picker sheet itself: a modal overlay, hidden while Idle.
            ClassroomImportSheet(
                state = classroomImport,
                onToggleCourse = onImportCourseToggled,
                onConfirm = onImportConfirmed,
                onDismiss = onImportDismissed,
            )
            Spacer(Modifier.height(Spacing.md))
        }

        CoursePickerDropdown(
            codes = codes,
            input = input,
            directory = directory,
            onInputChange = onInputChange,
            onAdd = onAdd,
        )

        if (junk || inputError) {
            Text(
                text = stringResource(R.string.auth_courses_bad_code),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** How many catalog rows the course menu renders before cutting off. */
private const val COURSE_MENU_LIMIT = 30

/**
 * The course rolldown: the same ExposedDropdownMenu mechanic as the
 * program / campus pickers, plus search — the field doubles as the
 * search box while the menu is open, and matches update live. The typed
 * text, when it is not a known code, is offered as a NEW course row at
 * the bottom of the menu; already-added courses carry a check mark.
 */
@Composable
private fun CoursePickerDropdown(
    codes: List<String>,
    input: String,
    directory: SchoolDirectoryState,
    onInputChange: (String) -> Unit,
    onAdd: (String?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val registry = (directory as? SchoolDirectoryState.Ready)?.directory
    val catalog = registry?.courses.orEmpty()
    val query = input.trim()
    val matches = catalog.filter { course ->
        query.isEmpty() ||
            course.code.contains(query, ignoreCase = true) ||
            course.title.contains(query, ignoreCase = true)
    }
    // The typed text as a candidate NEW code (cleaned; null = junk).
    val typedCode = registry?.normalizeCourseCode(query)
        ?: query.replace(Regex("\\s+"), " ").takeIf { looksLikeCourseCode(it) }
    val typedKnown = catalog.any { it.code.equals(query, ignoreCase = true) }
    val typedAlreadyAdded = typedCode != null &&
        codes.any { it.equals(typedCode, ignoreCase = true) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text(stringResource(R.string.auth_courses_field_label)) },
            placeholder = { Text(stringResource(R.string.auth_courses_search_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAdd(null) }),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            matches.take(COURSE_MENU_LIMIT).forEach { course ->
                val alreadyAdded = codes.any { it.equals(course.code, ignoreCase = true) }
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(course.code)
                            if (course.title.isNotBlank()) {
                                Text(
                                    text = course.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    trailingIcon = if (alreadyAdded) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onAdd(course.code)
                        expanded = false
                    },
                )
            }
            // The typed text, when it can be a code the catalog does not
            // know: offered as a NEW course — the community catalog grows
            // from exactly these rows.
            if (typedCode != null && !typedKnown && !typedAlreadyAdded) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.auth_courses_new, typedCode)) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        onAdd(typedCode)
                        expanded = false
                    },
                )
            }
            if (matches.isEmpty() && (typedCode == null || typedKnown || typedAlreadyAdded)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.auth_courses_no_match)) },
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}

/**
 * One exposed dropdown over registry options — searchable, the same
 * mechanic as the course picker: while the menu is open the field holds
 * the search text and the list filters live; closed it shows the
 * selection. The chosen row carries a check mark; a filter that empties
 * the list shows a "no match" row instead. Long program names scroll
 * horizontally in the single-line field rather than wrapping.
 */
@Composable
private fun <T> SchoolDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val needle = query.trim()
    val matches = options.filter { option ->
        needle.isEmpty() || optionLabel(option).contains(needle, ignoreCase = true)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        // Fresh search every time the menu opens: the query is a filter,
        // never data, so collapsing the menu (tap-outside, back, or the
        // anchor) always clears it.
        onExpandedChange = {
            expanded = it
            if (!it) query = ""
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            // Search box while open, the selection while closed — exactly
            // like the course picker, so every dropdown on the form feels
            // the same.
            value = if (expanded) query else (selected?.let(optionLabel) ?: ""),
            onValueChange = { query = it },
            label = { Text(label) },
            placeholder = {
                if (expanded && needle.isEmpty()) {
                    Text(stringResource(R.string.auth_registry_search_hint))
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = ""
            },
        ) {
            if (matches.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.auth_registry_no_match)) },
                    enabled = false,
                    onClick = {},
                )
            } else {
                matches.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        trailingIcon = if (option == selected) {
                            {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            onOptionSelected(option)
                            query = ""
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** Success-path notice after a sign-up that awaits email confirmation. */
@Composable
private fun ConfirmationNotice(onDismiss: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.auth_confirmation_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.auth_confirmation_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            TextButton(
                onClick = onDismiss,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 0.dp, end = 0.dp, top = 0.dp, bottom = 0.dp,
                ),
            ) {
                Text(stringResource(R.string.auth_confirmation_dismiss))
            }
        }
    }
}

/**
 * Shown when the Google button is tapped before GOOGLE_SERVER_CLIENT_ID has
 * been configured. Keeps the button honest instead of silently failing.
 */
@Composable
private fun GoogleSetupDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_google_setup_title)) },
        text = { Text(stringResource(R.string.auth_google_setup_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.auth_google_setup_dismiss))
            }
        },
    )
}

/** Resolves typed errors to display strings; null when there is nothing to show. */
@Composable
internal fun errorText(error: AuthError?): String? = when (error) {
    null -> null
    AuthError.Validation.EMAIL -> stringResource(R.string.auth_error_email)
    AuthError.Validation.NAME -> stringResource(R.string.auth_error_name)
    AuthError.Validation.SCHOOL -> stringResource(R.string.auth_error_school)
    AuthError.Validation.COURSES -> stringResource(R.string.auth_error_courses)
    AuthError.Validation.PASSWORD_LENGTH -> stringResource(R.string.auth_error_password_length)
    AuthError.Validation.PASSWORD_MISMATCH -> stringResource(R.string.auth_error_password_mismatch)
    AuthError.EmailTaken -> stringResource(R.string.auth_error_email_taken)
    is AuthError.Google -> googleErrorText(error)
    is AuthError.Remote -> error.message ?: stringResource(R.string.auth_error_generic)
}

/**
 * Device-side Google failures each carry a distinct, actionable message
 * (they have different fixes), plus the diagnostic detail when present.
 */
@Composable
private fun googleErrorText(error: AuthError.Google): String {
    val base = when (error.failure) {
        GoogleFailure.NO_ACCOUNT -> stringResource(R.string.auth_error_google_no_account)
        GoogleFailure.REJECTED -> stringResource(R.string.auth_error_google_rejected)
        GoogleFailure.GENERIC -> stringResource(R.string.auth_error_google)
    }
    val detail = error.detail?.takeIf { it.isNotBlank() } ?: return base
    return "$base ($detail)"
}


