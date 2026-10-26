package com.example.studentcodex.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.studentcodex.R
import com.example.studentcodex.ui.theme.Spacing

/**
 * Google Classroom provenance mark for activity rows.
 *
 * Imported rows are read-only evidence from the real outside service, so
 * they carry this compact brand-green chip beside their titles: one glance
 * answers "did the Classroom sync actually work?" - for the student who
 * synced AND for classmates seeing the same section rows. Deliberately
 * brighter than the app's library green so the chip reads as "this came
 * from Google", never as an in-app action or selection.
 */
@Composable
fun ClassroomBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = ClassroomBadgeGreen,
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.School,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.classroom_badge_label),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** Google Classroom's brand green - solid fill with white content keeps
 * the chip readable on the warm-paper light scheme and the dark scheme. */
private val ClassroomBadgeGreen = Color(0xFF0F9D58)

/**
 * In-app provenance mark for hand-entered activities.
 *
 * The counterpart of [ClassroomBadge]: every school work a person added by
 * hand in Student Codex - a personal planner entry, a student's class
 * report, a teacher's entry - wears this quiet neutral chip beside its
 * title. Where the Classroom chip says "this came from Google", this one
 * answers "a human entered this in the app", in theme container colors so
 * it always reads as part of the interface, never as an action.
 */
@Composable
fun ManualBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.manual_badge_label),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
