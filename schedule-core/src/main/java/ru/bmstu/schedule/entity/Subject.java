package ru.bmstu.schedule.entity;

import javax.persistence.*;
import java.util.Collection;
import java.util.Objects;

@Entity
@Table(name = "subject")
public class Subject {
    private int id;
    private String name;
    private Collection<CalendarItem> calendarItems;

    public Subject() {
    }

    public Subject(String name) {
        this.name = name;
    }

    @Id
    @Column(name = "subject_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Basic
    @Column(name = "subject_name", nullable = false, length = -1)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @OneToMany(mappedBy = "departmentSubject")
    public Collection<CalendarItem> getCalendarItems() {
        return calendarItems;
    }

    public void setCalendarItems(Collection<CalendarItem> calendarItems) {
        this.calendarItems = calendarItems;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subject subject = (Subject) o;
        return id == subject.id &&
                Objects.equals(name, subject.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

}
