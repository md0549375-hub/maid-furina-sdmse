package eu.darken.sdmse.furina.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.furina.feature.settings.GeminiSettingsViewModel.Status
import eu.darken.sdmse.furina.ui.FurinaColors

/** "Gemini AI Core Settings": key storage + health badge, model id, schedule. */
@Composable
fun GeminiSettingsScreen(viewModel: GeminiSettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    // Deliberately remember {} and not rememberSaveable {}: the key must never land in saved instance state.
    var keyInput by remember { mutableStateOf("") }
    var modelInput by rememberSaveable(state.model) { mutableStateOf(state.model) }
    var scheduleInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.status) {
        if (state.status == Status.ONLINE) keyInput = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.furina_settings_title),
            fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        StatusBadge(status = state.status, detail = state.statusDetail)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.furina_key_title))
            state.maskedKey?.let {
                Text(
                    text = stringResource(R.string.furina_key_saved, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.furina_key_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.saveKey(keyInput) },
                    enabled = keyInput.isNotBlank() && state.status != Status.CHECKING,
                ) { Text(stringResource(R.string.furina_key_save)) }
                if (state.maskedKey != null) {
                    TextButton(onClick = viewModel::removeKey) { Text(stringResource(R.string.furina_key_remove)) }
                    TextButton(
                        onClick = viewModel::recheck,
                        enabled = state.status != Status.CHECKING,
                    ) { Text(stringResource(R.string.furina_key_recheck)) }
                }
            }
            Text(
                text = stringResource(R.string.furina_key_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.furina_model_title))
            OutlinedTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.modelError,
                supportingText = if (state.modelError) {
                    { Text(stringResource(R.string.furina_model_error)) }
                } else null,
            )
            OutlinedButton(onClick = { viewModel.saveModel(modelInput) }) {
                Text(stringResource(R.string.furina_model_save))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.furina_schedule_title))
            Text(
                text = state.schedule?.let { stringResource(R.string.furina_schedule_active, it.describe()) }
                    ?: stringResource(R.string.furina_schedule_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = scheduleInput,
                onValueChange = { scheduleInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.furina_schedule_hint)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.applySchedule(scheduleInput) },
                    enabled = scheduleInput.isNotBlank(),
                ) { Text(stringResource(R.string.furina_schedule_apply)) }
                if (state.schedule != null) {
                    OutlinedButton(onClick = viewModel::cancelSchedule) {
                        Text(stringResource(R.string.furina_schedule_cancel))
                    }
                }
            }
            state.scheduleMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = stringResource(R.string.furina_schedule_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun StatusBadge(status: Status, detail: String?) {
    val (color: Color, label: String) = when (status) {
        Status.ONLINE -> FurinaColors.StatusOnline to stringResource(R.string.furina_status_online)
        Status.INVALID -> FurinaColors.StatusInvalid to stringResource(R.string.furina_status_invalid)
        Status.UNREACHABLE -> FurinaColors.StatusWarn to stringResource(R.string.furina_status_unreachable)
        Status.CHECKING -> FurinaColors.StatusWarn to stringResource(R.string.furina_status_checking)
        Status.NO_KEY -> MaterialTheme.colorScheme.outline to stringResource(R.string.furina_status_no_key)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = color.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, color.copy(alpha = 0.7f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (!detail.isNullOrBlank() && status != Status.ONLINE) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
