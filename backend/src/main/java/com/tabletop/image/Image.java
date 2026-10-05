package com.tabletop.image;

import com.tabletop.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/** Metadata of an uploaded file. The bytes live in ImageStorage (disk locally, S3 in prod). */
@Entity
@Table(name = "images")
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Image() {
    }

    public Image(User owner, String storageKey, String contentType, int sizeBytes) {
        this.owner = owner;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
