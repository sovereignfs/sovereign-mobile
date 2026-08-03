import Capacitor
import UIKit
import WebKit

/// Three responsibilities, all documented as this repo's own ADRs since
/// none has a sovereign-desktop precedent that transfers as-is:
///
/// 1. Enables WKWebView's edge-swipe back/forward gesture. Combined with
///    `onboarding.ts` using `location.assign` (not `.replace`) when loading
///    an instance, this is this shell's entire "switch instance" affordance
///    — see docs/adrs/0006-history-based-instance-switch-affordance.md.
/// 2. Enforces navigation policy per sovereign RFC 0058: keeps the active
///    instance in this WebView, opens everything else in the system
///    browser — see docs/adrs/0007-navigation-policy-enforcement.md.
/// 3. Swaps the WebView's `WKUserContentController` script/handler set at
///    the local↔remote navigation boundary (RFC 0083, workstream 0003 leg
///    4) — see "Bridge isolation" below.
///
/// Capacitor 8's `CAPBridgeViewController` does **not** conform to
/// `WKNavigationDelegate` itself — it installs a separate, concrete
/// `WebViewDelegationHandler` (`@objc(CAPWebViewDelegationHandler)`) as
/// `webView.navigationDelegate`, confirmed against the actual
/// Capacitor.swiftinterface resolved for this project (Capacitor 8.4.2).
/// This class takes over as `navigationDelegate` itself, keeping a typed
/// reference to that original concrete handler (not just the
/// `WKNavigationDelegate` protocol existential — Swift's `@objc optional`
/// protocol-method dispatch sugar is ambiguous for overloaded methods like
/// `decidePolicyFor`, so calling through a *concrete* class reference is
/// both simpler and unambiguous) and forwards every selector it doesn't
/// implement back to it via `responds(to:)` / `forwardingTarget(for:)` —
/// the standard Foundation proxy-forwarding pattern — so Capacitor's own
/// navigation lifecycle handling (page-load callbacks, auth challenges,
/// web-content-process termination, etc.) keeps working unchanged for
/// everything this class doesn't explicitly care about.
///
/// ## Bridge isolation
///
/// Capacitor installs `window.Capacitor` (and every registered plugin's JS)
/// as `WKUserScript`s on the WebView's single `WKUserContentController`,
/// with `forMainFrameOnly: true` and no origin scoping — confirmed against
/// `@capacitor/ios` 8.4.2's `JSExport.exportCapacitorGlobalJS`/`exportJS`
/// (`node_modules/@capacitor/ios/Capacitor/Capacitor/JSExport.swift`). That
/// means Capacitor's bridge runs on *every* main-frame navigation in this
/// WebView by default, including the loaded remote instance — the opposite
/// of this repo's hard rule ("remote instance content must never get
/// uncontrolled native access") and of `sovereign-desktop`'s Tauri
/// transport, whose capability grants are origin-scoped by the framework
/// itself. Capacitor gives no config knob for this; achieving it here means
/// actively removing Capacitor's own scripts and its `"bridge"`
/// `WKScriptMessageHandler` (`WebViewDelegationHandler.swift`'s
/// `handlerName`) whenever the WebView is about to show remote content, and
/// restoring them when navigating back to the bundled local page — done in
/// `enterRemoteMode()`/`enterLocalMode()` below, called from
/// `decidePolicyFor` since that fires before the destination's document is
/// created, which is early enough for `.atDocumentStart` script injection
/// to apply to the *next* page. `localModeScripts` is captured once, in
/// `capacitorDidLoad()`, from `userContentController.userScripts` (a public
/// property) — there is no Capacitor API to re-trigger its own injection.
class MainViewController: CAPBridgeViewController {
    private weak var originalNavigationDelegate: WebViewDelegationHandler?

    private enum ContentMode {
        case local
        case remote
    }
    private var contentMode: ContentMode = .local
    private var localModeScripts: [WKUserScript] = []
    private let bridgeMessageHandler = BridgeMessageHandler()

    override func capacitorDidLoad() {
        webView?.allowsBackForwardNavigationGestures = true
        originalNavigationDelegate = webView?.navigationDelegate as? WebViewDelegationHandler
        webView?.navigationDelegate = self
        localModeScripts = webView?.configuration.userContentController.userScripts ?? []
    }

    /// Removes Capacitor's own scripts and `"bridge"` message handler,
    /// installs only the narrow `window.__SOVEREIGN_BRIDGE__` script and its
    /// `sovereignBridge` message handler. Idempotent — a no-op once already
    /// in remote mode, so this can be called on every qualifying navigation
    /// decision without redundant work.
    private func enterRemoteMode() {
        guard contentMode != .remote, let contentController = webView?.configuration.userContentController else {
            return
        }
        contentController.removeAllUserScripts()
        contentController.removeScriptMessageHandler(forName: "bridge")
        contentController.addUserScript(
            WKUserScript(source: Self.bridgeScript(), injectionTime: .atDocumentStart, forMainFrameOnly: true)
        )
        contentController.add(bridgeMessageHandler, name: BridgeMessageHandler.handlerName)
        contentMode = .remote
    }

    /// Restores Capacitor's original scripts (captured once in
    /// `capacitorDidLoad()`) and its `"bridge"` message handler, removing
    /// the narrow bridge script/handler. Idempotent, mirroring
    /// `enterRemoteMode()`.
    private func enterLocalMode() {
        guard contentMode != .local, let contentController = webView?.configuration.userContentController else {
            return
        }
        contentController.removeAllUserScripts()
        contentController.removeScriptMessageHandler(forName: BridgeMessageHandler.handlerName)
        for script in localModeScripts {
            contentController.addUserScript(script)
        }
        if let original = originalNavigationDelegate {
            contentController.add(original, name: "bridge")
        }
        contentMode = .local
    }

