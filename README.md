# X For You Disable

Hides X's "For you" tab and always opens the Following timeline.

## Browser extension (`extension/`)
- **Chrome / Edge:** `chrome://extensions` → Developer mode → Load unpacked → pick `extension/`.
- **Firefox (desktop):** `about:debugging` → This Firefox → Load Temporary Add-on → `extension/manifest.json`. It's removed on restart unless the extension is signed.
- If your X interface isn't in English, add the tab labels to `FOR_YOU_LABELS` / `FOLLOWING_LABELS` in `content.js`. Without them, it falls back to tab position.

## Android, for the X app (`android/`)
1. Open `android/` in Android Studio and let Gradle sync.
2. Optional, if something doesn't match: with X open on Home, run
   `adb shell uiautomator dump /sdcard/x.xml && adb pull /sdcard/x.xml`
   and check the tab nodes' `text`, `selected` and `clickable` values against `ForYouBlockerService.kt`.
3. Run it on the phone (or Build → Build APK, then `adb install`).
4. Open the app → Open accessibility settings → turn on **X For You Blocker**.
   On Android 13+, if the switch is greyed out: App info → ⋮ → Allow restricted settings.
5. Logs: `adb logcat -s ForYouBlocker`.

If you use X's "Dim" theme, change the dark overlay color in `overlayColor()` to `0xFF15202B`.

## iOS
Nothing can change the native iOS X app without a jailbreak. On a Mac, you can wrap the extension for Safari with
`xcrun safari-web-extension-converter extension/`, build it to your iPhone in Xcode, and use x.com in Safari.
