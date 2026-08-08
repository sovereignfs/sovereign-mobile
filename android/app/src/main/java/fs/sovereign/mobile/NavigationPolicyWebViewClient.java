package fs.sovereign.mobile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;
import java.util.Collections;

/**
 * Navigation policy per sovereign RFC 0058 / docs/adrs/0007-navigation-policy-enforcement.md:
 * keep the configured instance in this WebView, open everything else in the
 * system browser. Extends (not replaces) Capacitor's own BridgeWebViewClient
 * so its request-interception and bridge.launchIntent() handling for
 * non-http(s) schemes (tel:, mailto:, etc.) keeps working unchanged — this
 * class only adds an earlier same-origin check for http(s) main-frame
 * navigations.
 *
 * Reads the active instance origin directly from the same
 * @capacitor/preferences storage the TS shell writes ("sovereign.activeUrl"
 * in the "CapacitorStorage" SharedPreferences file — see
 * @capacitor/preferences/android's Preferences.java / PreferencesConfiguration.java
 * for the default group name and key format this must stay in sync with),
 * so there is one source of truth, not a separately-synced native copy.
 *
 * ## Bridge isolation (RFC 0083, workstream 0003 leg 4)
 *
 * Unlike iOS, Capacitor's own JS/native bridge on Android is already
 * origin-scoped by the framework itself: {@code Bridge#loadWebView()}
 * registers {@code window.Capacitor} via {@code
 * WebViewCompat#addDocumentStartJavaScript} and the native {@code
 * androidBridge} channel via {@code WebViewCompat#addWebMessageListener},
 * both restricted to {@code bridge.getAllowedOriginRules()} — which, since
 * this app never sets {@code server.url}, contains only the bundled local
 * origin (confirmed against {@code @capacitor/android} 8.4.2's {@code
 * Bridge.java}/{@code MessageHandler.java}). So Capacitor's own bridge
 * already never reaches the loaded remote instance — nothing to remove
 * here, the opposite problem from iOS.
 *
 * What's added here is the narrow {@code window.__SOVEREIGN_BRIDGE__}
 * script and its {@code sovereignBridge} message listener
 * ({@link BridgeMessageListener}), registered with the *same* origin-scoped
 * APIs Capacitor uses for its own bridge, but scoped to the *active
 * instance's* origin instead of the local one. Since that origin is only
 * known once the user has entered an instance (unlike Capacitor's static
 * {@code appUrl}), registration happens dynamically at the same navigation
 * decision points as the same-origin check below, and is torn down again
 * when navigating back to local content — an origin's registration can't be
 * updated in place, only replaced.
 */
public class NavigationPolicyWebViewClient extends BridgeWebViewClient {

    private static final String PREFERENCES_GROUP = "CapacitorStorage";
    private static final String ACTIVE_URL_KEY = "sovereign.activeUrl";

    private final MainActivity activity;
    // Capacitor's own Bridge field on the superclass is private with no
    // getter for the field itself — keep our own reference rather than
    // relying on Bridge#getWebView() alone, since a future Capacitor version
    // may add one without also widening the inherited field's visibility.
    private final Bridge appBridge;
    private ScriptHandler bridgeScriptHandle;
    private String remoteModeOrigin;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingHideLaunchOverlay;
    // Generous on purpose: while this is pending, MainActivity's launch
    // overlay (or, once that's gone, index.html's pre-boot placeholder —
    // see styles.css) is showing, and the two are pixel-identical, so this
    // delay is never perceptible as lag. What it buys: enough time for
    // boot()'s single async Preferences round-trip to resolve and, if it
    // decides to redirect, for enterRemoteMode() below to cancel this
    // before it fires — see onPageFinished's doc comment for the full
    // reasoning this is guarding against.
    private static final long LOCAL_PAGE_SETTLE_DELAY_MS = 400;

