# Privacy Policy — ESC/POS Print Bridge

_Last updated: 25 August 2026_

## The short version

This app collects nothing, sends nothing anywhere, and has no accounts,
analytics, advertising or tracking of any kind.

## What it stores

Four settings, on your device only:

- the printer's address and port
- the port the bridge listens on
- whether the bridge accepts connections from other devices
- a short activity log of recent print jobs, kept so problems can be diagnosed

None of this leaves the device. Uninstalling the app deletes all of it. If you
have Android's backup enabled, these settings may be included in your own
device backup — that is Android's mechanism and your backup, not ours.

## What it sends, and where

**To your printer.** The receipt data handed to the app by whatever web page
you point at it, sent over your local network to the printer address you
entered. Nowhere else.

**To GitHub, once a day.** A request asking what the latest published version
number is, so the app can tell you an update exists. It sends no information
about you or your device beyond what any HTTPS request unavoidably reveals to
the server it contacts. This is the only request the app makes to the internet.

## What it does not do

- No analytics, crash reporting or telemetry
- No advertising and no advertising identifiers
- No accounts, sign-in or personal data
- No access to contacts, location, camera, microphone, photos or files
- Receipt contents are never stored or transmitted anywhere but your printer

## Permissions, and why

| Permission | Why |
| --- | --- |
| `INTERNET` | To open a socket to your printer and check for updates |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | To hold Wi-Fi awake while a print is in flight |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | To keep the bridge listening while you use other apps |
| `WAKE_LOCK` | To stop the device sleeping mid-print |
| `RECEIVE_BOOT_COMPLETED` | To start the bridge again after a restart |
| `POST_NOTIFICATIONS` | To show the ongoing status notification |

## Children

The app is a utility for printer hardware and is not directed at children.

## Changes

Any change to this policy will be committed to this repository, so its full
history is public and auditable.

## Contact

Raise an issue at
https://github.com/beingretrogamer/escpos-print-bridge/issues
