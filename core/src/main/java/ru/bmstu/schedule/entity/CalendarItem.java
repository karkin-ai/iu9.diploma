package ru.bmstu.schedule.entity;

import javax.persistence.*;
import java.util.Collection;
import java.util.Objects;

@Entity
@Table(name = "calendar_item", schema = "public", catalog = "schedule")
public class CalendarItem {
    private int id;
    private StudyFlow studyFlow;
    private Subject subject;
    private Collection<CalendarItemCell> calendarItemCells;

    @Id
    @Column(name = "calendar_item_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalendarItem that = (CalendarItem) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {

        return Objects.hash(id);
    }

    @ManyToOne
    @JoinColumn(name = "study_flow_id", referencedColumnName = "flow_id")
    public StudyFlow getStudyFlow() {
        return studyFlow;
    }

    public void setStudyFlow(StudyFlow studyFlow) {
        this.studyFlow = studyFlow;
    }

    @ManyToOne
    @JoinColumn(name = "subject_id", referencedColumnName = "subject_id")
    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    @OneToMany(mappedBy = "calendarItem")
    public Collection<CalendarItemCell> getCalendarItemCells() {
        return calendarItemCells;
    }

    public void setCalendarItemCells(Collection<CalendarItemCell> calendarItemCells) {
        this.calendarItemCells = calendarItemCells;
    }
}