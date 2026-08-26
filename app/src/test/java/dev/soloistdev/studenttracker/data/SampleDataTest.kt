package dev.soloistdev.studenttracker.data

import dev.soloistdev.studenttracker.ui.FilterEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Checks the shipped demo dataset against what JsonSyncEngine actually reads.
 *
 * The importer resolves cross-references by name - a gradebook row finds its student through
 * "lastName_firstName_firstClass", an assessment finds its period through the term name - and it
 * skips anything it cannot resolve, silently. A typo in the sample would therefore not fail the
 * import; it would just quietly produce a demo with missing grades. These assertions are what
 * stands between that and a tester's first impression.
 */
class SampleDataTest {

    private val sdf = SimpleDateFormat("MM-dd-yyyy", Locale.US).apply { isLenient = false }

    private val payload: JSONObject by lazy {
        val candidates = listOf(
            File("data/sample_data.json"),
            File("app/data/sample_data.json"),
            File("../app/data/sample_data.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("sample_data.json not found; looked in ${candidates.map { it.absolutePath }}")
        JSONObject(file.readText())
    }

    private fun array(key: String): JSONArray = payload.optJSONArray(key) ?: JSONArray()

    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }

    private fun assertParses(raw: String, what: String) {
        try {
            sdf.parse(raw)
        } catch (e: Exception) {
            throw AssertionError("$what is not an MM-dd-yyyy date: '$raw'")
        }
    }

    /** Mirrors JsonSyncEngine.participantKey, which is what attendance and grade rows are matched on. */
    private fun participantKey(student: JSONObject): String {
        val firstClass = student.optJSONArray("classNamesJson")?.optString(0, "") ?: ""
        return "${student.optString("lastName")}_${student.optString("firstName")}_$firstClass"
    }

    private val students by lazy { array("students").map { it } }
    private val classNames by lazy { array("classrooms").map { it.getString("name") }.toSet() }
    private val identifiers by lazy { students.map { participantKey(it) }.toSet() }

    // --- the dataset is actually substantial --------------------------------------------------

    @Test
    fun thereIsEnoughDataToDemoTheApp() {
        assertTrue("expected a roster worth scrolling", students.size >= 30)
        assertTrue("expected several classes", classNames.size >= 4)
        assertTrue("expected multiple assessments", array("gradeBook").length() >= 10)
        assertTrue("expected attendance sheets", array("attendanceRecord").length() >= 2)
        assertTrue("expected grading periods", array("gradingTerms").length() >= 2)
        assertTrue("expected weighted categories", array("assessmentCategories").length() >= 2)
        assertTrue("expected rubrics", array("rubrics").length() >= 1)
        assertTrue("expected saved filters", array("savedFilters").length() >= 3)
        assertTrue("expected message templates", array("messageTemplates").length() >= 3)
    }

    @Test
    fun everyStudentIsFullyPopulated() {
        students.forEach { s ->
            val who = "${s.optString("lastName")}, ${s.optString("firstName")}"
            assertTrue("$who has no first name", s.optString("firstName").isNotBlank())
            assertTrue("$who has no last name", s.optString("lastName").isNotBlank())
            assertTrue("$who has an odd gender", s.optString("gender") in setOf("M", "F"))
            assertTrue("$who has no address", s.optString("address").isNotBlank())
            assertTrue("$who has no contact number", s.optString("contactNumber").isNotBlank())
            assertParses(s.optString("birthday"), "$who birthday")

            val guardians = s.optJSONArray("guardiansJson") ?: JSONArray()
            assertTrue("$who has no guardian", guardians.length() >= 1)
            guardians.map { it }.forEach { g ->
                assertTrue("$who has a nameless guardian", g.optString("name").isNotBlank())
                assertTrue("$who has a guardian with no phone", (g.optJSONArray("phones")?.length() ?: 0) >= 1)
            }

            val custom = s.optJSONObject("customDataJson") ?: JSONObject()
            assertTrue("$who has no custom field values", custom.length() > 0)
        }
    }

    @Test
    fun addressesLookLikeSomethingAMapCouldFind() {
        // Street, barangay, city, region - enough for a geocoder to resolve, not just "88 Main Road".
        students.forEach { s ->
            val address = s.optString("address")
            assertTrue(
                "address is not specific enough to map: '$address'",
                address.count { it == ',' } >= 2 && address.any { it.isDigit() }
            )
        }
    }

    // --- cross-references the importer resolves by name ---------------------------------------

    @Test
    fun everyClassAStudentBelongsToExists() {
        // A class name that is not in `classrooms` leaves the student unfiltered and unseatable.
        students.forEach { s ->
            val classes = s.optJSONArray("classNamesJson") ?: JSONArray()
            assertTrue("${participantKey(s)} belongs to no class", classes.length() >= 1)
            (0 until classes.length()).forEach { i ->
                val name = classes.getString(i)
                assertTrue("unknown class '$name'", classNames.contains(name))
            }
            assertEquals(
                "classRoom must match the first entry of classNamesJson, which is what identifiers use",
                classes.getString(0),
                s.optString("classRoom")
            )

            val seating = s.optJSONObject("seatingJson") ?: JSONObject()
            seating.keys().forEach { seatedClass ->
                assertTrue("seated in '$seatedClass', which they do not belong to", classes.toString().contains(seatedClass))
                val coords = seating.getJSONObject(seatedClass)
                listOf("x", "y").forEach { axis ->
                    val v = coords.getDouble(axis)
                    assertTrue("seat $axis out of the 0..1 chart: $v", v in 0.0..1.0)
                }
            }
        }
    }

    @Test
    fun everyGradeRowResolvesToAStudent() {
        var scored = 0
        array("gradeBook").map { it }.forEach { column ->
            val name = column.optString("name")
            val maxPoints = column.optDouble("maxPoints", 100.0)
            assertParses(column.optString("examDate"), "$name examDate")
            assertParses(column.optString("checkDate"), "$name checkDate")

            val grades = column.optJSONArray("grades") ?: JSONArray()
            assertTrue("$name has no grades at all", grades.length() > 0)
            grades.map { it }.forEach { row ->
                val id = row.optString("studentIdentifier")
                assertTrue("$name references an unknown student '$id'", identifiers.contains(id))

                val raw = row.optString("score")
                if (raw.isNotBlank()) {
                    scored++
                    val value = raw.toDoubleOrNull()
                        ?: throw AssertionError("$name has a non-numeric score '$raw' for $id")
                    assertTrue("$name score $value exceeds maxPoints $maxPoints", value in 0.0..maxPoints)
                }
            }
        }
        assertTrue("the gradebook should arrive filled in, not blank", scored >= 300)
    }

    @Test
    fun everyAssessmentPointsAtRealTermsCategoriesAndRubrics() {
        val terms = array("gradingTerms").map { it.getString("name") }.toSet()
        val categories = array("assessmentCategories").map { it.getString("name") }.toSet()
        val rubrics = array("rubrics").map { it.getString("name") }.toSet()

        array("gradeBook").map { it }.forEach { column ->
            val name = column.optString("name")
            val term = column.optString("term")
            val category = column.optString("category")
            val rubric = column.optString("rubric")

            assertTrue("$name is in an unknown term '$term'", term.isEmpty() || terms.contains(term))
            assertTrue("$name is in an unknown category '$category'", category.isEmpty() || categories.contains(category))
            assertTrue("$name uses an unknown rubric '$rubric'", rubric.isEmpty() || rubrics.contains(rubric))
            // Unresolvable links fall back to 0, which quietly drops work out of a weighted grade.
            assertTrue("$name has no grading period", term.isNotEmpty())
            assertTrue("$name has no category, so it cannot be weighted", category.isNotEmpty())
        }
    }

    @Test
    fun theActiveQuarterProducesACredibleSpreadOfGrades() {
        // Run the real weighting over the active period. A demo where most of the section is
        // failing reads as a broken app rather than as a class, and one where everybody is at
        // 100 shows nothing at all - the Early Warning screen needs a few students to surface.
        val activeTerm = array("gradingTerms").map { it }
            .first { it.optBoolean("isActive", false) }
            .getString("name")
        val weights = array("assessmentCategories").map { it }
            .associate { it.getString("name") to it.getDouble("weight") }
        val columns = array("gradeBook").map { it }.filter { it.optString("term") == activeTerm }

        val percentages = identifiers.mapNotNull { id ->
            val earned = mutableMapOf<String, Double>()
            val possible = mutableMapOf<String, Double>()
            columns.forEach { column ->
                val score = (column.optJSONArray("grades") ?: JSONArray()).map { it }
                    .firstOrNull { it.optString("studentIdentifier") == id }
                    ?.optString("score")
                    ?.toDoubleOrNull() ?: return@forEach
                val category = column.optString("category")
                earned[category] = (earned[category] ?: 0.0) + score
                possible[category] = (possible[category] ?: 0.0) + column.optDouble("maxPoints")
            }
            val contributing = earned.keys.filter { (possible[it] ?: 0.0) > 0.0 && (weights[it] ?: 0.0) > 0.0 }
            if (contributing.isEmpty()) return@mapNotNull null
            val weightSum = contributing.sumOf { weights.getValue(it) }
            contributing.sumOf { (earned.getValue(it) / possible.getValue(it) * 100.0) * weights.getValue(it) } / weightSum
        }

        assertTrue("every student should have a running grade in the active quarter", percentages.size == students.size)
        val median = percentages.sorted()[percentages.size / 2]
        val failing = percentages.count { it < 75.0 }

        assertTrue("median grade of $median is implausibly low for a class", median in 78.0..92.0)
        assertTrue("nobody is struggling, so Early Warning has nothing to show", failing >= 1)
        assertTrue("$failing of ${percentages.size} below passing looks broken, not realistic", failing <= percentages.size / 4)
    }

    @Test
    fun categoryWeightsAddUpToAWholeGrade() {
        val total = array("assessmentCategories").map { it.getDouble("weight") }.sum()
        assertEquals("weights should total 100 so no grade leaks into the leftover bucket", 100.0, total, 0.001)
    }

    @Test
    fun exactlyOneGradingPeriodIsActive() {
        val active = array("gradingTerms").map { it }.filter { it.optBoolean("isActive", false) }
        assertEquals("the gradebook opens on the active period, so there must be exactly one", 1, active.size)
        array("gradingTerms").map { it }.forEach {
            assertParses(it.optString("startDate"), "${it.optString("name")} startDate")
            assertParses(it.optString("endDate"), "${it.optString("name")} endDate")
        }
    }

    @Test
    fun rubricLevelsDescendInValue() {
        array("rubrics").map { it }.forEach { rubric ->
            val name = rubric.optString("name")
            val levels = rubric.optJSONArray("levels") ?: JSONArray()
            assertTrue("$name has no levels", levels.length() >= 2)

            val points = levels.map { it.getDouble("points") }
            assertEquals("$name levels should read best-first", points.sortedDescending(), points)
            levels.map { it }.forEach {
                assertTrue("$name has an unlabelled level", it.optString("label").isNotBlank())
                assertTrue("$name has a level with no descriptor", it.optString("descriptor").isNotBlank())
            }
        }
    }

    // --- attendance ---------------------------------------------------------------------------

    @Test
    fun attendanceLogsAreResolvableAndUseKnownStatuses() {
        val allowed = setOf("PRESENT", "ABSENT", "EXCUSED", "REMOVED", "NOT_SET")
        var logs = 0

        array("attendanceRecord").map { it }.forEach { record ->
            val name = record.optString("name")
            assertParses(record.optString("startDate"), "$name startDate")
            assertParses(record.optString("endDate"), "$name endDate")

            val participants = record.optJSONArray("participants") ?: JSONArray()
            assertTrue("$name has no participants", participants.length() > 0)
            participants.map { it }.forEach { p ->
                val id = p.optString("studentIdentifier")
                assertTrue("$name references an unknown student '$id'", identifiers.contains(id))

                val attendance = p.optJSONObject("attendance") ?: JSONObject()
                assertTrue("$name has an empty sheet for $id", attendance.length() > 0)
                attendance.keys().forEach { day ->
                    assertParses(day, "$name attendance date")
                    val status = attendance.getString(day)
                    assertTrue("$name has an unknown status '$status'", allowed.contains(status))
                    logs++
                }
            }
        }
        assertTrue("attendance should span enough days to be worth looking at", logs >= 400)
    }

    @Test
    fun attendanceIsMostlyMarkedRatherThanMostlyBlank() {
        var marked = 0
        var total = 0
        array("attendanceRecord").map { it }.forEach { record ->
            (record.optJSONArray("participants") ?: JSONArray()).map { it }.forEach { p ->
                val attendance = p.optJSONObject("attendance") ?: JSONObject()
                attendance.keys().forEach { day ->
                    total++
                    if (attendance.getString(day) != "NOT_SET") marked++
                }
            }
        }
        assertTrue("most days should already be taken: $marked of $total", marked > total * 0.7)
    }

    // --- the remaining collections ------------------------------------------------------------

    @Test
    fun savedFiltersReferenceFieldsThatResolve() {
        val customFields = students
            .flatMap { s -> (s.optJSONObject("customDataJson") ?: JSONObject()).keys().asSequence().toList() }
            .toSet()
        val builtIn = setOf(
            "First Name", "Last Name", "Gender", "Address", "Home Address", "Student Contact",
            "Class", "Classroom", "Age", "Birthday", "Guardian Name", "Guardian Contact"
        )

        array("savedFilters").map { it }.forEach { f ->
            val name = f.optString("name")
            val field = f.optString("field")
            assertTrue("filter '$name' has no name", name.isNotBlank())
            assertTrue(
                "filter '$name' targets '$field', which is neither a built-in nor a custom field",
                builtIn.contains(field) || customFields.contains(field)
            )
            if (f.optString("comparison") == "member_of") {
                assertTrue(
                    "filter '$name' looks for class '${f.optString("value1")}', which does not exist",
                    classNames.contains(f.optString("value1"))
                )
            }
        }
    }

    @Test
    fun everySavedFilterActuallyMatchesSomeone() {
        // Run the real engine over the real dataset. A demo filter that opens onto an empty list
        // reads as a broken feature, and the operator tokens have to be the ones the filter sheet
        // itself writes - "member of", not "member_of" - or editing one shows a blank dropdown.
        val roster = students.map { s ->
            StudentEntity(
                firstName = s.optString("firstName"),
                lastName = s.optString("lastName"),
                gender = s.optString("gender"),
                birthday = sdf.parse(s.optString("birthday"))!!.time,
                address = s.optString("address"),
                contactNumber = s.optString("contactNumber"),
                guardiansJson = (s.optJSONArray("guardiansJson") ?: JSONArray()).toString(),
                customDataJson = (s.optJSONObject("customDataJson") ?: JSONObject()).toString(),
                classNamesJson = (s.optJSONArray("classNamesJson") ?: JSONArray()).toString()
            )
        }

        array("savedFilters").map { it }.forEach { f ->
            val name = f.getString("name")
            val matches = roster.count { student ->
                FilterEngine.evaluateCondition(
                    FilterEngine.getFieldValue(student, f.getString("field")),
                    f.getString("comparison"),
                    f.optString("value1"),
                    f.optString("value2")
                )
            }
            assertTrue("saved filter '$name' matches nobody in the sample roster", matches > 0)
            assertTrue("saved filter '$name' matches the entire roster, so it filters nothing", matches < roster.size)
        }
    }

    @Test
    fun formTemplatesCoverEveryCustomFieldInUse() {
        val declared = array("formTemplates").map { it.getString("fieldName") }.toSet()
        val used = students
            .flatMap { s -> (s.optJSONObject("customDataJson") ?: JSONObject()).keys().asSequence().toList() }
            .toSet()
        assertTrue("custom fields used but not declared: ${used - declared}", declared.containsAll(used))

        array("formTemplates").map { it }.forEach { t ->
            val type = t.optString("fieldType")
            assertTrue("unknown field type '$type'", type in setOf("TEXT", "NUMBER", "DROPDOWN"))
            if (type == "DROPDOWN") {
                assertTrue("${t.optString("fieldName")} is a dropdown with no options", (t.optJSONArray("options")?.length() ?: 0) > 0)
            }
        }
    }

    @Test
    fun dropdownValuesInUseAreOfferedByTheirField() {
        val options = array("formTemplates").map { it }
            .filter { it.optString("fieldType") == "DROPDOWN" }
            .associate { t ->
                val raw = t.optJSONArray("options") ?: JSONArray()
                t.getString("fieldName") to (0 until raw.length()).map { raw.getString(it) }
            }

        students.forEach { s ->
            val custom = s.optJSONObject("customDataJson") ?: JSONObject()
            custom.keys().forEach { key ->
                val allowed = options[key] ?: return@forEach
                val value = custom.getString(key)
                assertTrue("'$value' is not one of the options for '$key'", value.isEmpty() || allowed.contains(value))
            }
        }
    }

    @Test
    fun behaviourIncidentsAreWellFormed() {
        var incidents = 0
        students.forEach { s ->
            (s.optJSONArray("behaviorIncidents") ?: JSONArray()).map { it }.forEach { b ->
                incidents++
                assertTrue("an incident has no title", b.optString("title").isNotBlank())
                assertTrue("an incident has no description", b.optString("description").isNotBlank())
                assertTrue(
                    "unknown incident category '${b.optString("category")}'",
                    b.optString("category") in setOf("Positive", "Negative", "Neutral")
                )
                assertParses(b.optString("date"), "incident date")
            }
        }
        assertTrue("expected a meaningful behaviour log", incidents >= 30)
    }

    @Test
    fun participationCountersResolveAndAreUneven() {
        val entries = array("participation").map { it }
        entries.forEach {
            val id = it.optString("studentIdentifier")
            assertTrue("participation references an unknown student '$id'", identifiers.contains(id))
            assertTrue("participation is in an unknown class", classNames.contains(it.optString("className")))
            assertTrue("a participation counter is negative", it.optInt("timesCalled") >= 0)
        }
        if (entries.isNotEmpty()) {
            val counts = entries.map { it.optInt("timesCalled") }
            assertTrue(
                "an equity view is only interesting when the counts differ",
                counts.max() - counts.min() >= 3
            )
        }
    }

    @Test
    fun noStudentAppearsTwice() {
        // The importer matches on first + last + birthday; a collision would merge two students.
        val keys = students.map {
            "${it.optString("firstName").lowercase()}_${it.optString("lastName").lowercase()}_${it.optString("birthday")}"
        }
        assertEquals("duplicate students would merge on import", keys.size, keys.toSet().size)
        assertEquals("identifiers must be unique to resolve grades", students.size, identifiers.size)
    }
}
