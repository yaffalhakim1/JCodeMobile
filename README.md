# JCode Mobile

Native Android client for [jcode](https://github.com/1jehuang/jcode). The phone
drives sessions on your own PC over Tailscale; every LLM call, tool, file edit
and git operation stays on the host.

Kotlin + Jetpack Compose, Material 3, minSdk 26, compileSdk 36.

## Build

```sh
gradlew.bat :app:assembleDebug        # Windows
./gradlew :app:assembleDebug          # macOS/Linux
```

APK: `app/build/outputs/apk/release/app-release.apk`

Unit tests: `gradlew.bat :app:testDebugUnitTest`

## Download the APK

Every push to `main` publishes a rolling alpha release:

https://github.com/yaffalhakim1/JCodeMobile/releases/tag/alpha

Download `jcode-mobile-alpha-0.1.0.apk` and install it directly. It is signed
with the CI debug key (installable, not Play-ready).

If an older debug build is installed, uninstall it first: the signatures
differ and Android rejects an in-place update.

## Pair the phone

On the PC, with the jcode gateway enabled:

```sh
jcode pair
```

In the app, enter the host (Tailscale IP or MagicDNS name), port `7643`, and the
6-digit code. The token is sealed with AES-256-GCM under an Android keystore key.

`adb reverse tcp:7643 tcp:7643` plus host `127.0.0.1` works on an emulator.

## Project picker

jcode's gateway exposes `/health`, `/pair` and `/ws` only, so the phone cannot
list the host's projects by itself. `tools/projects_bridge.py` fills that gap by
grouping `~/.jcode/sessions/*.json` by `working_dir` and serving the ranked list
on port 7645.

```bat
tools\run-projects-bridge.bat
```

Keep it running while using **New session**. It binds `0.0.0.0:7645`, so keep it
on Tailscale; it has no auth by default. Pass `--token <token>` to require a
bearer token (the app currently does not send one).

If the bridge is not running, the picker falls back to a manual path field.

## Mock gateway (no API cost)

```sh
python tools/mock_gateway.py --port 7643 --code 123456 ^
  --token 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef ^
  --scenario tool --push-demo
```

The mock speaks the same NDJSON protocol as the daemon. It seeds a plan, a
question card, an inline response, a permission notice, and the kenari.id model
catalog from `~/.jcode/cache/kenari_models.json` when present.

## Known limits

- **Permissions:** the gateway protocol has no approve/deny frame. Requests are
  shown in the app, but decisions stay on the host (`jcode permissions`).
- **Session list:** there is no session-list event; the sidebar refreshes history
  while idle and updates the current session from live events.
- **No foreground service yet:** a turn survives a transport drop, not the app
  process being killed.

## Layout

```
app/src/main/java/dev/jcode/mobile/
  data/remote/       wire codec, gateway client, projects bridge client, socket
  data/chat/         pure session reducer and todo parser
  data/              repository, settings, sealed credentials
  ui/                sessions, chat, connect, settings, theme
tools/               mock gateway, projects bridge
maestro/             emulator flows
```
