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
            return super.shouldOverrideUrlLoading(view, request);
        }

        String activeOrigin = activeInstanceOrigin(view.getContext());
        if (activeOrigin == null) {
            // No active instance yet (still onboarding) — nothing to enforce.
            return super.shouldOverrideUrlLoading(view, request);
        }

        Uri activeUri = Uri.parse(activeOrigin);
        boolean sameOrigin =
            scheme.equals(activeUri.getScheme())
                && url.getHost() != null
                && url.getHost().equals(activeUri.getHost())
                && url.getPort() == activeUri.getPort();

        if (sameOrigin) {
            return super.shouldOverrideUrlLoading(view, request);
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
