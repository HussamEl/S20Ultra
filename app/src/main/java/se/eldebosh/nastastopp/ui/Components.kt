package se.eldebosh.nastastopp.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.ui.theme.Hairline

/** Minimum touch target for everything the driver taps. */
val TouchTarget: Dp = 48.dp

/**
 * The app's button: [primary] = filled brand colour (the main action of a screen), otherwise a
 * quiet tonal button with a hairline border. Compact (48 dp) unless [minHeight] says otherwise.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    minHeight: Dp = TouchTarget,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val colors = if (primary) {
        ButtonDefaults.buttonColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.primary,
            contentColor = contentColor ?: MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = contentColor ?: MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = colors,
        border = if (primary) null else BorderStroke(1.dp, Hairline),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier.heightIn(min = minHeight),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Top bar: back arrow, title (with an optional small [subtitle]) and actions. Mirrored in RTL.
 * [backRef] / [titleRef] number the arrow and the title.
 */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    backRef: Int? = null,
    titleRef: Int? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = (if (backRef != null) Modifier.refCorner(backRef) else Modifier).size(TouchTarget)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Column((if (titleRef != null) Modifier.ref(titleRef, centered = true) else Modifier).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/** Small section heading, optionally with a "?" explanation ([help] or [helpText]). */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    @StringRes help: Int? = null,
    helpText: String? = null,
    helpRef: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.heightIn(min = 32.dp).padding(start = 4.dp, top = 18.dp, bottom = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        val dot = if (helpRef != null) Modifier.refCorner(helpRef) else Modifier
        when {
            help != null -> HelpDot(help, dot, title = text)
            helpText != null -> HelpDot(helpText, dot, title = text)
        }
    }
}

/** A soft surface with a hairline border that groups related rows. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Hairline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** Hairline between the rows of a card. */
@Composable
fun CardDivider() = HorizontalDivider(thickness = 1.dp, color = Hairline, modifier = Modifier.padding(horizontal = 16.dp))

/** An icon in a small tinted rounded square (leading element of a row). */
@Composable
fun IconBadge(@DrawableRes icon: Int, tint: Color, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(tint.copy(alpha = 0.14f)),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * One row of a card: optional leading icon, title (with an optional "?" explanation), optional
 * status line and a trailing control (switch, radio, chevron).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    @StringRes help: Int? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** Reference number, shown inside the row's padding (clear of a card's rounded corner). */
    ref: Int? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(if (ref != null) Modifier.ref(ref) else Modifier),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            IconBadge(icon, iconTint)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                if (help != null) HelpDot(help, title = title)
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** A trailing chevron (the row opens something). */
@Composable
fun Chevron() = Icon(
    painterResource(R.drawable.ic_chevron),
    contentDescription = null,
    tint = MaterialTheme.colorScheme.outline,
    modifier = Modifier.size(20.dp),
)

@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth(), content = content)
}

/** A paragraph of explanation (Help); its direction follows the text, so Arabic reads right-to-left. */
@Composable
fun Paragraph(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
