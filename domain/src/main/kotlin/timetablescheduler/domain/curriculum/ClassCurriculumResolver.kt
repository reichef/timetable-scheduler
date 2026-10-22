package timetablescheduler.domain.curriculum

/**
 * The only place that decides which curriculum a class receives (FR-014, R9).
 *
 * In this feature a class always receives the curriculum of its level, so nothing is stored per class. This is the
 * extension point for later per-class deviations (e.g. elective languages, split groups): they will be applied here,
 * and every reader — readiness check, scheduler — already asks this resolver instead of reading the level directly.
 */
object ClassCurriculumResolver {
    fun curriculumOf(
        schoolClass: SchoolClass,
        level: ClassLevel,
    ): List<CurriculumEntry> {
        require(schoolClass.classLevelId == level.id) { "Level does not belong to the class" }
        return level.curriculum
    }
}
