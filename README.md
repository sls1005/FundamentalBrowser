Fundamental Browser is a simple and basic browser designed for running on Android. It is based on `WebView` and is intended to be developer-friendly, like [Eruda](https://github.com/liriliri/eruda-android).

This is usable but not yet finished. Several buttons and options will not respond.

### Screenshots

![](screenshots/screenshot1.jpg)

![](screenshots/screenshot2.jpg)

### Features to be implemented

A developer-friendly browser should: (A checked box means implemented or already supported.)

- [x] Provide a field for editing URL

    - [x] The field itself should be large enough and should allow multiline editing.

    - [x] Can be closed or collapsed, so that the user can focus on the content.

    - [x] Should use a monospace font.

    - [x] The text size should be large enough.

    - [x] The URL should be displayed as-is.

- [ ] Customizable headers

- [ ] Support desktop mode.

- [ ] Selectable or customizable locales (for the content rather than UI)

- [ ] Support searching in content.

- [x] Log everything so that nothing is secretly down- or up-loaded.

- [ ] Display cookies.

- [x] Support `view-source:`. (Supported through framework)

- [x] Support `javascript:`. (Supported through framework; the output of `console.log` will be joined with the app's log.)

**The following features are not planned and thus will not be implemented in near future:**

- [ ] Allow more than one window/tab.

- [ ] Ad-blocking

- [ ] Anti-trace

### Other features

#### Separated buttons for searching and URL loading

To make the search button usable, the 'Search URL' must be set. The search button is not the same as the 'Go' button, which loads a page of a given URL but will not do search, while the search button will not load a URL but... search it.

#### Console mode

To enter the console mode, input some code with the `javascript:` prefix in the URL field and press the 'Go' button while the log window is opened, or load it before loading any page or search. You must prefix each commend with `javascript:` to keep it in this mode. This can also be used as an interpreter. Remember not to use the 'Search' button, which will not execute any code in the URL field, but will search it.

![](screenshots/screenshot3.jpg)

Using a commend prefixed with `javascript:` in any other situation will cause it to be executed normally, as if loading a URL. The execution of such code, even in the console mode, is never handled by this app, but by the framework or system. This app only implemented the logic to display the log messages; things like `alert` or `confirm` might not work.

### Note

* Although newline chatacters are allowed in the URL field, they will not be treated as linebreaks, but as whitespaces. So be careful about your **code** that uses linebreaks, if you use any.

* Remember to (or not to) update your 'System WebView.' It may affect the behavior of this application.

### Disclaimer

- **JavaScript** is a trademark or registered trademark of Oracle Corporation.
