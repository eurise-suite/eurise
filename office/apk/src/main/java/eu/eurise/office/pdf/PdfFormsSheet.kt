package eu.eurise.office.pdf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.eurise.office.R

/** «Moduli» (tavola OffPdf): i campi del PDF in un elenco, da compilare con la tastiera; si scrivono nel file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfFormsSheet(model: PdfModel, onClose: () -> Unit) {
    val fields by produceState<List<FormField>?>(null) { value = model.session.read { PdfTools.fields(it) }.orEmpty() }
    val changes = remember { mutableStateMapOf<String, String>() }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pdf_forms_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    val values = changes.toMap()
                    if (values.isNotEmpty()) model.session.edit { d -> values.forEach { (k, v) -> PdfTools.setField(d, k, v) } }
                    onClose()
                }) { Text(stringResource(R.string.pdf_save)) }
            }
            Text(stringResource(R.string.pdf_forms_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val list = fields
            if (list == null) {
                Text(stringResource(R.string.pdf_loading))
            } else {
                LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.name }) { field ->
                        val value = changes[field.name] ?: field.value
                        when (field.kind) {
                            FormField.Kind.Text -> OutlinedTextField(
                                value = value, onValueChange = { changes[field.name] = it },
                                label = { Text(field.label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            FormField.Kind.Check -> Row(
                                Modifier.fillMaxWidth().clickable { changes[field.name] = if (value.isEmpty()) "1" else "" },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = value.isNotEmpty(), onCheckedChange = { changes[field.name] = if (it) "1" else "" })
                                Text(field.label)
                            }
                            FormField.Kind.Choice -> Column {
                                Text(field.label, style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    field.options.take(6).forEach { option ->
                                        FilterChip(selected = value == option, onClick = { changes[field.name] = option }, label = { Text(option) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
