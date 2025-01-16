package com.github.droibit.flutter.plugins.customtabs;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class CallbackActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri url = getIntent().getData();
        if (url == null) {
            url = fixAutoVerifyNotWorks(getIntent());
        }

        String scheme = (url != null) ? url.getScheme() : null;

        if (scheme != null) {
            CustomTabsPlugin.callbacks.remove(scheme).success(url.toString());
        }

        finishAndRemoveTask();
    }

    /**
     * Fixes the issue where android:autoVerify="true" does not work when it can't access Google after installation.
     * See https://stackoverflow.com/questions/76383106/auto-verify-not-always-working-in-app-links-using-android
     * <p>
     * Must register in AndroidManifest.xml:
     * <intent-filter>
     * <action android:name="android.intent.action.SEND" />
     * <category android:name="android.intent.category.DEFAULT" />
     * <data android:mimeType="text/plain" />
     * </intent-filter>
     */
    private Uri fixAutoVerifyNotWorks(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (extraText != null) {
                try {
                    // scheme://host/path#id_token=xxx
                    return Uri.parse(extraText);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }
}