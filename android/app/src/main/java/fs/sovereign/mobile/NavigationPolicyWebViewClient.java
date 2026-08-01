package fs.sovereign.mobile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

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
 */
public class NavigationPolicyWebViewClient extends BridgeWebViewClient {

    private static final String PREFERENCES_GROUP = "CapacitorStorage";
    private static final String ACTIVE_URL_KEY = "sovereign.activeUrl";

    public NavigationPolicyWebViewClient(Bridge bridge) {
        super(bridge);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri url = request.getUrl();
        String scheme = url.getScheme();
        boolean isHttpOrHttps = "http".equals(scheme) || "https".equals(scheme);

        if (!request.isForMainFrame() || !isHttpOrHttps) {
            // Non-http(s) requests (tel:, mailto:, etc.) are Capacitor's own
            // concern — forward to its real handler rather than deciding
            // ourselves.
            return super.shouldOverrideUrlLoading(view, request);
        }

        String activeOrigin = activeInstanceOrigin(view.getContext());
        if (activeOrigin == null) {
            // No active instance recorded yet. In practice this only
            // precedes the very first navigation *to* a freshly-added
            // instance, so let it load directly — do NOT forward to
            // super/bridge.launchIntent() here (see below).
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
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, url);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        view.getContext().startActivity(intent);
        return true;
    }

    private static String activeInstanceOrigin(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_GROUP, Context.MODE_PRIVATE);
        return prefs.getString(ACTIVE_URL_KEY, null);
    }
}
