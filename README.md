# Aiden Instant Coffee for Android

Small native Java app and home-screen widget for the user's Fellow Aiden.
Android 8 or newer. No PC server, USB, Bluetooth, web view, or third-party
runtime dependencies are needed after installation. The phone and brewer need
internet access to Fellow's service.

Sadly, the most obvious feature, "instant brew", does not exist in the original Fellow Aiden app. Don't understand why they have not included it. Luckily, we can use AI (sorry about the water, but I get coffee), so I generated a small custom app that does one thing: it starts brewing when the button is pressed.

/ Happy Coffee, Anders Bjerin

## Use

1. Open **Aiden Instant Coffee** and sign in with the Fellow account that owns the brewer.
2. If you own multiple Aidens, use **Choose brewer**.
3. Prepare the brewer with water, filter, coffee and the appropriate basket.
4. Press **Start instant brew**, or add the home-screen widget from your launcher’s Widgets menu.
5. A widget tap opens the app and sends one brew request, then shows status lights.

The saved Instant Brew profile and quantity are read from Fellow immediately
before sending the explicit-recipe PATCH already verified by the web prototype.
This app does not change the recipe, Advanced mode or any sensor setting.
Status is read every five seconds while the app is visible. The widget is a text-free tappable button with an accessibility description.

The widget launches a private activity via an immutable PendingIntent; the
public launcher activity cannot start brewing via an externally supplied action.
If not signed in, the widget opens sign-in but does not start a brew afterward.
Screen recreation does not repeat a widget command. Failed/timed-out start
requests are never automatically retried. A persistent 60-second guard limits
repeated start attempts across the app and widget.

## Sign-in and privacy

The email, password and access token are encrypted with AES-GCM using a key
held by Android Keystore and saved in the app's private storage. Automatic app
backup is disabled. API requests use HTTPS, and credentials and responses are
not logged. If a status read finds an expired session, the app signs in with
the saved credentials and retries that read once. Brew commands are never
retried. Rejected credentials return to sign-in with the saved fields filled in.
Sign out removes the saved credentials, session and selected brewer.
Android screenshots of the app are allowed.

After upgrading from 0.5 or older, enter your email and password once to save
them. Existing session tokens remain supported, but older versions did not
save the password, so it cannot be recovered from an existing session.

## Build

Run `build.ps1` in PowerShell. It uses the SDK/JDK installed with Unity at the
default path in the script; pass `-AndroidPlayer` to use another installation.
SDK platform 34 and build-tools 36.0.0 are used. No network dependency downloads
are needed. Output: `build/Aiden-Brew.apk`.

This is a personal development APK, signed with the locally generated development
key under ignored `build/`. Keep that key to install future updates over this
copy. It is not a Play Store release or an official Fellow app.

## Verification

Built with javac, aapt2 and D8; aligned and signature-verified. Installed and
opened successfully on the USB-connected S25. Seven pure-Java checks cover
new-brew timestamps, stale brewing flags, bloom/pulse-related state selection,
offline, pause, error and completion precedence. No real login credentials or
brew command were used in automated checks. Live sign-in, brewing, and widget
placement still need the owner to test.

The app uses the API recovered from Fellow Android 1.4.6, not a documented public
API contract. Future service changes may require maintenance. Cloud status can
lag or contain stale fields; check the brewer if a request is uncertain.

Widget implementation follows Android's native AppWidgetProvider/PendingIntent
pattern: https://developer.android.com/develop/ui/views/appwidgets/advanced
## Version 0.6 verification

All 22 brewing-policy checks and 21 on-device authentication checks passed.
The authentication checks use isolated preferences, dummy credentials and
simulated HTTPS responses; no real login or brew request is sent. They cover
encrypted storage, token renewal, rejected credentials, sign-out cleanup and
preventing retries of brew commands. Live Fellow reauthentication remains to
be verified with the owner's account.

To repeat the Android authentication checks after building, connect a phone
and run `tests/test-session.ps1 -Serial <adb-device-serial>`. This installs the
app and a temporary test package, then removes the test package when finished.