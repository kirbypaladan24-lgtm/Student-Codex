package com.example.studentcodex.ui.community

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Image as GalleryIcon
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studentcodex.R
import com.example.studentcodex.data.auth.SessionInfo
import com.example.studentcodex.di.AppContainer
import com.example.studentcodex.data.repo.CommunityRepository
import com.example.studentcodex.data.repo.ContentReportRepository
import com.example.studentcodex.domain.SchoolZone
import com.example.studentcodex.domain.model.Comment
import com.example.studentcodex.domain.model.CommunityPost
import com.example.studentcodex.ui.components.AuthorMark
import com.example.studentcodex.ui.components.EmptyState
import com.example.studentcodex.ui.components.ErrorState
import com.example.studentcodex.ui.components.PostListSkeleton
import com.example.studentcodex.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CommunityScreen(container: AppContainer, session: SessionInfo) {
    val viewModel: CommunityViewModel = viewModel {
        // The report entry point is only wired for signed-in users (guests
        // get an honest error from the dialog instead).
        CommunityViewModel(
            communityRepository = container.communityRepository,
            reportRepository = container.communityRepository as? ContentReportRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val commentsSheet by viewModel.commentsSheet.collectAsStateWithLifecycle()
    val composerSheet by viewModel.composerSheet.collectAsStateWithLifecycle()
    val reportSheet by viewModel.reportSheet.collectAsStateWithLifecycle()

    // Post images resolve through the repository: the composer's freshly
    // picked photo is a local content Uri, but SAVED posts carry bucket
    // storage paths ({uid}/{postId}/{file}) from the private
    // community-images bucket — those need an authenticated download.
    val imageLoader = remember { PostImageLoader(container.communityRepository) }

    // The composer posts as the signed-in user; guests post as "You".
    val youLabel = stringResource(R.string.community_author_you)
    val authorName = session.displayName ?: youLabel

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Text(
                text = stringResource(R.string.community_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(
                    if (session.isGuest) R.string.community_guest_notice
                    else R.string.community_subtitle_live,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            // The create-post bar: tapping it opens the composer sheet. For
            // guests it stays visible but the composer explains sign-in is
            // needed (no anonymous writes: ownership is server-enforced).
            ComposerBar(
                authorName = authorName,
                onOpen = { viewModel.openComposer(authorName, session.isGuest) },
            )
        }

        when (val s = state) {
            CommunityUiState.Loading -> PostListSkeleton(modifier = Modifier.fillMaxSize())
            is CommunityUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::retry)
            is CommunityUiState.Content -> {
                if (s.posts.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.community_empty_title),
                        message = stringResource(R.string.community_empty_message),
                    )
                } else {
                    val listState = rememberLazyListState()
                    // A newly created post lands at the TOP of the feed:
                    // snap the list to it. Without this, LazyList keeps its
                    // old anchor item on the prepend and the new card rides
                    // partially above the viewport, clipping its header.
                    LaunchedEffect(s.posts.firstOrNull()?.id) {
                        listState.scrollToItem(0)
                    }
                    PostList(
                        posts = s.posts,
                        listState = listState,
                        canInteract = !session.isGuest,
                        onToggleLike = viewModel::toggleLike,
                        onOpenComments = viewModel::openComments,
                        onReportPost = viewModel::openReportSheet,
                        imageLoader = imageLoader,
                    )
                }
            }
        }
    }

    composerSheet?.let { sheet ->
        ComposerSheet(
            state = sheet,
            isGuest = session.isGuest,
            imageLoader = imageLoader,
            onDismiss = viewModel::closeComposer,
            onDraftChange = viewModel::onComposerDraftChange,
            onAnonymousChange = viewModel::onComposerAnonymousChange,
            onImagePicked = viewModel::onComposerImagePicked,
            onRemoveImage = viewModel::removeComposerImage,
            onSubmit = viewModel::submitPost,
        )
    }

    commentsSheet?.let { sheet ->
        CommentsSheet(
            state = sheet,
            canInteract = !session.isGuest,
            onDismiss = viewModel::closeComments,
            onRetry = viewModel::retryComments,
            onDraftChange = viewModel::onDraftChange,
            onSend = viewModel::addComment,
        )
    }

    reportSheet?.let { sheet ->
        ReportPostSheet(
            state = sheet,
            onDismiss = viewModel::closeReportSheet,
            onReasonChange = viewModel::onReportReasonChange,
            onDetailsChange = viewModel::onReportDetailsChange,
            onSubmit = viewModel::submitReport,
        )
    }
}

