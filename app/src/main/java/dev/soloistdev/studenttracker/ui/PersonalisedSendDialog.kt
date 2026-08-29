package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.soloistdev.studenttracker.data.ContactLogEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch
import dev.soloistdev.studenttracker.R

/** One rendered message bound to one phone number. */
data class PersonalisedMessage(
    val recipientLabel: String,
    val phone: String,
    val body: String,
    /** Who this is about, so the send can be written to the contact log. */
    val studentId: Int = 0,
    val guardianName: String = "",
    val templateName: String = ""
)

/**
 * Steps through personalised messages one recipient at a time.
 *
 * Android has no way to hand a batch of individually-addressed SMS to the messaging app without
 * the SEND_SMS permission, which is a Play Store restricted permission and would let the app send
 * silently. Walking a queue of ACTION_SENDTO intents keeps the send under the control of the
 * teacher and the app permission list unchanged, at the cost of one tap per parent.
 */
@Composable
fun PersonalisedSendDialog(
    messages: List<PersonalisedMessage>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }
    var index by remember { mutableIntStateOf(0) }
    var sentCount by remember { mutableIntStateOf(0) }

    if (index >= messages.size) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Queue Finished", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Opened $sentCount of ${messages.size} messages. Anything skipped was not sent.",
                    fontSize = 13.sp
                )
            },
            confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.s_done)) } },
            shape = RoundedCornerShape(28.dp)
        )
        return
    }

    val current = messages[index]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Send Personally", fontWeight = FontWeight.Bold)
                Text(
                    text = "${index + 1} of ${messages.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (index.toFloat() / messages.size.toFloat()) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(current.recipientLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = current.phone,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        text = current.body,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (openSms(context, current)) {
                    sentCount++
                    // Recorded only when the messaging app actually opened. A skipped recipient
                    // must not look contacted - the whole value of the log is that it is true.
                    if (current.studentId > 0) {
                        scope.launch {
                            repository.logContact(
                                ContactLogEntity(
                                    studentId = current.studentId,
                                    guardianName = current.guardianName,
                                    phone = current.phone,
                                    channel = ContactLogEntity.CHANNEL_SMS,
                                    templateName = current.templateName,
                                    body = current.body
                                )
                            )
                        }
                    }
                }
                index++
            }) {
                Text(stringResource(R.string.s_open_sms))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                TextButton(onClick = { index++ }) { Text(stringResource(R.string.s_skip)) }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

private fun openSms(context: Context, message: PersonalisedMessage): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_SENDTO, "smsto:${message.phone}".toUri()).apply {
            putExtra("sms_body", message.body)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, R.string.notify_error_intent_failed, Toast.LENGTH_LONG).show()
        false
    }
}
