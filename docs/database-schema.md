# TableTop — схема базы данных (MVP)

Версия 03.10.2026. Дополняет `mvp-spec.md` (v0.2) и `stage-2-flows.md`.

Легенда: `||--o{` — один ко многим, `}o--o{` через таблицу-связку — многие ко многим. PK — первичный ключ, FK — внешний ключ, UK — уникальный.

## Вся схема MVP

```mermaid
erDiagram
    users ||--o{ recipes : "владеет"
    users ||--o{ tags : "создаёт"
    users ||--o{ folders : "создаёт"
    users ||--o{ images : "загружает"
    users ||--o{ refresh_tokens : "сессии"
    users ||--o{ import_jobs : "запускает"
    users ||--o{ recipe_drafts : "получает"
    users ||--o{ grocery_items : "покупает"
    users ||--o{ meal_plan_entries : "планирует"

    recipes ||--o{ recipe_ingredients : "состоит из"
    recipes ||--o{ recipe_steps : "шаги"
    recipes ||--o{ recipe_tags : ""
    tags ||--o{ recipe_tags : ""
    recipes ||--o{ folder_recipes : ""
    folders ||--o{ folder_recipes : ""
    images |o--o{ recipes : "обложка"
    recipes |o--o{ recipes : "копия из каталога"
    recipes |o--o{ recipe_ingredients : "рецепт как ингредиент (v2)"

    import_jobs |o--o| recipe_drafts : "результат"
    recipe_drafts |o--o| recipes : "сохранён как"
    recipes |o--o{ grocery_items : "откуда пункт"
    recipes |o--o{ meal_plan_entries : "что готовим"

    users {
        bigint id PK
        varchar email UK "unique по lower(email)"
        varchar password_hash
        varchar display_name
        bigint avatar_image_id FK "этап Аккаунт"
        jsonb settings "вид, язык, единицы"
        timestamptz created_at
    }
    refresh_tokens {
        bigint id PK
        bigint user_id FK
        varchar token_hash UK "сам токен не храним"
        timestamptz expires_at
        timestamptz revoked_at
        varchar device "Chrome на Mac"
        timestamptz created_at
    }
    images {
        bigint id PK
        bigint user_id FK
        varchar storage_key "путь на диске или ключ в S3"
        varchar content_type
        int size_bytes
        timestamptz created_at
    }
    recipes {
        bigint id PK
        bigint user_id FK "NULL у рецептов каталога"
        varchar title
        text description
        int servings
        int total_time_min
        varchar source_url
        varchar source_author
        varchar source_type "manual web youtube image catalog"
        bigint catalog_recipe_id FK "откуда скопирован"
        varchar language
        bigint cover_image_id FK
        text notes
        boolean is_favorite
        int version "оптимистичная блокировка"
        timestamptz created_at
        timestamptz updated_at
        timestamptz deleted_at "мягкое удаление"
    }
    recipe_ingredients {
        bigint id PK
        bigint recipe_id FK
        int position
        varchar raw_text "как ввёл пользователь"
        varchar name
        numeric quantity "NULL если по вкусу"
        varchar unit
        varchar note
        varchar category "отдел магазина"
        bigint linked_recipe_id FK "v2"
    }
    recipe_steps {
        bigint id PK
        bigint recipe_id FK
        int position
        text text
        boolean ai_generated
    }
    tags {
        bigint id PK
        bigint user_id FK "NULL у тегов каталога"
        varchar name "UK user_id + lower(name)"
    }
    recipe_tags {
        bigint recipe_id PK, FK
        bigint tag_id PK, FK
    }
    folders {
        bigint id PK
        bigint user_id FK
        varchar name "UK user_id + lower(name)"
        timestamptz created_at
    }
    folder_recipes {
        bigint folder_id PK, FK
        bigint recipe_id PK, FK
        timestamptz added_at
    }
    import_jobs {
        bigint id PK
        bigint user_id FK
        varchar type "web youtube image"
        text input
        varchar status "PENDING PROCESSING DONE FAILED"
        bigint draft_id FK
        text error
        timestamptz created_at
        timestamptz updated_at
    }
    recipe_drafts {
        bigint id PK
        bigint user_id FK
        varchar origin "import agent web_search"
        jsonb payload "рецепт целиком"
        varchar status "PENDING SAVED SKIPPED"
        bigint recipe_id FK
        timestamptz created_at
    }
    grocery_items {
        bigint id PK
        bigint user_id FK
        varchar name
        numeric quantity
        varchar unit "магазинные единицы"
        varchar category
        boolean checked
        bigint recipe_id FK
        timestamptz created_at
    }
    meal_plan_entries {
        bigint id PK
        bigint user_id FK
        date date
        varchar meal_type
        int position
        bigint recipe_id FK
        varchar custom_text "если без рецепта"
    }
```

