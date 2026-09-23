# Aiden Instant Coffee — App guide and technical overview

**Version 0.4 · 23 September 2026**

## What the app does

Aiden Instant Coffee is a small native Android app for starting a Fellow Aiden coffee
brewer from a phone. Its main action, **Start instant brew**, uses the profile
and water quantity already saved for Instant Brew on the brewer. A compact
home-screen widget performs the same action and opens the app to show progress.

The app displays the selected brewer, saved water quantity, stage indicator
lights and a countdown based on Fellow's reported finish time. It also supports
choosing between brewers on the account and signing out.

Once installed, it works independently of the development PC. Both the phone
and brewer need internet access to Fellow's service. They do not need USB,
Bluetooth or a connection to the local prototype webpage.

This is a personal app, not an official Fellow product. It uses the API behavior
recovered from Fellow's Android app during development.

## Using the app

1. Open **Aiden Instant Coffee** and sign in with the Fellow account that owns the brewer.
2. If the account has one Aiden, it is selected automatically. For multiple
   brewers, use **Choose brewer**.
3. Prepare the brewer with water, a filter, coffee and the appropriate basket.
4. Check the saved water quantity shown in the app.
5. Tap **Start instant brew**. The app sends one start request and then reads
   the brewer's status.

Change the Instant Brew recipe and quantity on the brewer or in Fellow's app.
This app reads those settings again immediately before starting, so it does not
depend on an old quantity displayed on screen.

An accepted request means Fellow accepted the API call. It is not, by itself,
proof that the machine has begun dispensing water. The stage lights provide
subsequent reported progress; check the brewer if the result is unclear.

## Home-screen widget

Add **Aiden Instant Coffee** through the phone launcher's Widgets menu. The widget requests
a single 1×1 cell, although the launcher controls its actual dimensions. It
shows the coffee-cup icon with no visible text or live status display.

Tapping it opens the app and automatically requests a brew. If sign-in is
missing or expired, the app asks you to sign in instead. Completing sign-in does
not automatically resume the interrupted start; tap Start instant brew when
ready.

A widget tap received while a status read is running is retained until that
read finishes. Taps during an in-flight start request are ignored. Recreating
the screen, for example during rotation, does not deliberately repeat the
original widget action.

## Stage lights and updates

The lights represent the latest cloud-reported state, rather than a direct
measurement by the phone.

| Light | Meaning in this app |
| --- | --- |
| Ready | The device is online and the app does not identify an active or completed brew from the current fields. This is not a full preparation check. |
| Bloom | The brewer reports the bloom stage (`b`). |
| Brewing | General brewing (`br`), a numbered pulse such as `p2`, or pouring (`pr`). The heading can show the pulse number or pouring. |
| Drip finish | The brewer reports the finishing-drip stage (`d`). |
| Complete | The brewer reports `bc`, or the reported end timestamp is later than the start and has now passed. |
| Attention | The brewer reports an error or pause (`pa`). Check its display for instructions. |

Offline status is shown separately. Failed reads show **Status unavailable** and
clear the stage lights. While the app is visible, it normally schedules another
read five seconds after the previous request finishes. Network delays can make
the interval longer. Polling stops when the app leaves the foreground and
resumes when it becomes visible again.

Fellow's fields have sometimes disagreed: a stale `brewing=true` or pulse value
can remain after an end timestamp. The app therefore considers timestamps as
well as the brewing flag. Completion based on an elapsed end timestamp is an
inference from cloud timing, not independent confirmation of physical completion.

## Countdown

The countdown follows the calculation found in Fellow's own Android app:

```text
remaining seconds = max(0, floor(brewEndTime − current Unix time in seconds))
```

`brewEndTime` can be a planned finish time while a brew is still running. It is
not exclusively a historical completion timestamp. The app updates the visible
countdown once per second and uses revised end times from subsequent status
responses.

The countdown is hidden when timing is missing, the brewer is offline, brewing
is not reported, or the remaining time reaches zero. A reported pause displays
**Countdown paused**. Otherwise, a failed read or timing data older than twenty
seconds displays **Waiting for timing update**.

This is not a fixed four-minute estimate or the delay before another start is
allowed. The calculation matches the recovered Fellow app code, but exact
agreement with the brewer's physical display still depends on clock accuracy,
cloud updates and network delay.

## How the brewing request works

The communication path is:

