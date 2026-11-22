# Google Play release guide

Everything needed to publish ScreenShare Web on Google Play: what to upload and what to answer in each Play Console form.

## Files

| What | Where |
|---|---|
| App bundle to upload | `app/build/outputs/bundle/playRelease/app-play-release.aab` (build with `./gradlew bundlePlayRelease`) |
| App icon (512×512) | `fastlane/metadata/android/en-US/images/icon.png` |
| Feature graphic (1024×500) | `fastlane/metadata/android/en-US/images/featureGraphic.png` (source: `play/feature-graphic.html`) |
| Phone screenshots | `fastlane/metadata/android/en-US/images/phoneScreenshots/` |
| Privacy policy | https://aiko929.github.io/ShareScreenWeb/privacy-policy.html |

## Signing

Release builds are signed with the **upload key** only when `keystore.properties` exists in the project root. That file is git-ignored, so builds from source (including F-Droid) stay unsigned.

```properties
storeFile=C:/path/to/sharescreenweb-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

When creating the app in Play Console, keep **Play App Signing** enabled (the default). Google holds the real app-signing key; you only keep the upload key. If the upload key is ever lost, it can be reset through Play Console support. Still, back it up.

## Ads (AdMob)

The `play` flavor shows a bottom banner on the main screen and a full-screen (interstitial) ad when the user taps Start sharing, at most once every 5 minutes (also across app restarts). Users can turn off each one separately in Settings. In the EU/UK/CH, Google's consent form (UMP) appears before ads load.

Until real IDs are set, the app uses **Google's official test ads**. Debug builds always use test ads, so you can't accidentally click your own ads (that gets AdMob accounts banned).

1. Create an AdMob account at https://admob.google.com (same Google account as Play Console is easiest) and add payment/tax details there.
2. **Apps → Add app** → Android → "ScreenShare Web" (link it to the Play listing once it's published).
3. **Ad units:** create one **Banner** and one **Interstitial** unit.
4. **Privacy & messaging → European regulations:** create a GDPR message for the app and publish it. Without it, EU users see no consent form and get no ads.
5. Create `admob.properties` in the project root (it is fine to commit; the IDs are public inside the APK anyway):
   ```properties
   appId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
   bannerId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
   interstitialId=ca-app-pub-XXXXXXXXXXXXXXXX/WWWWWWWWWW
   ```
6. Rebuild with `./gradlew bundlePlayRelease`.
7. Optional but recommended: AdMob asks for an `app-ads.txt` on the developer website listed in Play. AdMob shows the exact line to publish.

## Store listing

**App name** (max 30 characters)
```
ScreenShare Web
```

**Short description** (max 80 characters)
```
Share your screen to any browser on your local network
```

**Full description** (max 4000 characters)
```
ScreenShare Web mirrors your Android screen to any web browser on the same Wi-Fi network or on your phone's hotspot. Viewers don't need to install anything: they just open the address the app shows.

HOW IT WORKS
• Tap the big button and confirm Android's built-in screen-capture prompt.
• The app shows an address like http://192.168.1.23:8080
• Open it in a browser on a laptop, TV, tablet or another phone in the same network.
• Use the Share button to send the address through any app.

FEATURES
• Uses Android's own screen sharing, no root needed
• Works in every browser, nothing to install for viewers
• Keeps running in the background with a notification and a Stop button
• Handles screen rotation
• No accounts, no cloud: your screen never leaves your local network
• Contains ads; the banner and the full-screen ad can each be turned off in Settings
• Open source (GPL-3.0): github.com/aiko929/ShareScreenWeb