## Справочники без связей с пользователем

Общие для всех, заполняются автоматически или заранее, поэтому на схеме их нет.

| Таблица | Поля | Зачем | Этап |
|---|---|---|---|
| `product_categories` | `name_key` PK, `category` | Словарь «банан → Fruits & Vegetables» для автокатегоризации в списке покупок. Нет в словаре → спрашиваем Gemini и дописываем | 6 |
| `ingredient_substitutions_cache` | `ingredient_key` PK, `response` jsonb, `created_at` | Кеш ответов ИИ про замены, чтобы не платить за одинаковый вопрос дважды | 7 |
| `recipe_translations` | `recipe_id`, `language` (PK вместе), `payload` jsonb | Кеш перевода рецепта | после MVP |
| `flyway_schema_history` | — | Создаёт сам Flyway, руками не трогаем | 1 |

## Что когда появляется

| Этап | Таблицы |
|---|---|
| 1 (готово) | `users` |
| 2 (V2, V3) | `recipes`, `recipe_ingredients`, `recipe_steps`, `tags`, `recipe_tags`, `folders`, `folder_recipes`, `images` |
| Аккаунт | `refresh_tokens`, `users.avatar_image_id` |
| 3 | данные каталога в тех же `recipes` и `tags` с `user_id = NULL` |
| 4–5 | `import_jobs`, `recipe_drafts` |
| 6 | `grocery_items`, `meal_plan_entries`, `product_categories` |
| 7 | `ingredient_substitutions_cache` |
| v2 | `chat_sessions`, `chat_messages`, `linked_recipe_id`, таблицы Discover (ниже) |

## Чего в базе нет и почему

- **Логи и исключения.** Пишутся в стандартный вывод через SLF4J/Logback, на AWS их собирает CloudWatch. Если писать в PostgreSQL, база станет медленнее как раз в момент сбоя, а при падении базы потеряются именно те логи, которые нужны.
- **Access-токены JWT.** Не хранятся нигде на сервере: он проверяет подпись секретным ключом. Хранятся только refresh-токены, и только их хеши.
- **Глобальный справочник ингредиентов.** В рецепте ингредиент — это строка с количеством. Справочник продуктов нужен только для категорий в списке покупок (`product_categories`).

## Discover — идея на будущее (v2+)

Зафиксировано 03.10.2026, чтобы вернуться позже.

**Что не так в Honeydew**
- Блоги идут по алфавиту, найти блог, не зная названия, нельзя.
- В списке нет информации о блоге: подписчики, количество рецептов, рейтинг, когда последний раз обновлялся. Видно только после захода в блог или подписки.
- Вкладка открывает последний блог, на который подписались. Это не мешает.

**Чего хотим**
- Карточка блога в списке: подписчики, число рецептов, средний рейтинг, дата последнего обновления.
- Поиск блогов по смыслу (кухня, тема, рецепты внутри), а не только по точному названию.
- Сортировка и фильтры: популярность, посещаемость, частота обновлений, общие подписки с друзьями.
- Ощущение соцсети: подписки, лента обновлений, рекомендации.

**Таблицы, которые для этого понадобятся (черновик)**
- `blogs` (id, owner_user_id или внешний источник, name, description, avatar, created_at)
- `blog_follows` (follower_user_id, blog_id, created_at)
- `recipes.visibility` (private / public) и `recipes.blog_id`
- `recipe_ratings` (user_id, recipe_id, stars, created_at)
- `blog_stats` (blog_id, followers_count, recipes_count, avg_rating, last_post_at, views_7d) — пересчитывается по расписанию, чтобы сортировка по популярности не считала всё на лету