```text
Android app → HTTPS → Fellow cloud service → Aiden brewer
Android app ← device status ← Fellow cloud service
```

The current implementation uses these operations:

| Operation | API request |
| --- | --- |
| Sign in | `POST /auth/login`, with email, password and the phone's timezone |
| Read brewers and status | `GET /devices?dataType=real` |
| Start the selected recipe | `PATCH /devices/{deviceId}/start` |

Authenticated requests carry Fellow's access token. Before a start, the app
fetches the device list, verifies the selected device is online, and checks
whether the reported state indicates an existing brew. It then reads
`ibSelectedProfileId` and `ibWaterQuantity` and sends a body such as:

```json
{
  "profileId": "plocal1",
  "amountOfWater": 300
}
```

These values are examples from the user's Instant Brew setup, not constants in
the app. Water quantity must be between 1 and 1,500 ml and is sent as an integer.
The profile identifier must be present.

This explicit-recipe request is the path that worked in the web prototype and
in the native app's main button. The app does not need to enable Advanced mode,
write `doBrew`, modify sensor fields or enter an admin mode.

Start requests are serialized. A persisted sixty-second guard prevents another
attempt immediately afterward, including across app restarts. The app does not
automatically retry a failed or timed-out start, because the original request
may already have reached the machine. Read-only status requests continue while
the app is visible.

## Sign-in and stored data

The password is used for sign-in and is not saved. The returned access token is
encrypted with AES-GCM using a key held by Android Keystore. The encrypted token
is stored in the app's private preferences, alongside the selected brewer ID,
a brewer display label and the most recent start-attempt timestamp.

Requests use HTTPS; cleartext traffic and automatic Android app backup are
disabled. The app does not intentionally log credentials or cloud responses.
Users can take normal Android screenshots of the app.

The widget invokes a non-exported activity through an immutable PendingIntent.
Other apps cannot directly launch that private brew activity. Opening the
ordinary launcher activity does not automatically start brewing.

Signing out removes the saved session and brewer selection. An authentication
rejection clears the session and returns to sign-in. Automatic token renewal is
not implemented, so an expired session requires signing in again.

## Implementation and maintenance

The app is written in Java using native Android views and the Android widget
framework. It requires Android 8 or newer and currently targets API level 34.
It has no third-party runtime libraries and no Python server dependency.

| File | Responsibility |
| --- | --- |
| `MainActivity.java` | Sign-in screen, brewer selection, brew button, status polling, lights and countdown |
| `WidgetActivity.java` | Private entry point for widget-triggered starts |
| `BrewWidget.java` | Compact widget and its launch action |
| `Fellow.java` | HTTPS requests, device selection and start-request validation |
| `Session.java` | Encrypted token storage and sign-out |
| `BrewPolicy.java` | State interpretation and countdown calculation |
| `tests/PolicyCheck.java` | Fourteen deterministic state and countdown checks |
| `build.ps1` | Local compilation, APK assembly, alignment and signing |

Run `build.ps1` from PowerShell to produce `build/Aiden-Brew.apk`. The script
uses the JDK and Android SDK installed with Unity; its AndroidPlayer path can
be overridden. Keep the development signing key in the ignored build directory
to install later versions over this copy while preserving app data.

Version 0.4 was built, signature-verified, installed and opened on the user's
phone. The user confirmed that the native app's main brew button works. The
fourteen automated checks cover state transitions, stale/future timestamps,
offline/pause/error handling and countdown arithmetic. Automated checks did
not start a physical brew. Exact physical-display countdown agreement remains
unverified.

There is no recipe editor, scheduling feature, stop-brew button, background
progress notification or offline brewing path in this version. The Fellow API
is not a documented compatibility contract for this app; service or firmware
changes may require updates.

## Troubleshooting

| Symptom | What to do |
| --- | --- |
| Sign-in appears again | The session expired or was rejected. Sign in again. |
| Brewer offline | Check the brewer's internet connection and Fellow account. |
| Start is disabled or an attempt is blocked | Check whether a brew is already running or a start was attempted within the last minute. |
| Request timed out | Check the machine before trying again; the request was not retried automatically. |
| Lights or countdown stop updating | Bring the app to the foreground and check the phone's connection. |
| Widget retains its old large size | Resize it, or remove and re-add it through the launcher's Widgets menu. |
| More than one Aiden is on the account | Open Choose brewer and select the intended machine before using the widget. |
