package fs.sovereign.mobile;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.BridgeActivity;
import java.util.function.Consumer;

/**
 * Installs NavigationPolicyWebViewClient once the bridge has finished
 * loading — see docs/adrs/0007-navigation-policy-enforcement.md. The
 * Android hardware back button already routes through WebView history by
 * default (Capacitor's own BridgeActivity behavior), which is this shell's
 * "switch instance" affordance — see
 * docs/adrs/0006-history-based-instance-switch-affordance.md — so no
 * override is needed here for that part.
 *
 * Also owns the {@code POST_NOTIFICATIONS} runtime-permission launcher for
 * {@code notifications.native} (RFC 0083, workstream 0003 leg 4) —
 * {@code registerForActivityResult} must be called before the Activity
 * reaches {@code STARTED}, so it is a field initializer here rather than
 * created on demand inside {@link BridgeCapabilities}.
 */
public class MainActivity extends BridgeActivity {

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

    @Override
    protected void load() {
        super.load();
        bridge.setWebViewClient(new NavigationPolicyWebViewClient(bridge, this));
    }
}
