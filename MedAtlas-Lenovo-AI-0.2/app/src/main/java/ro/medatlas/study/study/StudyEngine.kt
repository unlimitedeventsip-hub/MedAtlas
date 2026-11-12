package ro.medatlas.study.study

/** Offline, deterministic study assistance. This is NOT a generative AI model. */
object StudyEngine {
    private val stop = setOf(
        "acest", "aceasta", "aceste", "acesta", "sunt", "pentru", "prin", "care", "dintre", "fiind", "dupa", "asupra", "foarte", "poate", "acolo", "astfel", "atunci", "trebuie", "pacient", "pacienti", "clinice", "prezinta", "este", "fost", "sau", "the", "with", "from", "that", "this", "where", "there", "about", "into", "other", "between", "medatlas", "slide", "pagina"
    )
    data class Question(val prompt: String, val options: List<String>, val correct: Int, val source: String)

    fun segments(text: String): List<String> = text.replace(Regex("[ \\t]+"), " ")
        .split(Regex("(?<=[.!?])\\s+|\\n+"))
        .map { it.trim().trim('-', '•', ' ') }
        .filter { it.length in 35..380 && it.count { c -> c.isLetter() } > 20 }

    private fun score(s: String): Int {
        val l = s.lowercase()
        return (if (s.any { it.isDigit() }) 4 else 0) +
            (if (listOf("diagnostic", "tratament", "criteri", "doz", "diferenț", "defini", "clasific", "mecanism", "contraindica", "semn", "risc", "recomand", "investiga").any { it in l }) 5 else 0) +
            (if (":" in s || ">" in s || "%" in s) 2 else 0) +
            (if (s.length in 65..220) 2 else 0)
    }

    fun importance(text: String): Int = score(text)

    fun summary(text: String, maxPoints: Int = 8): List<String> = segments(text).withIndex()
        .sortedWith(compareByDescending<IndexedValue<String>> { score(it.value) }.thenBy { it.index })
        .take(maxPoints).sortedBy { it.index }.map { it.value }

    fun terms(text: String): List<String> {
        val reg = Regex("[\\p{L}][\\p{L}\\-]{4,}")
        val counters = LinkedHashMap<String, Int>()
        reg.findAll(text.lowercase()).map { it.value }.filter { it !in stop }.forEach { counters[it] = (counters[it] ?: 0) + 1 }
        return counters.entries.sortedByDescending { it.value }.take(80).map { it.key }
    }

    fun quiz(text: String, max: Int = 10): List<Question> {
        val vocabulary = terms(text)
        if (vocabulary.size < 5) return emptyList()
        return segments(text).sortedByDescending(::score).mapNotNull { sentence ->
            val key = vocabulary.firstOrNull { token -> Regex("(?i)(?<![\\p{L}])${Regex.escape(token)}(?![\\p{L}])").containsMatchIn(sentence) }
                ?: return@mapNotNull null
            val choices = (listOf(key) + vocabulary.filter { it != key && !sentence.contains(it, ignoreCase = true) }.take(3))
            if (choices.size != 4) return@mapNotNull null
            val shuffled = choices.shuffled(kotlin.random.Random(sentence.hashCode()))
            val prompt = sentence.replaceFirst(Regex("(?i)(?<![\\p{L}])${Regex.escape(key)}(?![\\p{L}])"), "________")
            Question("Completează termenul lipsă conform cursului:\n$prompt", shuffled.map { it.replaceFirstChar { ch -> ch.uppercase() } }, shuffled.indexOf(key), sentence)
        }.distinctBy { it.source }.take(max).toList()
    }
}
