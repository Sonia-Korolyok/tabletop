package com.tabletop.recipe;

import jakarta.persistence.*;

@Entity
@Table(name = "recipe_steps")
public class RecipeStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated;

    protected RecipeStep() {
    }

    public RecipeStep(String text, boolean aiGenerated) {
        this.text = text;
        this.aiGenerated = aiGenerated;
    }

    void attachTo(Recipe recipe, int position) {
        this.recipe = recipe;
        this.position = position;
    }

    public Long getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public int getPosition() { return position; }
    public String getText() { return text; }
    public boolean isAiGenerated() { return aiGenerated; }
}
