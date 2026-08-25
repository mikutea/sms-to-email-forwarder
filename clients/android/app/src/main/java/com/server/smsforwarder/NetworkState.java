package com.server.smsforwarder;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

final class NetworkState {
    private NetworkState() {
    }

    static boolean isConnected(Context context) {
        return inspect(context).usableForBackground;
    }

    static Snapshot inspect(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return Snapshot.offline();
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return Snapshot.offline();
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        if (capabilities == null) {
            return Snapshot.offline();
        }
        boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        boolean validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        boolean wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean ethernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        boolean metered = manager.isActiveNetworkMetered();
        boolean restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && metered
                && manager.getRestrictBackgroundStatus()
                == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
        return new Snapshot(
                hasInternet,
                validated,
                cellular,
                wifi,
                ethernet,
                metered,
                restricted);
    }

    static String diagnosticSummary(Context context) {
        Snapshot snapshot = inspect(context);
        if (!snapshot.hasInternet) {
            return snapshot.label;
        }
        String policy = snapshot.backgroundRestricted
                ? "后台流量受限"
                : snapshot.metered ? "计费网络" : "非计费网络";
        return snapshot.label + " · " + policy;
    }

    static String describe(
            boolean hasInternet,
            boolean validated,
            boolean cellular,
            boolean metered,
            boolean backgroundRestricted) {
        String transport = cellular ? "移动网络" : "网络";
        if (!hasInternet) {
            return "离线";
        }
        if (backgroundRestricted && metered) {
            return transport + "已连接 · 后台流量受限";
        }
        if (!validated) {
            return transport + "已连接 · 互联网未验证";
        }
        return transport + "可用";
    }

    static final class Snapshot {
        final boolean hasInternet;
        final boolean validated;
        final boolean cellular;
        final boolean wifi;
        final boolean ethernet;
        final boolean metered;
        final boolean backgroundRestricted;
        final boolean usableForBackground;
        final String label;

        Snapshot(
                boolean hasInternet,
                boolean validated,
                boolean cellular,
                boolean wifi,
                boolean ethernet,
                boolean metered,
                boolean backgroundRestricted) {
            this.hasInternet = hasInternet;
            this.validated = validated;
            this.cellular = cellular;
            this.wifi = wifi;
            this.ethernet = ethernet;
            this.metered = metered;
            this.backgroundRestricted = backgroundRestricted;
            this.usableForBackground = hasInternet && validated && !backgroundRestricted;
            if (wifi) {
                this.label = describeTransport("Wi-Fi", hasInternet, validated, metered, backgroundRestricted);
            } else if (ethernet) {
                this.label = describeTransport("有线网络", hasInternet, validated, metered, backgroundRestricted);
            } else {
                this.label = describe(hasInternet, validated, cellular, metered, backgroundRestricted);
            }
        }

        private static Snapshot offline() {
            return new Snapshot(false, false, false, false, false, false, false);
        }

        private static String describeTransport(
                String transport,
                boolean hasInternet,
                boolean validated,
                boolean metered,
                boolean backgroundRestricted) {
            if (!hasInternet) return "离线";
            if (backgroundRestricted && metered) return transport + "已连接 · 后台流量受限";
            if (!validated) return transport + "已连接 · 互联网未验证";
            return transport + "可用";
        }
    }
}
