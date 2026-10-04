/**
 * ADTMusic (modified fork of Vivi Music)
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.vivi.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.vivi.BuildConfig
import com.music.vivi.LocalPlayerAwareWindowInsets
import com.music.vivi.R
import com.music.vivi.ui.component.ExpressiveSettingGroup
import com.music.vivi.ui.component.IconButton
import com.music.vivi.ui.component.Material3SettingsItem
import com.music.vivi.ui.utils.backToMain
import com.music.vivi.ui.utils.safeOpenUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val unknownString = stringResource(R.string.unknown)
    val cookieShape = MaterialShapes.Cookie7Sided.toShape()

    val installedDate = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val installTime = packageInfo.firstInstallTime
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(installTime))
        } catch (_: Exception) {
            unknownString
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.about),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))

        AppVersionTile(
            appName = stringResource(R.string.app_name),
            description = "v${BuildConfig.VERSION_NAME} • ${stringResource(if (BuildConfig.IS_NIGHTLY) R.string.build_nightly else R.string.build_stable)}",
            onGithubClick = { uriHandler.safeOpenUri(context, "https://github.com/alideniztartma/adt-music") },
        )

        Spacer(modifier = Modifier.height(10.dp))

        ExpressiveSettingGroup(
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.app_developer), color = MaterialTheme.colorScheme.primary) },
                    description = { Text(stringResource(R.string.developer_name)) },
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.dev),
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(cookieShape),
                            contentScale = ContentScale.Crop,
                        )
                    },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/alideniztartma") },
                    isExternalLink = true,
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.github),
                    title = { Text(stringResource(R.string.about_project_url)) },
                    description = { Text("github.com/alideniztartma") },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/alideniztartma") },
                    isExternalLink = true,
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.about_tagline)) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.link),
                    title = { Text(stringResource(R.string.about_based_on)) },
                    description = { Text("Vivi Music") },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/vivizzz007/vivi-music") },
                    isExternalLink = true,
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.github),
                    title = { Text(stringResource(R.string.about_original_project)) },
                    description = { Text("github.com/vivizzz007/vivi-music") },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/vivizzz007/vivi-music") },
                    isExternalLink = true,
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.about_credits_sources)) },
                    description = { Text("vivimusic lyrics provider") },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.about_credits_sources)) },
                    description = { Text("JioSaavn (via vivimusic)") },
                ),
            ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        ExpressiveSettingGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.deployed_app_update),
                    title = { Text(stringResource(R.string.installed_date_title)) },
                    trailingContent = { Text(installedDate) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text(stringResource(R.string.version_code)) },
                    trailingContent = { Text(BuildConfig.VERSION_CODE.toString()) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.license_vivi),
                    title = { Text(stringResource(R.string.license)) },
                    description = { Text("GNU GPL-3.0") },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/alideniztartma/adt-music/blob/main/LICENSE") },
                    isExternalLink = true,
                ),
            ),
        )
        Spacer(modifier = Modifier.height(10.dp))
    }

    TopAppBar(
        title = { },
        navigationIcon = {
            IconButton(
                onClick = { onBack?.invoke() ?: navController.navigateUp() },
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun AppVersionTile(
    appName: String,
    description: String,
    onGithubClick: () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(shape = CircleShape) {
        androidx.compose.material3.ListItem(
            leadingContent = {
                Image(
                    painter = painterResource(R.drawable.icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = containerColor,
            ),
            headlineContent = {
                Text(
                    text = appName,
                    color = onContainerColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            supportingContent = {
                Text(
                    text = description,
                    color = onContainerColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            trailingContent = {
                androidx.compose.material3.IconButton(onClick = onGithubClick) {
                    Icon(
                        painter = painterResource(R.drawable.github),
                        contentDescription = "GitHub",
                        tint = onContainerColor,
                    )
                }
            },
        )
    }
}
