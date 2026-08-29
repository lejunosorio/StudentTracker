package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A record that a guardian was actually contacted.
 *
 * Five screens could send a message to a guardian and none of them wrote anything down, so the
 * app could not answer "have I already called about this?". That matters twice: a teacher
 * repeating a call they made last week wastes everyone's time, and a teacher escalating a
 * concern is normally expected to show that contact was attempted first. Switching phones lost
 * the history entirely, because it only ever existed in the SMS app.
 */
@Entity(
    tableName = "contact_log",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["studentId"]), Index(value = ["sentAt"])]
)
data class ContactLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,

    /** Who was written to, as shown at the time. Kept as text: guardians are not rows. */
    val guardianName: String = "",
    val phone: String = "",

    /** "SMS" today; the column exists so a call or an email can be logged the same way. */
    val channel: String = CHANNEL_SMS,

    /** Which template was used, when one was. Blank for a message typed by hand. */
    val templateName: String = "",

    /** The rendered text, so the record shows what was actually said. */
    val body: String = "",

    val sentAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CHANNEL_SMS = "SMS"
        const val CHANNEL_CALL = "Call"
        const val CHANNEL_NOTE = "Note"
    }
}
