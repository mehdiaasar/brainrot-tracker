Every app that wants to *remember* something between launches needs a place to store data on the device. LoopOut remembers how many reels you watched each day, your personal limits, and your streak history. All of that lives in a small on-device database powered by **Room**. This chapter teaches you what Room is from the ground up, then walks through LoopOut's three tables and the code that reads and writes them.

## What "a database on the phone" even means

Android ships with a tiny, built-in database engine called **SQLite**. Think of it as a single file on the phone that stores data in tables — rows and columns, like a spreadsheet that lives inside your app's private storage. SQLite speaks **SQL** (Structured Query Language), the classic text language for asking a database questions like "give me all rows where date is today."

Writing raw SQLite by hand is tedious and error-prone: you open connections, run string queries, and manually copy each column into your Kotlin objects. **Room** is Google's official library that sits on top of SQLite and does that grunt work for you.

> 💡 **Concept — ORM.** Room is an *ORM* (Object-Relational Mapper). "Object" = your Kotlin classes; "Relational" = the rows-and-columns database. An ORM maps between the two automatically, so you work with normal Kotlin objects and Room translates them into SQL behind the scenes.

Room is built from three kinds of pieces, each marked with an **annotation** (a `@Something` tag that gives the compiler instructions):

| Piece | Annotation | Role |
|-------|-----------|------|
| Entity | `@Entity` | A Kotlin class that describes one table |
| DAO | `@Dao` | An interface listing the queries you can run |
| Database | `@Database` | The hub that ties entities and DAOs together |

> 💡 **Concept — code generation at build time.** You only write the *interfaces* and *annotations*. At compile time, an annotation processor reads your `@Query`, `@Entity`, and `@Dao` declarations and generates the real, working SQLite code for you. (In a Kotlin project this runs via **KSP**, Kotlin Symbol Processing.) If you mistype a column name in a query, the build fails with an error instead of the app crashing at runtime. That early checking is one of Room's biggest wins.

## The database hub: `AppDatabase.kt`

Everything starts here. `AppDatabase.kt` declares the whole database in one annotated class.

```kotlin
@Database(
    entities = [DailyLog::class, UserLimits::class, StreakRecord::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun userLimitsDao(): UserLimitsDao
    abstract fun streakDao(): StreakDao
```

Line by line:

- `@Database(...)` marks this class as the database definition.
- `entities = [...]` lists the three tables this database contains — we'll meet each one below.
- `version = 2` is the schema version number. Every time you change a table's shape, you bump this number (more on that shortly).
- `exportSchema = false` tells Room not to write a JSON snapshot of the schema to disk. That snapshot is useful for tracking migrations in mature apps; LoopOut is pre-launch and doesn't need it yet.
- The class is `abstract` and extends `RoomDatabase()`. You never write the body of these `dailyLogDao()` functions — the generated code supplies the implementations.

### Getting one shared instance

A database connection is expensive to open, so you want exactly **one** for the whole app. The `companion object` enforces that:

```kotlin
companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "brainrot_tracker_db"
            )
                .fallbackToDestructiveMigration(true)
                .build()
                .also { INSTANCE = it }
        }
    }
}
```

This is the classic **singleton** pattern (one object, shared everywhere):

- `@Volatile` ensures every thread sees the latest value of `INSTANCE` — important because the database is touched from background threads.
- `INSTANCE ?: synchronized(this) { ... }` means "if we already have an instance, return it; otherwise, lock and build one." The lock prevents two threads from building the database at the same time. The inner `INSTANCE ?:` is a second check after acquiring the lock (the well-known *double-checked locking* trick).
- `Room.databaseBuilder(...)` names the on-disk file `"brainrot_tracker_db"`.
- `.also { INSTANCE = it }` caches the freshly built database so the next call skips the work.

> 💡 **Concept — Why `applicationContext`?** A `Context` is Android's handle to app resources and services. Using `applicationContext` (rather than an Activity) keeps the long-lived database from accidentally holding onto a screen that gets destroyed, which would leak memory.

### Destructive migration, and why it's fine *for now*

```kotlin
// Pre-launch: schema changes recreate the DB instead of migrating
.fallbackToDestructiveMigration(true)
```

