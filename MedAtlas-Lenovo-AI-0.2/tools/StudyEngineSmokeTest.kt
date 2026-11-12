import ro.medatlas.study.study.StudyEngine

fun main() {
    val text = """
    Diagnosticul bolii este stabilit prin evaluarea clinică și investigații specifice.
    Tratamentul depinde de severitatea simptomelor, de contraindicații și de tolerabilitate.
    Un criteriu numeric de evaluare poate include o valoare de 12% pentru comparație.
    Evaluarea pacientului trebuie interpretată împreună cu manifestările clinice.
    Riscul de complicații impune monitorizarea periodică și evaluarea rezultatelor.
    Diagnosticul diferențial poate include afecțiuni cu simptome asemănătoare.
    """.trimIndent()
    check(StudyEngine.summary(text).isNotEmpty())
    check(StudyEngine.terms(text).isNotEmpty())
    val questions = StudyEngine.quiz(text)
    check(questions.all { it.correct in it.options.indices && it.options.size == 4 })
    println("PASS: summary=${StudyEngine.summary(text).size}, quizzes=${questions.size}, terms=${StudyEngine.terms(text).size}")
}
