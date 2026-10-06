# Vital.ity

An open-source Android health dashboard powered by [Health Connect](https://developer.android.com/health-and-fitness/health-connect).

Vital.ity reads your health data — steps, heart rate, sleep, workouts, blood oxygen and more — straight from Health Connect and presents it in a clean, Material 3 Expressive interface. All data stays on your device.

## Features

- **Today dashboard** — daily steps, distance, weekly cardio and energy rings shown against your goals
- **Exercise** — log workouts (run, walk, cycle, swim, strength and more), browse exercise days and weekly cardio minutes
- **Sleep** — sleep score, duration, stage breakdown (deep / REM / light / awake) and an interactive hypnogram
- **Vitals** — heart rate, resting heart rate, SpO₂, weight, blood pressure and hydration
- **Interactive charts** — bar, line and hypnogram charts with drag-to-scrub value inspection
- **Goals & focus tiles** — customizable tiles with editable daily and weekly goals
- **Material 3 Expressive UI** — dynamic color (Material You), light and dark themes, spring-based motion

## Requirements

- Android 8.0 (API 26) or newer
- Health Connect installed and connected (the app asks for permissions in context, with a stated reason)
- JDK 21 and Android Studio Hedgehog or newer — or just the Gradle wrapper

## Build

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Project structure

Single-module Kotlin/Compose app, packaged by layer under `com.healthcompanion.app`:

```
app/src/main/java/com/healthcompanion/app/
├── MainActivity.kt          # single-activity host: theme, permissions, bottom nav
├── data/
│   ├── healthconnect/       # Health Connect read/write source
│   └── usecase/             # logging use cases (workout, sleep, vitals, …)
├── model/                   # domain models and calculators
└── ui/
    ├── components/          # shared charts, cards and controls
    ├── format/              # formatting helpers
    ├── navigation/          # type-safe route table
    ├── screens/             # one folder per tab/feature
    ├── state/               # UI state holders
    └── theme/               # colors, typography, shapes, motion tokens
```

## Privacy

- All health data is read from and written to **Health Connect** — nothing leaves the device.
- No accounts, no analytics, no network calls.
- The app declares only the Health Connect permissions it needs (sleep, heart rate, resting heart rate, exercise and routes, steps, calories, distance, SpO₂, weight, hydration, blood pressure) and includes the `ViewPermissionUsageActivity` alias Health Connect requires to show data usage.

## Contributing

Issues and pull requests are welcome. Please run `./gradlew :app:assembleDebug :app:lintDebug` before submitting.

## License

[MIT](LICENSE)
