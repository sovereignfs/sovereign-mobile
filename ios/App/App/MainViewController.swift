import Capacitor
import UIKit
import WebKit

/// Two responsibilities, both documented as this repo's own ADRs since
/// neither has a sovereign-desktop precedent that transfers as-is:
///
/// 1. Enables WKWebView's edge-swipe back/forward gesture. Combined with
///    `onboarding.ts` using `location.assign` (not `.replace`) when loading
///    an instance, this is this shell's entire "switch instance" affordance
///    — see docs/adrs/0006-history-based-instance-switch-affordance.md.
/// 2. Enforces navigation policy per sovereign RFC 0058: keeps the active
///    instance in this WebView, opens everything else in the system
///    browser — see docs/adrs/0007-navigation-policy-enforcement.md.
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
class MainViewController: CAPBridgeViewController {
    private weak var originalNavigationDelegate: WebViewDelegationHandler?

    override func capacitorDidLoad() {
        webView?.allowsBackForwardNavigationGestures = true
        originalNavigationDelegate = webView?.navigationDelegate as? WebViewDelegationHandler
        webView?.navigationDelegate = self
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
            // than deciding ourselves.
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
            // Capacitor's own handler here (see below).
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
