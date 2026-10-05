package com.tabletop.recipe;

import com.tabletop.folder.Folder;
import com.tabletop.image.Image;
import com.tabletop.tag.Tag;
import com.tabletop.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null = recipe from the app catalog (stage 3). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User owner;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private Integer servings;

    @Column(name = "total_time_min")
    private Integer totalTimeMin;

    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @Column(name = "source_author", length = 200)
    private String sourceAuthor;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    /** Which catalog recipe this one was copied from (stage 3). Plain id: we never need to load it. */
    @Column(name = "catalog_recipe_id")
    private Long catalogRecipeId;

    @Column(length = 10)
    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_image_id")
    private Image coverImage;

    @Column(columnDefinition = "text")
    private String notes;

    /** Liking a recipe on the phone must not block editing it on the laptop: excluded from @Version. */
    @OptimisticLock(excluded = true)
    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    /** Optimistic locking: Hibernate adds "where version = ?" to every UPDATE. */
    @Version
    @Column(nullable = false)
    private int version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<RecipeStep> steps = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "recipe_tags",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "folder_recipes",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "folder_id"))
    private Set<Folder> folders = new HashSet<>();

    protected Recipe() {
    }

    public Recipe(User owner, String title, SourceType sourceType) {
        this.owner = owner;
        this.title = title.trim();
        this.sourceType = sourceType;
    }

    public void updateDetails(String title, String description, Integer servings, Integer totalTimeMin,
                              String sourceUrl, String sourceAuthor, String language, String notes) {
        this.title = title.trim();
        this.description = description;
        this.servings = servings;
        this.totalTimeMin = totalTimeMin;
        this.sourceUrl = sourceUrl;
        this.sourceAuthor = sourceAuthor;
        this.language = language;
        this.notes = notes;
    }

    /** Replaces all ingredients; old rows are deleted thanks to orphanRemoval. */
    public void replaceIngredients(List<RecipeIngredient> newIngredients) {
        ingredients.clear();
        for (int i = 0; i < newIngredients.size(); i++) {
            RecipeIngredient ingredient = newIngredients.get(i);
            ingredient.attachTo(this, i);
            ingredients.add(ingredient);
        }
    }

    public void replaceSteps(List<RecipeStep> newSteps) {
        steps.clear();
        for (int i = 0; i < newSteps.size(); i++) {
            RecipeStep step = newSteps.get(i);
            step.attachTo(this, i);
            steps.add(step);
        }
    }

    public void replaceTags(Collection<Tag> newTags) {
        tags.clear();
        tags.addAll(newTags);
    }

    public void replaceFolders(Collection<Folder> newFolders) {
        folders.clear();
        folders.addAll(newFolders);
    }

    public void setCoverImage(Image coverImage) { this.coverImage = coverImage; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public void softDelete(OffsetDateTime now) { this.deletedAt = now; }
    public void restore() { this.deletedAt = null; }
    public boolean isDeleted() { return deletedAt != null; }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getServings() { return servings; }
    public Integer getTotalTimeMin() { return totalTimeMin; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceAuthor() { return sourceAuthor; }
    public SourceType getSourceType() { return sourceType; }
    public Long getCatalogRecipeId() { return catalogRecipeId; }
    public String getLanguage() { return language; }
    public Image getCoverImage() { return coverImage; }
    public String getNotes() { return notes; }
    public boolean isFavorite() { return favorite; }
    public int getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    /** Read-only views: change collections only through replace* methods. */
    public List<RecipeIngredient> getIngredients() { return Collections.unmodifiableList(ingredients); }
    public List<RecipeStep> getSteps() { return Collections.unmodifiableList(steps); }
    public Set<Tag> getTags() { return Collections.unmodifiableSet(tags); }
    public Set<Folder> getFolders() { return Collections.unmodifiableSet(folders); }
}
