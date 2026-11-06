# NeonChess Trainer

Native Android chess training app built with Kotlin, Jetpack Compose, Room and DataStore.

## Features

- Offline play with estimated strength from 100–2800 Elo.
- Legal move validation including standard chess rules through chesslib.
- Completed-game import from the public Chess.com API (no live-game access).
- Local game library, analysis report, responsive neon UI and accessibility labels.
- A replaceable `ChessEngineRepository` boundary for a GPL Stockfish UCI implementation.

## Build

1. Install Android SDK 36 and JDK 17+.
2. Set `ANDROID_HOME` or add `sdk.dir` to `local.properties`.
3. Run `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Engine and licensing

The repository builds with `AdaptiveOfflineEngine`, a lightweight legal-move opponent. The UI and domain layer are already isolated behind `ChessEngineRepository`. A native Stockfish UCI binary can be added per ABI and exposed through that interface. When distributing a Stockfish build, include the corresponding GPLv3 notices and complete source as required by the Stockfish license.

## Fair-play boundary

NeonChess Trainer imports completed games only. It does not inspect active Chess.com games, draw overlays over other apps, or use Android Accessibility services.
