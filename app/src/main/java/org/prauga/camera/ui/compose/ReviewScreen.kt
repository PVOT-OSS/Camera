/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotGlassIconButton
import com.prauga.pvot.designsystem.components.PvotGlassSurface
import com.prauga.pvot.designsystem.components.camera.PvotCameraGlassDefaults
import com.prauga.pvot.designsystem.components.camera.PvotGlassChip
import com.prauga.pvot.designsystem.effects.pvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.camera.R

private data class MediaInfo(
    val name: String?,
    val dateTaken: Long?,
    val width: Int?,
    val height: Int?,
    val size: Long?,
    val mimeType: String?,
    val favorite: Boolean,
) {
    val isVideo: Boolean
        get() = mimeType?.startsWith("video/") == true
}

@Composable
fun ReviewScreen(
    visible: Boolean,
    media: List<Uri>,
    initialUri: Uri?,
    onDismiss: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible && media.isNotEmpty(),
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(220)),
    ) {
        BackHandler(onBack = onDismiss)
        Review(
            media = media,
            initialUri = initialUri,
            onDismiss = onDismiss,
            onOpenGallery = onOpenGallery,
        )
    }
}

@Composable
private fun Review(
    media: List<Uri>,
    initialUri: Uri?,
    onDismiss: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pagerState =
        rememberPagerState(initialPage = media.indexOf(initialUri).coerceAtLeast(0)) { media.size }
    val current = media.getOrNull(pagerState.currentPage)

    var chromeVisible by remember { mutableStateOf(true) }
    var infoVisible by remember { mutableStateOf(false) }
    var deleteVisible by remember { mutableStateOf(false) }
    var deletedMessage by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val info by
        produceState<MediaInfo?>(null, current, refreshKey) {
            value =
                current?.let {
                    withContext(Dispatchers.IO) { context.contentResolver.mediaInfo(it) }
                }
        }

    val intentSenderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                refreshKey++
            }
        }

    val deleteLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                deletedMessage = true
            }
        }

    LaunchedEffect(deletedMessage) {
        if (deletedMessage) {
            delay(1500)
            deletedMessage = false
        }
    }

    val backdrop = rememberPvotGlassBackdrop()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().pvotGlassBackdrop(backdrop),
        ) { page ->
            Box(
                modifier =
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        chromeVisible = !chromeVisible
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = mediaRequest(media[page]),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        if (info?.isVideo == true && current != null) {
            PvotGlassIconButton(
                onClick = { context.viewMedia(current, info?.mimeType) },
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.Center),
                size = 64.dp,
                style = PvotCameraGlassDefaults.Style,
                contentColor = Color.White,
            ) {
                CameraIcon(
                    icon = R.drawable.ic_cam_play,
                    size = 28.dp,
                    contentDescription = stringResource(R.string.review_play),
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(Color.Black)
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 8.dp)
            ) {
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterStart)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenGallery),
                    contentAlignment = Alignment.Center,
                ) {
                    CameraIcon(
                        icon = R.drawable.ic_cam_chevron,
                        size = 22.dp,
                        modifier = Modifier.rotate(180f),
                        contentDescription = stringResource(R.string.review_open_gallery),
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val dateTaken = info?.dateTaken
                    Text(
                        text =
                            dateTaken?.let { reviewDay(it) }
                                ?: stringResource(R.string.review_today),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    dateTaken?.let {
                        Text(
                            text = DateFormat.getTimeFormat(context).format(it),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.review_done),
                    color = CameraYellow,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier.align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(Color.Black)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Filmstrip(
                    media = media,
                    selected = pagerState.currentPage,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                )

                PvotGlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier.padding(horizontal = 11.dp).fillMaxWidth().height(52.dp),
                    cornerRadius = 26.dp,
                    style = PvotCameraGlassDefaults.PanelStyle,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolbarButton(R.drawable.ic_cam_share, R.string.review_share) {
                            current?.let { context.shareMedia(it, info?.mimeType) }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ToolbarButton(
                                R.drawable.ic_cam_heart,
                                R.string.review_favorite,
                                tint = if (info?.favorite == true) CameraYellow else Color.White,
                            ) {
                                current?.let {
                                    val request =
                                        MediaStore.createFavoriteRequest(
                                            context.contentResolver,
                                            listOf(it),
                                            info?.favorite != true,
                                        )
                                    intentSenderLauncher.launch(
                                        IntentSenderRequest.Builder(request).build()
                                    )
                                }
                            }
                        }
                        ToolbarButton(R.drawable.ic_cam_info, R.string.review_info) {
                            infoVisible = !infoVisible
                        }
                        ToolbarButton(R.drawable.ic_cam_sliders, R.string.review_edit) {
                            current?.let { context.editMedia(it, info?.mimeType) }
                        }
                        ToolbarButton(R.drawable.ic_cam_trash, R.string.review_delete) {
                            deleteVisible = true
                        }
                    }
                }
            }
        }

        info?.let { InfoSheet(info = it, visible = infoVisible, onClose = { infoVisible = false }) }

        DeleteDialog(
            visible = deleteVisible,
            isVideo = info?.isVideo == true,
            onDelete = {
                deleteVisible = false
                current?.let { uri ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val request =
                            MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                        deleteLauncher.launch(IntentSenderRequest.Builder(request).build())
                    } else {
                        scope.launch {
                            val deleted =
                                withContext(Dispatchers.IO) {
                                    runCatching { context.contentResolver.delete(uri, null, null) }
                                        .getOrDefault(0)
                                }
                            deletedMessage = deleted > 0
                        }
                    }
                }
            },
            onCancel = { deleteVisible = false },
        )

        AnimatedVisibility(
            visible = deletedMessage,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PvotGlassChip(text = stringResource(R.string.review_deleted), backdrop = backdrop)
        }
    }

    LaunchedEffect(media.size) {
        if (media.isEmpty()) {
            onDismiss()
        }
    }
}

