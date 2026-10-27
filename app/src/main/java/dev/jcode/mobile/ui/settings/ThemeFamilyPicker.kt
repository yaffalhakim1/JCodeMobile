package dev.jcode.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jcode.mobile.ui.components.Glyphs
import dev.jcode.mobile.ui.theme.ThemeFamily

/**
 * The palette picker.
 *
 * Each row is painted in the theme it offers so the choice is made by looking at
 * the theme rather than by reading its name. The preview uses the mode currently
 * in effect, so a theme is shown in the light or dark set the user would get.
 */
@Composable
internal fun ThemeFamilyPicker(
    selected: ThemeFamily,
    dark: Boolean,
    onSelect: (ThemeFamily) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeFamily.entries.forEach { family ->
            ThemeFamilyRow(
                family = family,
                dark = dark,
                isSelected = family == selected,
                onClick = { onSelect(family) },
            )
        }
    }
}

@Composable
private fun ThemeFamilyRow(
    family: ThemeFamily,
    dark: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = family.scheme(dark)
    val shape = MaterialTheme.shapes.medium

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow, shape)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) scheme.primary else scheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics {
                selected = isSelected
                role = Role.RadioButton
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemeSwatch(family = family, dark = dark)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = family.label,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
            Text(
                text = family.description,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Glyphs.Check,
                contentDescription = "Selected",
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ThemeSwatch(family: ThemeFamily, dark: Boolean) {
    val scheme = family.scheme(dark)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SwatchDot(scheme.primary)
        SwatchDot(scheme.primaryContainer)
        SwatchDot(scheme.tertiaryContainer)
    }
}

@Composable
private fun SwatchDot(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape),
    )
}