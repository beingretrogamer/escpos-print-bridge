# Store listing copy

Ready to paste. Character counts are Play Console's limits.

---

## App name (30 max)

```
ESC/POS Print Bridge
```
20 characters.

## Short description (80 max)

```
Let a web page print to your network thermal printer. No cloud, no account.
```
74 characters.

## Full description (4000 max)

```
Web pages cannot open a raw network connection, so they cannot talk to a
thermal printer directly. This app sits in between: it listens on a port on
your device and forwards what it receives to your printer over the network.

Point your web app at it and printing works — no cloud service, no account,
and nothing leaves your network except the receipt going to your printer.

WHAT IT IS FOR

Any web-based till, order screen, kitchen display or admin page that needs to
print to an ESC/POS thermal printer on the same network. If your printer
accepts raw data on port 9100, this will drive it.

HOW IT WORKS

Enter your printer's address, press Test print to confirm it answers, then
start the bridge. Point your web page at the address the app shows you. That
is the whole setup.

By default the bridge only accepts connections from this device, which is both
the safer arrangement and the one browsers permit most readily. You can open it
to the rest of the network if other devices need to print through this one.

BUILT TO STAY RUNNING

A till that stops printing halfway through a shift is worse than one that never
started, so the bridge runs as a foreground service, keeps the connection alive
while a job is in flight, and starts itself again after a reboot. The
notification tells you at a glance whether it is running, what it is printing
to, and how the last job went.

Jobs are queued one at a time, because a thermal printer accepts one connection
at a time — two at once and both receipts come out unreadable. If the printer is
asleep, the connection is retried. If a job fails after the printer has already
received part of it, it is reported rather than resent, so you never get a
duplicate receipt.

PRIVACY

No analytics, no advertising, no accounts, no tracking. Four settings are
stored on your device and nothing else. The only request it makes to the
internet is a once-a-day check for a newer version.

OPEN SOURCE

Source, issues and releases:
https://github.com/beingretrogamer/escpos-print-bridge
```

## Category

Tools · Utility

## Content rating

Everyone — no user-generated content, no data collection.

## Data safety form

- Does your app collect or share any of the required user data types? **No**
- Is all user data encrypted in transit? Not applicable — no user data leaves the device
- Do you provide a way for users to request data deletion? Not applicable — uninstalling removes everything

## Privacy policy URL

Enable GitHub Pages for this repository (Settings → Pages → Deploy from
`main`), then use:

```
https://beingretrogamer.github.io/escpos-print-bridge/PRIVACY
```

Or link the file directly if Pages is not enabled:

```
https://github.com/beingretrogamer/escpos-print-bridge/blob/main/PRIVACY.md
```

## Still needed, and only you can make these

- **Screenshots** — at least 2, phone and tablet. The settings screen running,
  and the notification expanded, tell the whole story.
- **Feature graphic** — 1024×500.
- **App icon** — 512×512 PNG. The launcher icon is a vector; it needs exporting
  at that size for the listing.
