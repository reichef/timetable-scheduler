package timetablescheduler.application.curriculum

class CurriculumUseCases(
    val createSubject: CreateSubject,
    val updateSubject: UpdateSubject,
    val deleteSubject: DeleteSubject,
    val listSubjects: ListSubjects,
    val getSubject: GetSubject,
    val createClassLevel: CreateClassLevel,
    val updateClassLevel: UpdateClassLevel,
    val deleteClassLevel: DeleteClassLevel,
    val listClassLevels: ListClassLevels,
    val getClassLevel: GetClassLevel,
    val proposeClassSuffix: ProposeClassSuffix,
    val createSchoolClass: CreateSchoolClass,
    val updateSchoolClass: UpdateSchoolClass,
    val deleteSchoolClass: DeleteSchoolClass,
    val getSchoolClass: GetSchoolClass,
)
