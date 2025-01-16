package com.github.droibit.flutter.plugins.customtabs;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsClient;

import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class CustomTabsPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {

    private Context context;
    // Define an EventChannel
    private EventChannel eventChannel;
    private EventChannel.EventSink eventSink;
    private static final int REQUEST_CODE = 1001;
    private static final String KEY_OPTION = "customTabsOption";
    private static final String KEY_URL = "url";
    private static final String KEY_URLS_TO_CLOSE = "urlsToClose";
    private static final String CODE_LAUNCH_ERROR = "LAUNCH_ERROR";
    public static final Map<String, Result> callbacks = new HashMap<>();

    @Nullable
    private Activity activity;

    @Nullable
    private MethodChannel channel;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        this.context = binding.getApplicationContext();
        channel = new MethodChannel(binding.getBinaryMessenger(), "plugins.flutter.droibit.github.io/custom_tabs");
        eventChannel = new EventChannel(binding.getBinaryMessenger(), "plugins.flutter.dhyash-simform.github.io/custom_tabs_status");
        eventChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object arguments, EventChannel.EventSink events) {
                eventSink = events;
            }

            @Override
            public void onCancel(Object arguments) {
                eventSink = null;
            }
        });
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        context = null;
        if (channel == null) {
            return;
        }
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        binding.addActivityResultListener((requestCode, resultCode, data) -> {
            if (requestCode == REQUEST_CODE) {
                if (eventSink != null) {
                    eventSink.success("CUSTOM_TAB_CLOSED");
                }
            }
            return false;
        });
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull final MethodChannel.Result result) {
        if ("launch".equals(call.method)) {
            launch(((Map<String, Object>) call.arguments), result);
        } else {
            result.notImplemented();
        }
    }

    @SuppressWarnings({"unchecked", "ConstantConditions"})
    private void launch(@NonNull Map<String, Object> args, @NonNull MethodChannel.Result result) {
        final Activity activity = this.activity;
        if (activity == null) {
            result.error(CODE_LAUNCH_ERROR, "Launching a CustomTabs requires a foreground activity.", null);
            return;
        }

        final CustomTabsFactory factory = new CustomTabsFactory(activity);
        try {
            final Uri url = Uri.parse(args.get(KEY_URL).toString());

            // Safely retrieve and cast the callbackUrlScheme
            List<String> callbackUrlSchemes = null;
            Object urlsToClose = args.get(KEY_URLS_TO_CLOSE);

            if (urlsToClose instanceof List<?>) {
                // Use a generic list and then cast each element to String
                List<?> tempList = (List<?>) urlsToClose;
                callbackUrlSchemes = new ArrayList<>();

                for (Object item : tempList) {
                    if (item instanceof String) {
                        callbackUrlSchemes.add((String) item);
                    }
                }
            }

            final Map<String, Object> options = (Map<String, Object>) args.get(KEY_OPTION);
            final CustomTabsIntent intent = factory.createIntent(options);

            // Store the result callback for the given scheme
            if (callbackUrlSchemes != null) {
                for (String callbackUrlScheme : callbackUrlSchemes) {
                    callbacks.put(callbackUrlScheme, result);
                }
            }

            Intent keepAliveIntent = new Intent(context, KeepAliveService.class);
            if (options != null && options.containsKey("intentFlags")) {
                intent.intent.addFlags((Integer) options.get("intentFlags"));
            }

            intent.intent.putExtra("android.support.customtabs.extra.KEEP_ALIVE", keepAliveIntent);

            String targetPackage = findTargetBrowserPackageName(options);
            if (targetPackage != null) {
                intent.intent.setPackage(targetPackage);
            }
            intent.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity.startActivityForResult(intent.intent.setData(url), REQUEST_CODE);
            result.success(null);
        } catch (ActivityNotFoundException e) {
            result.error(CODE_LAUNCH_ERROR, e.getMessage(), null);
        }
    }

    /**
     * Find Support CustomTabs Browser.
     * <p>
     * Priority:
     * 1. Chrome
     * 2. Custom Browser Order
     * 3. Default Browser
     * 4. Installed Browser
     */
    private String findTargetBrowserPackageName(Map<String, Object> options) {
        List<String> customTabsPackageOrder = (List<String>) options.get("customTabsPackageOrder");
        if (customTabsPackageOrder == null) {
            customTabsPackageOrder = Collections.emptyList();
        }

        // Check target browser
        for (String packageName : customTabsPackageOrder) {
            if (isSupportCustomTabs(packageName)) {
                return packageName;
            }
        }

        // Check default browser
        boolean defaultBrowserSupported = CustomTabsClient.getPackageName(context, Collections.emptyList(), true) != null;
        if (defaultBrowserSupported) {
            return null;
        }

        // Check installed browsers
        List<String> allBrowsers = getInstalledBrowsers();
        for (String packageName : allBrowsers) {
            if (isSupportCustomTabs(packageName)) {
                return packageName;
            }
        }

        // Safely fall back on Chrome just in case
        String chromePackage = "com.android.chrome";
        if (isSupportCustomTabs(chromePackage)) {
            return chromePackage;
        }

        return null; // If no suitable browser is found
    }

    private List<String> getInstalledBrowsers() {
        // Get all apps that can handle VIEW intents
        Intent activityIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://"));
        PackageManager packageManager = context.getPackageManager();

        List<ResolveInfo> viewIntentHandlers;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            viewIntentHandlers = packageManager.queryIntentActivities(activityIntent, PackageManager.MATCH_ALL);
        } else {
            viewIntentHandlers = packageManager.queryIntentActivities(activityIntent, 0);
        }

        List<String> allBrowser = new ArrayList<>();
        for (ResolveInfo resolveInfo : viewIntentHandlers) {
            allBrowser.add(resolveInfo.activityInfo.packageName);
        }

        Collections.sort(allBrowser, new Comparator<String>() {
            @Override
            public int compare(String pkg1, String pkg2) {
                if (isPreferredBrowser(pkg1)) {
                    return -1; // pkg1 is preferred
                }
                if (isFirefox(pkg1)) {
                    return 1; // pkg1 is Firefox
                }
                return 0; // no preference
            }

            private boolean isPreferredBrowser(String packageName) {
                return packageName.equals("com.android.chrome") || packageName.equals("com.chrome.beta") || packageName.equals("com.chrome.dev") || packageName.equals("com.microsoft.emmx");
            }

            private boolean isFirefox(String packageName) {
                return packageName.equals("org.mozilla.firefox");
            }
        });

        return allBrowser;
    }

    private boolean isSupportCustomTabs(String packageName) {
        String value = CustomTabsClient.getPackageName(context, Collections.singletonList(packageName), true);
        return value != null && value.equals(packageName);
    }
}
