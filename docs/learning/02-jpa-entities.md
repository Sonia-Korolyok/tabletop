# Этап 2, шаг 1: JPA-сущности. Что значит каждая аннотация

Код: `backend/src/main/java/com/tabletop/{recipe,tag,folder,image}`, тест `RecipePersistenceTest`.

Базовые аннотации `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column` разобраны в первом документе на примере `User`. Здесь только новое.

## Карта сущностей

```
User 1 ──── * Recipe 1 ──── * RecipeIngredient
                    1 ──── * RecipeStep
                    * ──── * Tag        (через recipe_tags)
                    * ──── * Folder     (через folder_recipes)
                    * ──── 1 Image      (обложка)
```

В Java связи — это не `Long userId`, а ссылки на объекты: `recipe.getOwner()` возвращает `User`. Hibernate сам превращает их в внешние ключи.

## @ManyToOne — «много этих к одному тому»

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
private User owner;
```

У одного пользователя много рецептов, а у рецепта один владелец. Смотрим со стороны рецепта: «много рецептов → один User», отсюда `ManyToOne`.

- **`@JoinColumn(name = "user_id")`** — в таблице `recipes` за эту связь отвечает колонка `user_id`.
- **`optional = false`** — связь обязательна. У `Recipe.owner` этого нет, потому что у рецептов каталога владельца нет.
- **`fetch = FetchType.LAZY`** — не загружать пользователя вместе с рецептом. Hibernate кладёт в поле «заглушку» (proxy), и SQL за пользователем уйдёт, только когда вы реально вызовете `owner.getEmail()`. По умолчанию у `@ManyToOne` стоит `EAGER` (грузить сразу), и это частая причина медленных запросов, поэтому почти всегда явно пишут `LAZY`.

Важно: ленивое поле можно прочитать только внутри транзакции. Если обратиться к нему после того, как транзакция закрылась (например, в контроллере), будет `LazyInitializationException`. Поэтому из сервиса наружу отдаём DTO, а не сущности.

## @OneToMany — обратная сторона

```java
@OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
@OrderBy("position")
private List<RecipeIngredient> ingredients = new ArrayList<>();
```

Это та же связь «рецепт — ингредиенты», только со стороны рецепта.

- **`mappedBy = "recipe"`** — «настоящая» связь описана в поле `recipe` класса `RecipeIngredient` (там `@ManyToOne` и `@JoinColumn`). Внешний ключ `recipe_id` физически лежит в таблице ингредиентов, поэтому хозяин связи — ингредиент. Без `mappedBy` Hibernate решил бы, что это вторая, отдельная связь, и стал бы искать таблицу-связку `recipes_ingredients`.
- **`cascade = CascadeType.ALL`** — всё, что делаем с рецептом, делаем и с его ингредиентами. `save(recipe)` сохранит и ингредиенты, удаление рецепта удалит и их. Отдельный `IngredientRepository` не нужен: ингредиент без рецепта не существует.
- **`orphanRemoval = true`** — если ингредиент убрали из списка (`ingredients.remove(...)` или `clear()`), он стал «сиротой», и Hibernate удалит его строку из базы. Каскад срабатывает при удалении рецепта, а `orphanRemoval` — при удалении из коллекции.
- **`@OrderBy("position")`** — при загрузке сортировать по полю `position`, тогда ингредиенты всегда идут в том порядке, в каком их ввели.

### Почему связь меняется только через методы

```java
public void replaceIngredients(List<RecipeIngredient> newIngredients) {
    ingredients.clear();
    for (int i = 0; i < newIngredients.size(); i++) {
        RecipeIngredient ingredient = newIngredients.get(i);
        ingredient.attachTo(this, i);   // заполняем обратную ссылку и позицию
        ingredients.add(ingredient);
    }
}
```

У двусторонней связи две стороны, и Java сама их не синхронизирует. Если добавить ингредиент в список, но не проставить ему `recipe`, Hibernate запишет `recipe_id = NULL`, потому что хозяин связи — ингредиент. Поэтому список нельзя менять снаружи: геттер отдаёт `unmodifiableList`, а изменения идут только через `replaceIngredients`, который заполняет обе стороны. `attachTo` без `public` виден только внутри пакета `recipe`.

## @ManyToMany — многие ко многим

```java
@ManyToMany
@JoinTable(name = "recipe_tags",
        joinColumns = @JoinColumn(name = "recipe_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
private Set<Tag> tags = new HashSet<>();
```

Рецепт может иметь много тегов, тег — много рецептов. В базе это таблица-связка `recipe_tags`, но отдельная сущность для неё не нужна: Hibernate сам вставляет и удаляет строки.

- **`@JoinTable`** — имя таблицы-связки и её колонки: `joinColumns` ссылается на нас (рецепт), `inverseJoinColumns` на другую сторону (тег).
- **Здесь нет `cascade`**, и это специально. Удалили рецепт → удалятся только строки в `recipe_tags`, а сам тег «Italian» останется для других рецептов. С `CascadeType.REMOVE` удаление рецепта удалило бы и тег у всех.
- **`Set`, а не `List`** — один тег у рецепта не может быть дважды. Кроме того, `List` в `@ManyToMany` Hibernate обновляет неэффективно: удаляет все строки связки и вставляет заново.
- У `@ManyToMany` по умолчанию уже `LAZY`.

Связь односторонняя: у `Tag` нет списка рецептов. Он не нужен, а лишняя сторона — лишняя синхронизация.

### equals и hashCode у Tag и Folder

Раз теги лежат в `HashSet`, им нужны `equals` и `hashCode`. Сравнивать по `id` опасно: до сохранения `id == null`, а после он появляется, и `hashCode` объекта поменялся бы прямо внутри `Set` — объект «потерялся» бы. Поэтому:
- `equals` сравнивает по `id`, только если он уже есть;
- `hashCode` одинаковый для всех тегов (`Tag.class.hashCode()`). Это немного медленнее для огромных множеств, но у рецепта 5–10 тегов, а правильность важнее.

Это стандартный рекомендуемый вариант для JPA-сущностей.

## @Enumerated

```java
@Enumerated(EnumType.STRING)
private SourceType sourceType;
```

Хранить enum как текст `'MANUAL'`, `'WEB'`. Без этого Hibernate хранил бы порядковый номер (0, 1, 2), и стоило бы добавить новое значение в середину enum — все старые данные стали бы значить другое.

## @Version — оптимистичная блокировка

```java
@Version
private int version;
```

Проблема: вы открыли рецепт на телефоне и на ноутбуке, поправили в обоих и сохранили. Без защиты второе сохранение молча затрёт первое.

С `@Version` Hibernate при каждом обновлении пишет:
```sql
UPDATE recipes SET ..., version = 4 WHERE id = 10 AND version = 3
```
Если другое устройство уже успело сохранить, в базе `version = 4`, условие не совпадёт, обновится 0 строк, и Hibernate бросит `OptimisticLockException`. Мы превратим её в ответ 409 «Рецепт изменился на другом устройстве».

«Оптимистичная» — потому что ничего заранее не блокируется: мы надеемся, что конфликта не будет, и только проверяем при записи. Это противоположность `SELECT ... FOR UPDATE` (пессимистичная блокировка), которая держит строку занятой.

## @CreationTimestamp и @UpdateTimestamp

```java
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private OffsetDateTime createdAt;

@UpdateTimestamp
private OffsetDateTime updatedAt;
```

Аннотации Hibernate (не стандарт JPA). Время ставится автоматически: `createdAt` при вставке, `updatedAt` при каждом изменении. `updatable = false` — дату создания потом никогда не перезаписывать.

В `User` для того же сделано по-другому: `insertable = false, updatable = false`, и время ставит база через `DEFAULT now()`. Минус того варианта: после `save` в объекте `createdAt` остаётся `null`, пока не перечитаешь из базы. С `@CreationTimestamp` значение есть сразу.

## columnDefinition = "text"

```java
@Column(columnDefinition = "text")
private String description;
```

В базе колонка `TEXT`, а Hibernate для `String` по умолчанию ожидает `varchar(255)`. У нас `ddl-auto: validate`, и при запуске Hibernate сверяет типы. Эта подсказка говорит ему, какой тип там на самом деле.

## Репозитории: @EntityGraph и проблема N+1

```java
@EntityGraph(attributePaths = {"tags", "coverImage"})
@Query("select r from Recipe r where r.id = :id and r.owner.id = :ownerId and r.deletedAt is null")
Optional<Recipe> findOwnedWithDetails(Long id, Long ownerId);
```

**N+1** — главная ловушка ленивых связей. Загрузили 30 рецептов одним запросом (это «1»), а потом в цикле для каждого вызвали `getTags()` — Hibernate сделал ещё 30 запросов (это «N»). Вместо одного запроса 31.

**`@EntityGraph`** говорит: в этом конкретном методе загрузи `tags` и `coverImage` сразу, тем же SQL через `JOIN`. Связь остаётся `LAZY` везде, а жадной становится только там, где мы это попросили.

Как увидеть N+1 своими глазами: в `application.yml` временно включить
```yaml
spring:
  jpa:
    show-sql: true
```
и посмотреть в консоль: если на одно действие летит пачка одинаковых `select ... from recipe_tags where recipe_id=?`, это оно.

Ещё два приёма в репозиториях:
- `findOwned(id, ownerId)` проверяет владельца прямо в запросе. Чужой рецепт просто «не найден» → 404, и не нужно отдельно сравнивать `recipe.getOwner()`.
- `findByIdAndOwnerId` в `FolderRepository` без `@Query`: Spring Data строит запрос по имени метода. `OwnerId` он понимает как `owner.id`.

## Миграция V3: зачем она появилась сразу

При редактировании рецепта мы заменяем весь список ингредиентов. Hibernate при сохранении выполняет сначала `INSERT` новых строк и только потом `DELETE` старых. На мгновение в таблице есть и старый, и новый ингредиент с одинаковыми `(recipe_id, position)`, и уникальное ограничение из V2 падает.

Решение — `DEFERRABLE INITIALLY DEFERRED`: PostgreSQL проверяет уникальность не после каждой команды, а в момент `COMMIT`, когда старые строки уже удалены.

Вот и пример правила «не трогать выполненные миграции»: V2 уже могла выполниться у вас локально, поэтому исправление идёт новым файлом V3, а не правкой V2.

## Тест RecipePersistenceTest

`@DataJpaTest` поднимает не всё приложение, а только JPA: сущности, репозитории, Flyway и базу. Это быстрее, чем `@SpringBootTest`. Каждый тест выполняется в транзакции, которая в конце откатывается, так что тесты не мешают друг другу.

`@AutoConfigureTestDatabase(replace = NONE)` — не подменять базу встроенной H2, использовать наш PostgreSQL из Testcontainers. Без этого `@DataJpaTest` попытался бы взять H2, а там нет ни `pg_trgm`, ни `NULLS NOT DISTINCT`.

`em.flush()` — отправить накопленные изменения в базу прямо сейчас. `em.clear()` — забыть все загруженные объекты, чтобы следующее чтение точно пошло в базу, а не взялось из кеша Hibernate.

Что проверяется:
1. Рецепт сохраняется одним `save` вместе с ингредиентами и шагами, порядок сохраняется.
2. Замена списка ингредиентов удаляет старые (`orphanRemoval`) и не падает на уникальности (V3).
3. Теги общие для рецептов; удаление рецепта не удаляет тег.
4. Мягко удалённый рецепт не находится, но остаётся в таблице.
5. Чужой рецепт не находится.
6. Устаревшая версия даёт `OptimisticLockException`.

Сам факт, что тест стартует, тоже проверка: при `ddl-auto: validate` Hibernate упал бы при запуске, если бы хоть одна колонка в сущностях не совпала с миграциями.
