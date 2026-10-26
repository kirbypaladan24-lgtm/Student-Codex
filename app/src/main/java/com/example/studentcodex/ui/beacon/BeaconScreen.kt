package com.example.studentcodex.ui.beacon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studentcodex.R
import com.example.studentcodex.data.auth.SessionInfo
import com.example.studentcodex.data.repo.MySection
import com.example.studentcodex.di.AppContainer
import com.example.studentcodex.domain.SchoolZone
import com.example.studentcodex.domain.model.Activity
import com.example.studentcodex.domain.model.ActivitySource
import com.example.studentcodex.domain.model.ActivityType
import com.example.studentcodex.domain.model.VerificationStatus
import com.example.studentcodex.ui.components.ActivityListSkeleton
import com.example.studentcodex.ui.components.ActivityTypeIcon
import com.example.studentcodex.ui.components.ClassroomBadge
import com.example.studentcodex.ui.components.ManualBadge
import com.example.studentcodex.ui.components.EmptyState
import com.example.studentcodex.ui.components.ErrorState
import com.example.studentcodex.ui.components.dateHeader
import com.example.studentcodex.ui.components.dueLabel
import com.example.studentcodex.ui.components.timeLabel
import com.example.studentcodex.ui.theme.Spacing
import java.time.Instant

@Composable
fun BeaconScreen(container: AppContainer, session: SessionInfo) {
    val viewModel: BeaconViewModel = viewModel { BeaconViewModel(container.activityRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorSheet by viewModel.editorSheet.collectAsStateWithLifecycle()

    // Cross-tab freshness: the saved-state back stack RESTORES this tab's
    // ViewModel when the student comes back (restoreState = true), so a
    // Classroom sync just run under Settings - or a classmate's newly
    // published work - would otherwise stay invisible until a process
    // restart. Every resume silently reloads the list (no skeleton flash;
    // a failed silent refresh keeps whatever is on screen).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Text(
                text = stringResource(R.string.beacon_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.beacon_subtitle_live),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The Phase 4/5 write entry point: recording a manual activity.
        // Guests see the signed-out notice instead (writes need an account).
        if (session.isGuest) {
            Text(
                text = stringResource(R.string.beacon_guest_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        } else {
            Button(
                onClick = viewModel::openEditor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.beacon_add_activity))
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        when (val s = state) {
            BeaconUiState.Loading -> ActivityListSkeleton(modifier = Modifier.fillMaxSize())
            is BeaconUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::retry)
            is BeaconUiState.Content -> BeaconContent(
                state = s,
                canEdit = !session.isGuest,
                onFilterSelected = viewModel::setFilter,
                onOpenEditor = viewModel::openEditor,
                onEditActivity = viewModel::openEditor,
                onDeleteActivity = viewModel::deleteActivity,
            )
        }
    }

    editorSheet?.let { sheet ->
        ActivityEditorSheet(
            state = sheet,
            onDismiss = viewModel::closeEditor,
            onTitleChange = viewModel::onEditorTitleChange,
            onDescriptionChange = viewModel::onEditorDescriptionChange,
            onTypeChange = viewModel::onEditorTypeChange,
            onDueDateChange = viewModel::onEditorDueDateChange,
            onSectionChange = viewModel::onEditorSectionChange,
            onSave = viewModel::saveEditor,
        )
    }

}

@Composable
private fun BeaconContent(
    state: BeaconUiState.Content,
    canEdit: Boolean,
    onFilterSelected: (BeaconFilter) -> Unit,
    onOpenEditor: () -> Unit,
    onEditActivity: (Activity) -> Unit,
    onDeleteActivity: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(BeaconFilter.entries.size) { index ->
            val filter = BeaconFilter.entries[index]
            FilterChip(
                selected = state.selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filterLabel(filter)) },
            )
        }
    }

    if (state.isEmptyOverall) {
        EmptyState(
            title = stringResource(R.string.beacon_empty_title),
            message = stringResource(R.string.beacon_empty_message_live),
            icon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        )
        return
    }

    if (state.filtered.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.beacon_filter_empty_title),
            message = stringResource(R.string.beacon_filter_empty_message),
        )
        return
    }

    // Group by school-calendar date (stable keys; null = no deadline). Order preserved.
    val zone = SchoolZone
    val grouped: List<Pair<java.time.LocalDate?, List<Activity>>> =
        state.filtered.groupBy { it.dueAt?.atZone(zone)?.toLocalDate() }.toList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xl),
    ) {
        grouped.forEach { (date, activities) ->
            item(key = "header-${date ?: "none"}") {
                Text(
                    text = dateHeader(activities.firstOrNull()?.dueAt),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                )
            }
            items(activities.size, key = { activities[it].id }) { index ->
                val activity = activities[index]
                val last = index == activities.lastIndex
                BeaconActivityRow(
                    activity = activity,
                    canEdit = canEdit,
                    showDivider = !last,
                    onEdit = { onEditActivity(activity) },
                    onDelete = { onDeleteActivity(activity.id) },
                )
            }
        }
    }
}

