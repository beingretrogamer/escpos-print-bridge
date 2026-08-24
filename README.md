# ESC/POS Print Bridge

A small Android app that lets a **web page print to a network thermal printer**.

Browsers cannot open raw TCP sockets, so a web app has no way to reach a
port-9100 ESC/POS printer on its own. This app listens on a local HTTP port,
accepts the receipt bytes, and forwards them to the printer over TCP.

```
web page  ──HTTP──▶  this app (localhost)  ──TCP──▶  printer (192.168.x.x:9100)
```

No dependencies — the HTTP server and the printer socket are written against
the JDK's own `ServerSocket`/`Socket`, so there is no dependency tree to age
badly on a device nobody updates.

## Install

**[Download the latest APK](../../releases/latest/download/escpos-print-bridge.apk)**

Open that link on the Android device itself, then allow "install unknown apps"
for whichever app you downloaded it with. The build is debug-signed, which is
fine for sideloading.

## Set up

1. Open **ESC/POS Print Bridge**.
2. Enter the printer's IP and port (usually `9100`), and the port to listen on
   (default `8080`).
3. **Test print** — this talks straight to the printer, skipping the HTTP hop,
   so a failure here means the printer or the network, not the bridge.
4. **Save & start**. An ongoing notification appears and should stay.
5. Point your web app at `http://localhost:8080`.

Then reboot the device and print again without opening the app. If that works,
autostart and the wake lock are doing their job.

## Why localhost matters

Browsers block an HTTPS page from calling a plain-HTTP address — but
`http://localhost` is exempt, because it counts as a trustworthy origin. So a
site served over HTTPS can talk to this app on the same device, while the same
site usually **cannot** talk to a bridge at `http://192.168.1.50:8080`.

That is the reason to run the bridge on the device doing the printing. The
bridge→printer hop is an ordinary socket and browser rules never apply to it.

By default the app binds to `127.0.0.1`, so it is unreachable from the rest of
the network. Untick **This device only** if other devices must print through
this one — then use the device's LAN address instead.

## HTTP API

| Method | Path | Body |
| --- | --- | --- |
| GET | `/` | text probe |
| GET | `/health` | JSON status |
| POST | `/print` | raw ESC/POS bytes, or JSON `{ ip?, port?, bytes: base64 }` |
| POST | `/print-base64` | JSON `{ ip?, port?, data: base64 }` |

`ip`/`port` in the body — or the `X-Printer-Ip` / `X-Printer-Port` headers —
override the configured printer for that one request.

Responses carry permissive CORS headers plus
`Access-Control-Allow-Private-Network: true`, so a browser preflight from an
HTTPS origin succeeds.

```js
await fetch('http://localhost:8080/print', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ bytes: base64EscPosBytes }),
});
```

## Why an app rather than a terminal

Running a bridge under a terminal app works until Android decides otherwise —
swipe it away or let the battery optimiser act, and printing stops mid-shift
with nothing to show for it. This runs as a **foreground service**, which is the
arrangement Android commits to keeping alive, with a partial wake lock so the
socket survives screen-off and a boot receiver so a reboot recovers on its own.

On aggressive vendor ROMs (Xiaomi, Oppo, Vivo) you may still need
Settings → Apps → ESC/POS Print Bridge → Battery → **Unrestricted**.

## Building

Nothing is built locally. GitHub Actions has the JDK and Android SDK:

- Push to `main` → APK as a run artifact.
- Push a tag `v*` → a public release with a stable download URL.

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Requirements if you build it yourself: JDK 17, Android SDK 34, Gradle 8.7.

## Source layout

| File | Role |
| --- | --- |
| `BridgeServer.kt` | HTTP server and printer socket. No dependencies. |
| `PrintBridgeService.kt` | Foreground service, notification, wake lock. |
| `BootReceiver.kt` | Restarts the bridge after a reboot. |
| `MainActivity.kt` | Setup screen, built in code — no XML layouts. |
| `Prefs.kt` | The handful of settings that ever change. |
| `BridgeLog.kt` | Rolling in-app activity log. |

## Publishing to Play Store

Not there yet. It would still need a release keystore (CI builds debug-signed),
a real launcher icon (it currently borrows a framework drawable), a privacy
policy, and a data-safety declaration — short, since the app collects nothing
and talks only to the printer you point it at.

## Licence

MIT — see [LICENSE](LICENSE).
