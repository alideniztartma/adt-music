package com.alideniz.adtmusic.ui.screens

import com.alideniz.adtmusic.platform.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alideniz.adtmusic.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alideniz.adtmusic.container
import com.alideniz.adtmusic.data.Bio
import com.alideniz.adtmusic.ui.components.Cover
import com.alideniz.adtmusic.ui.components.Txt
import com.alideniz.adtmusic.ui.components.press
import com.alideniz.adtmusic.ui.theme.AdtTheme

/** Sanatçı biyografisi; bulunamazsa hiçbir şey göstermez. */
@Composable
fun ArtistBio(name: String, modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    val bio by produceState<Bio?>(null, name) { value = app.wiki.artist(name) }
    bio?.let { BioCard(it, "HAKKINDA", modifier) }
}

/** Albüm hakkında bilgi; bulunamazsa hiçbir şey göstermez. */
@Composable
fun AlbumBio(title: String, artist: String, modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    val bio by produceState<Bio?>(null, title, artist) {
        value = if (artist.isBlank()) null else app.wiki.album(title, artist)
    }
    bio?.let { BioCard(it, "ALBÜM HAKKINDA", modifier) }
}

@Composable
private fun BioCard(bio: Bio, label: String, modifier: Modifier) {
    val context = LocalContext.current
    val c = AdtTheme.colors
    var expanded by remember(bio.url) { mutableStateOf(false) }
    Column(
        modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .border(1.dp, c.ink)
            .press(label = if (expanded) "Daralt" else "Devamını oku") { expanded = !expanded }
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 500f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(label, AdtTheme.type.label, color = c.accent, modifier = Modifier.weight(1f))
            Txt(if (bio.lang == "tr") "VİKİPEDİ" else "WIKIPEDIA", AdtTheme.type.label, color = c.faint)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            if (bio.image != null && expanded) {
                Cover(bio.image, Modifier.size(84.dp))
                Spacer(Modifier.width(12.dp))
            }
            Txt(
                bio.text,
                AdtTheme.type.body,
                color = c.ink,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(if (expanded) "DAHA AZ" else "DEVAMINI OKU", AdtTheme.type.label, color = c.ink, modifier = Modifier.weight(1f))
            if (expanded) {
                Txt(
                    "KAYNAĞI AÇ ↗",
                    AdtTheme.type.label,
                    color = c.accent,
                    modifier = Modifier
                        .background(c.paper)
                        .press(label = "Vikipedi'de aç") {
                            com.alideniz.adtmusic.platform.openUrl(bio.url)
                        }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}
