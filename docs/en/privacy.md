# HyperIsland Privacy Policy

Last updated: September 30, 2026<br>
Effective: September 30, 2026

HyperIsland is an open-source LSPosed module for HyperOS. This policy explains what information the app processes, why it is processed, where it goes, and how to contact us. Please read it before use. If you disagree, select “Decline and exit” on first launch.

## 1. Scope

This policy covers the HyperIsland Android app, its LSPosed module, and anonymous compatibility analytics sent by the app. Third-party AI services, GitHub, LSPosed, and other independent services you choose to use process data under their own policies.

## 2. Information processed

### 2.1 Anonymous compatibility analytics

After you explicitly accept this policy, the app sends an `environment_snapshot` to the project's [self-hosted Aptabase service](https://aptabase.1812z.top/), containing:

- HyperIsland version name and code;
- Android API level, Android release, and device market name or model;
- Full build from `ro.build.version.incremental`;
- HyperOS major version from `ro.mi.os.version.name`;
- SystemUI and SystemUI plugin version names and codes;
- Focus notification protocol version;
- Xposed framework name and version, and whether the module is enabled with SystemUI in scope;
- Whether initial onboarding has not yet been completed;
- A random installation identifier (`installation_id`, also used as the event's `sessionId`);
- Event time, debug flag, locale, app version, and device model.

The installation identifier is randomly generated and stored locally when analytics first runs after consent. It remains unchanged across app restarts, device restarts, and in-place updates. Clearing app data or uninstalling and reinstalling generates a new identifier. It is not an Android ID, advertising ID, or hardware identifier and is used only to associate compatibility events from the same installation. The self-hosted server receives the source IP during a connection and may derive country or region information. The app does not submit the raw IP as a custom event property.

A snapshot is sent each time the app starts or returns to the foreground, and after initial consent. There is no longer a 24-hour reporting limit. Analytics runs only in the main HyperIsland app process, never in SystemUI or another hooked process.

### 2.2 Data processed locally for app features

To provide its features, HyperIsland may locally read or process installed apps and notification channels; notification titles, text, icons, and actions; Bluetooth device information selected for allowlists; user settings and backups; and Root, LSPosed, SystemUI, and system-protocol status. This information is used locally by default and is not submitted as Aptabase analytics.

### 2.3 Optional third-party features

If you configure and enable AI notification processing, the notification text required for processing, prompts, model parameters, and the API credential you supply are sent to the endpoint you configure. That provider controls its own retention and use. HyperIsland does not send your AI credential to Aptabase.

Opening update, tutorial, policy, or download links may connect your browser or the app to GitHub, hyperisland.1812z.top, or another displayed destination. Those services may process ordinary network logs under their own policies.

## 3. Permissions

- Network access: Aptabase, updates, documentation, and user-configured AI endpoints;
- App-list access: displaying and configuring apps, notification channels, and compatibility features;
- Bluetooth access: user-configured Bluetooth features, not location tracking;
- Download-related access: downloads explicitly initiated by the user;
- Root and LSPosed capabilities: configuration, scope checks, and system operations explicitly initiated by the user.

Declining an optional permission may disable its related feature but does not change the scope of anonymous analytics already accepted.

## 4. Purposes

Information is used only to provide and maintain module features, understand compatibility, prioritize support, diagnose version distribution, and protect the service and project. It is not used for advertising, profiling, cross-app tracking, or data sales.

## 5. Sharing, storage, and international transfer

Compatibility analytics is sent over HTTPS to `aptabase.1812z.top`, a self-hosted Aptabase service operated by the project maintainers, and stored on that service's servers. Its storage region depends on the deployment location. Aptabase is the open-source analytics software used by this service. We do not sell analytics data or disclose it to other parties except where necessary for these purposes, legal compliance, or protection of users and the project.

Retention is controlled by the self-hosted service configuration. Data is retained only as needed for the analytics purpose and identifiable project-level records may be deleted following a reasonable request. The random installation identifier associates events from the same installation but does not directly identify you.

## 6. Information not collected by analytics

Analytics does not collect Android ID, advertising ID, IMEI, serial number, MAC address, phone number, accounts, precise location, contacts, messages, file contents, notification content, user settings, installed-app lists, Bluetooth-device lists, or per-hook runtime status. The current Aptabase Kotlin SDK does not automatically upload app crashes, exception messages, or stack traces.

## 7. Security

We minimize analytics fields, use HTTPS in transit, and restrict analytics to the app's main process. No transmission or storage system can be guaranteed completely secure. Please report security concerns through the contact channel below.

## 8. Your choices and rights

On first launch, consent is stored locally and analytics starts only after you press the highlighted “Agree and continue” button. Selecting “Decline and exit,” pressing Back, or dismissing the dialog does not store consent and closes the app. Clearing app data or reinstalling causes the prompt to appear again.

You may revoke permissions in system settings, stop using the app, or uninstall it. Contact us to request access, correction, deletion, or information about analytics. Because data is anonymous and aggregated, some requests concerning a particular device cannot be matched to historical events.

## 9. Children

HyperIsland is intended for users capable of managing Android, Root, and LSPosed environments and is not directed specifically at children. Minors should use it with the guidance and consent of a guardian.

## 10. Changes

We may update this policy when features, analytics fields, or providers change. Material changes may cause the app to request consent again. The latest update date appears at the top of this page.

## 11. Contact

For privacy, data-processing, or security questions, contact the maintainers through [HyperIsland GitHub Issues](https://github.com/1812z/HyperIsland/issues).

See also the [Aptabase privacy policy](https://aptabase.com/legal/privacy) and [Aptabase open-source projects](https://github.com/aptabase).
