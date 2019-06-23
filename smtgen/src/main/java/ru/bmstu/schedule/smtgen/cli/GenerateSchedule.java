package ru.bmstu.schedule.smtgen.cli;

import org.apache.commons.cli.ParseException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import ru.bmstu.schedule.dao.*;
import ru.bmstu.schedule.entity.StudyPlan;
import ru.bmstu.schedule.entity.DayOfWeek;
import ru.bmstu.schedule.entity.*;
import ru.bmstu.schedule.smtgen.*;

import java.util.*;

public class GenerateSchedule {

    private static final int NO_OF_STUDY_WEEKS = 17;
    private static final String PARITY_ALWAYS = "ЧС/ЗН";
    private static final String PARITY_NUM = "ЧС";
    private static final String PARITY_DEN = "ЗН";

    private static final Map<String, LessonKind> CLASS_TYPE_TO_LESSON_KIND;
    private static SessionFactory sessionFactory;

    static {
        CLASS_TYPE_TO_LESSON_KIND = new HashMap<>();
        CLASS_TYPE_TO_LESSON_KIND.put("семинар", LessonKind.sem);
        CLASS_TYPE_TO_LESSON_KIND.put("лекция", LessonKind.lec);
        CLASS_TYPE_TO_LESSON_KIND.put("лабораторная работа", LessonKind.lab);
    }

    private Map<Subject, DepartmentSubject> departmentSubjectMap = new HashMap<>();

    private ScheduleDayDao scheduleDayDao;
    private StudyGroupDao studyGroupDao;
    private TutorSubjectDao lecSubjDao;
    private TutorDao lecDao;
    private StudyPlanDao studyPlanDao;
    private ClassroomDao classroomDao;
    private ClassTimeDao classTimeDao;
    private ClassTypeDao classTypeDao;
    private WeekDao weekDao;

    public static void main(String[] args) {
        GenerateSchedule genSchedule = new GenerateSchedule();
        CommandLineParser parser = new CommandLineParser();
        try {
            genSchedule.initDaoObjects();
            genSchedule.runScheduleGeneration(parser.parse(args));
        } catch (ParseException e) {
            if (e.getMessage() != null) {
                System.err.println("Невалидные параметры командной строки: " + e.getMessage());
            }
            parser.printHelp();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sessionFactory.close();
        }
    }

    private void initDaoObjects() {
        sessionFactory = new Configuration().configure().buildSessionFactory();
        studyGroupDao = new StudyGroupDao(sessionFactory);
        scheduleDayDao = new ScheduleDayDao(sessionFactory);
        lecSubjDao = new TutorSubjectDao(sessionFactory);
        classroomDao = new ClassroomDao(sessionFactory);
        classTypeDao = new ClassTypeDao(sessionFactory);
        classTimeDao = new ClassTimeDao(sessionFactory);
        studyPlanDao = new StudyPlanDao(sessionFactory);
        lecDao = new TutorDao(sessionFactory);
        weekDao = new WeekDao(sessionFactory);
    }

    private void runScheduleGeneration(ScheduleConfiguration config) throws RuntimeException {
        Map<StudyGroup, Schedule> schedules = generateSchedules(config);
        printSchedules(schedules);
        removeSchedules(schedules);
        persistSchedules(schedules);
    }

    private void removeSchedules(Map<StudyGroup, Schedule> schedules) {
        for (StudyGroup studyGroup : schedules.keySet()) {
            for (ScheduleDay scheduleDay : studyGroup.getScheduleDays()) {
                scheduleDayDao.delete(scheduleDay);
            }
        }
    }

    private void checkAllGroups(List<StudyGroup> groups) {
        if (groups.size() == 0)
            return;

        StudyPlan firstStudyPlan = groups.get(0).getStudyPlan();
        for (int i = 1; i < groups.size(); i++) {
            if (!groups.get(i).getStudyPlan().equals(firstStudyPlan)) {
                throw new RuntimeException("Невозможно сгенерировать рассписание для данных групп: группы имеют разные учебные планы");
            }
        }
    }