    public NavigationPolicyWebViewClient(Bridge bridge, MainActivity activity) {
        super(bridge);
        this.appBridge = bridge;
        this.activity = activity;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri url = request.getUrl();
        String scheme = url.getScheme();
        boolean isHttpOrHttps = "http".equals(scheme) || "https".equals(scheme);

        if (!request.isForMainFrame() || !isHttpOrHttps) {
            // Non-http(s) requests (tel:, mailto:, etc.) are Capacitor's own
            // concern — forward to its real handler rather than deciding
            // ourselves. This is also how a back-navigation to the bundled
            // local page is detected, so tear down the remote-mode bridge
            // registration here.
            enterLocalMode();
            return super.shouldOverrideUrlLoading(view, request);
        }

        String activeOrigin = activeInstanceOrigin(view.getContext());
        if (activeOrigin == null) {
            // No active instance recorded yet. In practice this only
            // precedes the very first navigation *to* a freshly-added
            // instance, so let it load directly — do NOT forward to
            // super/bridge.launchIntent() here (see below). This is still
            // remote (http/https) content, so register the bridge for it —
            // the destination URL's own origin, since there's no stored
            // active origin yet to read.
            enterRemoteMode(view, originOf(url));
            return false;
        }

        Uri activeUri = Uri.parse(activeOrigin);
        boolean sameOrigin =
            scheme.equals(activeUri.getScheme())
                && url.getHost() != null
                && url.getHost().equals(activeUri.getHost())
                && url.getPort() == activeUri.getPort();

        if (sameOrigin) {
            // Decide this ourselves — do not forward to
            // super.shouldOverrideUrlLoading(), which delegates to
            // Bridge#launchIntent(). That compares the URL's host against
            // Capacitor's own configured app host (localhost) and the
            // static server.allowNavigation list, neither of which knows
            // about this shell's runtime-chosen active instance — so it
            // would hand the load off to an external Intent (the default
            // browser) instead of loading it in this WebView. Confirmed
            // empirically on the iOS side (identical bug in
            // MainViewController.swift, fixed the same way): forwarding
            // this exact case sent a freshly-onboarded instance to Safari
            // instead of loading it in-app. That default is right for a
            // normal Capacitor app whose real content is bundled locally,
            // but wrong here — this shell's entire purpose is to load a
            // user-chosen remote origin as primary content, per
            // docs/adrs/0005-server-url-not-bundled-assets.md.
            enterRemoteMode(view, activeOrigin);
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, url);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        view.getContext().startActivity(intent);
        return true;
    }

