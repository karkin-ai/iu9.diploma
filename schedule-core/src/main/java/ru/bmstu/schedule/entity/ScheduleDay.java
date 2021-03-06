package ru.bmstu.schedule.entity;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "schedule_day")
public class ScheduleDay {
    private int id;
    private DayOfWeek dayOfWeek;
    private StudyGroup studyGroup;
    private Set<ScheduleItem> scheduleItems = new HashSet<>();

    @Id
    @Column(name = "day_id", nullable = false)
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
        ScheduleDay that = (ScheduleDay) o;
        return id == that.id &&
                Objects.equals(dayOfWeek, that.dayOfWeek) &&
                Objects.equals(studyGroup, that.studyGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dayOfWeek, studyGroup);
    }

    @ManyToOne
    @JoinColumn(name = "weak_id", referencedColumnName = "weak_id")
    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    @ManyToOne
    @JoinColumn(name = "group_id", referencedColumnName = "group_id")
    public StudyGroup getStudyGroup() {
        return studyGroup;
    }

    public void setStudyGroup(StudyGroup studyGroup) {
        this.studyGroup = studyGroup;
    }

    @OneToMany(mappedBy = "scheduleDay", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    public Set<ScheduleItem> getScheduleItems() {
        return scheduleItems;
    }

    public void setScheduleItems(Set<ScheduleItem> scheduleItems) {
        this.scheduleItems = scheduleItems;
    }

    public void addScheduleItem(ScheduleItem item) {
        item.setScheduleDay(this);
        getScheduleItems().add(item);
    }
}
