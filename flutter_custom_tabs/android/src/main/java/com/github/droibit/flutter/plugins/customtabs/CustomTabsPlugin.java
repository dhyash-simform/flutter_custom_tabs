package com.github.droibit.flutter.plugins.customtabs;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;

import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.EventChannel;

public class CustomTabsPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {

    // Define an EventChannel
    private EventChannel eventChannel;
    private EventChannel.EventSink eventSink;

    private static final int REQUEST_CODE = 1001;
    private static final String KEY_OPTION = "customTabsOption";

    private static final String KEY_URL = "url";

    private static final String CODE_LAUNCH_ERROR = "LAUNCH_ERROR";

    @Nullable
    private Activity activity;

    @Nullable
    private MethodChannel channel;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
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
            final Map<String, Object> options = (Map<String, Object>) args.get(KEY_OPTION);
            final CustomTabsIntent customTabsIntent = factory.createIntent(options);
            final Uri uri = Uri.parse(args.get(KEY_URL).toString());
            activity.startActivityForResult(customTabsIntent.intent.setData(uri), REQUEST_CODE);
            result.success(null);
        } catch (ActivityNotFoundException e) {
            result.error(CODE_LAUNCH_ERROR, e.getMessage(), null);
        }
    }
}