    /**
     * Decides when it's safe to reveal the WebView by hiding
     * {@link MainActivity}'s launch overlay — see that method's doc comment
     * for what the overlay is covering for. Two cases, both driven from
     * here since this is the one place that reliably sees every page-load
     * completion regardless of origin:
     *
     * <ul>
     * <li>{@code remoteModeOrigin != null} — a real instance just finished
     * loading (either the direct "user tapped Connect" path or the
     * cold-launch auto-redirect path). Reveal immediately; there is nothing
     * left to wait for.
     * <li>Otherwise — the local page just finished loading, and this
     * doesn't yet know whether {@code boot()} is about to redirect to a
     * stored instance or settle into rendering onboarding. Schedule a
     * reveal after a short delay rather than either extreme: revealing
     * immediately would flash the destination's blank-page load if a
     * redirect is about to start (the original bug report this whole
     * mechanism exists to fix); never revealing without some fallback would
     * leave the overlay stuck forever if boot() decides not to redirect,
     * since nothing else would tell us to hide it. If a redirect *does*
     * start before the delay elapses, {@link #enterRemoteMode} cancels this
     * pending reveal, and this method fires again — with
     * {@code remoteModeOrigin != null} this time — once the real
     * destination finishes loading.
     * </ul>
     */
    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (remoteModeOrigin != null) {
            cancelPendingHideLaunchOverlay();
            activity.hideLaunchOverlay();
        } else {
            schedulePendingHideLaunchOverlay();
        }
    }

    private void schedulePendingHideLaunchOverlay() {
        cancelPendingHideLaunchOverlay();
        pendingHideLaunchOverlay = activity::hideLaunchOverlay;
        mainHandler.postDelayed(pendingHideLaunchOverlay, LOCAL_PAGE_SETTLE_DELAY_MS);
    }

    private void cancelPendingHideLaunchOverlay() {
        if (pendingHideLaunchOverlay != null) {
            mainHandler.removeCallbacks(pendingHideLaunchOverlay);
            pendingHideLaunchOverlay = null;
        }
    }

    private static String activeInstanceOrigin(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_GROUP, Context.MODE_PRIVATE);
        return prefs.getString(ACTIVE_URL_KEY, null);
    }

    private static String originOf(Uri url) {
        return url.buildUpon().path(null).fragment(null).clearQuery().build().toString();
    }

    /**
     * Registers {@code window.__SOVEREIGN_BRIDGE__} and its {@link
     * BridgeMessageListener}, scoped to {@code origin} only. Idempotent for
     * repeat navigations to the same origin; re-registers (via {@link
     * #enterLocalMode()} first) if the active instance changed, since an
     * origin-rule set can't be updated on an existing registration.
     */
    private void enterRemoteMode(WebView view, String origin) {
        // A redirect to a real instance is now confirmed happening — stop
        // waiting to reveal on the local page's own timeout (see
        // onPageFinished) and wait for *this* navigation to finish instead.
        cancelPendingHideLaunchOverlay();
        if (origin.equals(remoteModeOrigin)) {
            return;
        }
        enterLocalMode();

        if (
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        ) {
            // No safe way to scope the bridge to this origin on this device's
            // WebView build — leave window.__SOVEREIGN_BRIDGE__ absent rather
            // than fall back to an unscoped registration. The page's own
            // bridge detection degrades to the web transport, per RFC 0083.
            return;
        }

        WebViewCompat.addWebMessageListener(
            view,
            BridgeMessageListener.JS_OBJECT_NAME,
            Collections.singleton(origin),
            new BridgeMessageListener(activity, activity.getNotificationPermissionLauncher())
        );
        bridgeScriptHandle = WebViewCompat.addDocumentStartJavaScript(view, bridgeScript(), Collections.singleton(origin));
        remoteModeOrigin = origin;
    }

    /** Tears down the remote-mode bridge registration. Idempotent. */
    private void enterLocalMode() {
        if (remoteModeOrigin == null) {
            return;
        }
        if (bridgeScriptHandle != null) {
            bridgeScriptHandle.remove();
            bridgeScriptHandle = null;
        }
        WebViewCompat.removeWebMessageListener(appBridge.getWebView(), BridgeMessageListener.JS_OBJECT_NAME);
        remoteModeOrigin = null;
    }

    /**
     * JavaScript injected into the remote instance's page load, defining
     * {@code window.__SOVEREIGN_BRIDGE__} per {@code @sovereignfs/bridge}'s
     * {@code InstalledBridge} wire shape
     * ({@code packages/bridge/src/protocol.ts} in the monorepo). {@code
     * invoke()} posts to the narrow {@code sovereignBridge} message listener
     * ({@link BridgeMessageListener}) and resolves via {@code
     * window.sovereignBridge.onmessage} — {@code addWebMessageListener}'s
     * {@code JavaScriptReplyProxy} gives a proper reply channel, unlike
     * iOS's hand-rolled {@code __sovereignBridgeResolve__} polyfill (no
     * equivalent to {@code JavaScriptReplyProxy} in {@code
     * WKScriptMessageHandler}).
     */
    private String bridgeScript() {
        return (
            "(function () {\n" +
            "  var pending = {};\n" +
            "  var counter = 0;\n" +
            "  window.sovereignBridge.onmessage = function (event) {\n" +
            "    var envelope = JSON.parse(event.data);\n" +
            "    var resolve = pending[envelope.id];\n" +
            "    if (resolve) {\n" +
            "      delete pending[envelope.id];\n" +
            "      resolve(envelope.result);\n" +
            "    }\n" +
            "  };\n" +
            "  Object.defineProperty(window, '__SOVEREIGN_BRIDGE__', {\n" +
            "    value: Object.freeze({\n" +
            "      protocolVersion: 1,\n" +
            "      shell: Object.freeze({ name: 'sovereign-mobile', version: '" +
            appVersion() +
            "', platform: 'android' }),\n" +
            "      capabilities: [\n" +
            "        { name: 'haptics.impact', version: 1 },\n" +
            "        { name: 'notifications.native', version: 1 }\n" +
            "      ],\n" +
            "      invoke: function (capability, payload) {\n" +
            "        return new Promise(function (resolve) {\n" +
            "          var id = 'b' + counter++ + '_' + Date.now();\n" +
            "          pending[id] = resolve;\n" +
            "          window.sovereignBridge.postMessage(JSON.stringify({\n" +
            "            id: id,\n" +
            "            capability: capability,\n" +
            "            payload: payload || {}\n" +
            "          }));\n" +
            "        });\n" +
            "      }\n" +
            "    }),\n" +
            "    writable: false,\n" +
            "    configurable: false,\n" +
            "    enumerable: true\n" +
            "  });\n" +
            "})();"
        );
    }

    private String appVersion() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.versionName != null ? info.versionName : "0.0.0";
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0.0";
        }
    }
}
