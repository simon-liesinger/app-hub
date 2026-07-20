package com.simon.apphub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // Where friends' devices look for the catalog of Simon's apps.
    static final String DEFAULT_MANIFEST_URL =
            "https://raw.githubusercontent.com/simon-liesinger/app-hub/main/manifest.json";
    static final String PREFS = "apphub";
    static final String KEY_MANIFEST_URL = "manifest_url";

    // Status codes for a monitored app.
    static final int NOT_INSTALLED = 0;
    static final int UPDATE_AVAILABLE = 1;
    static final int UP_TO_DATE = 2;

    static final int C_INDIGO = 0xFF4F46E5;
    static final int C_INDIGO_DK = 0xFF3730A3;
    static final int C_GREEN = 0xFF16A34A;
    static final int C_ORANGE = 0xFFEA580C;
    static final int C_BLUE = 0xFF2563EB;
    static final int C_BG = 0xFFF3F4F6;
    static final int C_CARD = 0xFFFFFFFF;
    static final int C_TEXT = 0xFF111827;
    static final int C_MUTED = 0xFF6B7280;
    static final int C_BORDER = 0xFFE5E7EB;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout list;      // container for app cards
    private TextView subtitle;      // status line under the header

    // ------------------------------------------------------------------ model

    static class AppEntry {
        String name;
        String packageName;
        long versionCode;
        String versionName;
        String apkUrl;
        String description;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check after returning from the installer or the settings screen.
        if (list != null && list.getChildCount() > 0) refresh();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // Header bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(C_INDIGO);
        int hp = dp(20);
        header.setPadding(hp, dp(28), hp, dp(20));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("App Hub");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(title, tlp);

        Button refreshBtn = new Button(this);
        refreshBtn.setText("Refresh");
        refreshBtn.setAllCaps(false);
        styleButton(refreshBtn, Color.WHITE, C_INDIGO);
        refreshBtn.setOnClickListener(v -> refresh());
        titleRow.addView(refreshBtn);

        header.addView(titleRow);

        subtitle = new TextView(this);
        subtitle.setTextColor(0xFFC7D2FE);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setPadding(0, dp(6), 0, 0);
        subtitle.setText("Checking your apps…");
        header.addView(subtitle);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Scrollable list of cards
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(14), dp(14), dp(24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Footer: source settings
        TextView source = new TextView(this);
        source.setText("Catalog source ⚙");
        source.setTextColor(C_MUTED);
        source.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        source.setGravity(Gravity.CENTER);
        source.setPadding(0, dp(10), 0, dp(14));
        source.setOnClickListener(v -> editManifestUrl());
        root.addView(source);

        return root;
    }

    // ------------------------------------------------------------------ refresh

    private void refresh() {
        subtitle.setText("Checking your apps…");
        showMessage("Loading catalog…");
        final String url = manifestUrl();
        io.execute(() -> {
            try {
                String json = httpGet(url);
                final List<AppEntry> apps = parseManifest(json);
                ui.post(() -> renderList(apps));
            } catch (Exception e) {
                ui.post(() -> {
                    subtitle.setText("Couldn't load catalog");
                    showMessage("Failed to load catalog.\n\n" + e.getMessage()
                            + "\n\nSource:\n" + url + "\n\nTap “Catalog source” below to change it.");
                });
            }
        });
    }

    private void renderList(List<AppEntry> apps) {
        list.removeAllViews();
        int needAction = 0;
        for (AppEntry a : apps) {
            int status = statusOf(a);
            if (status != UP_TO_DATE) needAction++;
            list.addView(buildCard(a, status));
        }
        if (apps.isEmpty()) {
            showMessage("The catalog is empty.");
            subtitle.setText("No apps listed");
        } else if (needAction == 0) {
            subtitle.setText(apps.size() + " app" + (apps.size() == 1 ? "" : "s")
                    + " · all up to date");
        } else {
            subtitle.setText(apps.size() + " app" + (apps.size() == 1 ? "" : "s")
                    + " · " + needAction + " need" + (needAction == 1 ? "s" : "") + " attention");
        }
    }

    private void showMessage(String msg) {
        list.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextColor(C_MUTED);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setPadding(dp(16), dp(28), dp(16), dp(16));
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        list.addView(tv);
    }

    // ------------------------------------------------------------------ card

    private View buildCard(AppEntry a, int status) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), C_BORDER);
        card.setBackground(bg);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(12);
        card.setLayoutParams(clp);

        // Name + status chip row
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(a.name);
        name.setTextColor(C_TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        topRow.addView(buildChip(status));
        card.addView(topRow);

        // Description
        if (a.description != null && !a.description.isEmpty()) {
            TextView desc = new TextView(this);
            desc.setText(a.description);
            desc.setTextColor(C_MUTED);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            desc.setPadding(0, dp(4), 0, 0);
            card.addView(desc);
        }

        // Version line
        TextView ver = new TextView(this);
        ver.setText(versionLine(a, status));
        ver.setTextColor(C_MUTED);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        ver.setPadding(0, dp(8), 0, dp(12));
        card.addView(ver);

        // Action button
        Button action = new Button(this);
        action.setAllCaps(false);
        action.setTextColor(Color.WHITE);
        if (status == NOT_INSTALLED) {
            styleButton(action, C_BLUE, Color.WHITE);
            action.setText("Install");
            action.setOnClickListener(v -> onInstallClick(a, action));
        } else if (status == UPDATE_AVAILABLE) {
            styleButton(action, C_ORANGE, Color.WHITE);
            action.setText("Update");
            action.setOnClickListener(v -> onInstallClick(a, action));
        } else {
            styleButton(action, C_BORDER, C_MUTED);
            action.setTextColor(C_MUTED);
            action.setText("Up to date");
            action.setEnabled(false);
        }
        card.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return card;
    }

    private View buildChip(int status) {
        TextView chip = new TextView(this);
        int color;
        String text;
        if (status == NOT_INSTALLED) { color = C_BLUE; text = "Not installed"; }
        else if (status == UPDATE_AVAILABLE) { color = C_ORANGE; text = "Update"; }
        else { color = C_GREEN; text = "Installed"; }
        chip.setText(text);
        chip.setTextColor(color);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable cb = new GradientDrawable();
        cb.setColor(withAlpha(color, 0x22));
        cb.setCornerRadius(dp(20));
        chip.setBackground(cb);
        chip.setPadding(dp(12), dp(5), dp(12), dp(5));
        return chip;
    }

    private String versionLine(AppEntry a, int status) {
        String latest = "v" + a.versionName + " (" + a.versionCode + ")";
        if (status == NOT_INSTALLED) {
            return "Latest: " + latest;
        }
        String cur = installedVersionName(a.packageName);
        long curCode = installedVersionCode(a.packageName);
        String installed = "v" + cur + " (" + curCode + ")";
        if (status == UPDATE_AVAILABLE) {
            return "Installed " + installed + "  →  " + latest;
        }
        return "Installed " + installed;
    }

    // ------------------------------------------------------------------ install flow

    private void onInstallClick(AppEntry a, Button button) {
        if (!canInstall()) {
            promptEnableUnknownSources();
            return;
        }
        button.setEnabled(false);
        button.setText("Starting…");
        io.execute(() -> {
            File apk = null;
            try {
                if (!isAllowedApkHost(a.apkUrl)) {
                    throw new Exception("APK is not hosted on GitHub:\n" + a.apkUrl);
                }
                apk = download(a.apkUrl, a.packageName, button);

                // TOFU signature check: if the app is already installed, the new
                // APK must be signed with the same certificate.
                Set<String> installed = installedCertDigests(a.packageName);
                if (installed != null) {
                    Set<String> incoming = apkCertDigests(apk);
                    if (incoming == null || disjoint(installed, incoming)) {
                        throw new Exception("Signature mismatch – refusing to install. "
                                + "The download is signed with a different key than the installed app.");
                    }
                }

                final File ready = apk;
                ui.post(() -> launchInstaller(ready));
            } catch (Exception e) {
                if (apk != null) apk.delete();
                final String msg = e.getMessage();
                ui.post(() -> {
                    button.setEnabled(true);
                    button.setText(installedVersionCode(a.packageName) < 0 ? "Install" : "Update");
                    new AlertDialog.Builder(this)
                            .setTitle("Couldn't install " + a.name)
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private File download(String url, String pkg, Button button) throws Exception {
        File dir = new File(getCacheDir(), "updates");
        dir.mkdirs();
        File out = new File(dir, pkg + ".apk");

        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/octet-stream");
        conn.connect();

        // Re-validate the host we actually landed on after any redirects.
        if (!isAllowedHost(conn.getURL().getHost())) {
            conn.disconnect();
            throw new Exception("Download redirected to an untrusted host: " + conn.getURL().getHost());
        }
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new Exception("Server returned HTTP " + code);
        }

        long total = conn.getContentLengthLong();
        try (InputStream in = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[16 * 1024];
            long done = 0;
            int n;
            int lastPct = -1;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int pct = (int) (done * 100 / total);
                    if (pct != lastPct) {
                        lastPct = pct;
                        final int p = pct;
                        ui.post(() -> button.setText("Downloading " + p + "%"));
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
        ui.post(() -> button.setText("Installing…"));
        return out;
    }

    private void launchInstaller(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open installer: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean canInstall() {
        if (Build.VERSION.SDK_INT >= 26) {
            return getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    private void promptEnableUnknownSources() {
        new AlertDialog.Builder(this)
                .setTitle("Allow installing apps")
                .setMessage("To install and update apps, App Hub needs permission to install "
                        + "unknown apps. Enable it on the next screen, then come back and tap Install again.")
                .setPositiveButton("Open settings", (d, w) -> {
                    if (Build.VERSION.SDK_INT >= 26) {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ package inspection

    private int statusOf(AppEntry a) {
        long installed = installedVersionCode(a.packageName);
        if (installed < 0) return NOT_INSTALLED;
        if (installed < a.versionCode) return UPDATE_AVAILABLE;
        return UP_TO_DATE;
    }

    private long installedVersionCode(String pkg) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(pkg, 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : (long) pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private String installedVersionName(String pkg) {
        try {
            return getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private Set<String> installedCertDigests(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                SigningInfo si = pi.signingInfo;
                if (si == null) return null;
                sigs = si.hasMultipleSigners()
                        ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            } else {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                sigs = pi.signatures;
            }
            return digestsOf(sigs);
        } catch (Exception e) {
            return null;
        }
    }

    private Set<String> apkCertDigests(File apk) {
        try {
            PackageManager pm = getPackageManager();
            int flag = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo pi = pm.getPackageArchiveInfo(apk.getPath(), flag);
            if (pi == null) return null;
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) {
                SigningInfo si = pi.signingInfo;
                sigs = si.hasMultipleSigners()
                        ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            } else {
                sigs = pi.signatures;
            }
            return digestsOf(sigs);
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<String> digestsOf(Signature[] sigs) throws Exception {
        if (sigs == null) return null;
        Set<String> out = new HashSet<>();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (Signature s : sigs) {
            byte[] d = md.digest(s.toByteArray());
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            out.add(sb.toString());
        }
        return out;
    }

    private static boolean disjoint(Set<String> a, Set<String> b) {
        for (String s : a) if (b.contains(s)) return false;
        return true;
    }

    // ------------------------------------------------------------------ networking helpers

    private String httpGet(String urlStr) throws Exception {
        URL u = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");
        conn.connect();
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new Exception("HTTP " + code + " fetching catalog");
        }
        StringBuilder sb = new StringBuilder();
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) sb.append(new String(buf, 0, n, "UTF-8"));
        } finally {
            conn.disconnect();
        }
        return sb.toString();
    }

    private List<AppEntry> parseManifest(String json) throws Exception {
        List<AppEntry> out = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray apps = root.getJSONArray("apps");
        for (int i = 0; i < apps.length(); i++) {
            JSONObject o = apps.getJSONObject(i);
            AppEntry e = new AppEntry();
            e.name = o.optString("name", o.optString("packageName", "Unknown"));
            e.packageName = o.getString("packageName");
            e.versionCode = o.optLong("versionCode", 0);
            e.versionName = o.optString("versionName", "?");
            e.apkUrl = o.optString("apkUrl", "");
            e.description = o.optString("description", "");
            out.add(e);
        }
        return out;
    }

    private static boolean isAllowedApkHost(String url) {
        try {
            return isAllowedHost(new URL(url).getHost());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isAllowedHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        return host.equals("github.com")
                || host.endsWith(".github.com")
                || host.endsWith(".githubusercontent.com");
    }

    // ------------------------------------------------------------------ settings / prefs

    private String manifestUrl() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.getString(KEY_MANIFEST_URL, DEFAULT_MANIFEST_URL);
    }

    private void editManifestUrl() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(manifestUrl());
        input.setSingleLine(false);
        int pad = dp(20);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Catalog source")
                .setMessage("URL of the manifest.json listing apps to monitor.")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String v = input.getText().toString().trim();
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_MANIFEST_URL, v.isEmpty() ? DEFAULT_MANIFEST_URL : v).apply();
                    refresh();
                })
                .setNeutralButton("Reset", (d, w) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .remove(KEY_MANIFEST_URL).apply();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ view helpers

    private void styleButton(Button b, int bgColor, int textColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        b.setTextColor(textColor);
        b.setPadding(dp(20), dp(10), dp(20), dp(10));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
