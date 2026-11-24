# Nothing Shopping Widget

An offline shopping list for Android 8.0 (API 26) and later, designed for the Nothing Phone (4a) Pro. The home-screen widget shows a scrollable, priority-sorted list of remaining items. Tap the circle to mark an item purchased, the item text to edit it, or × to delete it. **Undo** restores the most recent deletion. To restore a purchased item, open **Purchased** in the companion app and uncheck the item.

The companion app also manages stores, filters items by store and priority, and controls the widget's store filter and light, dark, or system appearance. Store names entered when adding items are remembered and matched case-insensitively. Items with the same name stay separate.

## Architecture

- **Kotlin and Gradle Kotlin DSL** with a checked-in Gradle wrapper.
- **Jetpack Glance** renders the widget and routes launcher actions to Room-backed callbacks or a small Compose editor activity.
- **Jetpack Compose and Material 3** render the companion app, settings, and item editor.
- **Room** stores items, stores, the most recent deletion, and widget preferences. Mutations request an update of every placed widget. The widget has no periodic refresh and the app requires no network permission, account, or external service.

## Build on GitHub

1. Push this project to `main` on GitHub and open the repository's **Actions** tab. Enable Actions if GitHub prompts you.
2. Select **Android Build** and open a run triggered by the push. To start one manually, select **Run workflow**, choose `main`, and confirm. Pull requests also trigger the workflow.
3. Expand the build job to see the unit test, Android lint, and APK build output. The workflow uses Ubuntu, JDK 17, Android SDK 35, and the Gradle wrapper with dependency caching.

For development in a machine that already has JDK 17 and Android SDK 35 installed, run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Install the APK

1. Open a successful workflow run and download **nothing-shopping-widget** from its **Artifacts** section while signed in to GitHub.
2. Extract the ZIP and transfer or download `app-debug.apk` to your phone.
3. Open the APK in Files or your browser. If Android prompts you, allow that app to install unknown apps and confirm the installation. You can turn that permission off afterwards.

## Add and resize on Nothing OS

Long-press an empty part of the home screen and open **Widgets**. Find **Nothing Shopping**, then drag **Shopping list** onto the home screen. You can also use **Widget settings → Pin widget** inside the app if your launcher supports it.

Long-press the placed widget and drag the resize handles. Start with about **4 columns × 5 rows**. The launcher chooses the exact dimensions based on its grid, density, and available space, so the fraction of the screen varies. Scroll inside the widget to see more items. Tap **All stores** or the current store label to change what the widget shows.

## Android widget limitations

Android home-screen widgets cannot host an inline text editor. Add and edit open a floating activity with the keyboard. The launcher controls pinning, resize limits, and gesture handling, so the final size and scroll behavior can vary. All placed widgets share one store filter, appearance preference, and list. Undo retains one deletion across restarts, and the next deletion replaces it.

## Update without losing data

Install the new APK over the current app. Keep the application ID (`com.phydevs.shopping`) and signing key the same and keep the new APK's `versionCode` at least as high as the installed version. Increment it for each distributed update. Do not uninstall or clear app storage before updating. An in-place update preserves the Room database. Future schema changes must include Room migrations. Uninstalling or clearing app storage removes the locally stored list.

### Keep APK signing consistent

Set up a persistent signing key **before installing your first APK** if you want to install future builds in place. Without it, GitHub-hosted runners can generate different debug keys. An APK signed with a different key cannot replace the installed app.

Create a private PKCS12 keystore once using `keytool` on a trusted machine or a private cloud development workspace. The command prompts for the password and certificate details.

```sh
keytool -genkeypair -storetype PKCS12 -keystore shopping.keystore \
  -alias shopping -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 shopping.keystore
```

Keep an encrypted backup of the keystore and passwords. Under **Settings → Secrets and variables → Actions** in the GitHub repository, create these repository secrets:

| Secret | Value |
| --- | --- |
| `SHOPPING_KEYSTORE_BASE64` | Single-line Base64 encoding of the keystore |
| `SHOPPING_KEYSTORE_PASSWORD` | Keystore password |
| `SHOPPING_KEY_ALIAS` | Key alias, e.g. `shopping` |
| `SHOPPING_KEY_PASSWORD` | Key password. For the PKCS12 command above, use the keystore password. |

Push to `main` or manually run the workflow on `main` to produce the consistently signed APK. Pull request builds use transient debug signing. Keep the keystore and its Base64 encoding out of Git.
