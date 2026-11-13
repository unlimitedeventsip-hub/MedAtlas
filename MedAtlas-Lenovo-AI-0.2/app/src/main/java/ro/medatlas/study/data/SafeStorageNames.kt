package ro.medatlas.study.data

/**
 * Every document/note identifier used to construct a local file path must be
 * a single path segment. Old UUID-based MedAtlas IDs and "medical-notebook"
 * remain compatible; user-facing document names are not restricted.
 */
internal object SafeStorageNames {
    private val ID = Regex("[A-Za-z0-9_-]{1,120}")
    private val OFFICE_NAME = Regex("[A-Za-z0-9_-]{1,120}\\.(?:pdf|ppt|pptx|docx)", RegexOption.IGNORE_CASE)

    fun id(value: String): String {
        require(ID.matches(value)) { "Identificator de document invalid." }
        return value
    }

    fun documentFilename(value: String): String {
        require(OFFICE_NAME.matches(value)) { "Nume intern de document invalid." }
        return value
    }
}
