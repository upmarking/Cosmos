package app.cosmos.com.screens.events

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class EventGradient(val id: String, val label: String, val brush: Brush) {
    COSMOS_GLOW(
        "gradient:cosmos-glow",
        "Cosmos Glow",
        Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF581C87)))
    ),
    SUNSET_AURORA(
        "gradient:sunset-aurora",
        "Sunset Aurora",
        Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF701A75), Color(0xFFF43F5E)))
    ),
    CYBER_NEON(
        "gradient:cyber-neon",
        "Cyber Neon",
        Brush.linearGradient(listOf(Color(0xFF020617), Color(0xFF0F766E), Color(0xFF06B6D4)))
    ),
    DEEP_SPACE(
        "gradient:deep-space",
        "Deep Space",
        Brush.linearGradient(listOf(Color(0xFF030712), Color(0xFF1E1B4B), Color(0xFFDB2777)))
    ),
    EMERALD_MATRIX(
        "gradient:emerald-matrix",
        "Emerald Matrix",
        Brush.linearGradient(listOf(Color(0xFF022C22), Color(0xFF065F46), Color(0xFF10B981)))
    );

    companion object {
        fun fromId(id: String): EventGradient {
            return values().firstOrNull { it.id == id } ?: COSMOS_GLOW
        }
    }
}

fun addMinutesToTimeString(timeStr: String, minutesToAdd: Int): String {
    val regex = """(\d{1,2}):(\d{2})\s*(AM|PM)(?:\s+(\w+))?""".toRegex(RegexOption.IGNORE_CASE)
    val match = regex.find(timeStr.trim())
    if (match != null) {
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val amPm = match.groupValues[3].uppercase()
        val tz = match.groupValues[4]
        
        if (amPm == "PM" && hour < 12) hour += 12
        if (amPm == "AM" && hour == 12) hour = 0
        
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
        calendar.set(java.util.Calendar.MINUTE, minute)
        calendar.add(java.util.Calendar.MINUTE, minutesToAdd)
        
        val newHour24 = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val newMinute = calendar.get(java.util.Calendar.MINUTE)
        
        val newAmPm = if (newHour24 >= 12) "PM" else "AM"
        var newHour12 = newHour24 % 12
        if (newHour12 == 0) newHour12 = 12
        
        val timeFormatted = String.format(java.util.Locale.US, "%d:%02d %s", newHour12, newMinute, newAmPm)
        return if (!tz.isNullOrEmpty()) "$timeFormatted $tz" else timeFormatted
    }
    return timeStr
}

fun getRoundStartTime(eventTime: String, rounds: List<app.cosmos.com.data.model.EventRound>, roundIndex: Int): String {
    var offset = 15 // 15 mins for welcome session
    for (i in 0 until roundIndex) {
        offset += rounds.getOrNull(i)?.duration ?: 15
    }
    return addMinutesToTimeString(eventTime, offset)
}

fun cropEventBitmap(context: android.content.Context, uri: Uri, zoom: Float, panFraction: Float): ByteArray? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val sourceBitmap = android.graphics.BitmapFactory.decodeStream(inputStream) ?: return null
        
        val W_orig = sourceBitmap.width.toFloat()
        val H_orig = sourceBitmap.height.toFloat()
        
        val W_canvas = 800f
        val H_canvas = 400f
        
        val R_img = W_orig / H_orig
        val R_canvas = 2.0f
        
        val S_cover = if (R_img > R_canvas) {
            H_canvas / H_orig
        } else {
            W_canvas / W_orig
        }
        
        val W_scaled = W_orig * S_cover
        val H_scaled = H_orig * S_cover
        
        val X_draw = (W_canvas - W_scaled) / 2f
        val Y_draw = (H_canvas - H_scaled) / 2f
        
        val croppedBitmap = android.graphics.Bitmap.createBitmap(800, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(croppedBitmap)
        
        val matrix = android.graphics.Matrix()
        matrix.postScale(S_cover, S_cover)
        matrix.postTranslate(X_draw, Y_draw)
        
        val panYCanvas = panFraction * H_canvas
        matrix.postTranslate(0f, panYCanvas)
        matrix.postScale(zoom, zoom, W_canvas / 2f, H_canvas / 2f)
        
        val paint = android.graphics.Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(sourceBitmap, matrix, paint)
        
        val outputStream = java.io.ByteArrayOutputStream()
        croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
        val croppedBytes = outputStream.toByteArray()
        
        sourceBitmap.recycle()
        croppedBitmap.recycle()
        
        croppedBytes
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun parseAndroidEventDate(dateStr: String): java.util.Date? {
    if (dateStr.isBlank()) return null
    val cleanDate = dateStr
        .replace("Today, ", "")
        .replace("Tomorrow, ", "")
        .replace("Next ", "")
        .replace("Monday, ", "")
        .replace("Tuesday, ", "")
        .replace("Wednesday, ", "")
        .replace("Thursday, ", "")
        .replace("Friday, ", "")
        .replace("Saturday, ", "")
        .replace("Sunday, ", "")
        .trim()
    val formats = listOf(
        SimpleDateFormat("MMM d, yyyy", Locale.US),
        SimpleDateFormat("MMMM d, yyyy", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    )
    for (format in formats) {
        try {
            val parsed = format.parse(cleanDate)
            if (parsed != null) return parsed
        } catch (e: Exception) {
            // ignore
        }
    }
    return null
}

fun truncateToMidnight(date: java.util.Date): java.util.Date {
    val cal = Calendar.getInstance()
    cal.time = date
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}

fun getAndroidDayHeaderLabel(date: java.util.Date): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    val tomorrow = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    val eventMidnight = truncateToMidnight(date)

    val dayFormat = SimpleDateFormat("EEEE", Locale.US)
    val monthFormat = SimpleDateFormat("MMMM d", Locale.US)
    val dayOfWeek = dayFormat.format(date)

    return when (eventMidnight.time) {
        today.time -> "Today / $dayOfWeek"
        tomorrow.time -> "Tomorrow / $dayOfWeek"
        else -> "${monthFormat.format(date)} / $dayOfWeek"
    }
}
