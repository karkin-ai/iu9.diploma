package ru.bmstu.schedule.entity;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "day_of_weak")
public class DayOfWeek {
    private int id;
    private String shortName;
    private String name;

    @Id
    @Column(name = "weak_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Basic
    @Column(name = "short_title", columnDefinition = "bpchar", length = 3)
    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    @Basic
    @Column(name = "full_title", columnDefinition = "bpchar", nullable = false, length = 12)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DayOfWeek dayOfWeek = (DayOfWeek) o;
        return id == dayOfWeek.id &&
                Objects.equals(shortName, dayOfWeek.shortName) &&
                Objects.equals(name, dayOfWeek.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, shortName, name);
    }
}
