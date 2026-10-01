package com.bblvn.composebaseproject.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bblvn.composebaseproject.BuildConfig
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.OnUiEvent
import com.bblvn.composebaseproject.core.SettingManager
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.features.language.findLanguage
import com.bblvn.composebaseproject.features.language.nativeLanguageName
import com.bblvn.composebaseproject.ui.components.AppToast
import com.bblvn.composebaseproject.ui.components.ButtonVariant
import com.bblvn.composebaseproject.ui.components.ConfirmPopup
import com.bblvn.composebaseproject.ui.components.PrimaryButton
import com.bblvn.composebaseproject.ui.components.SettingDivider
import com.bblvn.composebaseproject.ui.components.SettingGroup
import com.bblvn.composebaseproject.ui.components.SettingRow
import com.bblvn.composebaseproject.ui.components.SettingTrailing
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.AppGradients
import com.bblvn.composebaseproject.ui.theme.AppShapes
import com.bblvn.composebaseproject.ui.theme.AppType
import com.bblvn.composebaseproject.ui.theme.glowGold
import com.bblvn.composebaseproject.utils.IntentUtils

@Composable
fun ProfileScreen(
    onOpenPaywall: () -> Unit,
    onOpenLanguage: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ProfileViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val appLocale by SettingManager.appLocale.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    OnUiEvent(vm.events) { event ->
        when (event) {
            is UiEvent.ShowToast -> toast = event.text.resolve(context)
            else -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize().background(AppColors.bgRoot)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding().padding(top = 8.dp)) {
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = AppType.display,
                        color = AppColors.textPrimary,
                    )
                }
            }

            item {
                PlanCard(
                    isPremium = state.isPremium,
                    onUpgrade = onOpenPaywall,
                )
            }

            item {
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = state.createdCount.toString(),
                        label = stringResource(R.string.profile_stat_created),
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = state.savedCount.toString(),
                        label = stringResource(R.string.profile_stat_saved),
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = state.templatesUsed.toString(),
                        label = stringResource(R.string.profile_stat_templates),
                    )
                }
            }

            item {
                SettingGroup(
                    modifier = Modifier.padding(top = 24.dp),
                    title = stringResource(R.string.profile_group_app),
                ) {
                    SettingRow(
                        icon = R.drawable.ic_globe,
                        title = stringResource(R.string.profile_language),
                        trailing = SettingTrailing.Value(
                            findLanguage(appLocale)?.let { nativeLanguageName(it.localeId) }.orEmpty()
                        ),
                        onClick = onOpenLanguage,
                    )
                    SettingDivider()
                    SettingRow(
                        icon = R.drawable.ic_sliders,
                        title = stringResource(R.string.profile_high_quality),
                        // Gated because a larger render costs more credits per run; free users see
                        // it disabled rather than hidden, so the benefit of PRO is legible.
                        enabled = state.isPremium,
                        trailing = SettingTrailing.Toggle(
                            checked = state.highQuality && state.isPremium,
                            onCheckedChange = vm::onHighQualityChanged,
                        ),
                        onClick = if (state.isPremium) null else onOpenPaywall,
                    )
                    SettingDivider()
                    SettingRow(
                        icon = R.drawable.ic_save,
                        title = stringResource(R.string.profile_auto_save),
                        trailing = SettingTrailing.Toggle(
                            checked = state.autoSave,
                            onCheckedChange = vm::onAutoSaveChanged,
                        ),
                    )
                }
            }

            item {
                SettingGroup(
                    modifier = Modifier.padding(top = 24.dp),
                    title = stringResource(R.string.profile_group_data),
                ) {
                    SettingRow(
                        icon = R.drawable.ic_database,
                        title = stringResource(R.string.profile_storage_used),
                        trailing = SettingTrailing.Value(state.storageLabel),
                        onClick = null,
                    )
                    SettingDivider()
                    SettingRow(
                        icon = R.drawable.ic_broom,
                        title = stringResource(R.string.profile_clear_cache),
                        trailing = SettingTrailing.None,
                        onClick = vm::onClearCache,
                    )
                    SettingDivider()
                    SettingRow(
                        icon = R.drawable.ic_trash,
                        title = stringResource(R.string.profile_clear_history),
                        trailing = SettingTrailing.None,
                        danger = true,
                        onClick = { confirmClearHistory = true },
                    )
                }
            }

            item {
                SettingGroup(
                    modifier = Modifier.padding(top = 24.dp),
                    title = stringResource(R.string.profile_group_other),
                ) {
                    SettingRow(
                        icon = R.drawable.ic_star,
                        title = stringResource(R.string.profile_rate),
                        onClick = { IntentUtils.openUrl(context, PLAY_STORE_URL) },
                    )
                    SettingDivider()
                    SettingRow(
                        icon = R.drawable.ic_shield,
                        title = stringResource(R.string.profile_privacy),
                        onClick = { IntentUtils.openUrl(context, PRIVACY_URL) },
                    )
                    SettingDivider()
                    SettingRow(
                        icon = R.drawable.ic_file_text,
                        title = stringResource(R.string.profile_terms),
                        onClick = { IntentUtils.openUrl(context, TERMS_URL) },
                    )
                    SettingDivider()
                    SettingRow(
                        icon = R.drawable.ic_info,
                        title = stringResource(R.string.profile_version),
                        trailing = SettingTrailing.Value(BuildConfig.VERSION_NAME),
                        onClick = null,
                    )
                }
            }

            item { Spacer(Modifier.height(240.dp)) }
        }

        ConfirmPopup(
            visible = confirmClearHistory,
            title = stringResource(R.string.profile_clear_history_title),
            message = stringResource(R.string.profile_clear_history_message),
            confirmText = stringResource(R.string.profile_clear_history_confirm),
            cancelText = stringResource(R.string.profile_cancel),
            destructive = true,
            onConfirm = {
                confirmClearHistory = false
                vm.onClearHistory()
            },
            onDismiss = { confirmClearHistory = false },
        )

        AppToast(
            visible = toast != null,
            text = toast.orEmpty(),
            onDismiss = { toast = null },
        )
    }
}

