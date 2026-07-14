package com.example.dreamescape_ai.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dreamescape_ai.ui.theme.ManaBlue
import com.example.dreamescape_ai.ui.theme.NightPanel
import com.example.dreamescape_ai.ui.theme.ScripulyaText

/**
 * Multi-image picker for the create flows. Selected images show as thumbnails in
 * a row; the first one is flagged as the preview (it is uploaded first, so the
 * backend's first media asset for the entity is the preview). Each thumbnail has
 * a remove button; the trailing tile opens the system Photo Picker.
 */
@Composable
fun ImagePickerSection(
    imageUris: List<String>,
    onAddImages: (List<String>) -> Unit,
    onRemoveImage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) onAddImages(uris.map { it.toString() })
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Images",
                color = ScripulyaText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "First image is the preview",
                color = ScripulyaText.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(imageUris, key = { i, _ -> i }) { index, uri ->
                ImageThumb(
                    uri = uri,
                    isPreview = index == 0,
                    onRemove = { onRemoveImage(index) }
                )
            }
            item(key = "add") {
                AddTile(onClick = {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                })
            }
        }
    }
}

@Composable
private fun ImageThumb(uri: String, isPreview: Boolean, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NightPanel)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isPreview) {
            Text(
                text = "Preview",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ManaBlue)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove image",
                tint = ScripulyaText
            )
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NightPanel.copy(alpha = 0.5f))
            .border(1.dp, ScripulyaText.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Add, contentDescription = "Add images", tint = ScripulyaText)
            Text(
                text = "Add",
                color = ScripulyaText.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