When you change a table and bump `version`, Room needs a plan to move existing user data from the old shape to the new one — a **migration**. Writing migrations is fiddly. `fallbackToDestructiveMigration(true)` says: "Don't migrate — just **delete the old database and rebuild it empty**."

That would be catastrophic in a shipped app (every user loses their data on update). But LoopOut hasn't launched yet, so there's no real user data to protect. The trade-off is deliberate: skip migration busywork during development, and add proper migrations before the first public release.

> ⚠️ **Gotcha — Bump the version when you change a table.** If you change a table's shape without bumping `version`, Room throws an error at startup. Right now, bumping the version simply wipes the database. The moment LoopOut goes live, this line must be replaced with real migrations.

## Table 1: `DailyLog` — one row per day

Each entity is a plain Kotlin `data class` with `@Entity` on top. Here's the daily reel log:

```kotlin
@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey
    val date: String,
    val instagramReels: Int = 0,
    val youtubeShorts: Int = 0,
    val tiktokVideos: Int = 0,
    val snapchatSpotlights: Int = 0,
    val brainHealthScore: Int = 100
)
```

- `@Entity(tableName = "daily_logs")` makes this class a table named `daily_logs`.
- `@PrimaryKey` marks `date` as the **primary key** — the unique identifier for each row. The date string (e.g. `"2026-06-19"`) is the key, so there is at most one row per calendar day. Ask for today's date and you get exactly today's row.
- Each platform gets its **own column**: `instagramReels`, `youtubeShorts`, `tiktokVideos`, `snapchatSpotlights`. They all default to `0`.
- `brainHealthScore` defaults to `100` — a fresh day starts at full health.

> 💡 **Concept — Column-per-platform vs. a separate table.** A more "textbook" design might store reels in a separate table with a `platform` column and many rows per day. LoopOut instead keeps one wide row with a column per platform. With a small, fixed set of platforms, this is simpler to read and update, and a single row holds the whole day's snapshot.

The `data class` also carries two helper functions in its body, so the rest of the app doesn't have to remember which column maps to which platform:

```kotlin
fun getTotalReels(): Int =
    instagramReels + youtubeShorts + tiktokVideos + snapchatSpotlights

fun getReelsForPlatform(platform: Platform): Int = when (platform) {
    Platform.INSTAGRAM -> instagramReels
    Platform.YOUTUBE -> youtubeShorts
    Platform.TIKTOK -> tiktokVideos
    Platform.SNAPCHAT -> snapchatSpotlights
}
```

`getReelsForPlatform` bridges the `Platform` enum (from `Platform.kt`, which pairs each app with its package name, display name, and emoji) to the matching column. The `when` is *exhaustive* — it has a branch for every enum value, so Kotlin guarantees no platform is forgotten.

## Table 2: `UserLimits` — your daily caps

```kotlin
@Entity(tableName = "user_limits")
data class UserLimits(
    @PrimaryKey
    val platform: String,
    val dailyReelLimit: Int = 30,
    val dailyMinuteLimit: Int = 60
)
```

Here the primary key is `platform` (stored as a `String` like `"INSTAGRAM"`), so there's one row per app. `dailyReelLimit` defaults to `30` reels and `dailyMinuteLimit` to `60` minutes. When no row exists for a platform yet, the app falls back to the default limit of 30 in its blocking logic.

## Table 3: `StreakRecord` — one row per evaluated day

```kotlin
@Entity(tableName = "streak_records")
data class StreakRecord(
    @PrimaryKey
    val date: String,
    val underLimit: Boolean,
    val streakDay: Int
)
```

