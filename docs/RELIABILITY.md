# Running reliably in the background

An SMS gateway is only useful if it is still running when the screen has
been off for hours. Android, and some manufacturers even more, work hard to
stop apps from doing exactly that. This page explains what the app does about
it and what the user has to allow.

## What the app does

| Mechanism | Why |
| --- | --- |
| **Foreground service** (`specialUse` type) with a permanent notification | Android does not kill a foreground service to reclaim memory, and lets it keep a network connection. The notification is mandatory and cannot be hidden by the app (the user can collapse it from the notification settings). |
| `START_STICKY` | If the system kills the process anyway, it restarts the service. |
| **Boot and update receiver** | Restarts the gateway after a reboot or an app update. |
| **Watchdog** (WorkManager, every 15 min) | Restarts the service if a battery manager killed it without a restart. |
| **Network callback** | Retries immediately when connectivity returns, instead of waiting for the backoff. |
| **Partial wake lock while sending** | Keeps the CPU awake until every message of a batch is handed to the radio. Held for seconds, never continuously. |
| **Durable reports** | Statuses and received SMS are queued in WorkManager, so nothing is lost while the gateway is down; they go out when it is back. |

## What the user allows

The home screen lists each of these with a button to fix it.

1. **SMS and SIM access** (required): `SEND_SMS`, `RECEIVE_SMS`,
   `READ_PHONE_STATE`.
2. **Unrestricted battery** (strongly recommended): exempts the app from
   Doze and App Standby. Without it, Android cuts the network of background
   apps when the phone is idle, and the gateway only catches up during
   maintenance windows (minutes to hours apart). It also lets the watchdog
   restart the service from the background on Android 12+.
3. **Notifications** (recommended, Android 13+): without it the foreground
   notification is hidden, and the user is not warned when the session ends.
4. **Manufacturer settings**: on Xiaomi, Huawei, Honor, Oppo, Realme,
   OnePlus, Vivo, Samsung and a few others, an extra "auto-start" or
   "unrestricted background" switch exists on top of Android's. The app opens
   that screen when it knows where it is, and links to
   [dontkillmyapp.com](https://dontkillmyapp.com), which documents each
   brand.

## Limits outside the app's control

**Android's SMS rate check.** When an app that is not the default SMS app
sends more than 30 SMS within 30 minutes, Android shows a confirmation
dialog for each further message. The server paces SMS to stay under it. On a
phone dedicated to the gateway, the limit can be raised over adb:

```sh
adb shell settings put global sms_outgoing_check_max_count 1000
adb shell settings put global sms_outgoing_check_interval_ms 60000
```

**Carrier terms.** Consumer plans with "unlimited SMS" usually forbid
automated or commercial sending, and carriers run their own spam filters. A
burst of identical messages can get a line suspended. The gateway is meant
for the user's own traffic at a human-like pace (notifications, reminders,
replies), not bulk campaigns.

**Delivery reports.** Many carriers do not send them, or only for some
destinations. A message staying `SENT` is normal and does not mean it was
not delivered.

**Number detection.** Many carriers do not write the SIM's number where the
phone can read it. The website then asks the user for it.

## Diagnosing

- The home screen shows the connection state and the last error.
- `adb logcat -s GatewayService SmsSender SmsReceivedReceiver StatusReportWorker InboundSmsWorker AuthRepository`
  shows what the gateway is doing.
- `adb shell dumpsys deviceidle whitelist | grep unlimitedmessaging` confirms
  the battery exemption.
- `adb shell dumpsys activity services app.unlimitedmessaging.android`
  confirms the service is running.
