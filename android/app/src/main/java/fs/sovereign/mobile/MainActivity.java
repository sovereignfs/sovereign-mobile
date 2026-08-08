package fs.sovereign.mobile;

import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
        addLaunchOverlay();
    }

    @Override
    protected void load() {
        super.load();
        bridge.setWebViewClient(new NavigationPolicyWebViewClient(bridge, this));
    }

    private FrameLayout launchOverlay;

    private static final int LAUNCH_OVERLAY_ICON_DP = 96;

    /**
     * A second, self-managed splash surface drawn directly on top of the
     * WebView from launch, held visible until
     * {@link NavigationPolicyWebViewClient} decides it's safe to reveal —
     * see that class's onPageFinished doc comment for exactly when. Without
     * this, the OS-level cold-start splash (the styles.xml/installSplashScreen
     * fix above) hides itself the moment the WebView starts rendering
     * *anything*, including the local page's brief pre-boot placeholder —
     * fine on its own, but on a cold launch with a stored instance, that
     * placeholder then gets torn down for a real cross-origin navigation to
     * the remote instance, and that navigation's own blank-page flash was
     * visibly reported on a real device once the earlier splash-continuity
     * fix (index.html's matching placeholder) made everything *before* it
     * seamless enough to notice.
     *
     * Deliberately does NOT reuse {@code @drawable/splash} (the full-screen
     * bitmap `@capacitor/assets` generates): {@code ImageView.setImageResource}
     * decodes that bitmap synchronously on the main thread in
     * {@code onCreate()}, and on a real device that showed up as a ~4s
     * blank-white-screen stall with dropped frames (confirmed via
     * ActivityTaskManager's "Displayed" timing and Choreographer's skipped-
     * frame warning) — the exact janky launch this overlay exists to avoid.
     * Instead this layers a flat {@code @color/splash_background} (same
     * day/night-resolved color styles.xml's windowSplashScreenBackground
     * already uses, so it's visually continuous with the OS splash) behind
     * a small, fixed-size {@code @mipmap/ic_launcher_foreground} — the
     * adaptive-icon foreground layer, already a small per-density PNG
     * (a few KB, not a full-screen image), so decoding it is cheap.
     */
    private void addLaunchOverlay() {
        launchOverlay = new FrameLayout(this);
        launchOverlay.setBackgroundColor(getResources().getColor(R.color.splash_background, getTheme()));
        launchOverlay.setLayoutParams(
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        );

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageResource(R.mipmap.ic_launcher_foreground);
        int iconSizePx = Math.round(LAUNCH_OVERLAY_ICON_DP * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSizePx, iconSizePx);
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        launchOverlay.addView(icon);

        ((ViewGroup) findViewById(android.R.id.content)).addView(launchOverlay);
    }

    /**
     * Idempotent — safe to call more than once (only the first call has any
     * effect) and safe to call before {@link #addLaunchOverlay()} has run.
     */
    void hideLaunchOverlay() {
        FrameLayout overlay = launchOverlay;
        if (overlay == null) {
            return;
        }
        launchOverlay = null;
        overlay
            .animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction(() -> {
                ViewGroup parent = (ViewGroup) overlay.getParent();
                if (parent != null) {
                    parent.removeView(overlay);
                }
            })
            .start();
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