@Composable
private fun filterLabel(filter: BeaconFilter): String = when (filter) {
    BeaconFilter.ALL -> stringResource(R.string.filter_all)
    BeaconFilter.CLASSROOM -> stringResource(R.string.filter_classroom)
    BeaconFilter.ASSIGNMENTS -> stringResource(R.string.filter_assignments)
    BeaconFilter.EXAMS -> stringResource(R.string.filter_exams)
    BeaconFilter.QUIZZES -> stringResource(R.string.filter_quizzes)
    BeaconFilter.PROJECTS -> stringResource(R.string.filter_projects)
    BeaconFilter.ACTIVITY -> stringResource(R.string.filter_activity)
}

/**
 * Flat, dense activity row. Anatomy: monochrome type icon; title; a
 * provenance chip (Classroom green / manual neutral); one quiet metadata
 * line carrying trust, effort, and scope as plain text; trailing due
 * emphasis. The user's OWN manual rows carry edit/delete affordances
 * (imported/classroom rows are read-only evidence).
 */
@Composable
private fun BeaconActivityRow(
    activity: Activity,
    canEdit: Boolean,
    showDivider: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editable = canEdit && activity.source != ActivitySource.GOOGLE_CLASSROOM && activity.isPersonal
    var confirmDelete by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityTypeIcon(type = activity.type)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Provenance chip on EVERY school work: Classroom imports wear
                // the green Google chip; everything a person entered by hand
                // (personal entry, class report, teacher entry) wears the
                // neutral in-app chip. One glance answers "imported or
                // manually added?" without reading the metadata line.
                if (activity.source == ActivitySource.GOOGLE_CLASSROOM) {
                    ClassroomBadge(modifier = Modifier.padding(top = Spacing.xs))
                } else {
                    ManualBadge(modifier = Modifier.padding(top = Spacing.xs))
                }
                val metaLine = activityMetadata(activity)
                if (metaLine.isNotEmpty()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
            DueBlock(activity.dueAt)
            if (editable) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.beacon_edit_cd, activity.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.beacon_delete_cd, activity.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.beacon_delete_confirm_title)) },
            text = { Text(stringResource(R.string.beacon_delete_confirm_message, activity.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.beacon_delete_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.beacon_delete_confirm_no)) }
            },
        )
    }
}

/** Plain-text trust metadata: "CC4 · teacher-confirmed · 45 min". */
@Composable
private fun activityMetadata(activity: Activity): String = buildList {
    // Course code first: which class the row belongs to (imports carry the
    // matched Student Codex course, e.g. MEETING).
    activity.course?.let { add(it.code) }
    // Provenance lives in the badge chips now (Classroom green or manual
    // neutral); this line keeps the trust/context half only.
    add(verificationLabel(activity.verification))
    if (activity.isPersonal) add(stringResource(R.string.label_personal))
    activity.effortMinutes?.let { add(stringResource(R.string.effort_minutes, it)) }
}.joinToString(" · ")

@Composable
private fun verificationLabel(verification: VerificationStatus): String = when (verification) {
    VerificationStatus.IMPORTED -> stringResource(R.string.verification_imported)
    VerificationStatus.TEACHER_CONFIRMED -> stringResource(R.string.verification_teacher_confirmed)
    VerificationStatus.STUDENT_REPORTED -> stringResource(R.string.verification_student_reported)
    VerificationStatus.NEEDS_REVIEW -> stringResource(R.string.verification_needs_review)
}

