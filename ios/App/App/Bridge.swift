import Foundation
import UIKit
import UserNotifications
import WebKit

/// The Capacitor transport of `@sovereignfs/bridge` (RFC 0083, workstream
/// 0003 leg 4) — a single narrow message handler, registered under
/// `BridgeMessageHandler.handlerName`, that the injected
/// `window.__SOVEREIGN_BRIDGE__` object (see `MainViewController`'s
/// `bridgeScript()`) posts to for every `sdk.device.*` capability call.
///
/// This handler is only ever installed on the `WKUserContentController`
/// while the WebView is showing the loaded *remote* instance — never
/// alongside Capacitor's own `"bridge"` handler or `window.Capacitor`
/// object, both of which are removed for that navigation. See
/// `MainViewController`'s navigation-boundary script swap for why: Capacitor
/// injects its global bridge as a WebView-level `WKUserScript` with no
/// origin scoping (confirmed against `@capacitor/ios` 8.4.2's
/// `JSExport.exportCapacitorGlobalJS`/`exportJS`), so genuinely withholding
/// it from the loaded instance requires actively removing it, not just
/// declining to reference it from page JS.
final class BridgeMessageHandler: NSObject, WKScriptMessageHandler {
    static let handlerName = "sovereignBridge"

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let webView = message.webView,
              let body = message.body as? [String: Any],
              let id = body["id"] as? String,
              let capability = body["capability"] as? String
        else {
            return
        }
        let payload = body["payload"] as? [String: Any] ?? [:]

        switch capability {
        case "haptics.impact":
            respond(webView, id: id, result: BridgeCapabilities.hapticsImpact(payload: payload))
        case "notifications.native":
            BridgeCapabilities.notificationsNative(payload: payload) { result in
                DispatchQueue.main.async {
                    self.respond(webView, id: id, result: result)
                }
            }
        default:
            respond(webView, id: id, result: ["status": "unavailable", "capability": capability])
        }
    }

    private func respond(_ webView: WKWebView, id: String, result: [String: Any]) {
        let envelope: [String: Any] = ["id": id, "result": result]
        guard let data = try? JSONSerialization.data(withJSONObject: envelope),
              let json = String(data: data, encoding: .utf8)
        else {
            return
        }
        webView.evaluateJavaScript("window.__sovereignBridgeResolve__ && window.__sovereignBridgeResolve__(\(json));")
    }
}

/// The actual capability implementations, kept separate from the message
/// plumbing above — mirrors `sovereign-desktop`'s `src-tauri/src/bridge.rs`
/// splitting `bridge_invoke`'s dispatch from `notify()`.
///
/// `notifications.native` is the only capability with real permission
/// handling (`haptics.impact` needs none, matching the desktop transport and
/// RFC 0083 §7). `getPermission()`/`requestPermission()`
/// (`packages/sdk/src/device-client.ts` in the monorepo) report `'granted'`
/// unconditionally on this transport — there is no separate permission-query
/// action here either, for the same reason as desktop: the OS gates the
/// real permission inline, at the point this capability actually runs.
enum BridgeCapabilities {
    static func hapticsImpact(payload: [String: Any]) -> [String: Any] {
        let style = payload["style"] as? String ?? "medium"
        let feedbackStyle: UIImpactFeedbackGenerator.FeedbackStyle
        switch style {
        case "light": feedbackStyle = .light
        case "heavy": feedbackStyle = .heavy
        default: feedbackStyle = .medium
        }
        UIImpactFeedbackGenerator(style: feedbackStyle).impactOccurred()
        return ["status": "ok", "value": NSNull()]
    }

    static func notificationsNative(payload: [String: Any], completion: @escaping ([String: Any]) -> Void) {
        guard let title = payload["title"] as? String else {
            completion(["status": "failed", "error": "missing required \"title\" field"])
            return
        }
        let body = payload["body"] as? String

        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .denied:
                completion(["status": "denied"])
            case .notDetermined:
                center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
                    if granted {
                        scheduleAndComplete(title: title, body: body, completion: completion)
                    } else {
                        completion(["status": "denied"])
                    }
                }
            default:
                scheduleAndComplete(title: title, body: body, completion: completion)
            }
        }
    }

    private static func scheduleAndComplete(
        title: String, body: String?, completion: @escaping ([String: Any]) -> Void
    ) {
        let content = UNMutableNotificationContent()
        content.title = title
        if let body = body {
            content.body = body
        }
        // `trigger: nil` fires as close to immediately as UNUserNotificationCenter
        // allows — this is the "show a notification now" capability, not a
        // scheduled reminder.
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                completion(["status": "failed", "error": error.localizedDescription])
            } else {
                completion(["status": "ok", "value": NSNull()])
            }
        }
    }
}
