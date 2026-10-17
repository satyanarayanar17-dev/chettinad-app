package com.example.portal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** A single foreground poller per signed-in portal; lifecycle cancellation stops background work. */
@Composable internal fun ForegroundPortalSync(vm: PortalViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                while (true) { vm.sync(); delay(15_000) }
            } finally { vm.stopSync() }
        }
    }
}

fun todayIndia(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date())
fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
fun displayDate(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return runCatching {
        val normalized = value.replace(Regex("\\.[0-9]+"), "")
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).parse(normalized)
        SimpleDateFormat("d MMM yyyy · h:mm a", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(requireNotNull(date))
    }.getOrElse { value.take(10) }
}
fun label(value: String): String = value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
fun validStaffPassword(value: String): Boolean = value.length in 12..72 &&
    value.any { it in 'A'..'Z' } && value.any { it in 'a'..'z' } &&
    value.any { it in '0'..'9' } && value.any { !(it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9') }
fun validDate(value: String): Boolean = runCatching {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && format.format(requireNotNull(format.parse(value))) == value
}.getOrDefault(false)

@Composable fun PageHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun InfoCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}
@Composable fun StatusPill(status: String) {
    val positive = status in setOf("AUTHORIZED", "COMPLETED", "TAKEN", "ACTIVE", "REVIEWED")
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = if (positive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer) {
        Text(label(status), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
@Composable fun EmptyCard(title: String, description: String) { InfoCard(title, description) }
@Composable fun ErrorCards(state: PortalState, sections: Set<String>, retry: () -> Unit) {
    state.errors.filterKeys { it in sections || it == "action" }.forEach { (section, message) ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${label(section)}: $message", color = MaterialTheme.colorScheme.onErrorContainer)
                if (section != "action") TextButton(onClick = retry, enabled = !state.loading && !state.busy) { Text("Try again") }
            }
        }
    }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium) }
    if (state.busy || state.loading || state.patientLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
}
@Composable fun ChoiceRow(values: List<String>, selected: String, onSelect: (String) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected = value == selected, onClick = { onSelect(value) }, enabled = enabled, label = { Text(label(value)) }) }
    }
}
@Composable fun Field(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean = true, lines: Int = 1) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, enabled = enabled,
        singleLine = lines == 1, minLines = lines, modifier = Modifier.fillMaxWidth())
}
@Composable fun ConfirmAction(title: String, explanation: String, confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text(title) }, text = { Text(explanation) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Keep current") } })
}
@Composable fun TrendLine(values: List<Double>, unit: String) {
    val color = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${values.size} readings · $unit · Range ${values.minOrNull()}–${values.maxOrNull()}", style = MaterialTheme.typography.labelMedium)
        if (values.size > 1) Canvas(Modifier.fillMaxWidth().height(140.dp).padding(8.dp)) {
            val low = values.minOrNull() ?: 0.0; val high = values.maxOrNull() ?: 1.0
            val span = (high-low).coerceAtLeast(1.0)
            val path = Path()
            values.forEachIndexed { i, value ->
                val x = size.width * i / (values.size-1)
                val y = size.height * (1 - (value-low)/span).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Oldest ${values.firstOrNull() ?: "—"}", style = MaterialTheme.typography.labelSmall)
            Text("Latest ${values.lastOrNull() ?: "—"}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable fun <T> PagedRows(rows: List<T>, content: @Composable (T) -> Unit) {
    var shown by remember(rows) { mutableStateOf(25) }
    rows.take(shown).forEach { content(it) }
    if (shown < rows.size) OutlinedButton(onClick = { shown += 25 }) { Text("Show more (${rows.size - shown} remaining)") }
}
