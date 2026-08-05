package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val birthday: Long,
    val address: String = "",
    val contactNumber: String = "",
    val picturePath: String = "",
    val guardiansJson: String = "[]",
    val customDataJson: String = "{}",
    val isDeleted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),

    // Serialized JSON array of classroom cohort names e.g., ["Class 10-A", "Class 10-B"]
    val classNamesJson: String = "[]",

    // Serialized JSON map binding classroom names to coordinate matrices e.g., {"Class 10-A": {"x": 0.5, "y": 0.5}}
    val seatingJson: String = "{}"
) {
    // Utility to deserialize the classroom list safely
    fun getClassNamesList(): List<String> {
        val list = mutableListOf<String>()
        if (classNamesJson.isBlank()) return list
        try {
            val array = JSONArray(classNamesJson)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // Utility to fetch specific seating coordinates for a targeted classroom
    fun getSeatingCoordinates(className: String): Pair<Float, Float>? {
        if (seatingJson.isBlank() || className.isBlank()) return null
        try {
            val obj = JSONObject(seatingJson)
            if (obj.has(className)) {
                val coords = obj.getJSONObject(className)
                val x = coords.optDouble("x", -1.0).toFloat()
                val y = coords.optDouble("y", -1.0).toFloat()
                return Pair(x, y)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // Immutable copy builder returning an updated coordinate mapped to a specific classroom
    fun withUpdatedSeating(className: String, x: Float, y: Float): StudentEntity {
        if (className.isBlank()) return this
        val obj = try {
            JSONObject(seatingJson)
        } catch (e: Exception) {
            JSONObject()
        }
        if (x < 0f || y < 0f) {
            obj.remove(className)
        } else {
            val coords = JSONObject().apply {
                put("x", x.toDouble())
                put("y", y.toDouble())
            }
            obj.put(className, coords)
        }
        return this.copy(seatingJson = obj.toString(), lastModified = System.currentTimeMillis())
    }
}