    private Map<StudyGroup, Schedule> generateSchedules(ScheduleConfiguration config) throws RuntimeException {
        List<StudyGroup> groups = new ArrayList<>();
        StudyPlan studyPlan;
        int term;
        if (config.getGroupCiphers() != null) {
            for (String grCipher : config.getGroupCiphers()) {
                Optional<StudyGroup> grOpt = studyGroupDao.findByCipher(grCipher);
                if (!grOpt.isPresent()) {
                    throw new RuntimeException("Группа с таким шифром не найдена: " + grCipher);
                }
                groups.add(grOpt.get());
            }
            checkAllGroups(groups);
            studyPlan = groups.get(0).getStudyPlan();
            term = groups.get(0).getTerm().getNumber();
        } else {
            int year = config.getEnrollmentYear();
            term = config.getNoOfTerm();
            String deptCipher = config.getDepartmentCipher();
            String specCode = config.getSpecializationCode();

            Optional<StudyPlan> calendarOpt = studyPlanDao.findByStartYearAndDepartmentCipherAndSpecCode(year, deptCipher, specCode);

            if (!calendarOpt.isPresent()) {
                throw new RuntimeException("Учебный план с заданными параметрами не найден");
            }
            studyPlan = calendarOpt.get();

            for (StudyGroup group : studyPlan.getStudyGroups()) {
                if (group.getTerm().getNumber() == term) {
                    groups.add(group);
                }
            }
        }

        Map<Subject, SubjectsPerWeek> subjectsPerWeekMap = new HashMap<>();
        List<TutorSubject> tutorSubjects = new ArrayList<>();

        for (StudyPlanItem item : studyPlan.getStudyPlanItems()) {
            DepartmentSubject deptSubj = item.getDepartmentSubject();
            Subject subject = deptSubj.getSubject();

            departmentSubjectMap.put(subject, deptSubj);
            for (StudyPlanItemCell itemCell : item.getStudyPlanItemCells()) {
                if (itemCell.getTerm().getNumber() == term) {
                    SubjectsPerWeek subjPerWeek = new SubjectsPerWeek();

                    for (HoursPerClass hpc : itemCell.getHoursPerClasses()) {
                        int noOfHours = hpc.getNoOfHours();
                        ClassType classType = hpc.getClassType();
                        LessonKind kind = CLASS_TYPE_TO_LESSON_KIND.get(classType.getName());
                        if (kind != null && noOfHours > 0) {
                            subjPerWeek.put(kind, (double) noOfHours / (NO_OF_STUDY_WEEKS * 2.0));
                        }
                    }
                    subjectsPerWeekMap.put(subject, subjPerWeek);
                }
            }

            for (TutorSubject lecSubj : deptSubj.getTutorSubjects()) {
                if (subjectsPerWeekMap.containsKey(lecSubj.getDepartmentSubject().getSubject())) {
                    tutorSubjects.add(lecSubj);
                }
            }
        }

        List<Classroom> classrooms = classroomDao.findAll();

        List<ClassType> classTypes = new ArrayList<>();
        for (String typeName : CLASS_TYPE_TO_LESSON_KIND.keySet()) {
            Optional<ClassType> ctOpt = classTypeDao.findByName(typeName);
            if (!ctOpt.isPresent()) {
                throw new IllegalStateException("Не известный тип занятий: " + typeName);
            }

            classTypes.add(ctOpt.get());
        }

        SmtScheduleGenerator scheduleGenerator = new SmtScheduleGenerator(
                subjectsPerWeekMap,
                tutorSubjects,
                classrooms,
                groups,
                classTypes
        );

        return scheduleGenerator.generateSchedule();
    }

    private void persistSchedules(Map<StudyGroup, Schedule> scheduleMap) {
        for (StudyGroup studyGroup : scheduleMap.keySet()) {
            Schedule schedule = scheduleMap.get(studyGroup);
            for (DayEntry dayEntry : schedule.getDayEntries()) {
                ScheduleDay scheduleDay;
                try {
                    scheduleDay = convertToScheduleDay(dayEntry);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    System.err.println("[ошибка] " + e.getMessage());
                    continue;
                }

                scheduleDay.setStudyGroup(studyGroup);
                scheduleDayDao.create(scheduleDay);
            }
        }
    }

    private ScheduleDay convertToScheduleDay(DayEntry entry) throws IllegalStateException {
        String weekAlias = entry.getDayOfWeek().getAlias();
        Optional<DayOfWeek> weekOpt = weekDao.findByShortName(weekAlias);

        if (!weekOpt.isPresent()) {
            throw new IllegalStateException("День недели не найден в базе: " + weekAlias);
        }

        ScheduleDay dayEntity = new ScheduleDay();
        dayEntity.setDayOfWeek(weekOpt.get());
        for (int i = 0; i < entry.getItems().length; i++) {
            ScheduleItem scheduleItem;
            LessonItem lessonItem = entry.getItems()[i];
            if (lessonItem == null) {
                continue;
            }

            scheduleItem = convertToScheduleItem(lessonItem);
            dayEntity.addScheduleItem(scheduleItem);
        }

        return dayEntity;
    }

