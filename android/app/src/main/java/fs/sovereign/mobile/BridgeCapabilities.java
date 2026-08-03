package fs.sovereign.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import java.util.function.Consumer;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The actual capability implementations for the Capacitor transport of
 * {@code @sovereignfs/bridge} (RFC 0083, workstream 0003 leg 4) — kept
 * separate from {@link BridgeMessageListener}'s message plumbing, mirroring
 * sovereign-desktop's {@code src-tauri/src/bridge.rs} splitting
 * {@code bridge_invoke}'s dispatch from {@code notify()}, and iOS's
 * {@code Bridge.swift} splitting {@code BridgeMessageHandler} from {@code
 * BridgeCapabilities}.
 *
 * {@code notifications.native} is the only capability with real permission
 * handling ({@code haptics.impact} needs none, matching both other
 * transports and RFC 0083 §7).
 * {@code getPermission()}/{@code requestPermission()}
 * ({@code packages/sdk/src/device-client.ts} in the monorepo) report
 * {@code 'granted'} unconditionally on this transport — there is no separate
 * permission-query action here either, for the same reason as desktop and
 * iOS: the OS gates the real permission inline, at the point this capability
 * actually runs.
 */
final class BridgeCapabilities {

    private static final String NOTIFICATION_CHANNEL_ID = "sovereign-bridge";

    private BridgeCapabilities() {}

    static JSONObject hapticsImpact(Context context, JSONObject payload) throws JSONException {
        String style = payload.optString("style", "medium");
        long durationMs;
        switch (style) {
            case "light":
                durationMs = 10;
                break;
            case "heavy":
                durationMs = 30;
                break;
            default:
                durationMs = 20;
        }

        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager != null ? manager.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator == null || !vibrator.hasVibrator()) {
            return unavailable("haptics.impact");
        }

        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
        return ok(JSONObject.NULL);
    }

    /**
     * Requests the Android 13+ (API 33) {@code POST_NOTIFICATIONS} runtime
     * permission if needed, then shows the notification. {@code activity}
     * and {@code permissionLauncher} come from {@link MainActivity} — the
     * launcher must be registered before the Activity reaches {@code
     * STARTED} (a field initializer there), so it cannot be created lazily
     * here.
     */
    static void notificationsNative(
        Activity activity,
        ActivityResultLauncher<String> permissionLauncher,
        JSONObject payload,
        Consumer<JSONObject> completion
    ) {
        String title = payload.optString("title", null);
        if (title == null) {
            completion.accept(failed("missing required \"title\" field"));
            return;
        }
        String body = payload.optString("body", null);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            showNotification(activity, title, body, completion);
            return;
        }

        boolean granted =
            ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED;
        if (granted) {
            showNotification(activity, title, body, completion);
            return;
        }

        MainActivity.setPendingNotificationPermissionCallback(result -> {
            if (Boolean.TRUE.equals(result)) {
                showNotification(activity, title, body, completion);
            } else {
                completion.accept(denied());
            }
        });
        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
    }

    private static void showNotification(Activity activity, String title, String body, Consumer<JSONObject> completion) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannelCompat channel = new NotificationChannelCompat.Builder(
                NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
                .setName("Sovereign")
                .build();
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(activity.getApplicationInfo().icon)
            .setContentTitle(title)
            .setAutoCancel(true);
        if (body != null) {
            builder.setContentText(body);
        }

        try {
            manager.notify((int) System.currentTimeMillis(), builder.build());
            completion.accept(ok(JSONObject.NULL));
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS was revoked between the check above and
            // this call (e.g. in Settings) — report denied, not a crash.
            completion.accept(denied());
        }
    }

    private static JSONObject ok(Object value) {
        try {
            return new JSONObject().put("status", "ok").put("value", value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private static JSONObject unavailable(String capability) {
        try {
            return new JSONObject().put("status", "unavailable").put("capability", capability);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private static JSONObject denied() {
        try {
            return new JSONObject().put("status", "denied");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private static JSONObject failed(String error) {
        try {
            return new JSONObject().put("status", "failed").put("error", error);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
