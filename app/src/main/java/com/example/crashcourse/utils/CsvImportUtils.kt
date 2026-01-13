package com.example.crashcourse.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvImportUtils {

    private const val TAG = "CsvImportUtils"

    // ---------- DATA MODELS ----------

    data class CsvStudentData(
        val studentId: String,
        val name: String,
        val className: String = "",
        val subClass: String = "",
        val grade: String = "",
        val subGrade: String = "",
        val program: String = "",
        val role: String = "",
        val photoUrl: String = ""
    )

    data class CsvParseResult(
        val students: List<CsvStudentData>,
        val errors: List<String>,
        val totalRows: Int,
        val validRows: Int
    )

    // ---------- PUBLIC API ----------

    suspend fun parseCsvFile(context: Context, uri: Uri): CsvParseResult {
        val students = mutableListOf<CsvStudentData>()
        val errors = mutableListOf<String>()
        var totalRows = 0
        var validRows = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    var lineNumber = 0
                    var headers: List<String>? = null

                    while (reader.readLine().also { line = it } != null) {
                        lineNumber++

                        val currentLine = line?.trim() ?: continue
                        if (currentLine.isEmpty()) continue

                        val columns = parseCsvLine(currentLine)

                        // ---------- HEADER ----------
                        if (headers == null) {
                            headers = columns.map { normalizeHeader(it) }
                            Log.d(TAG, "Normalized headers: $headers")
                            continue
                        }

                        totalRows++

                        try {
                            val student = parseStudentRow(headers, columns, lineNumber)
                            if (student != null) {
                                students.add(student)
                                validRows++
                            } else {
                                errors.add("Line $lineNumber: Missing required fields (studentId or name)")
                            }
                        } catch (e: Exception) {
                            errors.add("Line $lineNumber: ${e.message ?: "Parsing error"}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Failed to read CSV file: ${e.message ?: "Unknown error"}")
        }

        return CsvParseResult(
            students = students,
            errors = errors,
            totalRows = totalRows,
            validRows = validRows
        )
    }

    // ---------- CSV PARSING ----------

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            when (val char = line[i]) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> {
                    if (inQuotes) {
                        current.append(char)
                    } else {
                        result.add(current.toString().trim())
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
            i++
        }

        result.add(current.toString().trim())
        return result
    }

    // ---------- STUDENT ROW PARSING ----------

    private fun parseStudentRow(
        headers: List<String>,
        columns: List<String>,
        lineNumber: Int
    ): CsvStudentData? {

        val headerMap = headers.mapIndexed { index, header ->
            header to index
        }.toMap()

        fun getValue(possibleHeaders: List<String>): String {
            for (rawHeader in possibleHeaders) {
                val key = normalizeHeader(rawHeader)
                val index = headerMap[key]
                if (index != null && index < columns.size) {
                    return columns[index].trim()
                }
            }
            return ""
        }

        val studentId = getValue(
            listOf("studentid", "student id", "student_id", "id")
        ).trim()

        val name = getValue(
            listOf("name", "fullname", "full name")
        ).trim()

        if (studentId.isEmpty() || name.isEmpty()) {
            Log.w(TAG, "Invalid row $lineNumber → studentId='$studentId', name='$name'")
            return null
        }

        return CsvStudentData(
            studentId = studentId,
            name = name,
            className = getValue(listOf("class", "classname", "class name")),
            subClass = getValue(listOf("subclass", "sub class", "sub_class")),
            grade = getValue(listOf("grade", "level")),
            subGrade = getValue(listOf("subgrade", "sub grade", "sub_grade")),
            program = getValue(listOf("program", "course")),
            role = getValue(listOf("role", "position", "type")).ifEmpty { "Student" },
            photoUrl = getValue(listOf("photourl", "photo url", "photo", "image"))
        )
    }

    // ---------- HEADER NORMALIZATION ----------

    private fun normalizeHeader(header: String): String =
        header
            .lowercase()
            .trim()
            .replace(" ", "")
            .replace("_", "")

    // ---------- SAMPLE CSV ----------

    fun generateSampleCsv(): String {
        return """
            Student ID,Name,Class,Sub Class,Grade,Sub Grade,Program,Role,Photo URL
            STU001,John Doe,Class A,Sub A1,Grade 1,Sub 1A,Program X,Student,https://example.com/john.jpg
            STU002,Jane Smith,Class B,Sub B1,Grade 2,Sub 2A,Program Y,Student,https://example.com/jane.jpg
        """.trimIndent()
    }
}
