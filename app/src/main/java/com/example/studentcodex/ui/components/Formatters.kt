package com.example.studentcodex.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.studentcodex.R
import com.example.studentcodex.domain.SchoolZone
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Deadline display helpers. Deadlines are timezone-aware: everything renders
 * in the university's zone (Asia/Manila — see SchoolZone) so an 11:59 PM
 * deadline never looks ambiguous — and never drifts a day when the device
 * itself sits in another timezone.
 *
 * Locale is read from [LocalConfiguration] (observable state) so date/time
 * formats follow system locale changes. Note: LocalLocale was removed from
 * this Compose version, so the observable source for the platform locale is
 * the Configuration's LocaleList. Date math uses only java.time APIs
 * available since API 26 (our minSdk).
 */

@Composable
fun dueLabel(dueAt: Instant): String {
    val zone = SchoolZone
    val locale: Locale = LocalConfiguration.current.locales[0]
    val now = Instant.now()
    if (!dueAt.isAfter(now)) return stringResource(R.string.due_overdue)
    val hours = ChronoUnit.HOURS.between(now, dueAt)
    val days = ChronoUnit.DAYS.between(now.atZone(zone), dueAt.atZone(zone))
    return when {
        hours < 1 -> stringResource(R.string.due_within_hour)
        days < 1L -> pluralStringResource(R.plurals.due_in_hours, hours.toInt(), hours)
        days == 1L -> stringResource(R.string.due_tomorrow)
        days <= 7L -> pluralStringResource(R.plurals.due_in_days, days.toInt(), days)
        // Far deadlines carry the year too - same exactness as the list
        // headers (below), so a due date is never year-ambiguous.
        else -> DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale)
            .format(dueAt.atZone(zone))
    }
}

@Composable
fun dateHeader(instant: Instant?): String {
    if (instant == null) return stringResource(R.string.group_no_deadline)
    val zone = SchoolZone
    val locale: Locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now(zone)
    val date = instant.atZone(zone).toLocalDate()
    return when (date) {
        today -> stringResource(R.string.group_today)
        today.plusDays(1) -> stringResource(R.string.group_tomorrow)
        // Exact year on every grouped header: school works span terms -
        // and around year boundaries - so "Wednesday, Oct 29" alone is
        // ambiguous; "Wednesday, Oct 29, 2025" never is.
        else -> DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", locale).format(date)
    }
}

@Composable
fun timeLabel(instant: Instant): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern("HH:mm", locale)
        .format(instant.atZone(SchoolZone))
}
