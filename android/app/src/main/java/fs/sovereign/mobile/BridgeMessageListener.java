package fs.sovereign.mobile;

import android.app.Activity;
import android.net.Uri;
import android.webkit.WebView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The Capacitor transport of {@code @sovereignfs/bridge} (RFC 0083,
 * workstream 0003 leg 4) — the Android counterpart to iOS's {@code
 * BridgeMessageHandler} (Bridge.swift), registered as a {@code
 * WebViewCompat.WebMessageListener} under the JS object name {@link
 * #JS_OBJECT_NAME} by {@link NavigationPolicyWebViewClient}'s {@code
 * enterRemoteMode()}, scoped (via the listener's {@code allowedOriginRules})
 * to the active instance's origin only.
 *
 * Unlike iOS, no hand-rolled reply channel is needed: {@code
 * addWebMessageListener}'s {@link JavaScriptReplyProxy} gives a proper
 * per-message reply, matching {@code window.<name>.onmessage} on the JS
 * side — see {@link NavigationPolicyWebViewClient#bridgeScript}.
 */
final class BridgeMessageListener implements WebViewCompat.WebMessageListener {

    static final String JS_OBJECT_NAME = "sovereignBridge";

    private final Activity activity;
    private final ActivityResultLauncher<String> notificationPermissionLauncher;

    BridgeMessageListener(Activity activity, ActivityResultLauncher<String> notificationPermissionLauncher) {
        this.activity = activity;
        this.notificationPermissionLauncher = notificationPermissionLauncher;
    }

    @Override
    public void onPostMessage(
        @NonNull WebView view,
        @NonNull WebMessageCompat message,
        @NonNull Uri sourceOrigin,
        boolean isMainFrame,
        @NonNull JavaScriptReplyProxy replyProxy
    ) {
        if (!isMainFrame || message.getData() == null) {
            return;
        }

        String id;
        String capability;
        JSONObject payload;
        try {
            JSONObject request = new JSONObject(message.getData());
            id = request.getString("id");
            capability = request.getString("capability");
            payload = request.optJSONObject("payload");
            if (payload == null) {
                payload = new JSONObject();
            }
        } catch (JSONException e) {
            return;
        }

        switch (capability) {
            case "haptics.impact":
                respond(replyProxy, id, dispatchHaptics(payload));
                break;
            case "notifications.native":
                BridgeCapabilities.notificationsNative(
                    activity,
                    notificationPermissionLauncher,
                    payload,
                    result -> activity.runOnUiThread(() -> respond(replyProxy, id, result))
                );
                break;
            default:
                respond(replyProxy, id, unavailable(capability));
        }
    }

    private JSONObject dispatchHaptics(JSONObject payload) {
        try {
            return BridgeCapabilities.hapticsImpact(activity, payload);
        } catch (JSONException e) {
            return unavailable("haptics.impact");
        }
    }

    private static void respond(JavaScriptReplyProxy replyProxy, String id, JSONObject result) {
        try {
            JSONObject envelope = new JSONObject().put("id", id).put("result", result);
            replyProxy.postMessage(envelope.toString());
        } catch (JSONException ignored) {
            // Envelope construction from already-valid JSONObjects cannot fail.
        }
    }

    private static JSONObject unavailable(String capability) {
        try {
            return new JSONObject().put("status", "unavailable").put("capability", capability);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
