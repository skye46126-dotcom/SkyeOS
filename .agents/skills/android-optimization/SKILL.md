---
name: Android Architecture and Anti-Redundancy
description: A comprehensive guide to refactoring Android codebases, removing boilerplate, and implementing clean architecture.
---

# Android Architecture and Anti-Redundancy Skill

This skill provides a structured approach to removing redundant code and applying modern Android development practices. 

## 1. Eliminate Boilerplate Code
- **Kotlin Extensions**: Replace isolated utility classes with Extension Functions and Properties.
- **Sealed Classes / Interfaces**: Use them to handle UI states (`Loading`, `Success`, `Error`) or Intent actions, avoiding endless `if/else` or `switch` statements on strings/ints.
- **Coroutines & Flow**: Replace callbacks or RxJava with suspend functions and `Flow`.

## 2. Structural Optimization (Clean Architecture)
- **Data Layer (Room)**: 
  - Identify usages of `SQLiteOpenHelper`.
  - Migrate manual row-mapping and raw SQL queries to Jetpack **Room**.
  - Expose data as `Flow<List<T>>` from `@Dao` interfaces.
- **Dependency Injection (Hilt/Koin)**:
  - Eliminate direct instantiation (`new XRepository()`).
  - Set up a DI framework to provide necessary instances cleanly.
- **Domain & UI Layer (MVVM/MVI)**:
  - Extract all logic from `Activity`/`Fragment` into `ViewModel`.
  - Communicate with UI exclusively via `StateFlow` and `SharedFlow`.

## 3. Execution Plan for Optimizing an App
When applying this skill to a project:
1. **Analyze**: Identify the biggest sources of redundancy (e.g., manual SQLite setups, lack of Dependency Injection).
2. **Setup**: Add necessary dependencies (Room, Hilt/Koin, Lifecycle components) to `build.gradle`.
3. **Refactor**: Transform one feature or layer at a time to avoid breaking the application.
4. **Clean up**: Delete the old redundant classes.
