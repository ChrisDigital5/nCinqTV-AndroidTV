# nCinqTV for Android TV

Native Android TV client for nCinqTV. It is a separate project and does not modify or bundle the website.

## Features

- No login wall: movies, shows, search, playback, continue watching, and tracker work immediately.
- Native remote-first Compose UI with clear D-pad focus.
- Media3 playback for HLS and MP4, including subtitle tracks.
- Local on-device tracker and resume progress with DataStore.
- Automatic episode warm-up and next-episode playback.
- Signed APK updates published by GitHub Actions.
- Latest APK download at `https://tv.ncinq.app/dl`.

## Build

The project uses Java 21 and Android SDK 36.

```bash
./gradlew test assembleDebug
```

Release builds require these environment variables and a keystore at `keystore/ncinqtv-release.jks`:

- `NCINQ_KEYSTORE_PASSWORD`
- `NCINQ_KEY_ALIAS`
- `NCINQ_KEY_PASSWORD`

## Release

Push a semantic version tag such as `v1.0.1`. GitHub Actions builds the signed APK and publishes it as a GitHub Release. The app and `/dl` endpoint discover the latest public release automatically.

## Catalog worker

`backend/` is a small Cloudflare Worker dedicated to the Android app. It owns only `/api/android/*` and `/dl*`, leaving the existing web worker and codebase untouched. Store the TMDB key as a Worker secret before deploying:

```bash
npx wrangler secret put TMDB_API_KEY
npm run deploy
```