/** Trailing due emphasis: urgency label + exact local time. */
@Composable
private fun DueBlock(dueAt: Instant?) {
    if (dueAt == null) {
        Text(
            text = stringResource(R.string.no_deadline),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val overdue = !dueAt.isAfter(Instant.now())
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(min = 64.dp),
        ) {
            Text(
                text = dueLabel(dueAt),
                style = MaterialTheme.typography.labelMedium,
                color = if (overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
            )
            Text(
                text = timeLabel(dueAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Manual activity editor sheet (Phase 4)
// ---------------------------------------------------------------------------

/**
 * Creates or edits a manual activity: title, optional description, type,
 * optional due DATE (stored as local end-of-day) and the class picker -
 * every NEW activity is shared with its section (teacher included).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityEditorSheet(
    state: ActivityEditorUiState,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTypeChange: (ActivityType) -> Unit,
    onDueDateChange: (String) -> Unit,
    onSectionChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

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
                text = stringResource(
                    if (state.editingId == null) R.string.beacon_editor_new_title
                    else R.string.beacon_editor_edit_title,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.md))

            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.beacon_editor_title_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.beacon_editor_desc_label)) },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))

            // Type picker: chips, not a dropdown — all six options fit.
            Text(
                text = stringResource(R.string.beacon_editor_type_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(ActivityType.entries.size) { index ->
                    val type = ActivityType.entries[index]
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(typeLabel(type)) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = state.dueDateText,
                onValueChange = onDueDateChange,
                readOnly = true,
                label = { Text(stringResource(R.string.beacon_editor_due_label)) },
                placeholder = { Text(stringResource(R.string.beacon_editor_no_due)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.beacon_editor_pick_date_cd),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Audience: every NEW activity is a class activity - it
            // publishes straight to the cohort calendar for the teacher
            // and classmates. No personal-only mode, no approval flow.
            if (state.editingId == null) {
                Text(
                    text = stringResource(R.string.beacon_editor_class_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.sectionsLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = Spacing.xs),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.beacon_editor_courses_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.md),
                        )
                    }
                } else if (state.sections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.beacon_editor_no_sections),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Which course? — the same rolldown mechanic the program
                    // question uses, over the student's enrolled courses.
                    CourseSectionDropdown(
                        sections = state.sections,
                        selectedSectionId = state.selectedSectionId,
                        onSectionSelected = onSectionChange,
                    )
                    Text(
                        text = stringResource(R.string.beacon_editor_share_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
            } else if (!state.isPersonal) {
                // Editing an existing class report: scope is fixed server-side.
                Text(
                    text = state.originCourseCode?.let {
                        stringResource(R.string.beacon_editor_origin_course, it)
                    } ?: stringResource(R.string.beacon_editor_origin_class),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )
            }

            if (state.error != null) {
                Text(
                    text = editorErrorLabel(state.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = onSave,
                enabled = !state.saving && state.title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm, bottom = Spacing.lg),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        stringResource(
                            if (state.editingId == null) R.string.beacon_editor_save_new
                            else R.string.beacon_editor_save_edit,
                        ),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dueDateText.takeIf { it.isNotBlank() }
                ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                ?.atStartOfDay(SchoolZone)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val localDate = Instant.ofEpochMilli(millis)
                            .atZone(SchoolZone)
                            .toLocalDate()
                        onDueDateChange(localDate.toString())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.beacon_date_pick_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.beacon_date_pick_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * The "which course?" rolldown: the same ExposedDropdownMenu mechanic the
 * sign-up form's program question uses, plus search over the student's
 * enrolled courses. While the menu is open the field shows (and edits)
 * the search text; picking a row selects that course section and closes
 * the menu. The currently selected course carries a check mark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseSectionDropdown(
    sections: List<MySection>,
    selectedSectionId: String?,
    onSectionSelected: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val selected = sections.firstOrNull { it.sectionId == selectedSectionId }
    val needle = query.trim()
    val matches = sections.filter { section ->
        needle.isEmpty() ||
            (section.courseCode ?: "").contains(needle, ignoreCase = true) ||
            (section.courseTitle ?: "").contains(needle, ignoreCase = true) ||
            section.sectionName.contains(needle, ignoreCase = true)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = if (expanded) query else (selected?.let(::courseOptionLabel) ?: ""),
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.beacon_editor_course_field_label)) },
            placeholder = { Text(stringResource(R.string.auth_courses_search_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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
                    text = { Text(stringResource(R.string.auth_courses_no_match)) },
                    enabled = false,
                    onClick = {},
                )
            } else {
                matches.forEach { section ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(courseOptionLabel(section))
                                section.courseTitle
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                            }
                        },
                        trailingIcon = if (section.sectionId == selectedSectionId) {
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
                            onSectionSelected(section.sectionId)
                            query = ""
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** Dropdown row / closed-field label: "CS 101 · BSCS 2-A". */
private fun courseOptionLabel(section: MySection): String =
    listOfNotNull(
        section.courseCode,
        section.sectionName.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

@Composable
private fun typeLabel(type: ActivityType): String = when (type) {
    ActivityType.ASSIGNMENT -> stringResource(R.string.type_assignment)
    ActivityType.QUIZ -> stringResource(R.string.type_quiz)
    ActivityType.EXAM -> stringResource(R.string.type_exam)
    ActivityType.PROJECT -> stringResource(R.string.type_project)
    ActivityType.ANNOUNCEMENT -> stringResource(R.string.type_announcement)
    ActivityType.OTHER -> stringResource(R.string.type_other)
}

@Composable
private fun editorErrorLabel(error: String): String = when (error) {
    "TITLE_BLANK" -> stringResource(R.string.beacon_error_title_blank)
    "SECTION_REQUIRED" -> stringResource(R.string.beacon_error_section_required)
    else -> error
}
