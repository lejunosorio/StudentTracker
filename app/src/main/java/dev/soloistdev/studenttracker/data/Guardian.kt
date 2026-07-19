package dev.soloistdev.studenttracker.data

import org.json.JSONArray
import org.json.JSONObject

data class Guardian(
    val name: String,
    val relationship: String,
    val phones: List<String> // Supports multiple phone numbers per guardian!
) {
    // Serializes the object into a JSONObject
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("relationship", relationship)

            val phoneArray = JSONArray()
            phones.forEach { phoneArray.put(it) }
            put("phones", phoneArray)
        }
    }

    companion object {
        // Deserializes a JSONObject back into a Guardian model
        fun fromJsonObject(json: JSONObject): Guardian {
            val phoneList = mutableListOf<String>()
            val phoneArray = json.optJSONArray("phones")
            if (phoneArray != null) {
                for (i in 0 until phoneArray.length()) {
                    phoneList.add(phoneArray.getString(i))
                }
            } else {
                // Fallback to old single contact column structure if needed
                val singlePhone = json.optString("contact", "")
                if (singlePhone.isNotEmpty()) phoneList.add(singlePhone)
            }
            return Guardian(
                name = json.optString("name", ""),
                relationship = json.optString("relationship", ""),
                phones = phoneList
            )
        }

        // Serializes a complete List of Guardians into a JSON string
        fun listToJsonString(list: List<Guardian>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJsonObject()) }
            return array.toString()
        }

        // Deserializes a JSON string back into a List of Guardians
        fun listFromJsonString(jsonStr: String): List<Guardian> {
            val list = mutableListOf<Guardian>()
            if (jsonStr.isBlank()) return list
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    list.add(fromJsonObject(array.getJSONObject(i)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }
    }
}