@Composable
private fun Filmstrip(media: List<Uri>, selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem(selected.coerceAtLeast(0)) }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(media) { index, uri ->
            val isSelected = index == selected
            AsyncImage(
                model = mediaRequest(uri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.width(if (isSelected) 44.dp else 24.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .then(
                            if (isSelected)
                                Modifier.border(1.dp, Color.White, RoundedCornerShape(2.dp))
                            else Modifier
                        )
                        .clickable { onSelect(index) },
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: Int,
    description: Int,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CameraIcon(
            icon = icon,
            size = 22.dp,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
private fun InfoSheet(info: MediaInfo, visible: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current

    PvotBottomSheet(visible = visible, onDismissRequest = onClose, title = info.name) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            info.dateTaken?.let {
                InfoLine(
                    DateUtils.formatDateTime(
                        context,
                        it,
                        DateUtils.FORMAT_SHOW_DATE or
                            DateUtils.FORMAT_SHOW_TIME or
                            DateUtils.FORMAT_SHOW_YEAR,
                    )
                )
            }
            if (info.width != null && info.height != null && info.width > 0) {
                val megapixels = info.width.toLong() * info.height / 1_000_000f
                InfoLine("${info.width} × ${info.height} · ${"%.1f".format(megapixels)} MP")
            }
            info.size?.let { InfoLine(Formatter.formatShortFileSize(context, it)) }
            info.mimeType?.let { InfoLine(it) }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun DeleteDialog(
    visible: Boolean,
    isVideo: Boolean,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    PvotDialog(
        visible = visible,
        onDismissRequest = onCancel,
        title =
            stringResource(
                if (isVideo) R.string.review_delete_video else R.string.review_delete_photo
            ),
        destructive = true,
    ) {
        PvotDialogAction(
            label = stringResource(R.string.review_delete),
            onClick = onDelete,
            style = PvotDialogActionStyle.Destructive,
        )
        PvotDialogAction(label = stringResource(R.string.review_cancel), onClick = onCancel)
    }
}

@Composable
private fun reviewDay(dateTaken: Long) =
    when (DateUtils.isToday(dateTaken)) {
        true -> stringResource(R.string.review_today)
        false ->
            DateUtils.formatDateTime(
                LocalContext.current,
                dateTaken,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
            )
    }

private fun ContentResolver.mediaInfo(uri: Uri): MediaInfo? {
    val projection =
        buildList {
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                add(MediaStore.MediaColumns.DATE_TAKEN)
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
                add(MediaStore.MediaColumns.SIZE)
                add(MediaStore.MediaColumns.MIME_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(MediaStore.MediaColumns.IS_FAVORITE)
                }
            }
            .toTypedArray()

    return runCatching {
            query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }

                fun index(column: String) = cursor.getColumnIndex(column)
                fun string(column: String) =
                    index(column).takeIf { it >= 0 }?.let(cursor::getString)
                fun long(column: String) =
                    index(column).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)

                MediaInfo(
                    name = string(MediaStore.MediaColumns.DISPLAY_NAME),
                    dateTaken = long(MediaStore.MediaColumns.DATE_TAKEN),
                    width = long(MediaStore.MediaColumns.WIDTH)?.toInt(),
                    height = long(MediaStore.MediaColumns.HEIGHT)?.toInt(),
                    size = long(MediaStore.MediaColumns.SIZE),
                    mimeType = string(MediaStore.MediaColumns.MIME_TYPE) ?: getType(uri),
                    favorite =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            long(MediaStore.MediaColumns.IS_FAVORITE) == 1L,
                )
            }
        }
        .getOrNull()
}

private fun Context.viewMedia(uri: Uri, mimeType: String?) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}

private fun Context.shareMedia(uri: Uri, mimeType: String?) {
    runCatching {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(mimeType ?: "image/*")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                null,
            )
        )
    }
}

private fun Context.editMedia(uri: Uri, mimeType: String?) {
    runCatching {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_EDIT)
                    .setDataAndType(uri, mimeType ?: "image/*")
                    .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    ),
                null,
            )
        )
    }
}
