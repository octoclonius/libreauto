package io.github.octoclonius.libreauto.phone.certificate

import android.content.res.Resources
import android.icu.text.DateFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.octoclonius.libreauto.phone.R
import io.github.octoclonius.libreauto.phone.certificate.CertificateProviderUiState.CertificateState
import io.github.octoclonius.libreauto.phone.certificate.CertificateProviderUiState.Picker
import io.github.octoclonius.libreauto.phone.certificate.CertificateProviderUiState.Selection
import io.github.octoclonius.libreauto.phone.ui.StatusRow
import io.github.octoclonius.libreauto.projection.certificate.CertificateRepository
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

@Composable
fun CertificateProviderRow(
    state: CertificateProviderUiState,
    onClick: () -> Unit,
    onChoose: (CertificateRepository.Choice?) -> Unit,
    onDismissPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val selection = state.selection
    StatusRow(
        title = stringResource(R.string.certificate_provider_title),
        status =
            when (selection) {
                Selection.Loading -> stringResource(R.string.certificate_provider_loading)
                Selection.None -> stringResource(R.string.certificate_provider_not_selected)
                is Selection.Selected ->
                    when (val certificate = selection.certificate) {
                        CertificateState.Loading -> stringResource(R.string.certificate_pending)
                        is CertificateState.Success ->
                            validity(resources, certificate, Instant.now(), is24Hour)
                        is CertificateState.Error ->
                            stringResource(R.string.certificate_error, certificate.message)
                    }
            },
        onClick = onClick,
        modifier = modifier,
        enabled = selection != Selection.Loading && state.picker == null,
    )

    state.picker?.let { picker ->
        CertificateProviderPickerDialog(
            picker = picker,
            selection = selection,
            onChoose = onChoose,
            onDismiss = onDismissPicker,
        )
    }
}

private fun validity(
    resources: Resources,
    certificate: CertificateState.Success,
    now: Instant,
    is24Hour: Boolean,
): String {
    val (text, at) =
        when {
            now < certificate.notBefore ->
                R.string.certificate_invalid_until to certificate.notBefore
            now <= certificate.notAfter -> R.string.certificate_valid_until to certificate.notAfter
            else -> R.string.certificate_invalid_since to certificate.notAfter
        }
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val date =
        DateFormat.getInstanceForSkeleton(
                if (is24Hour) "EEEMMMdyyyyHmmz" else "EEEMMMdyyyyhmmz",
                locale,
            )
            .format(Date.from(at))
    val days =
        ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), at.atZone(zone).toLocalDate())
    val relative = RelativeDateTimeFormatter.getInstance(locale)
    val distance =
        when {
            days > 0 -> relative.format(days.toDouble(), Direction.NEXT, RelativeUnit.DAYS)
            days < 0 -> relative.format(-days.toDouble(), Direction.LAST, RelativeUnit.DAYS)
            else -> relative.format(Direction.THIS, AbsoluteUnit.DAY)
        }
    return resources.getString(text, date, distance)
}

@Composable
private fun CertificateProviderPickerDialog(
    picker: Picker,
    selection: Selection,
    onChoose: (CertificateRepository.Choice?) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(false) }
    fun pick(choice: CertificateRepository.Choice?) {
        if (picked) return
        picked = true
        onChoose(choice)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.certificate_provider_title)) },
        text = {
            when (picker) {
                Picker.Loading ->
                    Box(Modifier.fillMaxWidth(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                is Picker.Success ->
                    Column(Modifier.selectableGroup()) {
                        PickerOption(
                            stringResource(R.string.certificate_provider_none),
                            selected = selection == Selection.None,
                            enabled = !picked,
                            onClick = { pick(null) },
                        )
                        for (option in picker.options) {
                            PickerOption(
                                option.name,
                                selected = option.selected,
                                enabled = !picked,
                                onClick = { pick(option.choice) },
                            )
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun PickerOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        modifier =
            Modifier.selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
