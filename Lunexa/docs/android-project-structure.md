# Lunexa Android Project Structure

This project is organized as a production-style multi-module Android app using Kotlin, Jetpack Compose, MVVM, Clean Architecture, and Hilt.

No feature implementation code is generated in the new modules yet. The current setup only defines Gradle wiring, Android library modules, manifests, and package placeholders.

## Folder Structure

```text
Lunexa/
  app/
  core-common/
  core-ui/
  core-network/
  core-database/
  feature-auth/
  feature-home/
  feature-transactions/
  feature-budget/
  feature-analytics/
  docs/
  gradle/
    libs.versions.toml
  build.gradle.kts
  settings.gradle.kts
```

## Package Structure

```text
com.twango.lunexa
  app

com.twango.lunexa.core.common
  dispatcher
  error
  logging
  model
  result
  validation

com.twango.lunexa.core.ui
  components
  navigation
  state
  theme

com.twango.lunexa.core.network
  api
  auth
  di
  dto
  interceptor

com.twango.lunexa.core.database
  converter
  dao
  di
  entity
  migration

com.twango.lunexa.feature.auth
com.twango.lunexa.feature.home
com.twango.lunexa.feature.transactions
com.twango.lunexa.feature.budget
com.twango.lunexa.feature.analytics
  data
  domain
  di
  presentation
```

## Dependency Graph

```text
:app
  -> :feature-auth
  -> :feature-home
  -> :feature-transactions
  -> :feature-budget
  -> :feature-analytics
  -> :core-ui
  -> :core-network
  -> :core-database
  -> :core-common

:feature-*
  -> :core-ui
  -> :core-network
  -> :core-database
  -> :core-common

:core-ui
  -> :core-common

:core-network
  -> :core-common

:core-database
  -> :core-common

:core-common
  -> no project module dependencies
```

## Gradle Setup

The root project includes the following modules:

```kotlin
include(
    ":app",
    ":core-ui",
    ":core-network",
    ":core-database",
    ":core-common",
    ":feature-auth",
    ":feature-home",
    ":feature-transactions",
    ":feature-budget",
    ":feature-analytics",
)
```

The version catalog owns shared dependency versions for:

```text
Android Gradle Plugin
Kotlin
Compose BOM
Hilt
Navigation Compose
Room
Retrofit
OkHttp logging
Coroutines
Lifecycle
JUnit and AndroidX test libraries
```

Annotation processing uses KSP for Hilt and Room.

This project currently sets `android.builtInKotlin=false` and `android.newDsl=false` in `gradle.properties` because Hilt's Gradle plugin still relies on legacy Android variant APIs under AGP 9. The project is buildable with this compatibility mode and can move back to AGP 9's new DSL once Hilt supports it cleanly.

Compose is enabled in:

```text
:app
:core-ui
:feature-auth
:feature-home
:feature-transactions
:feature-budget
:feature-analytics
```

Hilt is enabled in:

```text
:app
:core-network
:core-database
:feature-auth
:feature-home
:feature-transactions
:feature-budget
:feature-analytics
```

Room dependencies are scoped to:

```text
:core-database
```

Retrofit and OkHttp dependencies are scoped to:

```text
:core-network
```
