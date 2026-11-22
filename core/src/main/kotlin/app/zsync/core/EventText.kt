package app.zsync.core

data class EventInfo(val reward: String?, val kind: String?, val loginDays: Int?, val extras: List<String> = emptyList())

/** Reads reward and type out of an announcement's HTML; the game exposes no structured data for it. */
object EventText {
    private val tag = Regex("<[^>]+>")
    private val sentence = Regex("""(?i)\b(?:obtain|earn|receive)\s+(.+?)(?:\s+as\s+(?:rewards?|a reward)\b|[.!•]|$)""")
    private val qty = Regex("""\s*[×x]\s*(\d[\d,]*)""")
    private val junk = Regex("""(?i)upgrade materials?|materials|rewards?|various|\bmore\b|\betc\b|\bexp\b""")
    private val prefix = Regex("""(?i)^(?:the |an? |some )?(?:exclusive |additional )?""")
    private val top = Regex("""(?i)\bW-Engine\b(?!\s+(?:Microcontroller|Energy))|Bangboo|\bAgent\b|Calibrator""")
    private val days = Regex("""(?i)(\d+)\s+days""")

    fun plain(html: String): String = html.replace(tag, " ").replace("&nbsp;", " ").replace("&quot;", "\"")
        .replace("&amp;", "&").replace("&#39;", "'").replace(Regex("\\s+"), " ").replace(Regex("\\s+([,.!;])"), "$1").trim()

    fun parse(title: String, html: String): EventInfo {
        val text = plain(html)
        val items = sentence.findAll(text).flatMap { m ->
            m.groupValues[1].split(Regex(""",\s*(?:and\s+)?|\s+and\s+"""))
                .map { qty.replace(prefix.replace(it.trim(), ""), " ×$1").trim() }
                .filter { it.length in 3..40 && !junk.containsMatchIn(it) }
        }.toList()
        val main = items.withIndex().minByOrNull { (i, it) -> score(it) * 100 + i }?.value
        val kind = kind(title + " " + text)
        val loginDays = if (kind == "login") days.find(text)?.groupValues?.get(1)?.toIntOrNull() else null
        return EventInfo(main, kind, loginDays, items.filter { it != main }.distinctBy { baseName(it).lowercase() }.take(3))
    }

    private fun score(item: String) = when {
        top.containsMatchIn(item) -> 0
        item.contains("Encrypted Master Tape", true) -> 1
        item.contains("Polychrome", true) -> 2
        item.contains("Boopon", true) -> 3
        else -> 4
    }

    fun kind(t: String): String? = when {
        Regex("(?i)log ?in|sign ?in").containsMatchIn(t) -> "login"
        Regex("(?i)\\bfund\\b|\\bstore\\b|purchase|\\bshop\\b").containsMatchIn(t) -> "shop"
        Regex("(?i)puzzle|mini-?game|repair|racing|\\bgame\\b").containsMatchIn(t) -> "minigame"
        Regex("(?i)commission|combat|battle|challenge|enem(y|ies)").containsMatchIn(t) -> "combat"
        Regex("(?i)\\bstory\\b|\\bchapter\\b").containsMatchIn(t) -> "story"
        else -> null
    }

    fun iconKey(reward: String): String? = when {
        reward.contains("Encrypted Master Tape", true) -> ItemIcon.ENCRYPTED_TAPE
        reward.contains("Master Tape", true) -> ItemIcon.MASTER_TAPE
        reward.contains("Polychrome", true) -> ItemIcon.POLYCHROME
        reward.contains("Monochrome", true) -> ItemIcon.MONOCHROME
        reward.contains("Boopon", true) -> ItemIcon.BOOPON
        else -> null
    }

    fun baseName(reward: String) = qty.replace(reward, "").trim()
}
