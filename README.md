<p align="center"><img src=".github/assets/icon.png" width="128" alt="Z-Sync icon, Anby"></p>

<h1 align="center">Z-Sync</h1>

<p align="center">A Zenless Zone Zero companion for Android that keeps your HoYoLAB account up to date on its own: Check-In, codes, Traveling Mimo, End-Game ranks and the banners to come.</p>

<p align="center">
  <img src=".github/assets/badges/android.svg" alt="Android 10+">
  <a href="../../releases/latest"><img src=".github/assets/badges/version.svg" alt="Version 1.0.0"></a>
  <img src=".github/assets/badges/kotlin.svg" alt="Kotlin 2.0, Jetpack Compose">
  <a href="LICENSE"><img src=".github/assets/badges/license.svg" alt="MIT License"></a>
</p>

<p align="center"><img src=".github/assets/hero.webp" alt="Z-Sync, a Zenless Zone Zero companion for Android" width="100%"></p>

## Download

Get the APK from the [latest release](../../releases/latest). Android 10 or newer. The app checks this repository for new versions and can update itself from Settings.

<img src=".github/assets/status.webp" alt="Status: battery, dailies, weeklies, Traveling Mimo and Check-In" width="100%">

<img src=".github/assets/endgame.webp" alt="End-Game: Shiyu Defense, Deadly Assault and Adversity Mode, with an Eous sticker for every rank" width="100%">

<img src=".github/assets/resources.webp" alt="Resources: currencies, current banners and pity" width="100%">

<img src=".github/assets/events.webp" alt="Events: every event with its banner" width="100%">

<img src=".github/assets/zsync-mode.webp" alt="Z-Sync Mode: automatic schedule" width="100%">

<img src=".github/assets/themes.webp" alt="Light and true-black themes, 68 launcher icons" width="100%">

## Build

Requires JDK 17+ and the Android SDK (platform 35). Create `local.properties` with `sdk.dir=<path to SDK>`, then:

    ./gradlew :app:assembleDebug

API smoke tests (no account needed): `LIVE=1 ./gradlew :core:test`

## Structure

- `core/` – plain Kotlin, no Android: HoYoLAB API, login, scheduling engine, Mimo, update checks, and readers for the official update notices, Game8 and the Fandom wiki.
- `app/` – Android: storage, notifications, background work, widget, Jetpack Compose UI.

## Credits and disclaimer

Z-Sync is a free fan project, distributed only through this GitHub repository. It is not on Google Play or
any other store.

**None of the images, icons, stickers, sounds, names or other game content used in Z-Sync belong to me.**
All credit goes to their respective authors:

- **HoYoVerse** – Zenless Zone Zero, HoYoLAB and every character, icon, artwork, sound and name from the game.
- **[torikushiii](https://github.com/torikushiii)** – creator of [hoyolab-auto](https://github.com/torikushiii/hoyolab-auto), the project that inspired Z-Sync.
- **[zzze.ro](https://zzze.ro/stickers)** – collected the stickers used in the app and in the promo images.
- **[Game8](https://game8.co)** and the **[Zenless Zone Zero Fandom wiki](https://zenless-zone-zero.fandom.com)** – upcoming banners and fallback boss information. Current bosses come from the official HoYoVerse update notices.
- **[genshin.py](https://github.com/thesadru/genshin.py)** – API details were cross-checked against it.

Z-Sync is not affiliated with, endorsed by or connected to HoYoVerse in any way. If you own any of this content
and want it removed, open an issue and it will be taken down.

## License

Code under the [MIT License](LICENSE). Game names, images and other assets belong to their respective owners.
