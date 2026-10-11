package se.eldebosh.nastastopp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import se.eldebosh.nastastopp.R
import se.eldebosh.nastastopp.core.youdrive.TripCardText
import se.eldebosh.nastastopp.route.model.Stop
import se.eldebosh.nastastopp.ui.TouchTarget
import se.eldebosh.nastastopp.ui.kindName
import se.eldebosh.nastastopp.ui.ref
import se.eldebosh.nastastopp.ui.refCorner
import se.eldebosh.nastastopp.ui.theme.AppTheme

/**
 * A trip's whole card on the phone (300), opened by a tap on its ≡ in the list: laid out as
 * YouDrive's own details window ([TripCardText]): its kind and time in the trip's colour, the
 * passenger's name, the estimated time and the second one (the time agreed on a pick-up, the
 * latest asked for on a drop-off), then every field with YouDrive's label (Address, Phone number,
 * Space Type(s), Mobility Aids, Fare amount, Compensation, Eligibility, Instructions). A trip
 * read from a screenshot has no card: its time, kind and address. On the driver's phone only;
 * never said. Its × (301) or a tap beside it closes it.
 */
@Composable
fun TripCardDialog(stop: Stop, onDismiss: () -> Unit) {
    val card = remember(stop.card) { stop.card?.let { TripCardText.of(it) } }
    val colors = AppTheme.colors
    val title = card?.title ?: listOfNotNull(stop.kind?.let { stringResource(kindName(it)) }, stop.time).joinToString(" ").ifEmpty { stop.displayText }
    val rows = card?.rows ?: listOf(TripCardText.Row(TripCardText.ADDRESS, stop.shownAddress))
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(18.dp)
        Column(Modifier.ref(300).fillMaxWidth().clip(shape).background(colors.card)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(colors.trip(stop.kind)).padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onTrip,
                    modifier = Modifier.weight(1f),
                )
                card?.status?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = colors.onTrip)
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.refCorner(301).size(TouchTarget)) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.display_card_close), tint = colors.onTrip)
                }
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val name = card?.name ?: stop.name
                if (name != null) Text(name, style = MaterialTheme.typography.titleMedium, color = colors.text)
                (card?.estimated ?: stop.time)?.let { Text("Estimated time $it", style = MaterialTheme.typography.bodyLarge, color = colors.text) }
                card?.negotiated?.let { Text("${card.secondLabel}: $it", style = MaterialTheme.typography.bodyLarge, color = colors.textMuted) }
                card?.kind?.let { Text(it, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = colors.text) }
                for (row in rows) {
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
                    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.Top) {
                        if (row.label != null) {
                            Text(row.label, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.width(112.dp))
                        }
                        val value = if (row.label == TripCardText.PHONE) TripCardText.spacedPhone(row.value) else row.value
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                            color = if (row.label == TripCardText.PHONE) colors.info else colors.text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