    private ScheduleItem convertToScheduleItem(LessonItem lessonItem) throws RuntimeException {
        ScheduleItem scheduleItem = new ScheduleItem();
        Optional<ClassTime> ctOpt = classTimeDao.findByOrderNumber(lessonItem.getIndex() + 1);
        if (!ctOpt.isPresent()) {
            String msg = String.format("[ошибка] Не существует занятия с таким номером: %d%n", lessonItem.getIndex() + 1);
            throw new RuntimeException(msg);
        }

        scheduleItem.setClassTime(ctOpt.get());

        if (lessonItem instanceof SingleLessonItem) {
            Lesson lesson = ((SingleLessonItem) lessonItem).getLesson();
            if (lesson != null) {
                scheduleItem.addItemParity(convertToItemParity(lesson, PARITY_ALWAYS));
            }
        } else if (lessonItem instanceof PairLessonItem) {
            PairLessonItem pairLessonItem = (PairLessonItem) lessonItem;
            Lesson numerator = pairLessonItem.getNumerator();
            Lesson denominator = pairLessonItem.getDenominator();

            if (numerator != null) {
                scheduleItem.addItemParity(convertToItemParity(numerator, PARITY_NUM));
            }
            if (denominator != null) {
                scheduleItem.addItemParity(convertToItemParity(denominator, PARITY_DEN));
            }
        }

        return scheduleItem;
    }

    private ScheduleItemParity convertToItemParity(Lesson lesson, String parity) throws RuntimeException {
        ScheduleItemParity itemParity = new ScheduleItemParity();
        ClassType classType = lesson.getClassType();
        itemParity.setClassroom(lesson.getClassroom());
        itemParity.setClassType(classType);
        itemParity.setDayParity(parity);
        Tutor tutor = lesson.getTutor();
        Subject subject = lesson.getSubject();

        DepartmentSubject deptSubj = departmentSubjectMap.get(subject);
        TutorSubject lecSubj;

        if (tutor == null) {
            Tutor unknownLec = lecDao.fetchUnknownLecturer();
            Optional<TutorSubject> lecSubjOpt = lecSubjDao.findByTutorAndDepartmentSubjectAndClassType(
                    unknownLec,
                    deptSubj,
                    classType
            );

            if (!lecSubjOpt.isPresent()) {
                lecSubj = new TutorSubject();
                lecSubj.setTutor(unknownLec);
                lecSubj.setDepartmentSubject(deptSubj);
                lecSubj.setClassType(classType);
                Integer lecSubjId = lecSubjDao.create(lecSubj);
                lecSubj.setId(lecSubjId);
            } else {
                lecSubj = lecSubjOpt.get();
            }
        } else {
            Optional<TutorSubject> lecSubjOpt = lecSubjDao.findByTutorAndDepartmentSubjectAndClassType(
                    tutor,
                    deptSubj,
                    classType
            );
            if (!lecSubjOpt.isPresent()) {
                String msg = String.format(
                        "Некорректные данные для построяения модели: не сеществует преподавателя '%s', который ведет предмет '%s' (%s.)",
                        tutor.getInitials(),
                        subject.getName(),
                        classType.getName().substring(0, 3)
                );
                throw new RuntimeException(msg);
            }
            lecSubj = lecSubjOpt.get();
        }


        itemParity.setTutorSubject(lecSubj);

        return itemParity;
    }

    private static void printSchedules(Map<StudyGroup, Schedule> scheduleMap) {
        for (Map.Entry<StudyGroup, Schedule> scheduleEntry : scheduleMap.entrySet()) {
            System.out.printf("Расписание для группы: %s%n%n", groupRepr(scheduleEntry.getKey()));
            System.out.println(scheduleEntry.getValue());
            System.out.println("-----------------------------");
        }
    }

    private static String groupRepr(StudyGroup studyGroup) {
        DepartmentSpecialization deptSpec = studyGroup.getStudyPlan().getDepartmentSpecialization();
        Specialization spec = deptSpec.getSpecialization();
        Department dept = deptSpec.getDepartment();
        int groupNo = studyGroup.getNumber();
        int termNo = studyGroup.getTerm().getNumber();
        int specNo = spec.getNumberInSpeciality();
        String specCode = spec.getSpeciality().getCode();
        String deptCipher = dept.getCipher();

        return String.format("%s-%d%d (%s_%d)", deptCipher, termNo, groupNo, specCode, specNo);
    }

}