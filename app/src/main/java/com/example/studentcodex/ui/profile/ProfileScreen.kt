package com.example.studentcodex.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studentcodex.R
import com.example.studentcodex.data.auth.SessionInfo
import com.example.studentcodex.di.AppContainer
import com.example.studentcodex.ui.components.AuthorMark
import com.example.studentcodex.ui.components.ErrorState
import com.example.studentcodex.ui.components.ProfileSkeleton
import com.example.studentcodex.ui.theme.Spacing

@Composable
fun ProfileScreen(
    container: AppContainer,
    session: SessionInfo,
    onBack: () -> Unit,
) {
    val viewModel: ProfileViewModel = viewModel {
        ProfileViewModel(profileRepository = container.profileRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        ProfileUiState.Loading -> ProfileSkeleton(modifier = Modifier.fillMaxSize())
        is ProfileUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::retry)
        is ProfileUiState.Content -> ProfileContent(
            state = s,
            session = session,
            onBack = onBack,
        )
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState.Content,
    session: SessionInfo,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        // Pushed page: a back button in the header returns to Settings
        // (the system back gesture does the same).
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.profile_back_cd),
                )
            }
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Spacer(Modifier.height(Spacing.md))

        // Flat header: mark, name, affiliation. Hierarchy from typography and
        // alignment - no card wrapper needed for a single identity block.
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuthorMark(
                isAnonymous = false,
                authorName = state.profile.displayName,
                markSize = 56.dp,
            )
            Spacer(Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.profile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${state.profile.university} · ${state.profile.program} · ${state.profile.yearLevel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.isGuest) {
                    Text(
                        text = stringResource(R.string.profile_guest_banner),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else {
                    // The account's real name (from sign-up / Google
                    // completion) with the email as the small subtitle; the
                    // demo profile's display name stays in the big headline.
                    session.displayName?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    session.email?.let { email ->
                        Text(
                            text = stringResource(R.string.profile_signed_in_as, email),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Every action moved to the Settings page (password, school
        // details, Google Classroom, notifications, sign-out): this page
        // is the identity itself.
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = stringResource(R.string.profile_version_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxl))
    }
}
