package com.tabletop.recipe;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** One line of a recipe: "200 g flour". raw_text is what the user typed, the rest is parsed. */
@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false)
    private int position;

    @Column(name = "raw_text", nullable = false, length = 500)
    private String rawText;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(length = 30)
    private String unit;

    @Column(length = 300)
    private String note;

    @Column(length = 50)
    private String category;

    /** v2: this ingredient is another recipe (pizza -> dough). Always null for now. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_recipe_id")
    private Recipe linkedRecipe;

    protected RecipeIngredient() {
    }

    public RecipeIngredient(String rawText, String name, BigDecimal quantity, String unit, String note) {
        this.rawText = rawText;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.note = note;
    }

    /** Called only by Recipe, which keeps both sides of the relation in sync. */
    void attachTo(Recipe recipe, int position) {
        this.recipe = recipe;
        this.position = position;
    }

    public Long getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public int getPosition() { return position; }
    public String getRawText() { return rawText; }
    public String getName() { return name; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public String getNote() { return note; }
    public String getCategory() { return category; }
    public Recipe getLinkedRecipe() { return linkedRecipe; }
}