@Composable
private fun PlanCard(
    isPremium: Boolean,
    onUpgrade: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .height(120.dp)
            .then(if (isPremium) Modifier.glowGold(AppShapes.card) else Modifier)
            .clip(AppShapes.card)
            .then(
                if (isPremium) {
                    Modifier.background(AppGradients.gold)
                } else {
                    Modifier
                        .background(AppColors.bgSurface)
                        .border(1.dp, AppColors.strokeSoft, AppShapes.card)
                }
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isPremium) R.string.profile_plan_pro else R.string.profile_plan_free
                    ),
                    style = AppType.titleM,
                    color = if (isPremium) AppColors.textOnAccent else AppColors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (isPremium) {
                            R.string.profile_plan_pro_note
                        } else {
                            R.string.profile_plan_free_note
                        }
                    ),
                    style = AppType.caption,
                    color = if (isPremium) {
                        AppColors.textOnAccent.copy(alpha = 0.8f)
                    } else {
                        AppColors.textSecondary
                    },
                )
            }
            // Both states occupy the same 40dp box so the card does not reflow when the
            // entitlement flips (R1).
            Box(
                modifier = Modifier.size(width = 120.dp, height = 40.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (isPremium) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_crown),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                            AppColors.textOnAccent
                        ),
                    )
                } else {
                    PrimaryButton(
                        text = stringResource(R.string.profile_upgrade),
                        variant = ButtonVariant.Brand,
                        fillMaxWidth = false,
                        height = 40.dp,
                        shape = AppShapes.chip,
                        onClick = onUpgrade,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.thumb)
            .background(AppColors.bgSurface)
            .border(1.dp, AppColors.strokeSoft, AppShapes.thumb)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, style = AppType.titleL, color = AppColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(text = label, style = AppType.caption, color = AppColors.textSecondary)
    }
}

private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.vezora.photo.ai.generator"
private const val PRIVACY_URL = "https://doc-hosting.flycricket.io/vezora-ai-photo-generator-privacy-policy/df218ef2-ab65-47d7-bfaa-fc12a3b1f948/privacy"
private const val TERMS_URL = "https://bblvn.com/terms"
