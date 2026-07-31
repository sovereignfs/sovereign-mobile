package fs.sovereign.mobile;

import com.getcapacitor.BridgeActivity;

/**
 * Installs NavigationPolicyWebViewClient once the bridge has finished
 * loading — see docs/adrs/0007-navigation-policy-enforcement.md. The
 * Android hardware back button already routes through WebView history by
 * default (Capacitor's own BridgeActivity behavior), which is this shell's
 * "switch instance" affordance — see
 * docs/adrs/0006-history-based-instance-switch-affordance.md — so no
 * override is needed here for that part.
 */
public class MainActivity extends BridgeActivity {

    @Override
    protected void load() {
        super.load();
        bridge.setWebViewClient(new NavigationPolicyWebViewClient(bridge));
    }
}