NOTE
The stream is not password-protected. Anyone on the same network who knows the address can watch while sharing is active, so only share on networks you trust.
```

**Category:** Tools
**Tags:** Screen mirroring, Remote access, Tools
**Contact email:** a public address for users (required by Play; your choice)
**Website:** https://github.com/aiko929/ShareScreenWeb

## App content forms (Policy → App content)

| Form | Answer |
|---|---|
| Privacy policy | https://aiko929.github.io/ShareScreenWeb/privacy-policy.html |
| Ads | **Yes**, the app contains ads |
| App access | All functionality is available without special access (no login) |
| Content rating (IARC) | Category "Utility, Productivity, Communication or Other". Answer No to violence, sexual content, language, controlled substances, gambling. For "Can users interact or exchange content?", the app streams the user's own screen to devices on their local network that they choose; there are no accounts, chat or public sharing. Answer as you see fit; "No" is defensible, "Yes" is the cautious choice and only adds a notice. |
| Target audience | 18 and over (keeps the app out of the Families policy; nothing is kid-specific) |
| News app | No |
| Government app | No |
| Financial features | None |
| Health | None |
| Data safety | See below |
| Foreground service permissions | See below |

### Data safety

The app itself sends nothing to the developer, and the screen stream only goes to browsers on the user's own local network. But the **AdMob SDK collects data**, and that must be declared. Before submitting, check Google's current list for the SDK: https://developers.google.com/admob/android/privacy/play-data-disclosure. As of this writing it is:

- **Does your app collect or share any of the required user data types?** Yes.
- **Is all of the user data collected by your app encrypted in transit?** Yes.
- **Do you provide a way for users to request that their data is deleted?** No (Google handles ad data; users can reset their advertising ID in Android settings).
- **Data types collected:**

| Data type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Location → Approximate location (from IP address) | Yes | Yes | Optional (ads can be turned off) | Advertising or marketing, Analytics, Fraud prevention |
| App activity → Other user-generated content / App interactions | Yes | Yes | Optional | Advertising or marketing, Analytics |
| App info and performance → Crash logs, Diagnostics | Yes | Yes | Optional | Analytics, Fraud prevention |
| Device or other IDs (advertising ID, app set ID) | Yes | Yes | Optional | Advertising or marketing, Analytics, Fraud prevention |

"Optional" is accurate because users can turn off both ad types in Settings, and then the SDK isn't started.

### Foreground service declaration (FOREGROUND_SERVICE_MEDIA_PROJECTION)

**Which feature uses it:** Screen sharing to a web browser on the local network.

**Description:**
```
The app's only function is to share the user's screen to a web browser on the same local network. When the user taps "Start sharing" and confirms Android's MediaProjection consent dialog, a foreground service of type mediaProjection captures the screen and serves it as a live MJPEG stream from a small HTTP server on the device. The service must keep running while the user switches to other apps, because showing those apps is the whole point of sharing. It shows an ongoing notification with a Stop button and stops immediately when the user taps Stop, uses Android's screen-sharing indicator, or the projection ends.
```

**Video:** record a short screen recording (30–60 s) showing: open app → tap Start sharing → Android's consent dialog → address shown → open the address in a laptop browser → switch apps on the phone and see it mirrored → tap Stop in the notification. Upload it unlisted on YouTube and paste the link.

## Release steps

1. Create a Google Play developer account (personal), pay the one-time $25 fee and complete identity verification.
2. **Create app** → name "ScreenShare Web", default language English (US), App, Free.
3. Fill in the store listing and all App content forms above.
4. **Testing → Closed testing:** create a track, upload `app-play-release.aab`, add release notes ("Initial release."), add testers (an email list or Google Group).
   New personal developer accounts need **at least 12 testers opted in for 14 days in a row** before production access can be requested.
5. Send testers the opt-in link. They join, install from Play, and keep it installed.
6. After 14 days: **Dashboard → Apply for production**, answer the short questionnaire about the test.
7. Once approved: **Production → Create release**, promote the tested bundle, roll out.

## Releasing updates

1. Bump `versionCode` (must always increase) and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. `./gradlew bundlePlayRelease` and upload the new `.aab`.
4. Commit and tag `v<versionName>`. The tag is also what F-Droid uses later.