    /// JavaScript injected into the remote instance's page load, defining
    /// `window.__SOVEREIGN_BRIDGE__` per `@sovereignfs/bridge`'s
    /// `InstalledBridge` wire shape (`packages/bridge/src/protocol.ts` in
    /// the monorepo). `invoke()` posts to the narrow `sovereignBridge`
    /// message handler (`Bridge.swift`) and resolves via a pending-promise
    /// registry — `WKScriptMessageHandler.postMessage` has no return value,
    /// unlike Tauri's `invoke()`, so the native side calls back into
    /// `window.__sovereignBridgeResolve__` once the capability call
    /// completes (immediately for `haptics.impact`, after the
    /// `UNUserNotificationCenter` round trip for `notifications.native`).
    private static func bridgeScript() -> String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
        return """
            (function () {
              var pending = {};
              var counter = 0;
              window.__sovereignBridgeResolve__ = function (envelope) {
                var resolve = pending[envelope.id];
                if (resolve) {
                  delete pending[envelope.id];
                  resolve(envelope.result);
                }
              };
              Object.defineProperty(window, '__SOVEREIGN_BRIDGE__', {
                value: Object.freeze({
                  protocolVersion: 1,
                  shell: Object.freeze({ name: 'sovereign-mobile', version: '\(version)', platform: 'ios' }),
                  capabilities: [
                    { name: 'haptics.impact', version: 1 },
                    { name: 'notifications.native', version: 1 }
                  ],
                  invoke: function (capability, payload) {
                    return new Promise(function (resolve) {
                      var id = 'b' + counter++ + '_' + Date.now();
                      pending[id] = resolve;
                      window.webkit.messageHandlers.\(BridgeMessageHandler.handlerName).postMessage({
                        id: id,
                        capability: capability,
                        payload: payload || {}
                      });
                    });
                  }
                }),
                writable: false,
                configurable: false,
                enumerable: true
              });
            })();
            """
    }

    // MARK: - Message forwarding for everything but decidePolicyFor

    override func responds(to aSelector: Selector!) -> Bool {
        if super.responds(to: aSelector) {
            return true
        }
        return originalNavigationDelegate?.responds(to: aSelector) ?? false
    }

    override func forwardingTarget(for aSelector: Selector!) -> Any? {
        if super.responds(to: aSelector) {
            return nil
        }
        return originalNavigationDelegate
    }
}

// MARK: - WKNavigationDelegate

extension MainViewController: WKNavigationDelegate {
    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        guard let url = navigationAction.request.url,
              let scheme = url.scheme, scheme == "http" || scheme == "https" else {
            // Non-http(s) requests (capacitor:// bridge traffic, etc.) are
            // Capacitor's own concern — forward to its real handler rather
            // than deciding ourselves. This is also how a back-navigation to
            // the bundled local page is detected, so restore Capacitor's own
            // bridge here before forwarding.
            enterLocalMode()
            forwardDecidePolicy(webView, navigationAction, decisionHandler)
            return
        }

        guard navigationAction.targetFrame?.isMainFrame ?? false else {
            // Sub-frame http(s) navigation (an iframe inside the loaded
            // instance) is the instance's own business, not this shell's
            // policy to enforce — allow directly.
            decisionHandler(.allow)
            return
        }

        guard let activeOrigin = MainViewController.activeInstanceOrigin(),
              let activeURL = URL(string: activeOrigin) else {
            // No active instance recorded yet. In practice this only
            // precedes the very first navigation *to* a freshly-added
            // instance, so allow it directly — do NOT forward to
            // Capacitor's own handler here (see below). This is still
            // remote (http/https) content, so isolate the bridge here too.
            enterRemoteMode()
            decisionHandler(.allow)
            return
        }

        let sameOrigin =
            url.scheme == activeURL.scheme && url.host == activeURL.host && url.port == activeURL.port

        if sameOrigin {
            // Decide this ourselves — do not forward to Capacitor's own
            // handler. Capacitor's default WebViewDelegationHandler treats
            // any main-frame navigation away from its bundled local
            // content as "external" and hands it to the system browser
            // (confirmed empirically: forwarding this exact case sent the
            // freshly-onboarded instance to Safari instead of loading it
            // in this WebView). That default is right for a normal
            // Capacitor app whose real content is bundled locally, but
            // wrong here — this shell's entire purpose is to load a
            // user-chosen remote origin as primary content, per
            // docs/adrs/0005-server-url-not-bundled-assets.md.
            enterRemoteMode()
            decisionHandler(.allow)
            return
        }

        decisionHandler(.cancel)
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }

    private func forwardDecidePolicy(
        _ webView: WKWebView,
        _ navigationAction: WKNavigationAction,
        _ decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        guard let original = originalNavigationDelegate else {
            decisionHandler(.allow)
            return
        }
        original.webView(webView, decidePolicyFor: navigationAction, decisionHandler: decisionHandler)
    }

    /// Reads the active instance origin directly from the same
    /// @capacitor/preferences storage the TS shell writes
    /// ("sovereign.activeUrl", "CapacitorStorage." prefix — see
    /// @capacitor/preferences/ios's Preferences.swift for the default group
    /// name and key-prefix format this must stay in sync with), so there is
    /// one source of truth, not a separately-synced native copy.
    private static func activeInstanceOrigin() -> String? {
        UserDefaults.standard.string(forKey: "CapacitorStorage.sovereign.activeUrl")
    }
}
