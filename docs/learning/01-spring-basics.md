# TableTop: что уже есть в бэкенде и что значат аннотации Spring

Сейчас в репозитории [Sonia-Korolyok/tabletop](https://github.com/Sonia-Korolyok/tabletop) один коммит: «Stage 1: Spring Boot skeleton, PostgreSQL + Flyway, JWT auth, docs». Это каркас приложения и авторизация. Рецептов, списка покупок и плана меню пока нет.

## Главная идея Spring

На курсе вы, скорее всего, сами создавали объекты: `new UserRepository()`, `new AuthService(repo)` и так далее. В Spring это делает фреймворк.

- При запуске Spring просматривает пакеты и находит классы, помеченные аннотациями `@Service`, `@RestController`, `@Configuration` и т.п.
- Каждый такой класс он создаёт в одном экземпляре и хранит у себя. Такой объект называется **бин** (bean), а место, где они хранятся, называется **контекст** или **контейнер**.
- Если конструктору класса нужны другие объекты, Spring сам подставляет подходящие бины. Это называется **внедрение зависимостей** (Dependency Injection, DI).

Поэтому в коде почти нет `new` для сервисов: вы пишете конструктор с параметрами, а Spring сам решает, что туда передать.

Аннотация в Java сама по себе ничего не делает. Это просто метка. Смысл ей придаёт тот, кто её читает: Spring, Hibernate или JUnit.

## Как идёт запрос

```
HTTP-запрос
  → Spring Security (проверка JWT-токена)
  → Controller (принимает JSON, проверяет поля)
  → Service (бизнес-логика, транзакции)
  → Repository (SQL к PostgreSQL)
  ← ответ в JSON
Если где-то брошено исключение → ApiExceptionHandler превращает его в понятную ошибку
```

Это та же схема controller / service / repository, которую вы уже знаете. Spring просто связывает слои между собой.

## Структура проекта

| Пакет | Файлы | Зачем |
|---|---|---|
| `com.tabletop` | `TableTopApplication` | Точка входа, `main` |
| `config` | `SecurityConfig`, `JwtProperties` | Настройки безопасности и JWT |
| `auth` | `AuthController`, `AuthService`, `JwtService`, `AuthDtos` | Регистрация, логин, выдача токена |
| `user` | `User`, `UserRepository`, `UserResponse`, `MeController` | Пользователь, таблица `users`, эндпоинт «кто я» |
| `common` | `ApiExceptionHandler` и 3 исключения | Единый формат ошибок |
| `resources` | `application.yml`, `db/migration/V1__create_users.sql` | Настройки и миграция БД |

Эндпоинты:
- `POST /api/auth/register` — регистрация, возвращает токен, статус 201.
- `POST /api/auth/login` — вход, возвращает токен.
- `GET /api/me` — данные текущего пользователя, нужен токен.
- `GET /actuator/health` — проверка, что приложение живо.

---

## Аннотации по файлам

### TableTopApplication

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class TableTopApplication {
    public static void main(String[] args) {
        SpringApplication.run(TableTopApplication.class, args);
    }
}
```

**`@SpringBootApplication`** — главная аннотация, она объединяет три:
- `@Configuration` — в этом классе можно объявлять бины;
- `@ComponentScan` — искать помеченные классы в этом пакете `com.tabletop` и во всех вложенных. Поэтому главный класс лежит в корне пакета;
- `@EnableAutoConfiguration` — автонастройка. Spring Boot смотрит, какие библиотеки есть в `pom.xml`, и сам настраивает их. Есть драйвер PostgreSQL и JPA, значит он создаст подключение к базе. Есть Flyway, значит при старте прогонит миграции. Есть spring-boot-starter-web, значит поднимет встроенный сервер Tomcat на порту 8080.

**`@ConfigurationPropertiesScan`** — найти все классы с `@ConfigurationProperties` (у нас это `JwtProperties`) и заполнить их значениями из `application.yml`.

`SpringApplication.run(...)` запускает всё это: создаёт контекст, бины и сервер.

---

### JwtProperties

```java
@ConfigurationProperties(prefix = "tabletop.jwt")
public record JwtProperties(String secret, long ttlMinutes) {}
```

**`@ConfigurationProperties(prefix = "tabletop.jwt")`** — взять из `application.yml` всё, что лежит под `tabletop.jwt`, и положить в поля этого record:

```yaml
tabletop:
  jwt:
    secret: ${JWT_SECRET:dev-secret-...}
    ttl-minutes: 1440
```

- `ttl-minutes` в YAML автоматически сопоставляется с `ttlMinutes` в Java.
- `${JWT_SECRET:dev-secret-...}` означает: взять переменную окружения `JWT_SECRET`, а если её нет, использовать значение после двоеточия. Так на проде секрет задаётся снаружи и не лежит в гите.

Это удобнее, чем разбросанные по коду строки: все настройки в одном типизированном объекте, который можно внедрить куда угодно.

---

### SecurityConfig

```java
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) { ... }
    @Bean
    SecretKey jwtKey(JwtProperties props) { ... }
    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { ... }
    @Bean
    JwtDecoder jwtDecoder(SecretKey key) { ... }
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
```

**`@Configuration`** — класс с настройками. Spring создаёт его при запуске и вызывает его методы с `@Bean`.

**`@Bean`** — то, что вернул метод, становится бином. Это нужно, когда объект из чужой библиотеки и повесить на него `@Service` нельзя. Например, `BCryptPasswordEncoder` написан не нами, поэтому мы создаём его в методе и отдаём Spring.

Обратите внимание на цепочку: `jwtKey` принимает `JwtProperties`, `jwtEncoder` принимает `SecretKey`. Spring сам вызывает методы в нужном порядке и передаёт результат одного в другой.

Что настраивает `securityFilterChain`:
- `csrf.disable()` — CSRF-защита нужна сайтам с cookie-сессиями. У нас токен в заголовке, поэтому она не нужна.
- `STATELESS` — сервер не хранит сессии. Каждый запрос сам приносит токен.
- `permitAll()` для `POST /api/auth/**` и `/actuator/health` — эти адреса открыты без токена, иначе зарегистрироваться было бы невозможно.
- `anyRequest().authenticated()` — всё остальное только с токеном, иначе ответ 401.
- `oauth2ResourceServer(o -> o.jwt(...))` — проверять токен из заголовка `Authorization: Bearer ...` с помощью бина `JwtDecoder`.

`BCryptPasswordEncoder` хеширует пароли. В базе хранится не пароль, а хеш, и по нему пароль восстановить нельзя.

---

### User (сущность)

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected User() {}
    ...
}
```

Эти аннотации не из Spring, а из **JPA** (стандарт Java для работы с БД). Реализует его библиотека **Hibernate**. Это ORM: она сама превращает объекты Java в строки таблицы и обратно, поэтому SQL для простых операций писать не нужно.

- **`@Entity`** — этот класс соответствует таблице в БД, Hibernate будет с ним работать.
- **`@Table(name = "users")`** — имя таблицы. Без этого Hibernate взял бы имя класса `user`, а в PostgreSQL `user` — зарезервированное слово.
- **`@Id`** — это первичный ключ.
- **`@GeneratedValue(strategy = GenerationType.IDENTITY)`** — id генерирует сама база (`GENERATED ... AS IDENTITY` или `BIGSERIAL`). Поэтому до сохранения `id` равен `null`, а после `save` в нём уже число.
- **`@Column`** — настройки колонки:
  - `name = "password_hash"` — в Java поле `passwordHash` (camelCase), в БД колонка `password_hash` (snake_case);
  - `nullable = false` — колонка `NOT NULL`;
  - `insertable = false, updatable = false` для `createdAt` — Hibernate никогда не пишет это поле сам. Значение ставит база через `DEFAULT now()` в миграции.

Пустой конструктор `protected User()` обязателен для Hibernate: при чтении из базы он сначала создаёт пустой объект, потом заполняет поля. `protected`, чтобы мы сами случайно не создали пользователя без email.

Сеттеров нет специально: создать пользователя можно только через конструктор со всеми нужными полями.

---

### UserRepository

```java
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(String email);
}
```

Самое необычное место для тех, кто писал на чистом JDBC: это **интерфейс без реализации**, и при этом он работает.

- **`extends JpaRepository<User, Long>`** — `User` это сущность, `Long` это тип id. Spring Data при запуске сам создаёт класс, который реализует этот интерфейс. Сразу доступны `save`, `findById`, `findAll`, `deleteById`, `count` и другие. Аннотация `@Repository` здесь не нужна, Spring и так находит такие интерфейсы.
- **`@Query`** — свой запрос. Это не SQL, а **JPQL**: в нём пишут имена классов и полей Java (`User u`, `u.email`), а не таблиц. Hibernate сам переведёт его в SQL для PostgreSQL.
- `:email` — именованный параметр, подставляется значение аргумента метода `email`. Это защищает от SQL-инъекций, как `?` в `PreparedStatement`.
- `lower(...)` с двух сторон — поиск без учёта регистра, чтобы `SONIA@example.com` и `sonia@example.com` считались одним адресом.

Кстати, Spring Data умеет строить запрос по названию метода: `findByEmailIgnoreCase` сработал бы и без `@Query`. Здесь запрос написан явно, чтобы было видно, что именно уходит в базу.

---

### AuthDtos (валидация)

```java
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Size(max = 100) String displayName) {}
```

Это аннотации **Bean Validation** (пакет `jakarta.validation`). Они описывают правила для полей:
- **`@NotBlank`** — не `null`, не пустая строка и не одни пробелы;
- **`@Email`** — похоже на email;
- **`@Size(min = 8, max = 100)`** — длина строки от 8 до 100.

Сами по себе они ничего не проверяют. Проверка запускается, когда в контроллере стоит `@Valid` (ниже).

DTO сделаны через `record`: это неизменяемые классы, у которых конструктор, геттеры, `equals` и `toString` создаются автоматически. Все DTO авторизации собраны в одном файле как вложенные record, чтобы не плодить мелкие файлы.

---

### AuthController

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.TokenResponse register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) { ... }
}
```

- **`@RestController`** — класс принимает HTTP-запросы, и всё, что возвращают методы, автоматически превращается в JSON (это делает библиотека Jackson). Это `@Controller` + `@ResponseBody`.
- **`@RequestMapping("/api/auth")`** — общий префикс для всех методов класса.
- **`@PostMapping("/register")`** — метод отвечает на `POST /api/auth/register`. Бывают также `@GetMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`.
- **`@ResponseStatus(HttpStatus.CREATED)`** — при успехе вернуть 201 вместо 200 по умолчанию. 201 — правильный статус, когда что-то создано.
- **`@RequestBody`** — взять JSON из тела запроса и превратить его в объект `RegisterRequest`.
- **`@Valid`** — проверить этот объект по правилам `@NotBlank`, `@Email`, `@Size`. Если что-то не так, метод даже не вызовется: Spring бросит `MethodArgumentNotValidException`, и мы её поймаем в `ApiExceptionHandler`.

Про конструктор: у класса **один** конструктор, поэтому Spring сам понимает, что зависимости нужно передать через него. Аннотация `@Autowired` здесь не нужна. Поле `final` гарантирует, что зависимость не поменяется и не будет `null`. Это рекомендуемый способ внедрения.

Контроллер ничего не решает сам, он только принимает запрос и отдаёт его сервису. Вся логика в `AuthService`.

---

### AuthService

```java
@Service
public class AuthService {
    ...
    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest req) { ... }

    @Transactional(readOnly = true)
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest req) { ... }
}
```

- **`@Service`** — класс с бизнес-логикой, Spring создаст его как бин. Технически это то же самое, что `@Component`. Отличается только смысл: по аннотации сразу видно, к какому слою относится класс. Аналогично `@Repository` и `@Controller`.
- **`@Transactional`** — всё, что метод делает с базой, выполняется в одной транзакции. Если метод закончился нормально, будет `COMMIT`. Если вылетело `RuntimeException`, будет `ROLLBACK`, и в базе ничего не изменится. Вручную `connection.setAutoCommit(false)` и `commit()` писать не нужно.
- **`@Transactional(readOnly = true)`** — транзакция только на чтение. Hibernate не проверяет, изменились ли объекты, и работает чуть быстрее. Плюс это сигнал читающему код: здесь ничего не меняется.

Логика `register`: проверить, что email свободен (иначе `ConflictException` → 409), захешировать пароль, сохранить пользователя, выдать токен.

Логика `login`: найти по email, сравнить пароль с хешем через `passwordEncoder.matches`. Если не найден **или** пароль неверный, одна и та же ошибка «Invalid email or password». Так злоумышленник не может узнать, какие email зарегистрированы.

Важная деталь: `@Transactional` работает только при вызове метода **снаружи** класса, через бин. Если вызвать `this.register(...)` из другого метода того же класса, транзакции не будет. Это частая ловушка в Spring.

---

### JwtService

```java
@Service
public class JwtService {
    @Autowired
    public JwtService(JwtEncoder encoder, JwtProperties props) {
        this(encoder, props, Clock.systemUTC());
    }

    JwtService(JwtEncoder encoder, JwtProperties props, Clock clock) { ... }

    public AuthDtos.TokenResponse issue(User user) { ... }
}
```

- **`@Autowired`** — «внедряй зависимости через этот конструктор». Здесь он **нужен**, потому что конструкторов два, и Spring должен знать, какой выбрать.
- Второй конструктор, с `Clock`, нужен для тестов. В тесте передаём фиксированное время и точно знаем, когда истечёт токен.

Что внутри токена:
- `subject` — id пользователя (по нему потом `/api/me` найдёт пользователя);
- `email`;
- `issuedAt` и `expiresAt` — когда выдан и когда истекает (через 1440 минут, то есть сутки);
- подпись алгоритмом HS256 секретным ключом. Подделать токен без ключа нельзя.

---

### MeController

```java
@GetMapping
public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    User user = users.findById(Long.valueOf(jwt.getSubject()))
            .orElseThrow(() -> new NotFoundException("User not found"));
    return UserResponse.from(user);
}
```

- **`@GetMapping`** без пути — отвечает на `GET` по адресу класса, то есть `/api/me`.
- **`@AuthenticationPrincipal`** — «дай мне того, кто сейчас авторизован». Spring Security уже проверил токен до контроллера и положил его сюда как объект `Jwt`. Из него берём id пользователя.

Возвращаем `UserResponse`, а не саму сущность `User`. Иначе в JSON попал бы `passwordHash`. Сущность для базы, DTO для внешнего мира.

---

### ApiExceptionHandler

```java
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
    ...
}
```

- **`@RestControllerAdvice`** — общий обработчик для всех контроллеров. Без него каждый контроллер ловил бы ошибки сам через try/catch.
- **`@ExceptionHandler(NotFoundException.class)`** — если где угодно, в контроллере или сервисе, вылетело `NotFoundException`, вызвать этот метод. Его результат станет ответом клиенту.

Соответствие:

| Исключение | HTTP-статус |
|---|---|
| `NotFoundException` | 404 |
| `ConflictException` | 409 (email уже занят) |
| `UnauthorizedException` | 401 (неверный логин) |
| `MethodArgumentNotValidException` | 400 + список полей с ошибками |

`ProblemDetail` — стандартный формат ошибок (RFC 7807), встроенный в Spring. Ответ на неверную регистрацию выглядит примерно так:

```json
{
  "status": 400,
  "title": "Bad Request",
  "detail": "Validation failed",
  "errors": {
    "email": "must be a well-formed email address",
    "password": "size must be between 8 and 100"
  }
}
```

Фронтенд сможет подсветить конкретное поле.

Свои исключения наследуют `RuntimeException`, а не `Exception`. Так их не нужно объявлять в `throws`, и `@Transactional` по умолчанию откатывает транзакцию именно на `RuntimeException`.

---

### Тесты

**JwtServiceTest** — обычный юнит-тест на JUnit 5, без Spring. `@Test` вы уже знаете. Объект создаётся через `new`, время фиксировано через `Clock.fixed`. `ReflectionTestUtils.setField(user, "id", 42L)` — способ записать значение в приватное поле без сеттера, только для тестов.

**AuthFlowTest** — интеграционный тест: поднимается всё приложение и настоящий PostgreSQL в Docker.

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;
    ...
}
```

- **`@SpringBootTest`** — запустить полный контекст Spring, как при настоящем старте приложения.
- **`@AutoConfigureMockMvc`** — создать `MockMvc`. Это способ слать HTTP-запросы в приложение без реального сервера и сети.
- **`@Testcontainers(disabledWithoutDocker = true)`** — включить Testcontainers. Если Docker не запущен, тест пропустится, а не упадёт.
- **`@Container`** — перед тестами запустить этот контейнер (PostgreSQL 16), после тестов остановить.
- **`@ServiceConnection`** — передать Spring адрес, логин и пароль этой временной базы. Иначе он полез бы в базу из `application.yml`.
- **`@Autowired`** на поле — в тестах внедрение через поле нормально, конструктор тест-класса Spring не вызывает.

Тест проходит весь сценарий: регистрация (201) → повторная регистрация (409) → логин с email в другом регистре (200) → `/api/me` с токеном (200) → без токена (401) → неверный пароль (401). Второй тест проверяет, что на плохие данные приходит 400 с ошибками по полям.

---

## Конфигурация и миграции

`application.yml`:
- `datasource` — адрес базы, логин, пароль, через переменные окружения с дефолтами для локальной разработки.
- `ddl-auto: validate` — Hibernate **не создаёт и не меняет** таблицы, только проверяет при старте, что сущности совпадают со схемой. Схемой управляет только Flyway. Это безопаснее для прода.
- `open-in-view: false` — соединение с базой закрывается после сервиса, а не держится до конца ответа. Хорошая практика.
- `flyway.enabled: true` — при старте выполнить новые миграции.
- `management...include: health` — открыть только `/actuator/health`.

**Flyway** — это то же, что миграции, с которыми вы работаете на работе, только автоматически. Файлы лежат в `db/migration` и называются `V1__описание.sql`, `V2__...`. Flyway помнит в таблице `flyway_schema_history`, какие уже выполнены, и запускает только новые. Уже выполненную миграцию менять нельзя, только добавлять новую.

## Шпаргалка

| Аннотация | Откуда | Коротко |
|---|---|---|
| `@SpringBootApplication` | Spring Boot | Главный класс: сканирование + автонастройка |
| `@ConfigurationPropertiesScan` | Spring Boot | Найти классы с настройками |
| `@ConfigurationProperties` | Spring Boot | Заполнить объект из `application.yml` |
| `@Configuration` | Spring | Класс с настройками и бинами |
| `@Bean` | Spring | Результат метода становится бином |
| `@Service` | Spring | Бин бизнес-логики |
| `@RestController` | Spring Web | Принимает HTTP, отвечает JSON |
| `@RequestMapping` | Spring Web | Общий префикс адреса |
| `@GetMapping` / `@PostMapping` | Spring Web | Метод отвечает на GET / POST |
| `@RequestBody` | Spring Web | JSON из тела → объект |
| `@ResponseStatus` | Spring Web | Какой HTTP-статус вернуть |
| `@Valid` | Jakarta Validation | Запустить проверку полей |
| `@NotBlank`, `@Email`, `@Size` | Jakarta Validation | Правила для полей |
| `@RestControllerAdvice` | Spring Web | Общий обработчик ошибок |
| `@ExceptionHandler` | Spring Web | Какое исключение ловит метод |
| `@AuthenticationPrincipal` | Spring Security | Текущий пользователь (токен) |
| `@Transactional` | Spring | Метод в транзакции, откат при ошибке |
| `@Autowired` | Spring | Внедрить зависимость (нужно, если конструкторов несколько, и в тестах) |
| `@Entity`, `@Table` | JPA | Класс ↔ таблица |
| `@Id`, `@GeneratedValue` | JPA | Первичный ключ, генерирует база |
| `@Column` | JPA | Настройки колонки |
| `@Query` | Spring Data | Свой JPQL-запрос в репозитории |
| `@Test` | JUnit 5 | Тестовый метод |
| `@SpringBootTest` | Spring Boot Test | Поднять всё приложение в тесте |
| `@AutoConfigureMockMvc` | Spring Boot Test | Дать `MockMvc` для HTTP-запросов |
| `@Testcontainers`, `@Container` | Testcontainers | База в Docker на время тестов |
| `@ServiceConnection` | Spring Boot Test | Подключить Spring к этой базе |