/**
 * The "What's on your mind?" bar at the top of the feed: the user's mark,
 * the prompt text, and a green photo icon, in a white pill with a light
 * border. Tapping anywhere on it opens the create-post sheet - the photo
 * icon hints at what the sheet can attach.
 */
@Composable
private fun ComposerBar(
    authorName: String,
    onOpen: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AuthorMark(
                isAnonymous = false,
                authorName = authorName,
                markSize = 36.dp,
            )
            Text(
                text = stringResource(R.string.community_composer_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Rounded.GalleryIcon,
                contentDescription = stringResource(R.string.community_composer_photo_cd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/**
 * The create-post sheet: who is posting, the text field, the
 * post-as-anonymous switch, an optional photo picked from the gallery, and
 * the Post action. Text, a photo, or both are required; posting prepends
 * the result to the feed and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerSheet(
    state: ComposerUiState,
    isGuest: Boolean,
    imageLoader: PostImageLoader,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit,
    onAnonymousChange: (Boolean) -> Unit,
    onImagePicked: (String?) -> Unit,
    onRemoveImage: () -> Unit,
    onSubmit: () -> Unit,
) {
    // The system photo picker needs no storage permission and hands back a
    // content Uri this sheet can read for the life of the session.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImagePicked(uri.toString())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .imePadding(),
        ) {
            Text(
                text = stringResource(R.string.community_composer_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.md))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AuthorMark(
                    isAnonymous = state.isAnonymous,
                    authorName = state.authorName.takeIf { !state.isAnonymous },
                    markSize = 32.dp,
                )
                Column {
                    Text(
                        text = if (state.isAnonymous) {
                            stringResource(R.string.anonymous_author)
                        } else {
                            state.authorName
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.isAnonymous) {
                        Text(
                            text = stringResource(R.string.community_composer_anon_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(R.string.community_composer_input_hint)) },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            // Post-as-anonymous switch: the identity is chosen at creation
            // and immutable afterwards (a post can never be de-anonymized,
            // by design).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = Spacing.xs),
            ) {
                Checkbox(
                    checked = state.isAnonymous,
                    onCheckedChange = { if (!state.isPosting) onAnonymousChange(it) },
                    enabled = !state.isPosting,
                )
                Text(
                    text = stringResource(R.string.community_composer_post_anonymous),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.imageUri != null) {
                Spacer(Modifier.height(Spacing.sm))
                PostImage(
                    imageUri = state.imageUri,
                    onRemove = onRemoveImage,
                    imageLoader = imageLoader,
                )
            }

            Spacer(Modifier.height(Spacing.md))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                IconButton(
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !state.isPosting && !isGuest,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GalleryIcon,
                        contentDescription = stringResource(R.string.community_composer_photo_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Button(
                    onClick = onSubmit,
                    enabled = !state.isPosting && !isGuest &&
                        (state.draft.isNotBlank() || state.imageUri != null),
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.community_composer_post))
                    }
                }
            }

            if (state.error != null) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            Text(
                text = stringResource(
                    if (isGuest) R.string.community_guest_note else R.string.community_composer_demo_note,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.lg),
            )
        }
    }
}

@Composable
private fun PostList(
    posts: List<CommunityPost>,
    listState: LazyListState,
    canInteract: Boolean,
    onToggleLike: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onReportPost: (String) -> Unit,
    imageLoader: PostImageLoader,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(posts.size, key = { posts[it].id }) { index ->
            PostCard(
                post = posts[index],
                canInteract = canInteract,
                onToggleLike = onToggleLike,
                onOpenComments = onOpenComments,
                onReportPost = onReportPost,
                imageLoader = imageLoader,
            )
        }
    }
}

/**
 * A post is a meaningful independent unit, so cards are the right container
 * here - unlike Home/Beacon rows, which are flat. The card surface is the
 * one elevation step above the canvas with no shadow or tonal overlay.
 * Every card carries a long-press-free report affordance (flag icon) so
 * moderation is always reachable.
 */
@Composable
private fun PostCard(
    post: CommunityPost,
    canInteract: Boolean,
    onToggleLike: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onReportPost: (String) -> Unit,
    imageLoader: PostImageLoader,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuthorMark(
                    isAnonymous = post.isAnonymous,
                    authorName = post.authorName.takeIf { !post.isAnonymous },
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (post.isAnonymous) {
                            stringResource(R.string.anonymous_author)
                        } else {
                            post.authorName
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = postTimestamp(post.postedAt.toEpochMilli()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canInteract) {
                    IconButton(
                        onClick = { onReportPost(post.id) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Flag,
                            contentDescription = stringResource(R.string.community_report_post_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                val likeLabel = if (post.isLikedByMe) {
                    stringResource(R.string.like_remove_cd, post.likeCount)
                } else {
                    stringResource(R.string.like_add_cd, post.likeCount)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { contentDescription = likeLabel },
                ) {
                    IconButton(
                        onClick = { onToggleLike(post.id) },
                        enabled = canInteract,
                    ) {
                        Icon(
                            imageVector = if (post.isLikedByMe) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = if (post.isLikedByMe) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = post.likeCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (post.text.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            post.imageUri?.let { uri ->
                Spacer(Modifier.height(Spacing.sm))
                PostImage(imageUri = uri, imageLoader = imageLoader)
            }

            Spacer(Modifier.height(Spacing.sm))
            // The comment count is the thread's doorway: tapping it opens
            // the comments sheet. Clipped so the ripple respects the shape.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onOpenComments(post.id) }
                    .padding(horizontal = Spacing.xs, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = pluralStringResource(R.plurals.comment_count, post.commentCount, post.commentCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One attached photo. In the composer it is the freshly picked local image
 * (a content Uri) with a remove button; in the feed it is the saved post's
 * storage-path image, downloaded from the private bucket through
 * [imageLoader]. Decoding downsamples so a full-size camera photo never
 * lands in memory unbounded. While a feed image downloads, a slim
 * placeholder holds its slot; an undecodable image renders nothing (the
 * card stays text-only, exactly the old behavior).
 */
@Composable
private fun PostImage(
    imageUri: String,
    imageLoader: PostImageLoader,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(imageUri) { mutableStateOf(true) }
    LaunchedEffect(imageUri) {
        bitmap = imageLoader.load(context, imageUri)
        loading = false
    }
    val resolved = bitmap
    if (!loading && resolved == null) return
    Box(modifier = modifier.fillMaxWidth()) {
        if (resolved != null) {
            Image(
                bitmap = resolved,
                contentDescription = stringResource(R.string.community_post_image_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
        } else {
            // Download placeholder: keeps the card from jumping when the
            // image arrives; carries no semantics (the real image will).
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        if (onRemove != null) {
            FilledIconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.community_composer_remove_photo_cd),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Decodes a picked photo with rough downsampling; null when unreadable. */
private fun decodeImage(context: Context, imageUri: String): ImageBitmap? = runCatching {
    val uri = Uri.parse(imageUri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= 800 || bounds.outHeight / (sampleSize * 2) >= 800) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }?.asImageBitmap()
}.getOrNull()

/** Decodes downloaded bucket bytes with the same rough downsampling. */
private fun decodeImageBytes(bytes: ByteArray): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= 800 || bounds.outHeight / (sampleSize * 2) >= 800) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()

/**
 * Resolves post image references into decoded [ImageBitmap]s. Local
 * references (content:// — the composer's freshly picked photo) decode
 * through the ContentResolver; bare storage paths (saved posts: the bytes
 * live in the PRIVATE community-images bucket, only the path in the row)
 * download once through the repository's authenticated storage access.
 * Decoded results are LRU-cached so scrolling the feed re-binds bitmaps
 * instead of re-downloading.
 */
private class PostImageLoader(private val repository: CommunityRepository) {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
            size > MAX_CACHED
    }

    suspend fun load(context: Context, reference: String): ImageBitmap? = mutex.withLock {
        cache[reference]?.let { return it }
        val isLocalReference = Uri.parse(reference).scheme != null
        val bitmap = withContext(Dispatchers.IO) {
            if (isLocalReference) decodeImage(context, reference)
            else repository.downloadPostImage(reference)?.let(::decodeImageBytes)
        }
        if (bitmap != null) cache[reference] = bitmap
        bitmap
    }

    private companion object {
        const val MAX_CACHED = 12
    }
}

/**
 * Comments for one post: the post text as context, the thread below it,
 * and a reply box. Bottom sheet so the feed stays in place behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(
    state: CommentsUiState,
    canInteract: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .imePadding(),
        ) {
            Text(
                text = stringResource(R.string.comments_title),
                style = MaterialTheme.typography.titleMedium,
            )

            when (state) {
                is CommentsUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is CommentsUiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = onRetry,
                )

                is CommentsUiState.Content -> {
                    Text(
                        text = state.post.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                    )

                    if (state.comments.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.comments_empty_title),
                            message = stringResource(R.string.comments_empty_message),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                            contentPadding = PaddingValues(bottom = Spacing.md),
                        ) {
                            items(state.comments.size, key = { state.comments[it].id }) { index ->
                                CommentRow(comment = state.comments[index])
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                    ) {
                        OutlinedTextField(
                            value = state.draft,
                            onValueChange = onDraftChange,
                            placeholder = { Text(stringResource(R.string.comments_input_hint)) },
                            singleLine = false,
                            maxLines = 4,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = onSend,
                            enabled = canInteract && state.draft.isNotBlank() && !state.isSending,
                        ) {
                            if (state.isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = stringResource(R.string.comments_send_cd),
                                    tint = if (state.draft.isNotBlank()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }

                    if (state.sendError != null) {
                        Text(
                            text = state.sendError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

/** One reply: author mark, name and time, then the text. Mirrors PostCard. */
@Composable
private fun CommentRow(comment: Comment) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AuthorMark(
            isAnonymous = comment.isAnonymous,
            authorName = comment.authorName.takeIf { !comment.isAnonymous },
            markSize = 24.dp,
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (comment.isAnonymous) {
                        stringResource(R.string.anonymous_author)
                    } else {
                        comment.authorName
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = postTimestamp(comment.postedAt.toEpochMilli()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Report post sheet (Phase 9 moderation entry point)
// ---------------------------------------------------------------------------

/**
 * Files a content report for one post: reason chips (spam / harassment /
 * off-topic / inappropriate / other) + optional details. Reports go to the
 * moderation queue (content_reports) for moderators to triage — nothing is
 * removed automatically.
 */
@Composable
private fun ReportPostSheet(
    state: ReportPostUiState,
    onDismiss: () -> Unit,
    onReasonChange: (String) -> Unit,
    onDetailsChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.community_report_title)) },
        text = {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ReportReasonChip(
                        label = stringResource(R.string.community_report_reason_spam),
                        wire = "spam",
                        selected = state.reason,
                        onSelect = onReasonChange,
                    )
                    ReportReasonChip(
                        label = stringResource(R.string.community_report_reason_harassment),
                        wire = "harassment",
                        selected = state.reason,
                        onSelect = onReasonChange,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                ) {
                    ReportReasonChip(
                        label = stringResource(R.string.community_report_reason_off_topic),
                        wire = "off-topic",
                        selected = state.reason,
                        onSelect = onReasonChange,
                    )
                    ReportReasonChip(
                        label = stringResource(R.string.community_report_reason_inappropriate),
                        wire = "inappropriate",
                        selected = state.reason,
                        onSelect = onReasonChange,
                    )
                    ReportReasonChip(
                        label = stringResource(R.string.community_report_reason_other),
                        wire = "other",
                        selected = state.reason,
                        onSelect = onReasonChange,
                    )
                }
                if (state.reason != null) {
                    OutlinedTextField(
                        value = state.details,
                        onValueChange = onDetailsChange,
                        label = { Text(stringResource(R.string.community_report_details_label)) },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                    )
                }
                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                if (state.submitted) {
                    Text(
                        text = stringResource(R.string.community_report_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            if (state.submitted) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.beacon_date_pick_confirm)) }
            } else {
                TextButton(
                    onClick = onSubmit,
                    enabled = state.reason != null && !state.sending,
                ) {
                    if (state.sending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.community_report_submit))
                    }
                }
            }
        },
        dismissButton = {
            if (!state.submitted) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.beacon_date_pick_cancel)) }
            }
        },
    )
}

@Composable
private fun ReportReasonChip(
    label: String,
    wire: String,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    FilterChip(
        selected = selected == wire,
        onClick = { onSelect(wire) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
    )
}

/**
 * Post/comment creation stamp: friendly while it is minute-fresh, then the
 * EXACT moment in the university zone - "Oct 25, 2025 · 15:45". Relative
 * time deliberately stops at one hour: past that, a real date answers
 * "when was this posted?" without arithmetic (and never wraps into
 * nonsense like "500 hours ago" for older posts).
 */
@Composable
private fun postTimestamp(epochMillis: Long): String {
    val minutes = (System.currentTimeMillis() - epochMillis) / 60000
    if (minutes < 60) {
        return when {
            minutes < 1 -> stringResource(R.string.time_just_now)
            else -> pluralStringResource(R.plurals.time_minutes_ago, minutes.toInt(), minutes)
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern("MMM d, yyyy '·' HH:mm", locale)
        .format(Instant.ofEpochMilli(epochMillis).atZone(SchoolZone))
}
