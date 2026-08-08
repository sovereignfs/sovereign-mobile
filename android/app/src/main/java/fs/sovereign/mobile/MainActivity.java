package fs.sovereign.mobile;

import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.splashscreen.SplashScreen;
import com.getcapacitor.BridgeActivity;
import java.util.function.Consumer;

/**
 * Installs NavigationPolicyWebViewClient once the bridge has finished
 * loading — see docs/adrs/0007-navigation-policy-enforcement.md.
 *
 * Also owns the {@code POST_NOTIFICATIONS} runtime-permission launcher for
 * {@code notifications.native} (RFC 0083, workstream 0003 leg 4) —
 * {@code registerForActivityResult} must be called before the Activity
 * reaches {@code STARTED}, so it is a field initializer here rather than
 * created on demand inside {@link BridgeCapabilities}.
 */
public class MainActivity extends BridgeActivity {

    private static final String LOCAL_ORIGIN = "https://localhost/";

    private static Consumer<Boolean> pendingNotificationPermissionCallback;

    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        granted -> {
            Consumer<Boolean> callback = pendingNotificationPermissionCallback;
            pendingNotificationPermissionCallback = null;
            if (callback != null) {
                callback.accept(granted);
            }
        }
    );

    /** Called by {@link BridgeCapabilities} right before launching the permission request. */
    static void setPendingNotificationPermissionCallback(Consumer<Boolean> callback) {
        pendingNotificationPermissionCallback = callback;
    }

    ActivityResultLauncher<String> getNotificationPermissionLauncher() {
        return notificationPermissionLauncher;
    }

    /**
     * {@code SplashScreen.installSplashScreen(this)} must run before {@code
     * super.onCreate()} (and before any {@code setContentView()}, which
     * {@link BridgeActivity#onCreate} does internally) — that's the
     * androidx.core.splashscreen contract, and skipping it entirely is what
     * left the splash screen showing a bare, unbranded window on real
     * devices: see values/styles.xml's AppTheme.NoActionBarLaunch doc
     * comment for the full diagnosis.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void load() {
        super.load();
        bridge.setWebViewClient(new NavigationPolicyWebViewClient(bridge, this));
    }

    /**
     * Explicitly navigates back to the local instance-manager page instead
     * of relying on {@link WebView#goBack()} — see
     * docs/adrs/0006-history-based-instance-switch-affordance.md's
     * Consequences section for the full empirical writeup of why. Summary:
     * after this shell's own {@code boot()}-triggered {@code
     * location.assign()} redirect on a cold launch (force-stop then
     * relaunch, or Android killing the app in the background then the user
     * reopening it), the WebView's own back-forward-list bookkeeping goes
     * stale — {@code canGoBack()} reports {@code false} and {@code
     * goBack()} silently no-ops, even though {@code
     * copyBackForwardList()} correctly shows two entries with the local
     * page behind the current one. Reproduced reliably (8/8 and more
     * consecutive back-presses across several clean installs) and not
     * fixed by delaying the redirect until after the local page's own
     * {@code load} event (tried first, ruled out timing as the cause).
     * User-initiated navigation (tapping Connect within an already-running
     * process) does not exhibit this — only the cold-launch auto-redirect
     * does. Bypassing the broken history mechanism entirely by loading the
     * known manage URL directly is what actually works, verified against
     * the exact prior repro across multiple clean installs; it also still
     * defers to the platform default (usually: exit the app) once already
     * on a local page, so this doesn't change back behavior for that case.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            WebView webView = bridge.getWebView();
            String currentUrl = webView != null ? webView.getUrl() : null;
            boolean showingRemoteInstance = currentUrl != null && !currentUrl.startsWith(LOCAL_ORIGIN);
            if (showingRemoteInstance) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    webView.loadUrl(LOCAL_ORIGIN + "?manage=1");
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
