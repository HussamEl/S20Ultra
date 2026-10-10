package se.eldebosh.nastastopp.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.ui.theme.AppTheme

/** Minimum touch target for everything the driver taps. */
val TouchTarget: Dp = 48.dp

/**
 * The app's button: [primary] = filled with the action colour (the main action of a screen),
 * otherwise a quiet tonal button with a hairline border. Compact (48 dp) unless [minHeight] says
 * otherwise. It shrinks a little while pressed ([AppTheme.effects]).
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    val colors = if (primary) {
        ButtonDefaults.buttonColors(
            containerColor = containerColor ?: AppTheme.colors.action,
            contentColor = contentColor ?: AppTheme.colors.onAction,
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = containerColor ?: AppTheme.colors.tonal,
            contentColor = contentColor ?: AppTheme.colors.text,
            disabledContainerColor = AppTheme.colors.card,
        )
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) AppTheme.effects.pressedScale else 1f, label = "press")
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = colors,
        border = if (primary) null else BorderStroke(1.dp, AppTheme.colors.cardBorder),
        contentPadding = contentPadding,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = minHeight).graphicsLayer { scaleX = scale; scaleY = scale },
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

/** A soft surface with a hairline border (and, by day, a soft shadow) that groups related rows. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .fillMaxWidth()
            .shadow(AppTheme.effects.cardShadow, shape)
            .clip(shape)
            .background(AppTheme.colors.card)
            .border(1.dp, AppTheme.colors.cardBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** AppTheme.colors.cardBorder between the rows of a card. */
@Composable
fun CardDivider() = HorizontalDivider(thickness = 1.dp, color = AppTheme.colors.cardBorder, modifier = Modifier.padding(horizontal = 16.dp))

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

/** The name of a trip's kind ("Pick-up", "Drop-off", "Start", "Back to depot"). */
@StringRes
fun kindName(kind: TripKind): Int = when (kind) {
    TripKind.PICK_UP -> R.string.kind_pick_up
    TripKind.DROP_OFF -> R.string.kind_drop_off
    TripKind.PULL_OUT -> R.string.kind_pull_out
    TripKind.PULL_IN -> R.string.kind_pull_in
}

/** A trip's kind as a small white pill, like YouDrive's status pills; readable on every card colour. */
@Composable
fun KindLabel(kind: TripKind, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        stringResource(kindName(kind)),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = AppTheme.colors.onKindPill,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(AppTheme.colors.kindPill)
            .border(1.dp, AppTheme.colors.onKindPill.copy(alpha = 0.14f), shape)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/**
 * Where a trip was finished, as small coloured dots with a check and no words: green = in YouDrive
 * ([youDrive]), blue = in this app ([here]). A green dot alone on a coming trip means YouDrive
 * already counts it done while the route has not passed it. Nothing shows when neither is true.
 */
@Composable
fun DoneMarks(youDrive: Boolean, here: Boolean, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    if (!youDrive && !here) return
    val inYouDrive = stringResource(R.string.done_in_youdrive)
    val inApp = stringResource(R.string.done_here)
    val description = listOfNotNull(inYouDrive.takeIf { youDrive }, inApp.takeIf { here }).joinToString(", ")
    Row(
        horizontalArrangement = Arrangement.spacedBy(size / 4),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.ref(202, centered = true).semantics { contentDescription = description },
    ) {
        if (youDrive) CheckDot(AppTheme.colors.success, AppTheme.colors.onStatus, size)
        if (here) CheckDot(AppTheme.colors.info, AppTheme.colors.onInfo, size)
    }
}

@Composable
private fun CheckDot(fill: Color, tick: Color, size: Dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    drawCircle(fill)
    val check = Path().apply {
        moveTo(w * 0.28f, w * 0.52f)
        lineTo(w * 0.44f, w * 0.68f)
        lineTo(w * 0.73f, w * 0.36f)
    }
    drawPath(check, tick, style = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * A trip's card in YouDrive's colours ([AppColors.trip][se.eldebosh.nastastopp.ui.theme.AppColors.trip]):
 * green pick-up, white drop-off, grey depot. The current trip ([current]) gets YouDrive's thick
 * border and lifts a little. The colour fades when it changes (the next trip moves up).
 */
@Composable
fun TripSurface(
    kind: TripKind?,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    val effects = AppTheme.effects
    val color by animateColorAsState(colors.trip(kind), tween(effects.colorFadeMs), label = "trip")
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = colors.onTrip,
        border = BorderStroke(if (current) 3.dp else 1.dp, if (current) colors.currentBorder else colors.cardBorder),
        shadowElevation = if (current) effects.currentShadow else effects.cardShadow,
        content = content,
    )
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
