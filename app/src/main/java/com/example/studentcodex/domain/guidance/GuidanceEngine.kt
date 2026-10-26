package com.example.studentcodex.domain.guidance

/**
 * Rule-based guidance assistant (Phase 1 foundation; full rule set in Phase 8).
 *
 * Contract: deterministic, keyword/phrase matching over normalized input with
 * explicit rule precedence. When confidence is low it must say so and offer a
 * safe next step - it never invents official university policies, dates, or
 * deadlines. Rules live in data (RULES), not in screen code, so they can be
 * revised and tested without touching UI.
 */
interface GuidanceEngine {
    /** Suggested starter topics shown when the conversation is empty. */
    val suggestedTopics: List<String>

    /** Match a user message to an answer. */
    fun respond(input: String): GuidanceAnswer
}

data class GuidanceAnswer(
    val text: String,
    val suggestions: List<String> = emptyList(),
)

/** Normalization: lowercase, collapse whitespace, strip punctuation. */
internal fun normalize(input: String): String =
    input.lowercase().trim().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ")

internal data class Rule(
    val keywords: Set<String>,
    /** Higher precedence wins when multiple rules match. */
    val precedence: Int,
    val answer: GuidanceAnswer,
)

class DemoGuidanceEngine : GuidanceEngine {

    override val suggestedTopics: List<String> = listOf(
        "How do I add an activity?",
        "How does the Activity Beacon work?",
        "Study tips for exams",
        "Community rules",
    )

    private val rules: List<Rule> = listOf(
        Rule(
            keywords = setOf("add activity", "add an activity", "manual activity", "create activity", "new activity", "record activity"),
            precedence = 30,
            answer = GuidanceAnswer(
                text = "Manual activities are for deadlines your teachers give you face-to-face " +
                    "or that are not published online: open the Activity Beacon tab, and use the " +
                    "add button on the upcoming list. You choose the course, a due date, and an " +
                    "optional effort estimate. If a similar activity already exists, Student Codex " +
                    "will ask whether it is the same one before saving - nothing is merged silently.",
                suggestions = listOf("How does matching work?", "How does the Activity Beacon work?"),
            ),
        ),
        Rule(
            keywords = setOf("activity beacon", "beacon", "workload", "upcoming", "deadline", "deadlines", "calendar"),
            precedence = 25,
            answer = GuidanceAnswer(
                text = "The Activity Beacon combines every deadline you are responsible for: items " +
                    "imported from Google Classroom once you connect it, and activities you record " +
                    "manually. Each item is counted exactly once - if several sources describe the " +
                    "same assignment they are linked to one activity, not duplicated. The list is " +
                    "grouped by due date so you can see what lands today, tomorrow, and this week.",
                suggestions = listOf("How do I connect Google Classroom?", "How do I add an activity?"),
            ),
        ),
        Rule(
            keywords = setOf("classroom", "google", "sync", "import", "connect"),
            precedence = 20,
            answer = GuidanceAnswer(
                text = "Google Classroom import is read-only: Student Codex only ever reads the " +
                    "coursework and announcements you are authorized to see, and you connect it " +
                    "from your Profile tab. Connecting is per-account - it never shares other " +
                    "students' data, and disconnecting removes access at any time.",
                suggestions = listOf("How does the Activity Beacon work?"),
            ),
        ),
        Rule(
            keywords = setOf("community", "post", "anonymous", "comment", "reply", "report", "moderation"),
            precedence = 15,
            answer = GuidanceAnswer(
                text = "The community is for study-related posts with up to three images. You can " +
                    "post anonymously - your name is hidden from other students, and you can edit " +
                    "or delete only your own posts and comments. If something breaks the rules, " +
                    "report it and moderators will review it.",
                suggestions = listOf("Community rules", "Study tips for exams"),
            ),
        ),
        Rule(
            keywords = setOf("study", "exam", "test", "cram", "review", "memorize", "focus", "concentrate"),
            precedence = 10,
            answer = GuidanceAnswer(
                text = "A dependable pattern: split the material into short sessions across days " +
                    "instead of one long cram, test yourself instead of re-reading, and start from " +
                    "the topics with the nearest deadlines - the Activity Beacon list is sorted " +
                    "exactly for that. Sleep before the exam beats one extra overnight hour.",
                suggestions = listOf("How do I plan my week?", "How does the Activity Beacon work?"),
            ),
        ),
        Rule(
            keywords = setOf("plan", "time management", "schedule", "week", "prioritize", "procrastinate"),
            precedence = 10,
            answer = GuidanceAnswer(
                text = "Start from what is due, not from what feels urgent: check the Beacon list, " +
                    "block the nearest deadlines first, and leave a buffer for the ones with no " +
                    "effort estimate yet. Two review moments a week - for example Sunday and " +
                    "Wednesday - are usually enough to keep the plan honest.",
                suggestions = listOf("Study tips for exams", "How does the Activity Beacon work?"),
            ),
        ),
    )

    override fun respond(input: String): GuidanceAnswer {
        val text = normalize(input)
        if (text.isEmpty()) {
            return GuidanceAnswer(
                text = "Ask me anything about using Student Codex, planning your study time, or the community rules.",
                suggestions = suggestedTopics,
            )
        }
        val best = rules
            .filter { rule -> rule.keywords.any { it in text } }
            .maxByOrNull { it.precedence }
        return best?.answer ?: GuidanceAnswer(
            text = "I do not have a reliable answer for that yet, and I would rather not guess. " +
                "Try one of the topics below, or rephrase your question - for official university " +
                "policies, dates, or instructor instructions, always check the official source.",
            suggestions = suggestedTopics.take(2),
        )
    }
}
