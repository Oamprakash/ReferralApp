package com.referral.app.resume

/** What we could read from a resume. Anything not found is null/empty; the user checks it before saving. */
data class ParsedResume(
    val name: String? = null,
    val email: String? = null,
    /** E.164, e.g. +919876543210 (Indian mobiles only). */
    val phone: String? = null,
    val experienceYears: Int? = null,
    val skills: List<String> = emptyList(),
    val city: String? = null,
) {
    val isEmpty get() = name == null && email == null && phone == null && experienceYears == null && skills.isEmpty() && city == null
}

/**
 * Pulls profile fields out of resume text with plain rules (no network, no AI): the text never
 * leaves the phone. Works on the text PdfBox extracts, which keeps line breaks roughly as laid out.
 * Pure Kotlin, so it's covered by unit tests (ResumeParserTest).
 */
object ResumeParser {

    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

    // Indian mobile: optional +91 / 91 / 0, then 10 digits starting 6-9, with spaces or dashes in the usual places
    private val PHONE = Regex("""(?<![\d+])(?:\(?\+?91\)?[\s-]*|0)?([6-9]\d{2}[\s-]?\d{2}[\s-]?\d{5}|[6-9]\d{4}[\s-]?\d{5})(?!\d)""")

    // "6 years of experience", "6+ yrs experience", "5.5 years of professional experience in Android"
    private val YEARS_BEFORE = Regex(
        """(\d{1,2}(?:\.\d{1,2})?)\s*\+?\s*(?:years?|yrs?)\.?(?:\s+(?:and\s+\d{1,2}\s+months?))?\s+(?:of\s+)?(?:[a-z/&-]+\s+){0,4}?(?:experience|exp\b)""",
        RegexOption.IGNORE_CASE,
    )
    // "Total experience: 6.5 years", "Experience - 7 Yrs", "Overall Experience 4+ years"
    private val YEARS_AFTER = Regex(
        """experience\s*(?:of|:|-|–)?\s*(?:about|around|over|nearly)?\s*(\d{1,2}(?:\.\d{1,2})?)\s*\+?\s*(?:years?|yrs?)""",
        RegexOption.IGNORE_CASE,
    )

    private val NAME_LABEL = Regex("""^\s*(?:full\s+)?name\s*[:\-–]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val NAME_LINE = Regex("""^[A-Za-z][A-Za-z.' ]{1,48}[A-Za-z.]$""")

    /** Lines that look like headings or labels, never a person's name. */
    private val NOT_A_NAME = Regex(
        """\b(resume|curriculum|vitae|cv|profile|summary|objective|contact|email|e-mail|phone|mobile|address|""" +
            """experience|education|skills|projects?|career|developer|engineer|manager|analyst|consultant|""" +
            """lead|senior|junior|intern|tester|designer|architect|india|linkedin|github|portfolio)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val CITIES: List<Pair<String, Regex>> = listOf(
        "Chennai" to "chennai|madras",
        "Bengaluru" to "bengaluru|bangalore",
        "Hyderabad" to "hyderabad|secunderabad",
        "Pune" to "pune",
        "Mumbai" to "mumbai|bombay|navi mumbai|thane",
        "Noida" to "noida|greater noida",
        "Gurugram" to "gurugram|gurgaon",
        "Delhi" to "new delhi|delhi",
        "Kolkata" to "kolkata|calcutta",
        "Kochi" to "kochi|cochin|ernakulam",
        "Thiruvananthapuram" to "thiruvananthapuram|trivandrum|technopark",
        "Coimbatore" to "coimbatore",
        "Madurai" to "madurai",
        "Tiruchirappalli" to "tiruchirappalli|trichy",
        "Ahmedabad" to "ahmedabad",
        "Jaipur" to "jaipur",
        "Chandigarh" to "chandigarh|mohali",
        "Indore" to "indore",
        "Bhubaneswar" to "bhubaneswar",
        "Mysuru" to "mysuru|mysore",
        "Visakhapatnam" to "visakhapatnam|vizag",
        "Nagpur" to "nagpur",
    ).map { (c, re) -> c to Regex("""(?<![a-z])(?:$re)(?![a-z])""") }

    fun parse(rawText: String): ParsedResume {
        val text = rawText
            .replace(' ', ' ')
            .replace(Regex("[‐-―]"), "-")
            .replace(Regex("[ \t]+"), " ")
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        return ParsedResume(
            name = findName(lines),
            email = EMAIL.find(text)?.value?.lowercase()?.trimEnd('.'),
            phone = findPhone(text),
            experienceYears = findYears(text),
            skills = Skills.find(text),
            city = findCity(lines),
        )
    }

    private fun findPhone(text: String): String? {
        val m = PHONE.find(text) ?: return null
        val digits = m.groupValues[1].filter(Char::isDigit)
        return if (digits.length == 10) "+91$digits" else null
    }

    /** The largest believable "N years … experience"; fractions round down (5.5 -> 5). */
    private fun findYears(text: String): Int? =
        (YEARS_BEFORE.findAll(text) + YEARS_AFTER.findAll(text))
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 0.0..45.0 }
            .maxOrNull()
            ?.toInt()

    private fun findName(lines: List<String>): String? {
        lines.take(30).firstNotNullOfOrNull { NAME_LABEL.find(it)?.groupValues?.get(1) }
            ?.let { cleanName(it) }
            ?.let { return it }

        // Otherwise: the first short, letters-only line near the top, before contact details pile up.
        return lines.take(8)
            .map { it.substringBefore('|').substringBefore(" - ").trim() }
            .firstOrNull { line ->
                val words = line.split(' ').filter { it.isNotBlank() }
                words.size in 1..4 && NAME_LINE.matches(line) && !NOT_A_NAME.containsMatchIn(line) &&
                    words.any { w -> w.trimEnd('.').length >= 2 }
            }
            ?.let { cleanName(it) }
    }

    private fun cleanName(raw: String): String? {
        val s = raw.trim().replace(Regex("\\s+"), " ").trim('.', ',', ' ')
        if (s.length !in 2..50 || s.any(Char::isDigit) || '@' in s) return null
        // ALL CAPS or all lowercase -> Title Case; mixed case is kept as written (e.g. "McDonald")
        val fixCase = s == s.uppercase() || s == s.lowercase()
        return if (!fixCase) s else s.split(' ').joinToString(" ") { w ->
            if (w.length <= 2 && w.endsWith('.')) w.uppercase()          // initials: "R." -> "R."
            else w.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    /** "Location: …" or "Address: …" wins; otherwise the first known city in the top part of the page. */
    private fun findCity(lines: List<String>): String? {
        val labelled = lines.take(40).firstOrNull {
            Regex("""^\s*(location|address|city|current location)\s*[:\-]""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }
        val candidates = listOfNotNull(labelled) + lines.take(12)
        for (line in candidates) {
            val lower = line.lowercase()
            CITIES.firstOrNull { (_, re) -> re.containsMatchIn(lower) }?.let { return it.first }
        }
        return null
    }
}
