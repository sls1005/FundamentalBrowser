# Fundamental Browser

Fundamental Browser is a basic browser designed for running on Android. It is based on `WebView` and is intended to be developer-friendly.

Although not originally designed for, it can also be used as a JavaScript interpreter (works offline), generalized (and programmable) intent sender and activity launcher.

When used as a JavaScript interpreter, it can even work on platform API level 29 and above. (i.e. not subject to the W^X restriction, not at all, or at least not in technical aspects.)

However, the functionality of this app will not exceed what it should have as a browser with built-in developer tools. (It is... was common for browsers to have a JavaScript console, wasn't it?)

### Screenshots

![](screenshots/screenshot1.jpg)

![](screenshots/screenshot2.jpg)

![](screenshots/screenshot3.jpg)

### Features to be implemented

A developer-friendly browser should: (A checked box means implemented or already supported.)

- [x] Provide a field for editing URL

    - [x] The field itself should be large enough and should allow multiline editing.

    - [x] Can be closed or collapsed, so that the user can focus on the content.

    - [x] Should use a monospace font.

    - [x] The text size should be large enough.

    - [x] The URL should be displayed as-is.

- [ ] Customizable headers

- [x] Support desktop mode (on mobile).

- [x] Selectable or customizable locales (for the content rather than UI; implemented and tested)

- [x] Support searching in content.

- [x] Log everything so that nothing is secretly down- or up-loaded.

    - [x] The log is searchable.

- [ ] Display cookies.

- [x] Support `view-source:`. (Supported through framework)

- [x] <strike>Support `javascript:`.</strike> (Supported through framework, but considered deprecated now in this app.)

**The following features are not planned and thus will not be implemented in near future:**

- [ ] Ad-blocking

- [ ] Anti-tracking

- [ ] Bookmarks

- [ ] History (Use the log instead.)

- [ ] PDF viewer

### Other features

#### Separated buttons for searching and URL loading

To make the search button usable, the 'Search URL' must be set. The search button is not the same as the 'Go' button, which loads a page of a given URL but will not do search, while the search button will not load a URL but... search for it.

#### Console and REPL

To use the console and REPL features, input some code (should normally be JS code) in the URL field and then use the 'Run' button (enabled in settings) to execute it.

![](screenshots/screenshot4.jpg)

![](screenshots/screenshot5.jpg)

![](screenshots/screenshot6.jpg)

The support for `javascript:` scheme in this app is considered deprecated since Mar. 2025, and now the recommended way to run code in this app is to use the 'Run' button. The execution of such code, even in the console mode, is never handled by this app, but by the framework or system. This app only implemented the logic to display the log messages.

If there is a loaded script, you can possibly access its API.

![](screenshots/screenshot7.jpg)

#### Generalized intent sender and activity launcher

![](screenshots/screenshot8.jpg)

See <a href="#Details">details</a> for more information about this usage.

### Note

* Since this app allows multiline editing, the 'Go' and 'Search' buttons (but not the 'Run' button) will by default replace newline characters in the URL field with spaces, or encode them as `%20` (meaning space).

* To keep things simple, new windows are implemented in this app as new tasks/activities. Using more than one window may consume a lot of system resources due to this reason. Hence, new windows are not created automatically in most cases.

* For security concerns, JavaScript is automatically disabled by default when opening a URL from within another application. This can be changed in settings. In addition, this app will not load a URL from an intent unless its scheme is `http:` or `https:` (and currently, only the latter will be successfully loaded). The latter rule is not enforced for URLs from a webpage or for URL/URIs loaded manually by the user.

* This app currently cannot be used to open PDF URLs. (It can load them, but will show nothing.) Such support is not planned and is unlikely to be implemented in the future, because PDF files are, as far as I know, not commonly used in web development nor in webcrawling.

* Remember to (or not to) update your 'System WebView.' It may affect the behavior of this application.

### Details

* The whole app (except for icons, etc.) is written in Kotlin. No JavaScript used except in examples, tests and documentation (for the console/REPL feature).

* JavaScript code, including user-input code, is executed via platform API (for which the underlying system could utilize something called V8, but I'm not sure), so this app can target the latest platform API level (35 as of Feb. 2025) while still enabling users to run code. (Whether or not it meets Play store's standard is another thing. If you ask me, I would say that it should be allowed because JavaScript in browser is an explicitly stated exception to the rule.)

* Intent scheme URIs are half-supported. That is to say, they will usually be processed correctly, and the instantiated intent may be launched if approved by the user; however, this app usually doesn't check if a URI is an `intent:` URI, except when trying to protect itself from intent-based attacks. (See <a href="#References">[1]</a>.) Instead, `intent:` URIs will be treated as if they were URIs of unrecognized schemes (such as `abc://`), for any of which this app will ask if it should launch an intent. Nonetheless, the app will not ask nor try to launch any intent if it detects that the intent would be sent back to the activity itself or to any other component of this application (for which case it might not create a log entry and can show an error message), so as to prevent the security issue mentioned in <a href="#References">[1]</a>. Despite being suggested in <a href="#References">[2]</a>, this app doesn't use `CATEGORY_BROWSABLE` to instantiate such intents, as it would still be insufficient according to <a href="#References">[1]</a> (and this app doesn't use the unsafe constant anyway). This application has its own security-related mechanism as mentioned above; basically, an `intent:` URI is forbidden if it would be sent back to this app; it is otherwise up to the user's explicit intention. If this mechanism is proved to still be insufficient, please open an issue to let me know.

* By using some code that looks like `location.href="intent:#Intent;...;end"`, you can utilize this app as a generalized intent sender and activity launcher (by specifying `action`, `component`, etc. in the form of text; remember to use constant values rather than their names). Unlike in <a href="#References">[1]</a>, you don't have to add `SEL` (which should have no effect anyway), as the rules mentioned above still apply: you can't use it to access any activity of this app; for other apps, only exported activities. This usage is still under test.

### Projects that offer similar functionalities

* [Eruda](https://github.com/liriliri/eruda)

* [MobiDevTools](https://sourceforge.net/projects/mobidevtools/)

* [monocles browser](https://codeberg.org/monocles/monocles_browser)

These are listed purely for comparison. They are not associated with this app nor its author.

### References

1. <https://www.mbsd.jp/Whitepaper/IntentScheme.pdf>

2. <https://developer.android.com/reference/kotlin/android/content/Intent#URI_ALLOW_UNSAFE:kotlin.Int>

3. <https://developer.android.com/develop/ui/views/layout/webapps/debugging>

### Disclaimer

- **JavaScript** is a trademark or registered trademark of Oracle Corporation.

- **Android** and **Google Play** are trademarks or registered trademarks of Google LLC.