Keyed by `date` again, each row records whether that day stayed `underLimit` and the running `streakDay` count (how many consecutive under-limit days you'd reached). The streak engine in `UsageRepository` fills these in for past days.

## DAOs: the menu of database operations

A **DAO** (Data Access Object) is an interface that lists the queries you can run against a table. You declare the *what*; the generated code writes the *how*.

### Reading: `Flow` vs. `suspend`

Look at the two ways `DailyLogDao` reads today's row:

```kotlin
@Query("SELECT * FROM daily_logs WHERE date = :date")
fun getByDate(date: String): Flow<DailyLog?>

@Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
suspend fun getByDateOnce(date: String): DailyLog?
```

- `@Query("...")` holds the raw SQL. `:date` is a placeholder filled by the `date` parameter — the build checks that the parameter name matches.
- `getByDate` returns a **`Flow`** — a stream that emits the current row *and re-emits automatically whenever that row changes*. The UI subscribes to it once and stays live: count a new reel, and the screen updates with no extra code. The `?` means the result can be `null` (no row for that date yet).
- `getByDateOnce` is a **`suspend`** function — it fetches the value a single time and returns. `suspend` marks a function that can pause without blocking the thread, so database work happens off the main thread and the UI never freezes. This one is for code that needs a one-shot snapshot, not a live stream.

> 💡 **Concept — When to use which.** Use `Flow` when the screen should react to changes (the dashboard). Use a `suspend` "...Once" function when you just need the value right now to make a decision (the reel counter checking whether to write a new row).

### Writing: the increment queries

The most interesting DAO methods are the per-platform increments. There's one for each platform; here's Instagram's:

```kotlin
@Query("UPDATE daily_logs SET instagramReels = instagramReels + 1 WHERE date = :date")
suspend fun incrementInstagramReels(date: String)
```

This is a raw `UPDATE`: "for the row matching `date`, set `instagramReels` to its current value plus one." Doing the `+ 1` **inside the database** is deliberate — it's atomic, so two rapid reel counts can't read the same old value and clobber each other. Alongside the four increments, `DailyLogDao` also has `updateBrainHealthScore(date, score)` to overwrite the day's score.

These updates only touch a row that already exists. That's why the repository pairs them with an insert step:

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertOrUpdate(log: DailyLog)
```

`@Insert` adds a row. `onConflict = OnConflictStrategy.REPLACE` means "if a row with the same primary key already exists, replace it instead of crashing." This makes it an **upsert** (update-or-insert). In `UsageRepository.incrementReelCount`, the flow is: call `ensureTodayLogExists()` (which inserts a blank `DailyLog` for today only if today's row is missing), then run the matching `increment*` query. The blank-row insert and the increment together guarantee the counter always lands on a real row.

> 💡 **Concept — Upsert.** Without `REPLACE`, inserting a duplicate primary key throws an error. With it, the same call safely creates *or* overwrites — exactly what `UserLimitsDao.upsert` relies on when you change a limit.

## Why screen time is *not* in this database

You might expect a column for minutes watched. There isn't one — and that's on purpose. Screen time is **not stored in Room at all**. Instead, `ScreenTimeHelper` queries Android's `UsageStatsManager` for raw activity events (app resumed/paused) *live*, every time a screen needs the number.

The reason: Android's convenient pre-aggregated usage stats bleed sessions across day boundaries, producing wrong daily totals. Computing from raw events on demand is accurate, and it avoids duplicating a number the OS already tracks. Reel counts, limits, and streaks are LoopOut's *own* data, so they live in Room; screen time is the OS's data, so the app reads it fresh each time.

> ⚠️ **Gotcha — Don't "cache" screen time into a column to save effort.** It would drift out of sync with reality and reintroduce the day-boundary bug. The split — own data in Room, OS data queried live — is a core design decision of the app.

## Recap

- **Room** is an ORM over **SQLite**: you write `@Entity` classes, `@Dao` interfaces, and one `@Database` class, and the build generates the SQL plumbing for you.
- LoopOut has three tables: `DailyLog` (one wide row per day, a column per platform plus `brainHealthScore`), `UserLimits` (one row per platform), and `StreakRecord` (one evaluated row per day).
- DAOs return `Flow` for live UI updates and `suspend` functions for one-shot snapshots; the `increment*` queries do atomic `+ 1` updates, paired with an `insertOrUpdate` upsert so the target row always exists.
- `version = 2` with `fallbackToDestructiveMigration(true)` trades real migrations for a wipe-and-rebuild — acceptable only because the app hasn't launched.
- Screen time is deliberately queried live from the OS, never stored in Room.
