package com.tabletop.tag;

import com.tabletop.user.User;
import jakarta.persistence.*;

/** Tag like "breakfast" or "vegan". owner == null means a catalog tag. */
@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    @Column(nullable = false, length = 50)
    private String name;

    protected Tag() {
    }

    public Tag(User owner, String name) {
        this.owner = owner;
        this.name = name.trim();
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getName() { return name; }

    // Tags are kept in a Set inside Recipe, so equals/hashCode must be stable
    // before and after the entity gets its id from the database.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tag other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Tag.class.hashCode();
    }
}
