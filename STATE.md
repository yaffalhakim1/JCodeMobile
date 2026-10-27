# JCode Mobile - work state

Updated 2026-10-27.

## Where it stands

- Project: `Y:\Learn\Mobile Apps\JCodeMobile`
- Build: `gradlew.bat :app:assembleDebug` passes.
- Tests: `gradlew.bat :app:testDebugUnitTest` passes (26 tests: wire codec + reducer).
- Debug APK: `app\build\outputs\apk\debug\app-debug.apk`
- Maestro: `C:\Users\yafit\maestro\maestro\bin\maestro.bat`
- AVD: `agent-avd`

## WebSocket

Verified on the emulator: `attempt=0` -> `onOpen`, stable for minutes.
The earlier "Disconnected" was repeated pairing churn, not a transport bug.
`JCodeConn` logs remain available for diagnostics.

## Done this session

- Per-session project folder: `subscribe.working_dir`; new-session sheet with
  path field plus recent projects (persisted per server); project shown in the
  chat header as `.../parent/project`.
- Steering queue: `soft_interrupt.client_id` per message, per-item cancel via
  `cancel_soft_interrupts.client_ids`, server-confirmed removal via
  `soft_interrupts_cancelled`; keyboard Send action.
- Themes: 7 palettes ported from OpenCodeMobile (JCode, Midnight, Tide, Plum,
  Ember, Sand, Mono) plus System/Light/Dark mode, persisted in DataStore.
  System bar icons follow the selected mode.
- Live sidebar: session events add the current session immediately; the list
  survives reconnects instead of flashing empty.
- Providers: `kenari.id` in the mock catalog; Settings lists every provider
  reported by the host. Real providers are configured on the host.
- Copy: removed "your machine" phrasing; removed token in/out from chat;
  removed the "done" tool label; plan counts read "n/m steps".
- Todo seed no longer says "Render the transcript".
- Relaunch keeps the chosen theme; test flows found no UI-thread stalls.

## Permissions

The gateway wire protocol has no permission request or decision frame. jcode
approvals are host-side (`jcode permissions` / TUI). The app shows incoming
permission notices and the host command, but deliberately offers no dead
accept/reject buttons. Mobile approvals need a protocol change on the host.

## Open items

- Foreground service so a turn survives backgrounding.
- QR camera scanning for `jcode://pair` invites.
- Real jcode smoke test on an isolated daemon/port (do not disturb the live
  daemon on 7643).
- End-to-end steering test against a deliberately slow server; the reducer and
  wire frames are unit-tested.

## Manual test recipe

```sh
python tools\mock_gateway.py --port 7643 --code 123456 ^
  --token 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef ^
  --scenario tool --push-demo
adb reverse tcp:7643 tcp:7643
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Pair with host `10.0.2.2` (or `127.0.0.1` with `adb reverse`), code `123456`.