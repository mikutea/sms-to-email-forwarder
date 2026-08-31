package com.server.smsforwarder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.joanzapata.iconify.Icon;
import com.joanzapata.iconify.IconDrawable;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.MaterialCommunityIcons;
import com.joanzapata.iconify.fonts.MaterialCommunityModule;
import com.joanzapata.iconify.fonts.MaterialIcons;
import com.joanzapata.iconify.fonts.MaterialModule;

import androidx.core.content.FileProvider;

import java.text.SimpleDateFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.mail.MessagingException;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final int REQUEST_RECEIVE_SMS = 1001;
    private static final int REQUEST_EXPORT_CONFIG = 1002;
    private static final int REQUEST_IMPORT_CONFIG = 1003;
    private static final int REQUEST_INSTALL_SOURCE = 1004;
    private static final int REQUEST_INSTALL_APK = 1005;
    private static final int PAGE_GUARDIAN = UiDestination.GUARDIAN;
    private static final int PAGE_EMAIL = UiDestination.EMAIL;
    private static final int PAGE_RULES = UiDestination.RULES;
    private static final int PAGE_HISTORY = UiDestination.HISTORY;
    private static final int PAGE_SETTINGS = UiDestination.SETTINGS;
    private static final int PAGE_SYSTEM_GUARDIAN = UiDestination.SYSTEM_GUARDIAN;
    private static final int PAGE_MAINTENANCE = UiDestination.MAINTENANCE;
    private static final int PAGE_ONBOARDING = UiDestination.ONBOARDING;
    private static final int PAGE_HEARTBEAT = UiDestination.HEARTBEAT;
    private static final int PAGE_PRIVACY = UiDestination.PRIVACY;
    private static final int PAGE_CONFIG_TRANSFER = UiDestination.CONFIG_TRANSFER;
    private static final int PAGE_PLATFORM_CAPABILITIES = UiDestination.PLATFORM_CAPABILITIES;
    private static final int PAGE_OPEN_SOURCE_LICENSES = UiDestination.OPEN_SOURCE_LICENSES;
    private static final int PAGE_LOCKSCREEN_TEST = UiDestination.LOCKSCREEN_TEST;
    private static final int PAGE_ABOUT = UiDestination.ABOUT;

    private static final int COLOR_INK = Color.rgb(15, 34, 48);
    private static final int COLOR_JADE = Color.rgb(78, 141, 124);
    private static final int COLOR_JADE_DARK = Color.rgb(28, 119, 104);
    private static final int COLOR_JADE_SOFT = Color.rgb(186, 224, 216);
    private static final int COLOR_CINNABAR = Color.rgb(190, 65, 52);
    private static final int COLOR_AMBER = Color.rgb(166, 89, 0);
    private static final int COLOR_PAPER = Color.rgb(248, 249, 248);
    private static final int COLOR_CARD = Color.rgb(252, 253, 252);
    private static final int COLOR_INSET = Color.rgb(240, 246, 244);
    private static final int COLOR_GLASS = Color.argb(224, 252, 253, 253);
    private static final int COLOR_GLASS_BORDER = Color.rgb(220, 234, 230);
    private static final int COLOR_MUTED = Color.rgb(94, 111, 126);
    private static final float UI_SCALE = 0.92f;
    private static final float TEXT_SCALE = 0.93f;
    private static final int MIN_TOUCH_DP = 52;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SparseArray<LinearLayout> rootPageCache = new SparseArray<>();
    private LinearLayout page;
    private LinearLayout navigation;
    private ScrollView pageScroll;
    private int currentPage;
    private boolean enableAfterPermission;
    private boolean visualTestMode;
    private boolean visualTestSmtpFailure;
    private boolean firstResume = true;
    private boolean awaitingInstallPermission;
    private boolean awaitingVendorSettingsReturn;
    private boolean awaitingNotificationAccess;
    private File pendingUpdateApk;
    private int historyFilter;

    private EditText primaryHost;
    private EditText primaryPort;
    private Spinner primarySecurity;
    private EditText primaryUsername;
    private EditText primaryPassword;
    private EditText primaryFrom;
    private EditText primaryRecipients;
    private Switch backupEnabled;
    private LinearLayout backupFields;
    private EditText backupHost;
    private EditText backupPort;
    private Spinner backupSecurity;
    private EditText backupUsername;
    private EditText backupPassword;
    private EditText backupFrom;
    private EditText backupRecipients;
    private Spinner dispatchStrategy;
    private CheckBox privacyConsent;

    private Spinner ruleMode;
    private Spinner contentMode;
    private Spinner simRule;
    private EditText senderAllow;
    private EditText senderBlock;
    private EditText bodyInclude;
    private EditText bodyExclude;
    private EditText bodyRegex;
    private CheckBox includeAll;
    private Switch scheduleEnabled;
    private EditText scheduleStart;
    private EditText scheduleEnd;
    private CheckBox[] weekdays;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Iconify.with(new MaterialModule());
        Iconify.with(new MaterialCommunityModule());
        visualTestMode = BuildConfig.DEBUG
                && getIntent().getBooleanExtra("visual_test_mode", false);
        visualTestSmtpFailure = visualTestMode
                && getIntent().getBooleanExtra("visual_test_smtp_failure", false);
        // Keep user content out of screenshots in distributable builds while allowing
        // emulator screenshot comparison and automated visual QA for debug builds.
        if (!BuildConfig.DEBUG) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        getWindow().setStatusBarColor(COLOR_PAPER);
        getWindow().setNavigationBarColor(COLOR_PAPER);
        setContentView(buildShell());
        // The decor view is attached by setContentView. Calling the API 30+
        // insets controller before that point crashes on some platform builds.
        applyImmersiveCanvas();
        int initialPage = PAGE_GUARDIAN;
        if (visualTestMode) {
            int requestedPage = getIntent().getIntExtra("visual_test_destination", PAGE_GUARDIAN);
            if (UiDestination.isValid(requestedPage)) {
                initialPage = requestedPage;
            }
        }
        showPage(initialPage);
        if (visualTestMode && getIntent().getBooleanExtra("visual_test_install_cached_update", false)) {
            verifyAndInstallCachedVisualUpdate();
        } else if (visualTestMode
                && getIntent().getBooleanExtra("visual_test_force_update", false)) {
            checkForUpdates(true, true);
        } else {
            checkForUpdates(false, false);
        }
        ForwardScheduler.reconcile(this);
        ReadReceiptCleanupWorker.reconcile(this);
        SmsNotificationListener.requestProcessing(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveCanvas();
        }
    }

    private void applyImmersiveCanvas() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A revoked device-level grant is an explicit user privacy decision. Process it before
        // ordinary TTL settlement so even an already-expired request records the accurate
        // "feature disabled" outcome and all linkage clues are cleared through the opt-out path.
        if (SmsReadFeature.isEnabled(this) && !SmsReadFeature.hasNotificationAccess(this)) {
            SmsReadFeature.disableAndScheduleCleanup(this);
        }
        try {
            // Huawei and other vendor power managers may postpone WorkManager after the logical
            // read-link TTL. Reconcile on every foreground return so the UI and encrypted
            // temporary data converge immediately without waiting for the vendor scheduler.
            QueueDatabase.get(this).expireReadReceipts(System.currentTimeMillis());
        } catch (RuntimeException error) {
            ReadReceiptCleanupWorker.scheduleReceiptReconcile(this);
        }
        if (firstResume) {
            firstResume = false;
        } else if (page != null) {
            rootPageCache.clear();
            showPage(currentPage);
        }
        if (awaitingInstallPermission) {
            awaitingInstallPermission = false;
            handleInstallPermissionReturn();
        }
        if (awaitingVendorSettingsReturn) {
            awaitingVendorSettingsReturn = false;
            if (!TravelGuard.isBackgroundConfirmed(this)) {
                showVendorSettingsReturnDialog();
            }
        }
        if (awaitingNotificationAccess) {
            awaitingNotificationAccess = false;
            if (SmsReadFeature.hasNotificationAccess(this)) {
                if (SmsReadFeature.enableAfterAccess(this)) {
                    SmsNotificationListener.requestProcessing(this);
                    showToast("已开启：转发成功后尝试标记系统短信已读");
                } else {
                    showToast("正在完成上次关闭清理，请稍后重试");
                }
            } else {
                SmsReadFeature.disableAndScheduleCleanup(this);
                showGlassDialog(
                        "通知使用权尚未开启",
                        "雁笺没有获得通知使用权，因此不会读取通知，也不会尝试标记短信已读。你可以稍后重新开启。",
                        "知道了", null, null);
            }
            rootPageCache.clear();
            showPage(PAGE_PRIVACY);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        int parent = UiDestination.parent(currentPage);
        if (parent >= 0) {
            showPage(parent);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_RECEIVE_SMS) {
            return;
        }
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        int returnPage = enableAfterPermission ? PAGE_GUARDIAN : currentPage;
        if (granted && enableAfterPermission) {
            AppConfig.setEnabled(this, true);
            AppConfig.setStatus(this, "自动转发已启用，等待新短信");
        } else if (!granted) {
            showGlassDialog(
                    "短信权限尚未允许",
                    "雁笺只在新短信到达时读取系统广播，不会读取历史短信。你可以再次授权，或在系统应用详情中手动允许。",
                    "打开权限设置", this::openAppSettings, "稍后再说");
        }
        enableAfterPermission = false;
        showPage(returnPage);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INSTALL_SOURCE) {
            return; // onResume() checks the real per-source authorization state.
        }
        if (requestCode == REQUEST_INSTALL_APK) {
            if (resultCode != RESULT_OK) showToast("安装尚未完成，可再次检查更新重试");
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        executor.execute(() -> {
            try {
                if (requestCode == REQUEST_EXPORT_CONFIG) {
                    writeText(uri, ConfigBackup.exportJson(this));
                    runOnUiThread(() -> showToast("配置已导出；文件不包含 SMTP 授权码"));
                } else if (requestCode == REQUEST_IMPORT_CONFIG) {
                    ConfigBackup.importJson(this, readText(uri));
                    runOnUiThread(() -> {
                        showToast("配置已导入，请重新填写授权码并完成隐私确认");
                        showPage(PAGE_EMAIL);
                    });
                }
            } catch (Exception error) {
                String message = ForwardProcessor.safeMessage(error);
                runOnUiThread(() -> showToast("配置文件处理失败：" + message));
            }
        });
    }

    private View buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackground(paperSurface());
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, 0, 0, 0);
            return insets;
        });

        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setClipToPadding(false);
        pageScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(18));
        pageScroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(pageScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(5), dp(4), dp(5), dp(4));
        navigation.setBackground(glassSurface(32));
        applyGlassDepth(navigation, 18f, true);
        addNavigationButton("记录", MaterialCommunityIcons.mdi_file_document, PAGE_HISTORY);
        addNavigationButton("规则", MaterialCommunityIcons.mdi_filter_outline, PAGE_RULES);
        addNavigationButton("守护", MaterialCommunityIcons.mdi_shield_outline, PAGE_GUARDIAN);
        addNavigationButton("设置", MaterialCommunityIcons.mdi_settings, PAGE_SETTINGS);
        LinearLayout.LayoutParams navParams = matchWrap();
        navParams.setMargins(dp(18), dp(6), dp(18), dp(9));
        shell.addView(navigation, navParams);
        return shell;
    }

    private void addNavigationButton(String label, Icon iconValue, int pageIndex) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setTag(pageIndex);
        item.setPadding(dp(6), dp(4), dp(6), dp(3));
        ImageView icon = new ImageView(this);
        icon.setTag("icon");
        icon.setImageDrawable(icon(iconValue, COLOR_MUTED, 23));
        item.addView(icon, new LinearLayout.LayoutParams(dp(25), dp(25)));
        TextView labelView = text(label, 11.5f, COLOR_MUTED, false);
        labelView.setTag("label");
        labelView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(2), 0, 0);
        item.addView(labelView, labelParams);
        item.setContentDescription(label + "页面");
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(view -> showPage(pageIndex, true));
        MotionEffects.bindPress(item);
        navigation.addView(item, new LinearLayout.LayoutParams(
                0,
                dp(58),
                1f));
    }

    private void showPage(int index) {
        showPage(index, false);
    }

    private void showPage(int index, boolean preferCachedRoot) {
        int previousPage = currentPage;
        if (preferCachedRoot && isRootDestination(previousPage) && page != null) {
            rootPageCache.put(previousPage, page);
        }
        LinearLayout cachedPage = preferCachedRoot && isRootDestination(index)
                ? rootPageCache.get(index) : null;
        currentPage = index;
        pageScroll.removeAllViews();
        if (cachedPage != null) {
            page = cachedPage;
            pageScroll.addView(page, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            pageScroll.scrollTo(0, 0);
            updateNavigation(index);
            return;
        }
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        pageScroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!preferCachedRoot) {
            rootPageCache.remove(index);
        }
        pageScroll.scrollTo(0, 0);
        int topPadding = 40;
        if (index == PAGE_SETTINGS || index == PAGE_MAINTENANCE
                || index == PAGE_HEARTBEAT || index == PAGE_PRIVACY
                || index == PAGE_CONFIG_TRANSFER || index == PAGE_PLATFORM_CAPABILITIES
                || index == PAGE_OPEN_SOURCE_LICENSES || index == PAGE_LOCKSCREEN_TEST
                || index == PAGE_ABOUT) {
            topPadding = 22;
        } else if (index == PAGE_SYSTEM_GUARDIAN) {
            topPadding = 38;
        } else if (index == PAGE_ONBOARDING) {
            topPadding = 45;
        } else if (index == PAGE_RULES) {
            topPadding = 45;
        } else if (index == PAGE_EMAIL) {
            topPadding = 34;
        } else if (index == PAGE_HISTORY) {
            topPadding = 37;
        }
        page.setPadding(dp(29), dp(topPadding), dp(29), dp(18));
        updateNavigation(index);
        buildPage(index);
        if (isRootDestination(index)) {
            rootPageCache.put(index, page);
        }
        if (!preferCachedRoot) {
            MotionEffects.enterPage(page, dp(10));
        }
    }

    private void updateNavigation(int index) {
        navigation.setVisibility(isRootDestination(index) ? View.VISIBLE : View.GONE);
        int selectedRoot = rootPage(index);
        for (int i = 0; i < navigation.getChildCount(); i++) {
            LinearLayout item = (LinearLayout) navigation.getChildAt(i);
            boolean wasSelected = item.isSelected();
            boolean selected = ((Integer) item.getTag()) == selectedRoot;
            ImageView iconView = (ImageView) item.findViewWithTag("icon");
            TextView labelView = (TextView) item.findViewWithTag("label");
            int tint = selected ? COLOR_JADE_DARK : COLOR_MUTED;
            if (iconView != null && iconView.getDrawable() != null) iconView.getDrawable().setTint(tint);
            if (labelView != null) labelView.setTextColor(tint);
            item.setBackground(selected
                    ? glassSelection()
                    : roundRect(Color.TRANSPARENT, 24));
            if (selected) {
                applyGlassDepth(item, 8f, true);
            } else {
                item.setElevation(0f);
            }
            item.setSelected(selected);
            if (wasSelected != selected) {
                MotionEffects.select(item, selected, dp(2));
            }
        }
    }

    private void buildPage(int index) {
        switch (index) {
            case PAGE_EMAIL:
                showEmailPage();
                break;
            case PAGE_RULES:
                showRulesPage();
                break;
            case PAGE_HISTORY:
                showHistoryPage();
                break;
            case PAGE_SETTINGS:
                showSettingsPage();
                break;
            case PAGE_SYSTEM_GUARDIAN:
                showSystemGuardianPage();
                break;
            case PAGE_MAINTENANCE:
                showMaintenancePage();
                break;
            case PAGE_ONBOARDING:
                showOnboardingPage();
                break;
            case PAGE_HEARTBEAT:
                showHeartbeatPage();
                break;
            case PAGE_PRIVACY:
                showPrivacyPage();
                break;
            case PAGE_CONFIG_TRANSFER:
                showConfigTransferPage();
                break;
            case PAGE_PLATFORM_CAPABILITIES:
                showPlatformCapabilitiesPage();
                break;
            case PAGE_OPEN_SOURCE_LICENSES:
                showOpenSourceLicensesPage();
                break;
            case PAGE_LOCKSCREEN_TEST:
                showLockscreenTestPage();
                break;
            case PAGE_ABOUT:
                showAboutPage();
                break;
            default:
                showOverviewPage();
                break;
        }
    }

    private static boolean isRootDestination(int index) {
        return index == PAGE_GUARDIAN || index == PAGE_RULES
                || index == PAGE_HISTORY || index == PAGE_SETTINGS;
    }

    private static int rootPage(int pageIndex) {
        return UiDestination.root(pageIndex);
    }

    private void showOverviewPage() {
        DeviceHealth health = DeviceHealth.inspect(this);
        boolean guardEnabled = TravelGuard.isEnabled(this);
        boolean displayReady = visualTestMode ? !visualTestSmtpFailure : health.readyForTravel();
        boolean displayGuardEnabled = visualTestMode || guardEnabled;
        boolean displaySmtpFailure = visualTestSmtpFailure || health.smtpFailed;
        addGuardianHeader();

        LinearLayout statusCard = card();
        int statusColor = displayReady ? COLOR_JADE
                : displayGuardEnabled ? COLOR_CINNABAR : COLOR_AMBER;
        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(4), dp(2), dp(4), dp(15));
        hero.addView(heartbeatOrb(statusColor), new LinearLayout.LayoutParams(dp(104), dp(104)));
        LinearLayout heroCopy = new LinearLayout(this);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setPadding(dp(17), 0, 0, 0);
        String guardianHeadline = displayReady && displayGuardEnabled
                ? "守护运行中"
                : displayGuardEnabled && displaySmtpFailure ? "守护需处理" : "守护待设置";
        heroCopy.addView(text(guardianHeadline, 23f, statusColor, true));
        String heartbeat = AppConfig.getLastStatus(this);
        heroCopy.addView(text(displaySmtpFailure
                ? "最近一次 SMTP 验证失败 · 点按查看原因"
                : visualTestMode ? "最近心跳 11:40 · 下一次约 23:40"
                : heartbeat == null || heartbeat.isBlank()
                ? "最近心跳 尚无记录 · 下一次待设置"
                : heartbeat, 12.5f, COLOR_MUTED, false));
        hero.addView(heroCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusCard.addView(hero, matchWrap());

        LinearLayout readiness = new LinearLayout(this);
        readiness.setPadding(dp(3), dp(7), dp(3), dp(7));
        readiness.setBackground(roundStroke(Color.argb(214, 246, 249, 248), Color.WHITE, 22));
        readiness.addView(readinessCell(MaterialCommunityIcons.mdi_message_text_outline, "短信权限", visualTestMode || health.smsPermission ? "已授权" : "未授权", visualTestMode || health.smsPermission), weightedWrap());
        readiness.addView(readinessCell(MaterialCommunityIcons.mdi_cellphone, "后台运行", visualTestMode || (health.batteryExempt && health.backgroundConfirmed) ? "已允许" : "待完成", visualTestMode || (health.batteryExempt && health.backgroundConfirmed)), weightedWrap());
        String smtpReadinessLabel = visualTestSmtpFailure
                ? "验证失败" : visualTestMode ? "已验证" : health.smtpLabel;
        readiness.addView(readinessCell(
                MaterialCommunityIcons.mdi_email_outline,
                "SMTP",
                smtpReadinessLabel,
                visualTestMode ? !visualTestSmtpFailure : health.smtpVerified), weightedWrap());
        readiness.addView(readinessCell(MaterialCommunityIcons.mdi_shield_outline, "真实短信", visualTestMode || health.lastSmsForwardedAt > 0L ? "已验证" : "待验证", visualTestMode || health.lastSmsForwardedAt > 0L), weightedWrap());
        statusCard.addView(readiness, matchWrap());
        if (displaySmtpFailure) {
            statusCard.setClickable(true);
            statusCard.setFocusable(true);
            statusCard.setContentDescription("SMTP 验证失败，点按查看原因");
            statusCard.setOnClickListener(view -> showGlassDialog(
                    "SMTP 验证失败",
                    visualTestSmtpFailure
                            ? "主通道测试失败：SMTP 服务器在会话中提前断开（诊断码 CONNECTION-CLOSED）\n\n建议先尝试 587 + STARTTLS，并检查 VPN、私人 DNS 和运营商端口限制。"
                            : AppConfig.getLastStatus(this),
                    "前往邮箱", () -> showPage(PAGE_EMAIL), "稍后处理"));
            MotionEffects.bindPress(statusCard);
        }
        page.addView(statusCard, cardParams());

        AppConfig config = AppConfig.load(this);
        Button forwarding = actionButton(config.enabled || visualTestMode ? "暂停自动转发" : "启用自动转发", COLOR_JADE);
        forwarding.setOnClickListener(view -> {
            if (visualTestMode) {
                showToast("视觉测试状态不会修改真实转发开关");
            } else if (config.enabled) {
                AppConfig.setEnabled(this, false);
                AppConfig.setStatus(this, "自动转发已由用户暂停");
                showPage(PAGE_GUARDIAN);
            } else {
                requestEnableForwarding();
            }
        });
        LinearLayout.LayoutParams forwardingParams = matchWrap();
        forwardingParams.setMargins(dp(6), dp(2), dp(6), dp(9));
        page.addView(forwarding, forwardingParams);

        LinearLayout guardRow = card();
        guardRow.setOrientation(LinearLayout.HORIZONTAL);
        guardRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView guardIcon = new ImageView(this);
        guardIcon.setImageDrawable(icon(MaterialCommunityIcons.mdi_shield_outline, COLOR_JADE, 28));
        guardRow.addView(guardIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView guardText = text("旅行守护", 16f, COLOR_INK, false);
        guardText.setPadding(dp(12), 0, 0, 0);
        guardRow.addView(guardText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        boolean guardDegraded = displayGuardEnabled && !displayReady;
        TextView guardState = text(guardDegraded ? "需处理" : displayGuardEnabled ? "已开启" : "未开启", 13f,
                guardDegraded ? COLOR_CINNABAR : displayGuardEnabled ? COLOR_JADE_DARK : COLOR_MUTED, true);
        guardRow.addView(guardState);
        Switch guardSwitch = new Switch(this);
        guardSwitch.setMinHeight(dp(MIN_TOUCH_DP));
        guardSwitch.setMinWidth(dp(MIN_TOUCH_DP));
        guardSwitch.setChecked(displayGuardEnabled);
        guardSwitch.setContentDescription("旅行守护开关");
        guardSwitch.setButtonTintList(ColorStateList.valueOf(COLOR_JADE));
        guardSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked == TravelGuard.isEnabled(this)) return;
            toggleTravelGuard();
            // A blocked enable attempt must never leave the visual switch in an
            // enabled state while the persisted guard is still disabled.
            if (checked && !TravelGuard.isEnabled(this)) button.setChecked(false);
        });
        guardRow.addView(guardSwitch, new LinearLayout.LayoutParams(dp(60), dp(MIN_TOUCH_DP)));
        page.addView(guardRow, cardParams());

        LinearLayout tools = card();
        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setGravity(Gravity.CENTER);
        View test = toolCell(MaterialCommunityIcons.mdi_send, "测试邮件", () -> testConfiguredProfile(false));
        toolRow.addView(test, weightedWrap());
        View heartbeatAction = toolCell(MaterialCommunityIcons.mdi_pulse, "发送心跳", () -> {
            if (!AppConfig.load(this).enabled) {
                showToast("请先启用自动转发");
                return;
            }
            TravelGuard.enqueueHeartbeatNow(this, "旅行前手动自检", false);
            showToast("状态心跳已进入发送队列");
            showPage(PAGE_GUARDIAN);
        });
        toolRow.addView(heartbeatAction, weightedWrap());
        View retry = toolCell(MaterialCommunityIcons.mdi_refresh, "重试队列", () -> {
            int released = ForwardScheduler.retryAllNow(this);
            showToast(released == 0
                    ? "当前没有可立即释放的等待项"
                    : "已将 " + released + " 条消息设为立即重试");
            showPage(PAGE_GUARDIAN);
        });
        toolRow.addView(retry, weightedWrap());
        tools.addView(toolRow, matchWrap());
        page.addView(tools, cardParams());

        TextView queue = navigationRow(
                "队列状态",
                "共 " + health.pendingCount + " 条 · " + health.pendingStats.compactLabel(),
                MaterialCommunityIcons.mdi_inbox);
        queue.setOnClickListener(view -> showPage(PAGE_HISTORY));
        queue.setBackground(glassSurface(24));
        applyGlassDepth(queue, 7f, false);
        page.addView(queue, cardParams());

        TextView networkTip = text(
                health.networkLabel + " · 网络恢复后待发消息会自动补发",
                13f,
                health.connected ? COLOR_MUTED : COLOR_CINNABAR,
                false);
        networkTip.setGravity(Gravity.CENTER);
        networkTip.setCompoundDrawablePadding(dp(8));
        networkTip.setCompoundDrawablesWithIntrinsicBounds(icon(MaterialCommunityIcons.mdi_wifi, COLOR_JADE, 20), null, null, null);
        networkTip.setPadding(0, dp(7), 0, dp(2));
        page.addView(networkTip, matchWrap());
    }

    private void showEmailPage() {
        AppConfig config = AppConfig.load(this);
        DeviceHealth health = DeviceHealth.inspect(this);
        addEmailHeader();

        LinearLayout channelTabs = new LinearLayout(this);
        channelTabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        channelTabs.setBackground(roundStroke(Color.argb(232, 247, 250, 249), Color.WHITE, 23));
        TextView primaryTab = text("主通道", 14f, COLOR_JADE_DARK, true);
        primaryTab.setGravity(Gravity.CENTER);
        primaryTab.setBackground(glassSelection());
        applyGlassDepth(primaryTab, 6f, true);
        primaryTab.setPadding(dp(8), dp(7), dp(8), dp(7));
        TextView backupTab = text("备用通道", 14f, COLOR_MUTED, false);
        backupTab.setGravity(Gravity.CENTER);
        backupTab.setPadding(dp(8), dp(7), dp(8), dp(7));
        channelTabs.addView(primaryTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        channelTabs.addView(backupTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(channelTabs, cardParams());

        LinearLayout primaryContent = new LinearLayout(this);
        primaryContent.setOrientation(LinearLayout.VERTICAL);

        LinearLayout primaryPreset = groupedCard();
        primaryPreset.setPadding(dp(10), 0, dp(10), 0);
        addProviderPreset(primaryPreset, true);
        primaryContent.addView(primaryPreset, cardParams());

        LinearLayout primary = card();
        primary.addView(fieldLabel("SMTP 主机"));
        primaryHost = input(primary, "SMTP 主机，例如 smtp.qq.com", InputType.TYPE_CLASS_TEXT);
        LinearLayout connectionLabels = new LinearLayout(this);
        connectionLabels.addView(fieldLabel("端口"), weightedWrap());
        connectionLabels.addView(fieldLabel("加密"), weightedWrap());
        primary.addView(connectionLabels, matchWrap());
        LinearLayout primaryConnection = new LinearLayout(this);
        primaryPort = createInput("465", InputType.TYPE_CLASS_NUMBER);
        primarySecurity = createSpinner(new String[]{"SSL/TLS（465）", "STARTTLS（587）"});
        primaryConnection.addView(primaryPort, weightedWrap());
        primaryConnection.addView(primarySecurity, weightedWrap());
        primary.addView(primaryConnection, matchWrap());
        primary.addView(fieldLabel("SMTP 用户名"));
        primaryUsername = input(primary, "SMTP 用户名", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        primary.addView(fieldLabel("授权码 / 应用专用密码"));
        primaryPassword = passwordInput(primary, "授权码 / 应用专用密码");
        primary.addView(fieldLabel("发件邮箱"));
        primaryFrom = input(primary, "发件邮箱", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        primary.addView(fieldLabel("收件邮箱"));
        primaryRecipients = input(primary, "收件邮箱，可用逗号或换行填写多个", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        primaryRecipients.setMinLines(1);
        fillProfile(config.primaryProfile(), primaryHost, primaryPort, primarySecurity, primaryUsername, primaryPassword, primaryFrom, primaryRecipients);
        bindSecurityPort(primarySecurity, primaryPort);
        if (visualTestMode && config.primaryProfile().validate() != null) {
            primaryHost.setText("smtp.qq.com");
            primaryPort.setText("465");
            primarySecurity.setSelection(0);
            primaryUsername.setText("yanjian@example.com");
            primaryPassword.setText("visual-test-code");
            primaryFrom.setText("yanjian@example.com");
            primaryRecipients.setText("alice@example.com, bob@example.com");
        }
        if (visualTestMode) {
            primaryRecipients.setVisibility(View.GONE);
            primary.addView(recipientChips(primaryRecipients,
                    "alice@example.com", "bob@example.com"), matchWrap());
        }
        primaryContent.addView(primary, cardParams());

        LinearLayout backupContent = new LinearLayout(this);
        backupContent.setOrientation(LinearLayout.VERTICAL);
        backupContent.setVisibility(View.GONE);
        LinearLayout backupPreset = groupedCard();
        backupPreset.setPadding(dp(10), 0, dp(10), 0);
        addProviderPreset(backupPreset, false);
        backupContent.addView(backupPreset, cardParams());

        LinearLayout backupCard = card();
        backupCard.addView(fieldLabel("备用 SMTP 主机"));
        backupHost = input(backupCard, "备用 SMTP 主机", InputType.TYPE_CLASS_TEXT);
        LinearLayout backupLabels = new LinearLayout(this);
        backupLabels.addView(fieldLabel("端口"), weightedWrap());
        backupLabels.addView(fieldLabel("加密"), weightedWrap());
        backupCard.addView(backupLabels, matchWrap());
        LinearLayout backupConnection = new LinearLayout(this);
        backupPort = createInput("465", InputType.TYPE_CLASS_NUMBER);
        backupSecurity = createSpinner(new String[]{"SSL/TLS（465）", "STARTTLS（587）"});
        backupConnection.addView(backupPort, weightedWrap());
        backupConnection.addView(backupSecurity, weightedWrap());
        backupCard.addView(backupConnection, matchWrap());
        backupCard.addView(fieldLabel("SMTP 用户名"));
        backupUsername = input(backupCard, "备用 SMTP 用户名", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        backupCard.addView(fieldLabel("授权码 / 应用专用密码"));
        backupPassword = passwordInput(backupCard, "备用授权码");
        backupCard.addView(fieldLabel("发件邮箱"));
        backupFrom = input(backupCard, "备用发件邮箱", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        backupCard.addView(fieldLabel("收件邮箱"));
        backupRecipients = input(backupCard, "备用收件邮箱，可填写多个", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        fillProfile(config.backupProfile(), backupHost, backupPort, backupSecurity, backupUsername, backupPassword, backupFrom, backupRecipients);
        bindSecurityPort(backupSecurity, backupPort);
        backupContent.addView(backupCard, cardParams());

        page.addView(primaryContent, matchWrap());
        page.addView(backupContent, matchWrap());

        LinearLayout connectionState = groupedCard();
        connectionState.setOrientation(LinearLayout.HORIZONTAL);
        connectionState.setGravity(Gravity.CENTER_VERTICAL);
        connectionState.setPadding(dp(10), 0, dp(10), 0);
        ImageView pulse = new ImageView(this);
        boolean displaySmtpVerified = visualTestMode ? !visualTestSmtpFailure : health.smtpVerified;
        boolean displaySmtpFailed = visualTestSmtpFailure || health.smtpFailed;
        int smtpStateColor = displaySmtpVerified
                ? COLOR_JADE : displaySmtpFailed ? COLOR_CINNABAR : COLOR_AMBER;
        pulse.setImageDrawable(icon(MaterialCommunityIcons.mdi_pulse, smtpStateColor, 22));
        connectionState.addView(pulse, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView state = text(
                "验证状态   " + (visualTestSmtpFailure
                        ? "验证失败" : visualTestMode ? "已验证" : health.smtpLabel),
                12.5f,
                smtpStateColor,
                false);
        state.setPadding(dp(8), 0, 0, 0);
        connectionState.addView(state, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button testPrimary = secondaryButton("测试主通道");
        testPrimary.setMinHeight(dp(MIN_TOUCH_DP));
        testPrimary.setOnClickListener(view -> {
            if (saveEmailConfiguration()) {
                testProfile(AppConfig.load(this).primaryProfile(), "主通道");
            }
        });
        connectionState.addView(testPrimary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(MIN_TOUCH_DP)));
        page.addView(connectionState, compactCardParams());

        page.addView(emailPreviewCard(), cardParams());

        LinearLayout fallback = groupedCard();
        fallback.setOrientation(LinearLayout.HORIZONTAL);
        fallback.setGravity(Gravity.CENTER_VERTICAL);
        fallback.setPadding(dp(10), 0, dp(10), 0);
        ImageView fallbackIcon = new ImageView(this);
        fallbackIcon.setImageDrawable(icon(MaterialCommunityIcons.mdi_backup_restore, COLOR_JADE, 22));
        fallback.addView(fallbackIcon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView fallbackText = text("失败时切换备用通道", 13.5f, COLOR_INK, false);
        fallbackText.setPadding(dp(8), 0, 0, 0);
        fallback.addView(fallbackText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        backupEnabled = new Switch(this);
        backupEnabled.setMinHeight(dp(MIN_TOUCH_DP));
        backupEnabled.setMinWidth(dp(MIN_TOUCH_DP));
        backupEnabled.setChecked(visualTestMode || config.backupEnabled);
        backupEnabled.setContentDescription("失败时切换备用通道");
        fallback.addView(backupEnabled, new LinearLayout.LayoutParams(dp(60), dp(MIN_TOUCH_DP)));
        page.addView(fallback, compactCardParams());

        dispatchStrategy = createSpinner(new String[]{"主通道成功即止，失败切备用", "仅使用主通道", "主备通道都发送"});
        dispatchStrategy.setSelection(strategyIndex(config.dispatchStrategy));
        dispatchStrategy.setVisibility(View.GONE);
        page.addView(dispatchStrategy, new LinearLayout.LayoutParams(1, 1));
        backupFields = backupContent;

        primaryTab.setClickable(true);
        primaryTab.setFocusable(true);
        primaryTab.setContentDescription("查看主通道配置");
        backupTab.setClickable(true);
        backupTab.setFocusable(true);
        backupTab.setContentDescription("查看备用通道配置");
        primaryTab.setOnClickListener(view -> {
            primaryTab.setTextColor(COLOR_JADE_DARK);
            primaryTab.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            primaryTab.setBackground(glassSelection());
            applyGlassDepth(primaryTab, 6f, true);
            backupTab.setTextColor(COLOR_MUTED);
            backupTab.setBackground(roundRect(Color.TRANSPARENT, 19));
            backupTab.setElevation(0f);
            primaryContent.setVisibility(View.VISIBLE);
            backupContent.setVisibility(View.GONE);
        });
        backupTab.setOnClickListener(view -> {
            if (!backupEnabled.isChecked()) backupEnabled.setChecked(true);
            backupTab.setTextColor(COLOR_JADE_DARK);
            backupTab.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            backupTab.setBackground(glassSelection());
            applyGlassDepth(backupTab, 6f, true);
            primaryTab.setTextColor(COLOR_MUTED);
            primaryTab.setBackground(roundRect(Color.TRANSPARENT, 19));
            primaryTab.setElevation(0f);
            primaryContent.setVisibility(View.GONE);
            backupContent.setVisibility(View.VISIBLE);
        });
        MotionEffects.bindPress(primaryTab);
        MotionEffects.bindPress(backupTab);
        backupEnabled.setOnCheckedChangeListener((button, checked) -> {
            dispatchStrategy.setSelection(checked ? 0 : 1);
            if (!checked && backupContent.getVisibility() == View.VISIBLE) {
                primaryTab.performClick();
            }
        });

        LinearLayout privacy = groupedCard();
        privacyConsent = new CheckBox(this);
        privacyConsent.setText("我理解短信可能包含验证码和个人信息");
        privacyConsent.setChecked(visualTestMode || config.privacyConsent);
        styleCheckBox(privacyConsent);
        privacy.addView(privacyConsent);
        page.addView(privacy, compactCardParams());

        Button save = actionButton("保存邮箱配置", COLOR_JADE);
        save.setOnClickListener(view -> saveEmailConfiguration());
        page.addView(save, matchWrap());
    }

    private LinearLayout emailPreviewCard() {
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(2), dp(3), dp(2), dp(7));

        TextView brand = text("雁笺  ·  收到新短信", 11.5f, COLOR_JADE_DARK, true);
        brand.setAllCaps(false);
        preview.addView(brand, matchWrap());

        TextView subject = text("来自 10086 的短信", 19f, COLOR_INK, true);
        subject.setPadding(0, dp(5), 0, dp(10));
        preview.addView(subject, matchWrap());

        LinearLayout metadata = new LinearLayout(this);
        metadata.setOrientation(LinearLayout.HORIZONTAL);
        metadata.setPadding(dp(11), dp(8), dp(11), dp(8));
        metadata.setBackground(insetSurface(16));
        metadata.addView(text("发送方\n10086", 11.5f, COLOR_MUTED, false), weightedWrap());
        metadata.addView(text("接收时间\n今天 11:38", 11.5f, COLOR_MUTED, false), weightedWrap());
        metadata.addView(text("SIM\nSIM 1", 11.5f, COLOR_MUTED, false), weightedWrap());
        preview.addView(metadata, matchWrap());

        TextView body = text("您的验证码是 123456，5 分钟内有效。", 14f, COLOR_INK, false);
        body.setPadding(dp(12), dp(13), dp(12), dp(13));
        body.setBackground(roundStroke(Color.argb(190, 245, 249, 247), COLOR_GLASS_BORDER, 17));
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.setMargins(0, dp(9), 0, 0);
        preview.addView(body, bodyParams);

        TextView deliveryId = text("投递编号  7f39a8c2d10e", 10.5f, COLOR_MUTED, false);
        deliveryId.setPadding(dp(2), dp(9), 0, 0);
        preview.addView(deliveryId, matchWrap());

        return collapsibleCard(
                MaterialCommunityIcons.mdi_email_outline,
                "邮件样式预览",
                "HTML 主视图 · 纯文本兼容",
                preview,
                visualTestMode);
    }

    private void showRulesPage() {
        RuleConfig rules = RuleConfig.load(this);
        if (visualTestMode) {
            rules = new RuleConfig(
                    RuleConfig.MODE_OTP_ONLY,
                    "10086, 95588", "1069*", "验证码, 动态码", "广告", "",
                    false, -1, true, 8 * 60, 23 * 60, 0x1f,
                    RuleConfig.CONTENT_CODE_ONLY);
        }
        addPageTitle("转发规则", "按时段、SIM、发送方与正文依次判断", page);
        addStatusChip("当前规则：" + ruleModeLabel(rules.mode) + " · " + (rules.simSlot < 0 ? "全部 SIM" : "SIM " + (rules.simSlot + 1)), page);

        LinearLayout modeCard = card();
        modeCard.addView(sectionHeader(MaterialIcons.md_center_focus_weak, "范围与隐私"));
        modeCard.addView(fieldLabel("短信范围"));
        ruleMode = segmentedSpinner(modeCard,
                new String[]{"全部短信", "仅验证码", "排除验证码"},
                Math.min(2, ruleModeIndex(rules.mode)));
        modeCard.addView(fieldLabel("隐私模式"));
        contentMode = spinner(modeCard, new String[]{"完整正文", "仅提取验证码", "隐藏连续数字", "只发元数据"});
        contentMode.setSelection(contentModeIndex(rules.contentMode));
        modeCard.addView(fieldLabel("SIM 范围"));
        simRule = segmentedSpinner(modeCard,
                new String[]{"全部 SIM", "SIM 1", "SIM 2"},
                rules.simSlot < 0 ? 0 : rules.simSlot + 1);
        page.addView(modeCard, cardParams());

        LinearLayout senderFields = new LinearLayout(this);
        senderFields.setOrientation(LinearLayout.VERTICAL);
        senderAllow = input(senderFields, "白名单：号码、1069* 或 re:正则；留空不限制", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        senderBlock = input(senderFields, "黑名单：优先于白名单", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        senderAllow.setText(rules.senderAllow);
        senderBlock.setText(rules.senderBlock);
        page.addView(collapsibleCard(MaterialIcons.md_person_outline, "发送方",
                visualTestMode ? "白名单 2 项 · 黑名单 1 项" : "白名单与黑名单",
                senderFields, false), cardParams());

        LinearLayout bodyFields = new LinearLayout(this);
        bodyFields.setOrientation(LinearLayout.VERTICAL);
        bodyInclude = input(bodyFields, "必须包含的关键词，逗号或换行分隔", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bodyExclude = input(bodyFields, "排除关键词", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bodyRegex = input(bodyFields, "正文正则表达式（可选）", InputType.TYPE_CLASS_TEXT);
        includeAll = new CheckBox(this);
        includeAll.setText("所有包含关键词必须同时出现");
        includeAll.setChecked(rules.includeAll);
        styleCheckBox(includeAll);
        bodyFields.addView(includeAll);
        bodyInclude.setText(rules.bodyInclude);
        bodyExclude.setText(rules.bodyExclude);
        bodyRegex.setText(rules.bodyRegex);
        page.addView(collapsibleCard(MaterialIcons.md_description, "正文匹配",
                visualTestMode ? "包含：验证码、动态码 · 排除：广告" : "关键词与正则条件",
                bodyFields, false), cardParams());

        LinearLayout scheduleCard = card();
        scheduleCard.addView(sectionHeader(MaterialIcons.md_access_time, "生效时段"));
        LinearLayout scheduleToggle = new LinearLayout(this);
        scheduleToggle.setGravity(Gravity.CENTER_VERTICAL);
        scheduleToggle.setPadding(dp(10), dp(2), dp(5), dp(2));
        scheduleToggle.setBackground(insetSurface(18));
        scheduleToggle.addView(text("仅在指定时段转发", 14f, COLOR_INK, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        scheduleEnabled = new Switch(this);
        scheduleEnabled.setMinHeight(dp(MIN_TOUCH_DP));
        scheduleEnabled.setMinWidth(dp(MIN_TOUCH_DP));
        scheduleEnabled.setChecked(rules.scheduleEnabled);
        scheduleToggle.addView(scheduleEnabled, new LinearLayout.LayoutParams(dp(60), dp(MIN_TOUCH_DP)));
        scheduleCard.addView(scheduleToggle, matchWrap());
        scheduleCard.addView(fieldLabel("时间范围"));
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setBackground(insetSurface(18));
        scheduleStart = compactTimeInput("开始 HH:mm");
        scheduleEnd = compactTimeInput("结束 HH:mm");
        timeRow.addView(scheduleStart, new LinearLayout.LayoutParams(0, dp(MIN_TOUCH_DP), 1f));
        TextView dash = text("—", 15f, COLOR_JADE_DARK, true);
        dash.setGravity(Gravity.CENTER);
        timeRow.addView(dash, new LinearLayout.LayoutParams(dp(28), dp(MIN_TOUCH_DP)));
        timeRow.addView(scheduleEnd, new LinearLayout.LayoutParams(0, dp(MIN_TOUCH_DP), 1f));
        LinearLayout.LayoutParams timeParams = matchWrap();
        timeParams.setMargins(0, dp(5), 0, dp(7));
        scheduleCard.addView(timeRow, timeParams);
        scheduleStart.setText(formatMinute(rules.startMinute));
        scheduleEnd.setText(formatMinute(rules.endMinute));
        scheduleCard.addView(fieldLabel("重复日期"));
        LinearLayout dayRow = new LinearLayout(this);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        dayRow.setGravity(Gravity.CENTER);
        weekdays = new CheckBox[7];
        String[] dayNames = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < weekdays.length; i++) {
            weekdays[i] = new CheckBox(this);
            weekdays[i].setText(dayNames[i]);
            weekdays[i].setChecked((rules.weekdayMask & (1 << i)) != 0);
            styleDayCheckBox(weekdays[i]);
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(0, dp(MIN_TOUCH_DP), 1f);
            dayParams.setMargins(dp(2), 0, dp(2), 0);
            dayRow.addView(weekdays[i], dayParams);
        }
        scheduleCard.addView(dayRow);
        page.addView(scheduleCard, cardParams());

        Button preview = secondaryButton("用示例短信测试当前规则");
        preview.setOnClickListener(view -> previewRules());
        page.addView(preview, matchWrap());
        Button save = actionButton("保存规则", COLOR_JADE);
        save.setOnClickListener(view -> saveRules());
        page.addView(save, matchWrap());
    }

    private void showHistoryPage() {
        addBirdPageTitle("转发记录", "正文与发送方加密保存在本机", page);
        List<HistoryItem> allHistory = QueueDatabase.get(this).recentHistory(50);
        if (allHistory.isEmpty() && visualTestMode) {
            allHistory = visualHistoryItems();
        }
        List<HistoryItem> history = new ArrayList<>();
        for (HistoryItem item : allHistory) {
            boolean include = historyFilter == 0
                    || (historyFilter == 1 && !"SUCCESS".equals(item.status) && !"FILTERED".equals(item.status))
                    || (historyFilter == 2 && "SUCCESS".equals(item.status))
                    || (historyFilter == 3 && "FILTERED".equals(item.status));
            if (include) history.add(item);
        }
        addHistorySummary(allHistory);

        LinearLayout filters = new LinearLayout(this);
        filters.setPadding(dp(4), dp(4), dp(4), dp(4));
        filters.setBackground(roundStroke(Color.argb(232, 247, 250, 249), Color.WHITE, 23));
        String[] filterLabels = {"全部", "待发", "成功", "已过滤"};
        for (int i = 0; i < filterLabels.length; i++) {
            int filterIndex = i;
            boolean selected = historyFilter == i;
            TextView filter = text(filterLabels[i], 13f, selected ? Color.WHITE : COLOR_INK, selected);
            filter.setGravity(Gravity.CENTER);
            filter.setPadding(dp(7), dp(9), dp(7), dp(9));
            filter.setBackground(selected
                    ? roundStroke(COLOR_JADE, Color.argb(150, 255, 255, 255), 19)
                    : roundRect(Color.TRANSPARENT, 19));
            filter.setClickable(true);
            filter.setFocusable(true);
            filter.setContentDescription("筛选" + filterLabels[i]);
            filter.setOnClickListener(view -> {
                historyFilter = filterIndex;
                showPage(PAGE_HISTORY);
            });
            MotionEffects.bindPress(filter);
            filters.addView(filter, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        page.addView(filters, cardParams());

        if (history.isEmpty()) {
            LinearLayout empty = card();
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView emptyIcon = new ImageView(this);
            emptyIcon.setImageDrawable(icon(MaterialIcons.md_inbox, COLOR_JADE_SOFT, 52));
            LinearLayout.LayoutParams emptyIconParams = new LinearLayout.LayoutParams(dp(64), dp(64));
            emptyIconParams.setMargins(0, dp(18), 0, dp(8));
            empty.addView(emptyIcon, emptyIconParams);
            TextView emptyTitle = text("还没有转发记录", 18f, COLOR_INK, true);
            emptyTitle.setGravity(Gravity.CENTER);
            empty.addView(emptyTitle, matchWrap());
            TextView emptyCopy = text("完成一条真实短信测试后，这里会按时间线显示接收、过滤、重试和成功状态。", 13f, COLOR_MUTED, false);
            emptyCopy.setGravity(Gravity.CENTER);
            emptyCopy.setPadding(dp(12), dp(7), dp(12), dp(18));
            empty.addView(emptyCopy, matchWrap());
            page.addView(empty, cardParams());
        } else {
            LinearLayout timeline = card();
            timeline.setPadding(dp(10), dp(7), dp(10), dp(7));
            int visibleCount = Math.min(history.size(), 12);
            for (int i = 0; i < visibleCount; i++) {
                timeline.addView(historyTimelineRow(history.get(i), i == visibleCount - 1), matchWrap());
            }
            page.addView(timeline, cardParams());
        }
        TextView retry = singleActionRow("立即重试全部待发消息", MaterialIcons.md_refresh);
        retry.setOnClickListener(view -> {
            int released = ForwardScheduler.retryAllNow(this);
            showToast(released == 0
                    ? "当前没有可释放的等待项，正在发送的消息不会重复处理"
                    : "已将 " + released + " 条消息设为立即重试");
            showPage(PAGE_HISTORY);
        });
        LinearLayout.LayoutParams retryParams = matchWrap();
        retryParams.setMargins(0, dp(3), 0, dp(8));
        page.addView(retry, retryParams);

        Button clear = secondaryButton("清空历史记录");
        clear.setTextColor(COLOR_CINNABAR);
        clear.setCompoundDrawablesWithIntrinsicBounds(icon(MaterialIcons.md_delete, COLOR_CINNABAR, 20), null, null, null);
        clear.setCompoundDrawablePadding(dp(7));
        clear.setOnClickListener(view -> showGlassDialog(
                "清空历史记录？",
                "不会删除仍在待发队列中的消息；将同时清除已发送短信尚未完成的临时已读联动数据。历史删除后无法恢复。",
                "确认清空", () -> {
                    QueueDatabase.get(this).clearHistory();
                    showPage(PAGE_HISTORY);
                }, "取消"));
        page.addView(clear, matchWrap());
    }

    private void showSettingsPage() {
        addSettingsHeader();
        addStatusChip("授权码与短信敏感数据仅加密保存在本机", page);
        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));

        AppConfig config = AppConfig.load(this);
        RuleConfig rules = RuleConfig.load(this);
        DeviceHealth health = DeviceHealth.inspect(this);

        page.addView(settingsSectionLabel("转发配置"));
        LinearLayout forwarding = groupedCard();
        forwarding.addView(settingsRow(
                "邮箱通道",
                visualTestMode
                        ? "SMTP 已验证 · 备用通道已启用"
                        : !health.smtpValid
                        ? "等待完成配置"
                        : "SMTP " + health.smtpLabel + (config.backupEnabled ? " · 备用通道已启用" : ""),
                MaterialCommunityIcons.mdi_email_outline,
                () -> showPage(PAGE_EMAIL)));
        forwarding.addView(hairlineDivider());
        forwarding.addView(settingsRow(
                "转发规则",
                ruleModeLabel(rules.mode) + " · " + (rules.simSlot < 0 ? "全部 SIM" : "SIM " + (rules.simSlot + 1)),
                MaterialCommunityIcons.mdi_filter_outline,
                () -> showPage(PAGE_RULES)));
        page.addView(forwarding, cardParams());

        page.addView(settingsSectionLabel("系统守护"));
        LinearLayout guardian = groupedCard();
        guardian.addView(settingsRow(
                "后台授权",
                visualTestMode || (health.backgroundConfirmed && health.batteryExempt && health.smsPermission)
                        ? "关键授权已完成" : "仍有项目待完成",
                MaterialCommunityIcons.mdi_shield_outline,
                () -> showPage(PAGE_SYSTEM_GUARDIAN)));
        guardian.addView(hairlineDivider());
        guardian.addView(settingsRow(
                "状态心跳",
                visualTestMode ? "每 12 小时 · 最近 11:40" : "每 " + TravelGuard.heartbeatHours(this) + " 小时",
                MaterialCommunityIcons.mdi_pulse,
                () -> showPage(PAGE_HEARTBEAT)));
        page.addView(guardian, cardParams());

        page.addView(settingsSectionLabel("数据与隐私"));
        LinearLayout data = groupedCard();
        data.addView(settingsRow(
                "隐私与安全",
                "本机加密 · 禁止系统截图",
                MaterialIcons.md_lock_outline,
                () -> showPage(PAGE_PRIVACY)));
        data.addView(hairlineDivider());
        data.addView(settingsRow(
                "配置迁移",
                "无密码导入与导出",
                MaterialIcons.md_swap_horiz,
                () -> showPage(PAGE_CONFIG_TRANSFER)));
        page.addView(data, cardParams());

        page.addView(settingsSectionLabel("应用"));
        LinearLayout application = groupedCard();
        application.addView(settingsRow(
                "维护与更新",
                "诊断、队列与版本更新",
                MaterialIcons.md_build,
                () -> showPage(PAGE_MAINTENANCE)));
        application.addView(hairlineDivider());
        application.addView(settingsRow(
                "关于雁笺",
                "版本、平台能力与开源许可",
                MaterialIcons.md_info_outline,
                () -> showPage(PAGE_ABOUT)));
        page.addView(application, cardParams());
    }

    private void showSystemGuardianPage() {
        addSystemGuardianHeader();
        DeviceHealth health = DeviceHealth.inspect(this);
        boolean smsAllowed = visualTestMode || health.smsPermission;
        boolean batteryAllowed = visualTestMode || health.batteryExempt;
        boolean backgroundAllowed = visualTestMode || health.backgroundConfirmed;
        boolean lockNetwork = visualTestMode || health.connected;
        int completed = (smsAllowed ? 1 : 0)
                + (batteryAllowed ? 1 : 0)
                + (backgroundAllowed ? 1 : 0);

        LinearLayout progress = card();
        LinearLayout progressTop = new LinearLayout(this);
        progressTop.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout progressIcon = new LinearLayout(this);
        progressIcon.setGravity(Gravity.CENTER);
        progressIcon.setBackground(roundStroke(Color.argb(220, 232, 243, 240), Color.WHITE, 28));
        ImageView shield = new ImageView(this);
        shield.setImageDrawable(icon(MaterialIcons.md_verified_user, COLOR_JADE, 34));
        progressIcon.addView(shield, new LinearLayout.LayoutParams(dp(40), dp(40)));
        progressTop.addView(progressIcon, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView progressTitle = text("后台设置  " + completed + " / 3  已完成", 20f,
                completed == 3 ? COLOR_JADE_DARK : COLOR_AMBER, true);
        progressTitle.setPadding(dp(14), 0, 0, 0);
        progressTop.addView(progressTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        progress.addView(progressTop, matchWrap());
        LinearLayout progressTrack = new LinearLayout(this);
        progressTrack.setBackground(roundRect(Color.rgb(224, 231, 229), 4));
        if (completed > 0) {
            TextView progressLine = new TextView(this);
            progressLine.setBackground(roundRect(COLOR_JADE, 4));
            progressTrack.addView(progressLine, new LinearLayout.LayoutParams(0, dp(7), completed));
        }
        if (completed < 3) {
            progressTrack.addView(new View(this), new LinearLayout.LayoutParams(0, dp(7), 3 - completed));
        }
        LinearLayout.LayoutParams progressParams = matchWrap();
        progressParams.height = dp(7);
        progressParams.setMargins(dp(72), dp(9), dp(7), dp(3));
        progress.addView(progressTrack, progressParams);
        page.addView(progress, cardParams());

        LinearLayout guide = card();
        guide.addView(sectionHeader(MaterialIcons.md_security, "后台授权清单"));
        guide.addView(actionStatusRow(MaterialCommunityIcons.mdi_message_text_outline,
                "接收短信权限", smsAllowed ? "已允许" : "点按授权", smsAllowed,
                () -> requestSmsPermission(false)));
        guide.addView(actionStatusRow(MaterialCommunityIcons.mdi_battery_charging,
                "忽略电池优化", batteryAllowed ? "已允许" : "点按授权", batteryAllowed,
                this::requestBatteryExemption));
        guide.addView(actionStatusRow(MaterialCommunityIcons.mdi_power,
                "厂商后台启动", backgroundAllowed ? "已确认" : "点按设置", backgroundAllowed,
                this::openHuaweiLaunchSettings));
        guide.addView(actionStatusRow(MaterialCommunityIcons.mdi_lock_outline,
                "当前网络",
                health.networkLabel,
                lockNetwork,
                () -> showNetworkStatus(health)));
        page.addView(guide, cardParams());

        Button continueGuide = actionButton(completed == 3 ? "进入锁屏试投" : "继续完成授权", COLOR_JADE);
        continueGuide.setOnClickListener(view -> runNextPermissionStep());
        page.addView(continueGuide, matchWrap());
        if (!backgroundAllowed) {
            Button confirmVendor = secondaryButton("我已完成厂商后台启动设置");
            confirmVendor.setOnClickListener(view -> showVendorSettingsManualConfirmation());
            page.addView(confirmVendor, matchWrap());
        }
        Button appSettings = secondaryButton("打开应用详情设置");
        appSettings.setOnClickListener(view -> openAppSettings());
        page.addView(appSettings, matchWrap());

        TextView heartbeat = navigationRow(
                "状态心跳",
                "每 " + TravelGuard.heartbeatHours(this) + " 小时 · 单独管理与手动发送",
                MaterialCommunityIcons.mdi_pulse);
        heartbeat.setOnClickListener(view -> showPage(PAGE_HEARTBEAT));
        heartbeat.setBackground(glassSurface(24));
        applyGlassDepth(heartbeat, 7f, false);
        page.addView(heartbeat, cardParams());

    }

    private void showHeartbeatPage() {
        addSubPageTitle("状态心跳", "单独管理离家期间的在线确认与手动自检", PAGE_SETTINGS);
        DeviceHealth health = DeviceHealth.inspect(this);
        int current = TravelGuard.heartbeatHours(this);

        LinearLayout summary = card();
        summary.addView(sectionHeader(MaterialCommunityIcons.mdi_pulse, "当前状态"));
        summary.addView(settingsInlineRow(
                "发送间隔", "每 " + current + " 小时", MaterialIcons.md_schedule, false));
        summary.addView(hairlineDivider());
        String recent = visualTestMode ? "今天 11:40"
                : health.lastSuccessAt == 0L ? "尚无成功记录"
                : new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(health.lastSuccessAt));
        summary.addView(settingsInlineRow(
                "最近成功", recent, MaterialIcons.md_verified_user, false));
        summary.addView(hairlineDivider());
        summary.addView(settingsInlineRow(
                "旅行守护", TravelGuard.isEnabled(this) ? "已开启" : "未开启",
                MaterialCommunityIcons.mdi_shield_outline, false));
        page.addView(summary, cardParams());

        LinearLayout schedule = card();
        schedule.addView(sectionHeader(MaterialIcons.md_schedule, "心跳频率"));
        Spinner hours = segmentedSpinner(schedule,
                new String[]{"6 小时", "12 小时", "24 小时"},
                current == 6 ? 0 : current == 24 ? 2 : 1);
        hours.setSelection(current == 6 ? 0 : current == 24 ? 2 : 1);
        hours.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selected = position == 0 ? 6 : position == 2 ? 24 : 12;
                TravelGuard.setHeartbeatHours(MainActivity.this, selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Keep the stored interval.
            }
        });
        TextView scheduleHint = text(
                "频率越高，越早发现断网、断电或后台被限制，但会增加少量耗电与邮件数量。",
                12.5f, COLOR_MUTED, false);
        scheduleHint.setPadding(dp(4), dp(10), dp(4), dp(2));
        schedule.addView(scheduleHint, matchWrap());
        page.addView(schedule, cardParams());

        Button sendNow = actionButton("立即发送一次心跳", COLOR_JADE);
        sendNow.setOnClickListener(view -> {
            if (!AppConfig.load(this).enabled) {
                showGlassDialog(
                        "暂时无法发送心跳",
                        "状态心跳使用已经配置好的邮箱通道。请先启用自动转发，再回来进行手动自检。",
                        "前往旅行守护", () -> showPage(PAGE_GUARDIAN), "稍后再说");
                return;
            }
            TravelGuard.enqueueHeartbeatNow(this, "设置页手动自检", false);
            showToast("状态心跳已进入发送队列");
        });
        page.addView(sendNow, matchWrap());
        Button permissions = secondaryButton("查看后台授权状态");
        permissions.setOnClickListener(view -> showPage(PAGE_SYSTEM_GUARDIAN));
        page.addView(permissions, matchWrap());
        addNotice("心跳只能证明 App 当时仍可联网并完成邮件投递，不能替代真实短信与锁屏试投。", page);
    }

    private void showAboutPage() {
        addSubPageTitle("关于雁笺", "版本、兼容范围与开放源代码信息", PAGE_SETTINGS);

        LinearLayout identity = card();
        LinearLayout identityRow = new LinearLayout(this);
        identityRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(2), dp(2), dp(2), dp(2));
        TextView name = text("雁笺", 24f, COLOR_INK, true);
        name.setTypeface(Typeface.SERIF, Typeface.BOLD);
        copy.addView(name);
        copy.addView(text("版本 " + BuildConfig.VERSION_NAME + " · 短信直达邮箱", 13f,
                COLOR_MUTED, false));
        identityRow.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        identity.addView(identityRow, matchWrap());
        TextView statement = text(
                "短信在本机加密入队，并直接连接你配置的 SMTP 服务；雁笺不建设短信中转服务器。",
                12.5f, COLOR_MUTED, false);
        statement.setPadding(dp(2), dp(12), dp(2), dp(2));
        identity.addView(statement, matchWrap());
        page.addView(identity, cardParams());

        page.addView(settingsSectionLabel("产品信息"));
        LinearLayout links = groupedCard();
        links.addView(settingsRow(
                "版本与更新",
                "稳定版通道 · 当前 " + BuildConfig.VERSION_NAME,
                MaterialIcons.md_system_update,
                () -> showPage(PAGE_MAINTENANCE)));
        links.addView(hairlineDivider());
        links.addView(settingsRow(
                "平台与兼容性",
                "Android、兼容 APK 的鸿蒙与 NEXT 边界",
                MaterialIcons.md_phone_android,
                () -> showPage(PAGE_PLATFORM_CAPABILITIES)));
        links.addView(hairlineDivider());
        links.addView(settingsRow(
                "开源许可",
                "雁笺 Apache-2.0 · 第三方组件声明",
                MaterialIcons.md_code,
                () -> showPage(PAGE_OPEN_SOURCE_LICENSES)));
        page.addView(links, cardParams());

        addNotice("正式包禁止系统截图；版本、许可和平台边界仍可随时在本页查看。", page);
    }

    private void showPrivacyPage() {
        addSubPageTitle("隐私与安全", "看清短信、授权码和诊断信息如何被保护", PAGE_SETTINGS);

        LinearLayout protections = card();
        protections.addView(sectionHeader(MaterialIcons.md_lock_outline, "本机保护"));
        protections.addView(statusRow(MaterialIcons.md_vpn_key,
                "SMTP 授权码", "Android Keystore 加密", true));
        protections.addView(statusRow(MaterialCommunityIcons.mdi_message_text_outline,
                "待发消息与历史", "AES-GCM 加密保存", true));
        protections.addView(statusRow(MaterialIcons.md_visibility_off,
                "系统截图", BuildConfig.DEBUG ? "测试包允许视觉验收" : "正式包已禁止", !BuildConfig.DEBUG));
        protections.addView(statusRow(MaterialIcons.md_share,
                "诊断报告", "默认移除授权码与正文", true));
        page.addView(protections, cardParams());

        boolean notificationAccess = SmsReadFeature.hasNotificationAccess(this);
        boolean markReadEnabled = SmsReadFeature.isEnabled(this) && notificationAccess;
        LinearLayout readCard = card();
        readCard.addView(sectionHeader(
                MaterialCommunityIcons.mdi_message_text_outline,
                "成功后的系统短信状态"));
        Switch markRead = new Switch(this);
        markRead.setMinHeight(dp(MIN_TOUCH_DP));
        markRead.setMinWidth(dp(MIN_TOUCH_DP));
        markRead.setText("转发成功后标记系统短信已读");
        markRead.setTextColor(COLOR_INK);
        markRead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f * TEXT_SCALE);
        markRead.setPadding(dp(6), dp(7), dp(2), dp(7));
        markRead.setChecked(markReadEnabled);
        final boolean[] changingMarkRead = {false};
        markRead.setOnCheckedChangeListener((button, checked) -> {
            if (changingMarkRead[0]) {
                return;
            }
            if (checked) {
                if (SmsReadFeature.hasNotificationAccess(this)) {
                    if (SmsReadFeature.enableAfterAccess(this)) {
                        SmsNotificationListener.requestProcessing(this);
                        showToast("已开启自动已读联动");
                    } else {
                        changingMarkRead[0] = true;
                        button.setChecked(false);
                        changingMarkRead[0] = false;
                        showToast("正在完成上次关闭清理，请稍后重试");
                    }
                } else {
                    changingMarkRead[0] = true;
                    button.setChecked(false);
                    changingMarkRead[0] = false;
                    requestNotificationAccess();
                }
            } else {
                SmsReadFeature.disableAndScheduleCleanup(this);
                showToast("自动已读联动已关闭");
            }
        });
        readCard.addView(markRead, matchWrap());
        readCard.addView(settingsInlineRow(
                "通知使用权",
                notificationAccess ? "系统已授权" : "尚未授权",
                MaterialIcons.md_notifications,
                false));
        Button manageNotificationAccess = secondaryButton(
                notificationAccess ? "管理或撤销系统授权" : "打开通知使用权设置");
        manageNotificationAccess.setOnClickListener(view -> openNotificationAccessSettings(false));
        readCard.addView(manageNotificationAccess, matchWrap());
        page.addView(readCard, cardParams());
        addNotice(
                "该功能默认关闭。开启后，雁笺只匹配默认短信应用在成功转发前后产生的通知，并只调用语义明确的“标记已读”动作。为兼容正文脱敏规则，原短信只保留最多 16 个规范化字符的 Keystore 加密匹配线索；不会把其他通知正文写入队列、日志或诊断报告。",
                page);

        page.addView(informationCard(
                MaterialCommunityIcons.mdi_email_outline,
                "直连邮箱",
                "短信由手机直接连接你配置的 SMTP 服务器发送，不经过雁笺自建中转服务器。"),
                cardParams());
        page.addView(informationCard(
                MaterialIcons.md_file_upload,
                "无密码迁移",
                "导出的配置不会包含 SMTP 授权码；换机导入后必须重新填写密码。"),
                cardParams());

        Button diagnostics = secondaryButton("生成脱敏诊断报告");
        diagnostics.setOnClickListener(view -> shareDiagnostics());
        page.addView(diagnostics, matchWrap());
        Button emailSettings = secondaryButton("检查邮箱与收件人配置");
        emailSettings.setOnClickListener(view -> showPage(PAGE_EMAIL));
        page.addView(emailSettings, matchWrap());
        addNotice("短信可能包含验证码、账户与个人信息。请只转发到你控制的邮箱，并为邮箱开启独立应用密码。", page);
    }

    private void showConfigTransferPage() {
        addSubPageTitle("配置迁移", "导出规则与服务器信息，授权码始终留在原设备", PAGE_SETTINGS);

        page.addView(informationCard(
                MaterialIcons.md_verified_user,
                "导出内容已最小化",
                "包含 SMTP 主机、端口、发件人与收件人、转发规则和守护偏好；不包含授权码与短信正文。"),
                cardParams());

        LinearLayout actions = groupedCard();
        actions.addView(settingsRow("导出无密码配置", "保存为 JSON 配置文件",
                MaterialIcons.md_file_upload, this::exportConfiguration));
        actions.addView(hairlineDivider());
        actions.addView(settingsRow("导入配置", "导入后重新填写 SMTP 授权码",
                MaterialIcons.md_file_download, this::importConfiguration));
        page.addView(actions, cardParams());

        LinearLayout steps = card();
        steps.addView(sectionHeader(MaterialIcons.md_swap_horiz, "换机顺序"));
        steps.addView(statusRow(MaterialIcons.md_file_upload, "1. 原设备导出", "生成无密码文件", true));
        steps.addView(statusRow(MaterialIcons.md_file_download, "2. 新设备导入", "恢复服务器与规则", true));
        steps.addView(statusRow(MaterialIcons.md_vpn_key, "3. 重新填写授权码", "再发送测试邮件", false));
        page.addView(steps, cardParams());
        addNotice("配置文件仍包含邮箱地址和过滤规则，请使用可信渠道传输并在导入后妥善删除。", page);
    }

    private void showPlatformCapabilitiesPage() {
        addSubPageTitle("平台能力说明", "同一设计语言下，不同系统仍受各自权限模型限制", PAGE_ABOUT);

        page.addView(informationCard(
                MaterialIcons.md_phone_android,
                "Android 与兼容 Android 应用的 HarmonyOS",
                "当前 APK 可在允许安装 Android 应用的系统上运行；实时读取短信需要系统授予短信权限。"),
                cardParams());
        page.addView(informationCard(
                MaterialCommunityIcons.mdi_shield_outline,
                "锁屏与长期后台",
                "电池优化、自启动、关联启动和锁屏网络由设备系统控制，App 只能引导设置并通过心跳与试投验证。"),
                cardParams());
        page.addView(informationCard(
                MaterialIcons.md_info_outline,
                "HarmonyOS NEXT",
                "HarmonyOS NEXT 不直接运行本 Android APK；需要单独的原生客户端与平台允许的短信能力，当前安装包不宣称支持。"),
                cardParams());
        page.addView(informationCard(
                MaterialCommunityIcons.mdi_email_outline,
                "邮件链路",
                "SMTP 直连不依赖第三方中转服务器，但仍受邮箱服务商授权策略、网络与投递延迟影响。"),
                cardParams());
    }

    private void showOpenSourceLicensesPage() {
        addSubPageTitle("开源许可", "项目协议、完整文本与第三方组件声明", PAGE_ABOUT);

        page.addView(informationCard(
                MaterialIcons.md_code,
                "雁笺 · Apache License 2.0",
                "雁笺源代码以 Apache-2.0 开放：可在遵守许可证、保留版权与 NOTICE 的前提下使用、修改与再发布。"),
                cardParams());

        LinearLayout dependencies = card();
        dependencies.addView(sectionHeader(MaterialIcons.md_apps, "主要第三方组件"));
        dependencies.addView(settingsInlineRow(
                "AndroidX Core", "Apache-2.0", MaterialIcons.md_extension, false));
        dependencies.addView(hairlineDivider());
        dependencies.addView(settingsInlineRow(
                "AndroidX WorkManager", "Apache-2.0", MaterialIcons.md_build, false));
        dependencies.addView(hairlineDivider());
        dependencies.addView(settingsInlineRow(
                "Android Iconify", "Apache-2.0", MaterialIcons.md_palette, false));
        dependencies.addView(hairlineDivider());
        dependencies.addView(settingsInlineRow(
                "RE2/J", "BSD-3-Clause", MaterialIcons.md_filter_list, false));
        dependencies.addView(hairlineDivider());
        dependencies.addView(settingsInlineRow(
                "Android Mail / Activation", "EPL-2.0 OR GPL-2.0+CPE",
                MaterialCommunityIcons.mdi_email_outline, false));
        page.addView(dependencies, cardParams());

        LinearLayout fullLicense = new LinearLayout(this);
        fullLicense.setOrientation(LinearLayout.VERTICAL);
        TextView licenseText = text(readBundledLicense(), 10.5f, COLOR_MUTED, false);
        licenseText.setTextIsSelectable(true);
        licenseText.setLineSpacing(dp(1), 1.06f);
        licenseText.setPadding(dp(3), dp(4), dp(3), dp(8));
        fullLicense.addView(licenseText, matchWrap());
        page.addView(collapsibleCard(
                MaterialIcons.md_description,
                "Apache-2.0 完整协议",
                "点按展开 · 已内置，可离线查看",
                fullLicense,
                false), cardParams());

        LinearLayout thirdParty = new LinearLayout(this);
        thirdParty.setOrientation(LinearLayout.VERTICAL);
        TextView thirdPartyText = text(readBundledText(
                "licenses/third-party-notices.txt",
                "无法读取内置第三方声明。请查看仓库 THIRD_PARTY_NOTICES.md。"),
                10.5f, COLOR_MUTED, false);
        thirdPartyText.setTextIsSelectable(true);
        thirdPartyText.setLineSpacing(dp(1), 1.06f);
        thirdPartyText.setPadding(dp(3), dp(4), dp(3), dp(8));
        thirdParty.addView(thirdPartyText, matchWrap());
        page.addView(collapsibleCard(
                MaterialIcons.md_receipt,
                "第三方声明",
                "版本、许可证与上游项目",
                thirdParty,
                false), cardParams());
        addNotice("第三方组件仍分别遵循其上游许可证；仓库根目录包含 LICENSE、NOTICE 与 THIRD_PARTY_NOTICES.md。", page);
    }

    private void showOnboardingPage() {
        addBrandHeader("退出向导", () -> showPage(PAGE_GUARDIAN));
        addStepProgress(3, 4);
        TextView onboardingTitle = text("允许系统唤醒雁笺", 31f, COLOR_INK, true);
        onboardingTitle.setTypeface(Typeface.SERIF, Typeface.BOLD);
        onboardingTitle.setPadding(dp(8), 0, 0, 0);
        page.addView(onboardingTitle);
        TextView onboardingSubtitle = text("能直接授权的项目会弹出系统确认；厂商启动管理需在系统页确认", 14f, COLOR_MUTED, false);
        onboardingSubtitle.setPadding(dp(8), dp(4), 0, dp(14));
        page.addView(onboardingSubtitle);
        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));

        LinearLayout path = card();
        path.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout pathRow = new LinearLayout(this);
        pathRow.setGravity(Gravity.CENTER);
        pathRow.addView(pathStep(MaterialIcons.md_settings, "设置"), weightedWrap());
        pathRow.addView(pathArrow());
        pathRow.addView(pathStep(MaterialIcons.md_apps, "应用和服务"), weightedWrap());
        pathRow.addView(pathArrow());
        pathRow.addView(pathStep(MaterialIcons.md_tune, "启动管理"), weightedWrap());
        pathRow.addView(pathArrow());
        pathRow.addView(pathBirdStep("雁笺"), weightedWrap());
        path.addView(pathRow, matchWrap());
        page.addView(path, cardParams());
        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        DeviceHealth health = DeviceHealth.inspect(this);
        boolean smsAllowed = visualTestMode || health.smsPermission;
        boolean batteryAllowed = visualTestMode || health.batteryExempt;
        boolean backgroundAllowed = visualTestMode || health.backgroundConfirmed;
        LinearLayout permissions = card();
        permissions.addView(actionStatusRow(MaterialCommunityIcons.mdi_message_text_outline,
                "接收短信", smsAllowed ? "已允许" : "点按授权", smsAllowed,
                () -> requestSmsPermission(false)));
        permissions.addView(hairlineDivider());
        permissions.addView(actionStatusRow(MaterialCommunityIcons.mdi_battery_charging,
                "忽略电池优化", batteryAllowed ? "已允许" : "点按授权", batteryAllowed,
                this::requestBatteryExemption));
        permissions.addView(hairlineDivider());
        permissions.addView(actionStatusRow(MaterialIcons.md_power_settings_new,
                "自启动 · 关联启动 · 后台活动",
                backgroundAllowed ? "已确认" : "点按设置",
                backgroundAllowed, this::openHuaweiLaunchSettings));
        page.addView(permissions, cardParams());

        Button launch = actionButton(backgroundAllowed ? "进入锁屏试投" : "完成下一项授权", COLOR_JADE);
        launch.setOnClickListener(view -> runNextPermissionStep());
        page.addView(launch, matchWrap());
        if (!backgroundAllowed) {
            Button done = secondaryButton("我已完成后台启动设置");
            done.setOnClickListener(view -> showVendorSettingsManualConfirmation());
            page.addView(done, matchWrap());
        }
        TextView delay = navigationRow("锁屏试投与延迟排查", "验证真实短信并检查休眠网络", MaterialIcons.md_help_outline);
        delay.setOnClickListener(view -> showPage(PAGE_LOCKSCREEN_TEST));
        page.addView(delay, cardParams());
    }

    private void showLockscreenTestPage() {
        addBrandHeader("退出向导", () -> showPage(PAGE_GUARDIAN));
        addStepProgress(4, 4);
        TextView title = text("完成锁屏试投", 31f, COLOR_INK, true);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        page.addView(title);
        TextView subtitle = text("用一条真实短信验证锁屏接收、入队与邮件投递", 14f, COLOR_MUTED, false);
        subtitle.setPadding(dp(8), dp(4), 0, dp(14));
        page.addView(subtitle);

        DeviceHealth health = DeviceHealth.inspect(this);
        boolean forwardingReady = visualTestMode || (health.forwardingEnabled && health.smtpVerified);
        boolean smsReceived = visualTestMode || health.lastSmsReceivedAt > 0L;
        boolean smsForwarded = visualTestMode || health.lastSmsForwardedAt > 0L;

        LinearLayout steps = card();
        steps.addView(sectionHeader(MaterialIcons.md_verified_user, "试投步骤"));
        steps.addView(statusRow(MaterialCommunityIcons.mdi_email_outline,
                "1. 自动转发与邮箱", forwardingReady ? "已就绪" : "待完成", forwardingReady));
        steps.addView(hairlineDivider());
        steps.addView(statusRow(MaterialCommunityIcons.mdi_lock_outline,
                "2. 锁屏后发送真实短信", smsReceived ? "已接收" : "等待短信", smsReceived));
        steps.addView(hairlineDivider());
        steps.addView(statusRow(MaterialCommunityIcons.mdi_send,
                "3. 邮箱收到转发", smsForwarded ? "闭环通过" : "等待投递", smsForwarded));
        page.addView(steps, cardParams());

        LinearLayout live = card();
        live.addView(sectionHeader(MaterialCommunityIcons.mdi_pulse, "实时状态"));
        live.addView(settingsInlineRow("当前网络", health.networkLabel,
                MaterialCommunityIcons.mdi_wifi, false));
        live.addView(hairlineDivider());
        String receivedAt = visualTestMode ? "今天 11:38"
                : health.lastSmsReceivedAt <= 0L ? "尚无记录"
                : new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(health.lastSmsReceivedAt));
        live.addView(settingsInlineRow("最近真实短信", receivedAt,
                MaterialCommunityIcons.mdi_message_text_outline, false));
        page.addView(live, cardParams());

        Button refresh = actionButton(smsForwarded ? "试投已通过，返回旅行守护" : "刷新试投结果", COLOR_JADE);
        refresh.setOnClickListener(view -> showPage(smsForwarded ? PAGE_GUARDIAN : PAGE_LOCKSCREEN_TEST));
        page.addView(refresh, matchWrap());
        Button email = secondaryButton("先发送一封测试邮件");
        email.setOnClickListener(view -> testConfiguredProfile(false));
        page.addView(email, matchWrap());
        addNotice("测试邮件只能验证 SMTP。离家前仍需锁屏并从另一号码发送一条真实短信；雁笺检测到成功转发后会自动标记闭环通过。", page);
    }

    private void showMaintenancePage() {
        addMaintenanceHeader();
        DeviceHealth health = DeviceHealth.inspect(this);
        LinearLayout status = card();
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout stateOrb = new LinearLayout(this);
        stateOrb.setGravity(Gravity.CENTER);
        stateOrb.setBackground(roundStroke(Color.rgb(234, 244, 241), Color.WHITE, 28));
        ImageView stateIcon = new ImageView(this);
        stateIcon.setImageDrawable(icon(MaterialIcons.md_verified_user, COLOR_JADE, 31));
        stateOrb.addView(stateIcon, new LinearLayout.LayoutParams(dp(37), dp(37)));
        status.addView(stateOrb, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout stateCopy = new LinearLayout(this);
        stateCopy.setOrientation(LinearLayout.VERTICAL);
        stateCopy.setPadding(dp(12), 0, dp(8), 0);
        stateCopy.addView(text("运行状态", 15f, COLOR_INK, true));
        stateCopy.addView(text(visualTestMode || health.readyForTravel() ? "良好" : "仍有项目待完成", 20f,
                visualTestMode || health.readyForTravel() ? COLOR_JADE_DARK : COLOR_AMBER, true));
        status.addView(stateCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        status.addView(statDivider());
        status.addView(statColumn("短信", Integer.toString(health.pendingStats.sms)));
        status.addView(statDivider());
        String recentSuccess = visualTestMode ? "11:40"
                : health.lastSuccessAt == 0L ? "尚无"
                : new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(health.lastSuccessAt));
        status.addView(statColumn("最近心跳", recentSuccess));
        status.addView(statDivider());
        status.addView(statColumn("SMTP", visualTestMode ? "已验证" : health.smtpLabel));
        page.addView(status, cardParams());

        Button diagnostics = secondaryButton("查看完整诊断");
        diagnostics.setCompoundDrawablesWithIntrinsicBounds(
                icon(MaterialIcons.md_graphic_eq, COLOR_JADE, 19), null, null, null);
        diagnostics.setCompoundDrawablePadding(dp(6));
        diagnostics.setOnClickListener(view -> shareDiagnostics());
        LinearLayout.LayoutParams diagnosticParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        diagnosticParams.gravity = Gravity.END;
        diagnosticParams.setMargins(0, 0, 0, dp(6));
        page.addView(diagnostics, diagnosticParams);

        page.addView(settingsSectionLabelWithIcon("隐私与清理", MaterialIcons.md_verified_user));
        LinearLayout privacy = groupedCard();
        privacy.addView(settingsRow("分享脱敏诊断报告", "不包含授权码与短信正文",
                MaterialIcons.md_share, this::shareDiagnostics));
        privacy.addView(hairlineDivider());
        privacy.addView(settingsRow(
                "清空本机待发送队列",
                "共 " + health.pendingCount + " 条 · " + health.pendingStats.compactLabel(),
                MaterialIcons.md_delete, this::confirmClearQueue));
        page.addView(privacy, cardParams());

        page.addView(settingsSectionLabelWithIcon("版本与更新", MaterialIcons.md_refresh));
        LinearLayout updates = groupedCard();
        View versionRow = settingsInlineRow(
                "当前版本", BuildConfig.VERSION_NAME, MaterialIcons.md_info_outline, false);
        updates.addView(versionRow);
        updates.addView(hairlineDivider());
        LinearLayout channelRow = new LinearLayout(this);
        channelRow.setGravity(Gravity.CENTER_VERTICAL);
        channelRow.setPadding(dp(8), dp(5), dp(2), dp(5));
        channelRow.addView(text("更新通道", 13f, COLOR_INK, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Spinner updateChannel = createSpinner(new String[]{"稳定版（推荐）", "Beta 测试版"});
        updateChannel.setSelection(UpdateChecker.CHANNEL_BETA.equals(UpdateChecker.channel(this)) ? 1 : 0);
        updateChannel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                UpdateChecker.setChannel(MainActivity.this,
                        position == 1 ? UpdateChecker.CHANNEL_BETA : UpdateChecker.CHANNEL_STABLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        channelRow.addView(updateChannel, new LinearLayout.LayoutParams(
                dp(154), dp(MIN_TOUCH_DP)));
        updates.addView(channelRow);
        updates.addView(hairlineDivider());
        Switch automaticUpdates = new Switch(this);
        automaticUpdates.setMinHeight(dp(MIN_TOUCH_DP));
        automaticUpdates.setMinWidth(dp(MIN_TOUCH_DP));
        automaticUpdates.setText("每天最多自动检查一次所选通道");
        automaticUpdates.setTextColor(COLOR_INK);
        automaticUpdates.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * TEXT_SCALE);
        automaticUpdates.setPadding(dp(8), dp(6), dp(2), dp(6));
        automaticUpdates.setChecked(visualTestMode || UpdateChecker.isAutomaticEnabled(this));
        automaticUpdates.setOnCheckedChangeListener(
                (button, checked) -> UpdateChecker.setAutomaticEnabled(this, checked));
        updates.addView(automaticUpdates);
        updates.addView(hairlineDivider());
        updates.addView(settingsInlineRow(
                "应用内更新", "下载并校验后由系统确认安装", MaterialIcons.md_system_update, false));
        updates.addView(hairlineDivider());
        View checkUpdate = settingsInlineRow(
                "立即检查更新", "", MaterialIcons.md_refresh, true);
        checkUpdate.setOnClickListener(view -> checkForUpdates(true));
        updates.addView(checkUpdate);
        TextView releasePrivacy = text("只访问项目公开 GitHub Releases，不上传设备信息", 10.5f, COLOR_MUTED, false);
        releasePrivacy.setGravity(Gravity.CENTER);
        releasePrivacy.setCompoundDrawablePadding(dp(6));
        releasePrivacy.setCompoundDrawablesWithIntrinsicBounds(
                icon(MaterialIcons.md_verified_user, COLOR_JADE, 16), null, null, null);
        releasePrivacy.setPadding(dp(4), dp(4), dp(4), dp(4));
        updates.addView(releasePrivacy, matchWrap());
        page.addView(updates, cardParams());

        addNotice("强制停止、关机、重启后尚未首次解锁、SIM 失去服务或 SMTP 服务商延迟都超出普通 App 的控制范围。", page);
    }

    private void requestEnableForwarding() {
        AppConfig config = AppConfig.load(this);
        String error = config.validateForForwarding();
        if (error != null) {
            showGlassDialog(
                    "邮箱配置尚未完成",
                    error + "\n\n完成邮箱配置并发送测试邮件后，才能启用自动转发。",
                    "前往邮箱配置", () -> showPage(PAGE_EMAIL), "稍后再说");
            return;
        }
        String ruleError = RuleConfig.load(this).validate();
        if (ruleError != null) {
            showGlassDialog(
                    "转发规则需要修正",
                    ruleError,
                    "前往转发规则", () -> showPage(PAGE_RULES), "稍后再说");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            enableAfterPermission = true;
            requestSmsPermission(true);
            return;
        }
        AppConfig.setEnabled(this, true);
        AppConfig.setStatus(this, "自动转发已启用，等待新短信");
        if (QueueDatabase.get(this).count() > 0) {
            ForwardScheduler.scheduleFromQueue(this);
        }
        showPage(PAGE_GUARDIAN);
    }

    private void toggleTravelGuard() {
        if (TravelGuard.isEnabled(this)) {
            TravelGuard.setEnabled(this, false);
            showToast("旅行守护已关闭，自动转发仍保持原状态");
            showPage(PAGE_GUARDIAN);
            return;
        }
        DeviceHealth health = DeviceHealth.inspect(this);
        if (!health.readyForTravel()) {
            String blockers = "• " + TextUtils.join("\n• ", health.travelBlockers());
            showGlassDialog(
                    "旅行守护尚未就绪",
                    "请先完成：\n" + blockers + "\n\n网络是实时状态，不会伪装成永久授权。完成后再开启守护。",
                    "处理第一项",
                    () -> openFirstTravelBlocker(health),
                    "稍后再说");
            return;
        }
        TravelGuard.setEnabled(this, true);
        TravelGuard.enqueueHeartbeatNow(this, "旅行守护已开启", false);
        showToast("旅行守护已开启，并已发送首封心跳");
        showPage(PAGE_GUARDIAN);
    }

    private boolean saveEmailConfiguration() {
        SmtpProfile primary = profileFromFields(
                "主通道", primaryHost, primaryPort, primarySecurity,
                primaryUsername, primaryPassword, primaryFrom, primaryRecipients);
        if (primary == null) {
            return false;
        }
        String error = primary.validate();
        if (error != null) {
            showToast(error);
            return false;
        }
        SmtpProfile backup = profileFromFields(
                "备用通道", backupHost, backupPort, backupSecurity,
                backupUsername, backupPassword, backupFrom, backupRecipients);
        if (backupEnabled.isChecked()) {
            if (backup == null) {
                return false;
            }
            error = backup.validate();
            if (error != null) {
                showToast(error);
                return false;
            }
        }
        if (!privacyConsent.isChecked()) {
            showToast("请先确认短信隐私转发风险");
            return false;
        }
        if (backup == null) {
            backup = new SmtpProfile("备用通道", "", 465, AppConfig.SECURITY_SSL_TLS, "", "", "", "");
        }
        AppConfig old = AppConfig.load(this);
        AppConfig.save(
                this,
                primary.host, primary.port, primary.security, primary.username, primary.password,
                primary.fromAddress, primary.recipientsText,
                backupEnabled.isChecked(),
                backup.host, backup.port, backup.security, backup.username, backup.password,
                backup.fromAddress, backup.recipientsText,
                strategyValue(dispatchStrategy.getSelectedItemPosition()),
                old.senderAllowlist, old.skipOtp, true, old.enabled);
        AppConfig.resetSmtpVerification(this);
        showToast("邮箱配置已保存");
        return true;
    }

    private void testConfiguredProfile(boolean backup) {
        AppConfig config = AppConfig.load(this);
        String error = config.validateForForwarding();
        if (error != null) {
            showGlassDialog(
                    "邮箱尚未就绪",
                    error + "\n\n请先保存邮箱配置，再发送测试邮件。",
                    "前往邮箱配置", () -> showPage(PAGE_EMAIL), "稍后再说");
            return;
        }
        testProfile(backup ? config.backupProfile() : config.primaryProfile(), backup ? "备用通道" : "主通道");
    }

    private void testProfile(SmtpProfile profile, String label) {
        showToast("正在安全测试" + label + "…");
        executor.execute(() -> {
            String result;
            boolean success;
            try {
                QueueItem item = new QueueItem(
                        java.util.UUID.randomUUID().toString(),
                        QueueItem.KIND_TEST,
                        System.currentTimeMillis(),
                        "设备自检",
                        "雁笺 SMTP 自检成功。请继续完成一条真实短信和锁屏测试。",
                        -1,
                        0);
                SmtpMailer.send(profile, item);
                result = label + "测试邮件已被 SMTP 服务器接受";
                success = true;
            } catch (MessagingException | RuntimeException error) {
                result = label + "测试失败：" + SmtpFailure.describeForRecord(error)
                        + " · " + NetworkState.diagnosticSummary(this);
                success = false;
            }
            if (success) {
                AppConfig.setSuccess(this, result);
                int released = ForwardScheduler.retryAllNow(this);
                if (released > 0) {
                    result += "，已安排补发 " + released + " 条历史待发消息";
                }
            } else {
                AppConfig.setSmtpFailure(this, result);
            }
            String message = result;
            runOnUiThread(() -> {
                showToast(message);
                if (currentPage == PAGE_GUARDIAN || currentPage == PAGE_EMAIL) {
                    showPage(currentPage);
                }
            });
        });
    }

    private boolean saveRules() {
        int start = parseTime(scheduleStart.getText().toString());
        int end = parseTime(scheduleEnd.getText().toString());
        if (start < 0 || end < 0) {
            showToast("生效时段必须使用 HH:mm 格式");
            return false;
        }
        int dayMask = 0;
        for (int i = 0; i < weekdays.length; i++) {
            if (weekdays[i].isChecked()) {
                dayMask |= 1 << i;
            }
        }
        RuleConfig config = new RuleConfig(
                ruleModeValue(ruleMode.getSelectedItemPosition()),
                senderAllow.getText().toString(),
                senderBlock.getText().toString(),
                bodyInclude.getText().toString(),
                bodyExclude.getText().toString(),
                bodyRegex.getText().toString(),
                includeAll.isChecked(),
                simRule.getSelectedItemPosition() - 1,
                scheduleEnabled.isChecked(),
                start,
                end,
                dayMask,
                contentModeValue(contentMode.getSelectedItemPosition()));
        String error = config.validate();
        if (error != null) {
            showToast(error);
            return false;
        }
        RuleConfig.save(this, config);
        showToast("转发规则已保存");
        return true;
    }

    private void previewRules() {
        if (!saveRules()) {
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(20), 0, dp(20), 0);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText sender = input(form, "示例发送方", InputType.TYPE_CLASS_TEXT);
        EditText body = input(form, "示例短信正文", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        sender.setText("10086");
        body.setText("您的验证码是 123456，5 分钟内有效");
        showGlassDialog("规则测试", form, "测试", () -> {
                    RuleConfig config = RuleConfig.load(this);
                    MessageFilter.Decision decision = MessageFilter.decide(
                            sender.getText().toString(),
                            body.getText().toString(),
                            simRule.getSelectedItemPosition() - 1,
                            System.currentTimeMillis(),
                            config);
                    showGlassDialog(
                            decision == MessageFilter.Decision.FORWARD ? "会转发" : "不会转发",
                            decision == MessageFilter.Decision.FORWARD
                                    ? "邮件正文预览：\n\n" + MessageFilter.transformBody(body.getText().toString(), config)
                                    : "命中结果：" + decision.name(),
                            "知道了", null, null);
                }, "取消");
    }

    private void confirmClearQueue() {
        int count = QueueDatabase.get(this).count();
        if (count == 0) {
            showToast("当前没有待发送消息");
            return;
        }
        showGlassDialog(
                "清空待发送队列？",
                "将删除 " + count + " 条尚未成功发送的加密短信、心跳或提醒，删除后无法恢复。",
                "确认删除", () -> {
                    try {
                        QueueDatabase.get(this).clear();
                        AppConfig.setStatus(this, "待发送队列已由用户清空");
                        showPage(currentPage);
                    } catch (RuntimeException error) {
                        AppConfig.setStatus(this, "安全状态更新失败，本次未清空待发送队列");
                        showGlassDialog(
                                "暂时无法安全清空",
                                "已读联动状态未能安全更新，本次没有删除待发送数据。请稍后重试。",
                                "知道了", null, null);
                    }
                }, "取消");
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        if (openIntentSafely(intent, null)) return;
        if (openIntentSafely(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), null)) return;
        if (openIntentSafely(new Intent(Settings.ACTION_SETTINGS), null)) return;
        showGlassDialog(
                "无法打开系统设置",
                "当前系统没有向第三方 App 提供可用的设置入口。请手动打开“设置”，搜索“雁笺”进入应用详情。",
                "知道了", null, null);
    }

    private void requestNotificationAccess() {
        showGlassDialog(
                "允许自动标记系统短信已读？",
                "系统授予的是设备级“通知使用权”，范围可能包含其他应用通知。雁笺只会在真实短信被 SMTP 接受后，检查默认短信应用的通知并调用其“标记已读”动作；为兼容正文脱敏规则，原短信只保留最多 16 个规范化字符的 Keystore 加密匹配线索，不会保存其他通知正文。该能力默认关闭，也可以随时在系统设置中撤销。",
                "前往系统授权", () -> openNotificationAccessSettings(true), "暂不开启");
    }

    private void openNotificationAccessSettings(boolean enabling) {
        Intent detail;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            detail.putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    SmsReadFeature.listenerComponent(this).flattenToString());
        } else {
            detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        awaitingNotificationAccess = enabling;
        if (openIntentSafely(detail, null)) {
            return;
        }
        Intent list = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        if (openIntentSafely(list, null)) {
            return;
        }
        awaitingNotificationAccess = false;
        showGlassDialog(
                "无法打开通知使用权设置",
                "当前系统没有提供可调用的通知使用权页面。自动标记已读保持关闭，不影响短信转发。",
                "知道了", null, null);
    }

    private void openBackgroundDataSettings() {
        Intent perApp = new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS);
        perApp.setData(Uri.parse("package:" + getPackageName()));
        if (openIntentSafely(perApp, null)) return;
        if (openIntentSafely(new Intent(Settings.ACTION_DATA_USAGE_SETTINGS), null)) return;
        openAppSettings();
    }

    private void showNetworkStatus(DeviceHealth health) {
        if (health.backgroundDataRestricted) {
            showGlassDialog(
                    "移动网络后台受限",
                    "系统省流量策略正在限制雁笺使用计费网络。请允许后台数据，或把雁笺加入“不受数据用量限制的应用”。",
                    "打开流量设置", this::openBackgroundDataSettings, "稍后处理");
            return;
        }
        if (!health.connected) {
            showGlassDialog(
                    "当前网络不可用",
                    health.networkLabel + "。请检查移动数据、WLAN、VPN 或私人 DNS；网络恢复后待发消息会自动补发。",
                    "打开流量设置", this::openBackgroundDataSettings, "稍后处理");
            return;
        }
        showGlassDialog(
                "网络状态正常",
                health.networkLabel + "。网络连接属于实时状态，离家前仍建议完成一次锁屏真实短信试投。",
                "知道了", null, null);
    }

    private void requestSmsPermission(boolean enableForwarding) {
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            if (enableForwarding) requestEnableForwarding();
            else showToast("接收短信权限已经允许");
            return;
        }
        enableAfterPermission = enableForwarding;
        requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, REQUEST_RECEIVE_SMS);
    }

    @SuppressLint("BatteryLife")
    private void requestBatteryExemption() {
        DeviceHealth health = DeviceHealth.inspect(this);
        if (health.batteryExempt) {
            showToast("忽略电池优化已经允许");
            showPage(currentPage);
            return;
        }
        Intent direct = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        if (openIntentSafely(direct, null)) return;
        Intent list = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        if (openIntentSafely(list, null)) return;
        openAppSettings();
    }

    private void runNextPermissionStep() {
        if (visualTestMode) {
            showPage(PAGE_LOCKSCREEN_TEST);
            return;
        }
        DeviceHealth health = DeviceHealth.inspect(this);
        if (!health.smsPermission) {
            requestSmsPermission(false);
        } else if (!health.batteryExempt) {
            requestBatteryExemption();
        } else if (!health.backgroundConfirmed) {
            openHuaweiLaunchSettings();
        } else {
            showPage(PAGE_LOCKSCREEN_TEST);
        }
    }

    private void openFirstTravelBlocker(DeviceHealth health) {
        if (!health.smsPermission) {
            requestSmsPermission(false);
        } else if (!health.forwardingEnabled) {
            requestEnableForwarding();
        } else if (!health.smtpVerified) {
            showPage(PAGE_EMAIL);
        } else if (!health.batteryExempt) {
            requestBatteryExemption();
        } else if (!health.backgroundConfirmed) {
            openHuaweiLaunchSettings();
        } else {
            showPage(PAGE_GUARDIAN);
            showToast("请发送一条真实短信，确认锁屏状态下能收到转发邮件");
        }
    }

    private void checkForUpdates(boolean manual) {
        checkForUpdates(manual, false);
    }

    private void checkForUpdates(boolean manual, boolean forceShow) {
        if (!manual && !UpdateChecker.beginAutomaticCheck(this, System.currentTimeMillis())) {
            return;
        }
        if (manual) {
            showToast("正在检查 GitHub "
                    + (UpdateChecker.CHANNEL_BETA.equals(UpdateChecker.channel(this)) ? "Beta 与正式版本…" : "正式版本…"));
        }
        executor.execute(() -> {
            UpdateChecker.ReleaseInfo release = null;
            String errorMessage = null;
            try {
                release = UpdateChecker.fetchLatest(this);
            } catch (Exception error) {
                errorMessage = ForwardProcessor.safeMessage(error);
            }
            UpdateChecker.ReleaseInfo result = release;
            String failure = errorMessage;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (failure != null) {
                    if (manual) {
                        showToast("检查更新失败：" + failure);
                    }
                    return;
                }
                if (result == null) {
                    if (manual) {
                        showToast("所选更新通道暂无可用版本");
                    }
                    return;
                }
                if (!forceShow && !UpdateChecker.isNewer(result.version, BuildConfig.VERSION_NAME)) {
                    if (manual) {
                        showToast("当前已经是所选通道的最新版本");
                    }
                    return;
                }
                showUpdateDialog(result);
            });
        });
    }

    private void verifyAndInstallCachedVisualUpdate() {
        String version = UpdateChecker.normalizeVersion(
                getIntent().getStringExtra("visual_test_update_version"));
        File apk = AppUpdater.downloadedApk(this);
        if (version.isEmpty() || !apk.isFile()) {
            showToast("视觉测试更新文件或版本号缺失");
            return;
        }
        UpdateChecker.ReleaseInfo release = new UpdateChecker.ReleaseInfo(
                version, "雁笺视觉测试更新", "", "", true,
                apk.getName(), "", "", apk.length());
        executor.execute(() -> {
            try {
                AppUpdater.verifyPackage(this, apk, release);
                pendingUpdateApk = apk;
                runOnUiThread(this::requestInstallPermissionOrInstall);
            } catch (Exception error) {
                String message = ForwardProcessor.safeMessage(error);
                runOnUiThread(() -> showGlassDialog(
                        "测试更新校验失败", message, "知道了", null, null));
            }
        });
    }

    private void showUpdateDialog(UpdateChecker.ReleaseInfo release) {
        String notes = release.notes.isEmpty() ? "请前往项目官方发布页查看更新内容。" : release.notes;
        if (!release.hasDownload()) {
            showGlassDialog(
                    "发现雁笺 " + release.version + (release.prerelease ? " Beta" : ""),
                    notes + "\n\n该发布缺少可验证的 APK 下载信息，将打开项目官方 GitHub Release。",
                    "查看发布页",
                    () -> openIntentSafely(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse(release.releaseUrl)),
                            "无法打开浏览器，请稍后重试"),
                    "稍后");
            return;
        }
        showGlassDialog(
                "发现雁笺 " + release.version + (release.prerelease ? " Beta" : ""),
                notes + "\n\n雁笺将在应用内下载 APK，并校验大小、SHA-256、包名、版本号和正式签名。校验通过后由系统确认覆盖安装，本机配置会保留。",
                "下载并更新",
                () -> beginUpdateDownload(release),
                "稍后");
    }

    private void beginUpdateDownload(UpdateChecker.ReleaseInfo release) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView status = text("正在连接 GitHub 安全下载通道…", 14f, COLOR_INK, true);
        status.setPadding(dp(2), dp(2), dp(2), dp(10));
        content.addView(status, matchWrap());

        ProgressBar progress = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0);
        progress.setProgressTintList(ColorStateList.valueOf(COLOR_JADE_DARK));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(COLOR_JADE_SOFT));
        content.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));

        TextView detail = text("0% · 下载后自动执行五项安全校验", 11.5f, COLOR_MUTED, false);
        detail.setPadding(dp(2), dp(10), dp(2), dp(2));
        detail.setCompoundDrawablePadding(dp(7));
        detail.setCompoundDrawablesWithIntrinsicBounds(
                icon(MaterialCommunityIcons.mdi_shield_outline, COLOR_JADE_DARK, 18),
                null, null, null);
        content.addView(detail, matchWrap());

        Dialog dialog = showGlassDialog(
                "正在下载雁笺 " + release.version,
                content,
                "取消下载",
                () -> cancelled.set(true),
                null);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(ignored -> cancelled.set(true));

        executor.execute(() -> {
            try {
                int[] lastProgress = {-1};
                File apk = AppUpdater.download(this, release, new AppUpdater.ProgressListener() {
                    @Override
                    public void onProgress(long downloaded, long total) {
                        int value = total <= 0L ? 0 : (int) Math.min(1000L,
                                downloaded * 1000L / total);
                        if (value == lastProgress[0]) return;
                        lastProgress[0] = value;
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                progress.setProgress(value, MotionEffects.enabled(MainActivity.this));
                            } else {
                                progress.setProgress(value);
                            }
                            int percent = value / 10;
                            status.setText(percent < 100 ? "正在下载更新…" : "正在验证更新安全性…");
                            detail.setText(percent + "% · " + humanBytes(downloaded)
                                    + " / " + humanBytes(total));
                        });
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }
                });
                AppUpdater.verifyPackage(this, apk, release);
                pendingUpdateApk = apk;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    dialog.dismiss();
                    requestInstallPermissionOrInstall();
                });
            } catch (Exception error) {
                String message = ForwardProcessor.safeMessage(error);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    dialog.dismiss();
                    if (!cancelled.get()) {
                        showGlassDialog(
                                "更新未能完成",
                                message + "\n\n没有执行安装，现有版本和本机配置均未改变。",
                                "重试", () -> beginUpdateDownload(release), "稍后");
                    }
                });
            }
        });
    }

    private void requestInstallPermissionOrInstall() {
        if (pendingUpdateApk == null || !pendingUpdateApk.isFile()) {
            showToast("更新文件已失效，请重新检查更新");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            showGlassDialog(
                    "允许雁笺安装更新",
                    "系统要求你首次允许“来自此来源的应用”。雁笺只会安装已通过哈希、包名和正式签名校验的自身更新；授权后返回即可继续。",
                    "打开系统授权", this::openInstallSourceSettings, "稍后");
            return;
        }
        launchSystemInstaller();
    }

    private void openInstallSourceSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            launchSystemInstaller();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
        try {
            awaitingInstallPermission = true;
            startActivityForResult(intent, REQUEST_INSTALL_SOURCE);
        } catch (ActivityNotFoundException | SecurityException error) {
            awaitingInstallPermission = false;
            showGlassDialog(
                    "无法打开安装授权",
                    "请手动打开“设置 → 安全 → 安装外部来源应用 → 雁笺”，允许安装应用后返回重试。",
                    "打开应用设置", this::openAppSettings, "稍后");
        }
    }

    private void handleInstallPermissionReturn() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls()) {
            launchSystemInstaller();
            return;
        }
        showGlassDialog(
                "安装授权尚未开启",
                "系统仍未允许雁笺发起覆盖安装。更新文件已经安全保存在本机缓存中，你可以再次授权或稍后重新检查更新。",
                "再次授权", this::openInstallSourceSettings, "稍后");
    }

    private void launchSystemInstaller() {
        File apk = pendingUpdateApk;
        if (apk == null || !apk.isFile()) {
            showToast("更新文件已失效，请重新检查更新");
            return;
        }
        Uri contentUri;
        try {
            contentUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".updates", apk);
        } catch (IllegalArgumentException error) {
            showGlassDialog("无法准备安装", "更新文件不在受信任的应用缓存目录中。",
                    "知道了", null, null);
            return;
        }

        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.setData(contentUri);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        try {
            startActivityForResult(install, REQUEST_INSTALL_APK);
        } catch (ActivityNotFoundException | SecurityException error) {
            Intent fallback = new Intent(Intent.ACTION_VIEW);
            fallback.setDataAndType(contentUri, "application/vnd.android.package-archive");
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!openIntentSafely(fallback, null)) {
                showGlassDialog(
                        "系统安装器不可用",
                        "当前系统没有提供可用的 APK 安装器。现有版本和本机配置未发生改变。",
                        "知道了", null, null);
            }
        }
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(
                Locale.CHINA, "%.1f KB", bytes / 1024d);
        return String.format(Locale.CHINA, "%.1f MB", bytes / (1024d * 1024d));
    }

    private void shareDiagnostics() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "雁笺脱敏诊断报告");
        intent.putExtra(Intent.EXTRA_TEXT, DiagnosticReport.create(this));
        startActivity(Intent.createChooser(intent, "分享诊断报告"));
    }

    private void exportConfiguration() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "yanjian-config-" + new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date()) + ".json");
        startActivityForResult(intent, REQUEST_EXPORT_CONFIG);
    }

    private void importConfiguration() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_CONFIG);
    }

    private void writeText(Uri uri, String value) throws Exception {
        ContentResolver resolver = getContentResolver();
        try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new IllegalStateException("无法打开导出文件");
            }
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readText(Uri uri) throws Exception {
        ContentResolver resolver = getContentResolver();
        try (InputStream input = resolver.openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IllegalStateException("无法打开配置文件");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > 1_048_576) {
                    throw new IllegalArgumentException("配置文件超过 1 MiB 上限");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String readBundledLicense() {
        return readBundledText(
                "licenses/apache-2.0.txt",
                "无法读取内置许可证文本。请查看仓库根目录 LICENSE。");
    }

    private String readBundledText(String assetPath, String fallback) {
        try (InputStream input = getAssets().open(assetPath);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            return fallback;
        }
    }

    private void openHuaweiLaunchSettings() {
        Intent[] candidates = new Intent[]{
                new Intent().setComponent(new ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                new Intent().setComponent(new ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
                new Intent().setComponent(new ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"))
        };
        for (Intent candidate : candidates) {
            if (openVendorSettingsIntent(candidate,
                    "请允许雁笺自动启动、关联启动和后台活动；返回后会自动确认")) {
                return;
            }
        }

        // Huawei does not publish a stable third-party Intent for this page.
        // On versions that hide or rename the internal activity, application
        // details is the only portable per-app destination. Start it directly:
        // startActivity() is not subject to the package-visibility query result.
        Intent appDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + getPackageName()));
        if (openVendorSettingsIntent(appDetails,
                "请进入“耗电详情/应用启动管理”允许后台启动；返回后会自动确认")) {
            return;
        }
        if (openVendorSettingsIntent(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                "请搜索“应用启动管理”并选择雁笺；返回后会自动确认")
                || openVendorSettingsIntent(new Intent(Settings.ACTION_SETTINGS),
                "请搜索“应用启动管理”并选择雁笺；返回后会自动确认")) {
            return;
        }
        showGlassDialog(
                "无法打开系统设置",
                "鸿蒙没有向当前 App 暴露可用的设置页面。请手动打开“设置 → 应用和服务 → 应用启动管理 → 雁笺”。",
                "知道了", null, null);
    }

    private boolean openVendorSettingsIntent(Intent intent, String guidance) {
        awaitingVendorSettingsReturn = true;
        if (openIntentSafely(intent, null)) {
            showToast(guidance);
            return true;
        }
        awaitingVendorSettingsReturn = false;
        return false;
    }

    private void showVendorSettingsReturnDialog() {
        showGlassDialog(
                "后台启动设置完成了吗？",
                "请确认雁笺的自动启动、关联启动和后台活动均已允许。鸿蒙不会把这三个厂商开关的状态返回给普通 App，因此需要你确认一次。",
                "已全部允许",
                () -> {
                    TravelGuard.setBackgroundConfirmed(this, true);
                    showPage(PAGE_SYSTEM_GUARDIAN);
                    showToast("厂商后台启动设置已确认");
                },
                "继续设置",
                this::openHuaweiLaunchSettings);
    }

    private void showVendorSettingsManualConfirmation() {
        showGlassDialog(
                "确认厂商后台设置",
                "仅当你已在系统的“应用启动管理”中允许雁笺自动启动、关联启动和后台活动时再确认。这个确认不会代替系统开关。",
                "我已全部允许",
                () -> {
                    TravelGuard.setBackgroundConfirmed(this, true);
                    showPage(PAGE_SYSTEM_GUARDIAN);
                },
                "继续检查",
                this::openHuaweiLaunchSettings);
    }

    private boolean openIntentSafely(Intent intent, String failureMessage) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            if (failureMessage != null) showToast(failureMessage);
            return false;
        }
    }

    private void addProviderPreset(LinearLayout parent, boolean primary) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView providerIcon = new ImageView(this);
        providerIcon.setImageDrawable(icon(MaterialCommunityIcons.mdi_email_outline, COLOR_JADE, 22));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        iconParams.setMargins(0, 0, dp(7), 0);
        row.addView(providerIcon, iconParams);
        Spinner provider = createSpinner(new String[]{"QQ 邮箱", "飞书邮箱", "163/126 邮箱", "Gmail", "Outlook", "iCloud", "自定义"});
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(
                0, dp(MIN_TOUCH_DP), 1f);
        providerParams.setMargins(0, 0, dp(8), 0);
        row.addView(provider, providerParams);
        Button apply = secondaryButton("套用预设");
        apply.setMinHeight(dp(MIN_TOUCH_DP));
        apply.setOnClickListener(view -> applyProviderPreset(provider.getSelectedItemPosition(), primary));
        row.addView(apply, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(MIN_TOUCH_DP)));
        parent.addView(row, matchWrap());
    }

    private void applyProviderPreset(int index, boolean primary) {
        String host;
        int port;
        int security;
        switch (index) {
            case 1:
                host = "smtp.feishu.cn";
                port = 465;
                security = 0;
                break;
            case 2:
                host = "smtp.163.com";
                port = 465;
                security = 0;
                break;
            case 3:
                host = "smtp.gmail.com";
                port = 465;
                security = 0;
                break;
            case 4:
                host = "smtp-mail.outlook.com";
                port = 587;
                security = 1;
                break;
            case 5:
                host = "smtp.mail.me.com";
                port = 587;
                security = 1;
                break;
            case 6:
                return;
            default:
                host = "smtp.qq.com";
                port = 465;
                security = 0;
                break;
        }
        EditText hostView = primary ? primaryHost : backupHost;
        EditText portView = primary ? primaryPort : backupPort;
        Spinner securityView = primary ? primarySecurity : backupSecurity;
        if (hostView != null) {
            hostView.setText(host);
            portView.setText(Integer.toString(port));
            securityView.setSelection(security);
        }
    }

    private SmtpProfile profileFromFields(
            String name,
            EditText host,
            EditText port,
            Spinner security,
            EditText username,
            EditText password,
            EditText from,
            EditText recipients) {
        int portValue;
        try {
            portValue = Integer.parseInt(port.getText().toString().trim());
        } catch (NumberFormatException error) {
            showToast(name + " SMTP 端口必须是数字");
            return null;
        }
        return new SmtpProfile(
                name,
                host.getText().toString().trim(),
                portValue,
                security.getSelectedItemPosition() == 1 ? AppConfig.SECURITY_STARTTLS : AppConfig.SECURITY_SSL_TLS,
                username.getText().toString().trim(),
                password.getText().toString(),
                from.getText().toString().trim(),
                recipients.getText().toString().trim());
    }

    private void fillProfile(
            SmtpProfile profile,
            EditText host,
            EditText port,
            Spinner security,
            EditText username,
            EditText password,
            EditText from,
            EditText recipients) {
        host.setText(profile.host);
        port.setText(Integer.toString(profile.port));
        security.setSelection(AppConfig.SECURITY_STARTTLS.equals(profile.security) ? 1 : 0);
        username.setText(profile.username);
        password.setText(profile.password);
        from.setText(profile.fromAddress);
        recipients.setText(profile.recipientsText);
    }

    private void bindSecurityPort(Spinner security, EditText port) {
        security.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String current = port.getText().toString().trim();
                if (current.isEmpty() || "465".equals(current) || "587".equals(current)) {
                    port.setText(position == 1 ? "587" : "465");
                    port.setSelection(port.getText().length());
                }
                port.setContentDescription(position == 1
                        ? "STARTTLS 端口，推荐 587" : "SSL TLS 端口，推荐 465");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(glassSurface(27));
        applyGlassDepth(card, 18f, false);
        return card;
    }

    private ImageView birdMark(int widthDp, int heightDp) {
        ImageView bird = new ImageView(this);
        bird.setImageResource(R.drawable.yanjian_bird);
        bird.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        bird.setAdjustViewBounds(true);
        bird.setContentDescription("雁笺折纸飞雁标志");
        bird.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        return bird;
    }

    private Button glassBackButton(String label, Runnable action) {
        Button back = secondaryButton(label);
        back.setCompoundDrawablesWithIntrinsicBounds(
                icon(MaterialIcons.md_chevron_left, COLOR_JADE_DARK, 20), null, null, null);
        back.setCompoundDrawablePadding(dp(3));
        back.setOnClickListener(view -> action.run());
        back.setMinHeight(dp(MIN_TOUCH_DP));
        back.setPadding(dp(10), dp(5), dp(12), dp(5));
        return back;
    }

    private void addBrandHeader(String actionLabel, Runnable action) {
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView seal = new ImageView(this);
        seal.setImageResource(R.drawable.yanjian_app_icon);
        seal.setScaleType(ImageView.ScaleType.CENTER_CROP);
        seal.setContentDescription("雁笺折纸飞雁标志");
        applyGlassDepth(seal, 7f, false);
        brand.addView(seal, new LinearLayout.LayoutParams(dp(55), dp(55)));
        TextView name = text("雁笺", 25f, COLOR_INK, true);
        name.setTypeface(Typeface.SERIF, Typeface.BOLD);
        name.setPadding(dp(13), 0, 0, 0);
        brand.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!TextUtils.isEmpty(actionLabel) && action != null) {
            Button actionButton = secondaryButton(actionLabel);
            actionButton.setOnClickListener(view -> action.run());
            actionButton.setMinHeight(dp(MIN_TOUCH_DP));
            actionButton.setPadding(dp(11), dp(5), dp(11), dp(5));
            brand.addView(actionButton, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(MIN_TOUCH_DP)));
        }
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(16));
        page.addView(brand, params);
    }

    private void addBirdPageTitle(String title, String subtitle, LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(birdMark(50, 45));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView heading = text(title, 25f, COLOR_INK, true);
        heading.setTypeface(Typeface.SERIF, Typeface.BOLD);
        copy.addView(heading);
        copy.addView(text(subtitle, 13.5f, COLOR_MUTED, false));
        header.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(4), 0, dp(15));
        parent.addView(header, params);
    }

    private void addEmailHeader() {
        addSubPageTitle("邮箱通道", "直连你的 SMTP，授权码仅加密保存在本机", PAGE_SETTINGS);
    }

    private void addSystemGuardianHeader() {
        addBackNavigation("设置", PAGE_SETTINGS);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(birdMark(68, 60));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        TextView title = text("系统守护", 27f, COLOR_INK, true);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        copy.addView(title);
        copy.addView(text("完成后台授权，提高锁屏与长期运行可靠性", 13f, COLOR_MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView help = new ImageView(this);
        help.setImageDrawable(icon(MaterialIcons.md_help_outline, COLOR_JADE, 23));
        help.setBackground(roundStroke(Color.argb(238, 251, 252, 252), Color.WHITE, 22));
        help.setPadding(dp(8), dp(8), dp(8), dp(8));
        help.setContentDescription("后台守护说明");
        help.setClickable(true);
        help.setFocusable(true);
        help.setOnClickListener(view -> showGlassDialog(
                "后台守护说明",
                "短信权限和忽略电池优化会由系统直接确认；厂商启动管理需要在系统页面允许后返回雁笺确认。当前网络只显示实时状态，不计入授权进度。",
                "知道了", null, null));
        MotionEffects.bindPress(help);
        top.addView(help, new LinearLayout.LayoutParams(dp(MIN_TOUCH_DP), dp(MIN_TOUCH_DP)));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(2), 0, dp(14));
        page.addView(top, params);
    }

    private void addMaintenanceHeader() {
        addSubPageTitle("维护与更新", "排查问题、管理队列并安全更新", PAGE_SETTINGS);
    }

    private void addPageTitle(String title, String subtitle, LinearLayout parent) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView seal = new ImageView(this);
        seal.setImageResource(R.drawable.yanjian_app_icon);
        seal.setScaleType(ImageView.ScaleType.CENTER_CROP);
        seal.setContentDescription("雁笺折纸飞雁标志");
        applyGlassDepth(seal, 7f, false);
        header.addView(seal, new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        TextView heading = text(title, 25f, COLOR_INK, true);
        heading.setTypeface(Typeface.SERIF, Typeface.BOLD);
        copy.addView(heading);
        TextView sub = text(subtitle, 13.5f, COLOR_MUTED, false);
        sub.setPadding(0, dp(3), 0, 0);
        copy.addView(sub);
        header.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(2), 0, dp(17));
        parent.addView(header, params);
    }

    private void addGuardianHeader() {
        addBrandHeader(null, null);
        View headingGap = new View(this);
        page.addView(headingGap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
        TextView title = text("旅行守护", 37f, COLOR_INK, true);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        title.setPadding(dp(7), 0, 0, 0);
        page.addView(title);
        TextView subtitle = text("离家前确认状态，故障短信会加密保存并自动补发", 14f, COLOR_MUTED, false);
        subtitle.setPadding(dp(7), dp(4), 0, dp(18));
        page.addView(subtitle);
    }

    private void addSettingsHeader() {
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView seal = new ImageView(this);
        seal.setImageResource(R.drawable.yanjian_app_icon);
        seal.setScaleType(ImageView.ScaleType.CENTER_CROP);
        seal.setContentDescription("雁笺折纸飞雁标志");
        brand.addView(seal, new LinearLayout.LayoutParams(dp(55), dp(55)));
        TextView name = text("雁笺", 25f, COLOR_INK, true);
        name.setTypeface(Typeface.SERIF, Typeface.BOLD);
        name.setPadding(dp(13), 0, 0, 0);
        brand.addView(name);
        LinearLayout.LayoutParams brandParams = matchWrap();
        brandParams.setMargins(0, 0, 0, dp(20));
        page.addView(brand, brandParams);
        TextView title = text("设置", 31f, COLOR_INK, true);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        page.addView(title);
        TextView subtitle = text("邮箱、守护、隐私与应用维护", 14f, COLOR_MUTED, false);
        subtitle.setPadding(0, dp(4), 0, dp(13));
        page.addView(subtitle);
    }

    private void addSubPageTitle(String title, String subtitle, int backPage) {
        String parentLabel = backPage == PAGE_ABOUT ? "关于" : backPage == PAGE_GUARDIAN ? "守护" : "设置";
        addBackNavigation(parentLabel, backPage);
        addPageTitle(title, subtitle, page);
    }

    private void addBackNavigation(String parentLabel, int backPage) {
        Button back = glassBackButton(parentLabel, () -> showPage(backPage));
        back.setContentDescription("返回" + parentLabel);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(MIN_TOUCH_DP));
        backParams.setMargins(0, 0, 0, dp(10));
        page.addView(back, backParams);
    }

    private void addStatusChip(String value, LinearLayout parent) {
        TextView chip = text(value, 13f, COLOR_JADE_DARK, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(9), dp(14), dp(9));
        chip.setBackground(roundStroke(COLOR_GLASS, COLOR_GLASS_BORDER, 20));
        applyGlassDepth(chip, 6f, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        parent.addView(chip, params);
    }

    private void addStepProgress(int current, int total) {
        TextView caption = text("守护设置 · 第 " + current + " 步，共 " + total + " 步", 13f, COLOR_INK, false);
        caption.setPadding(dp(3), dp(11), 0, dp(9));
        page.addView(caption);
        LinearLayout steps = new LinearLayout(this);
        steps.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 1; i <= total; i++) {
            FrameLayout dot = new FrameLayout(this);
            dot.setBackground(roundStroke(i <= current ? COLOR_JADE : COLOR_PAPER,
                    i <= current ? Color.WHITE : Color.rgb(210, 220, 218), 15));
            if (i < current) {
                ImageView check = new ImageView(this);
                check.setImageDrawable(icon(MaterialIcons.md_check, Color.WHITE, 17));
                dot.addView(check, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
            } else {
                TextView number = text(Integer.toString(i), 12f,
                        i <= current ? Color.WHITE : COLOR_MUTED, true);
                number.setGravity(Gravity.CENTER);
                dot.addView(number, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            steps.addView(dot, new LinearLayout.LayoutParams(dp(30), dp(30)));
            if (i < total) {
                View line = new View(this);
                line.setBackgroundColor(i < current ? COLOR_JADE : Color.rgb(210, 220, 218));
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(0, dp(1), 1f);
                lineParams.setMargins(dp(5), 0, dp(5), 0);
                steps.addView(line, lineParams);
            }
        }
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(dp(8), 0, dp(8), dp(37));
        page.addView(steps, params);
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 17f, COLOR_INK, true);
        title.setPadding(0, 0, 0, dp(8));
        return title;
    }

    private TextView settingsSectionLabel(String value) {
        TextView label = text(value, 12f, COLOR_JADE_DARK, true);
        label.setPadding(dp(10), dp(2), 0, dp(1));
        return label;
    }

    private TextView settingsSectionLabelWithIcon(String value, Icon iconValue) {
        TextView label = settingsSectionLabel(value);
        label.setCompoundDrawablePadding(dp(7));
        label.setCompoundDrawablesWithIntrinsicBounds(icon(iconValue, COLOR_JADE, 18), null, null, null);
        return label;
    }

    private LinearLayout groupedCard() {
        LinearLayout group = card();
        group.setPadding(dp(10), dp(4), dp(10), dp(4));
        return group;
    }

    private View statColumn(String title, String value) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        TextView titleView = text(title, 10.5f, COLOR_MUTED, false);
        titleView.setGravity(Gravity.CENTER);
        TextView valueView = text(value, 12f, COLOR_MUTED, false);
        valueView.setGravity(Gravity.CENTER);
        column.addView(titleView);
        column.addView(valueView);
        column.setLayoutParams(new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    private View statDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(222, 231, 229));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(1), dp(42));
        params.setMargins(dp(2), 0, dp(2), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private View hairlineDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(224, 233, 231));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(dp(4), 0, dp(4), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private TextView settingsRowText(String title, String subtitle, Icon iconValue) {
        TextView row = text(title + "\n" + subtitle, 13.5f, COLOR_INK, false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLineSpacing(0f, 1.08f);
        row.setPadding(dp(7), dp(6), dp(7), dp(6));
        row.setCompoundDrawablePadding(dp(11));
        row.setCompoundDrawablesWithIntrinsicBounds(
                icon(iconValue, COLOR_JADE, 24), null,
                icon(MaterialIcons.md_chevron_right, COLOR_MUTED, 20), null);
        row.setClickable(true);
        row.setFocusable(true);
        MotionEffects.bindPress(row);
        return row;
    }

    private View settingsInlineRow(
            String title, String value, Icon iconValue, boolean chevron) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(6), dp(7), dp(6));
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(iconValue, COLOR_JADE, 22));
        row.addView(iconView, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView titleView = text(title, 13.5f, COLOR_INK, false);
        titleView.setPadding(dp(9), 0, dp(8), 0);
        row.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!value.isBlank()) {
            row.addView(text(value, 11.5f, COLOR_MUTED, false));
        }
        if (chevron) {
            ImageView arrow = new ImageView(this);
            arrow.setImageDrawable(icon(MaterialIcons.md_chevron_right, COLOR_MUTED, 20));
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(22), dp(22));
            arrowParams.setMargins(dp(5), 0, 0, 0);
            row.addView(arrow, arrowParams);
        }
        row.setClickable(chevron);
        row.setFocusable(chevron);
        if (chevron) MotionEffects.bindPress(row);
        return row;
    }

    private View informationCard(Icon iconValue, String title, String body) {
        LinearLayout container = card();
        container.addView(sectionHeader(iconValue, title));
        TextView copy = text(body, 13f, COLOR_MUTED, false);
        copy.setLineSpacing(dp(2), 1f);
        copy.setPadding(dp(4), dp(5), dp(4), dp(2));
        container.addView(copy, matchWrap());
        return container;
    }

    private Spinner segmentedSpinner(LinearLayout parent, String[] values, int selectedIndex) {
        Spinner backing = createSpinner(values);
        backing.setVisibility(View.GONE);
        backing.setSelection(selectedIndex);
        LinearLayout track = new LinearLayout(this);
        track.setPadding(dp(3), dp(3), dp(3), dp(3));
        track.setBackground(roundStroke(Color.argb(230, 244, 248, 247), Color.WHITE, 22));
        List<TextView> labels = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            int index = i;
            TextView label = text(values[i], 12.5f,
                    i == selectedIndex ? COLOR_JADE_DARK : COLOR_INK,
                    i == selectedIndex);
            label.setGravity(Gravity.CENTER);
            label.setPadding(dp(4), dp(8), dp(4), dp(8));
            label.setMinHeight(dp(MIN_TOUCH_DP));
            label.setBackground(i == selectedIndex ? glassSelection() : roundRect(Color.TRANSPARENT, 19));
            if (i == selectedIndex) applyGlassDepth(label, 6f, true);
            label.setClickable(true);
            label.setFocusable(true);
            label.setOnClickListener(view -> {
                backing.setSelection(index);
                for (int j = 0; j < labels.size(); j++) {
                    TextView item = labels.get(j);
                    boolean selected = j == index;
                    item.setTextColor(selected ? COLOR_JADE_DARK : COLOR_INK);
                    item.setTypeface(Typeface.create(
                            selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
                    item.setBackground(selected ? glassSelection() : roundRect(Color.TRANSPARENT, 19));
                    if (selected) {
                        applyGlassDepth(item, 6f, true);
                    } else {
                        item.setElevation(0f);
                    }
                }
            });
            MotionEffects.bindPress(label);
            labels.add(label);
            track.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        parent.addView(track, matchWrap());
        parent.addView(backing, new LinearLayout.LayoutParams(1, 1));
        return backing;
    }

    private EditText compactTimeInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * TEXT_SCALE);
        input.setTextColor(COLOR_JADE_DARK);
        input.setHintTextColor(COLOR_MUTED);
        input.setGravity(Gravity.CENTER);
        input.setInputType(InputType.TYPE_CLASS_DATETIME);
        input.setSingleLine(true);
        input.setMinHeight(dp(MIN_TOUCH_DP));
        input.setPadding(dp(5), 0, dp(5), 0);
        input.setBackgroundColor(Color.TRANSPARENT);
        return input;
    }

    private void styleDayCheckBox(CheckBox checkBox) {
        checkBox.setButtonDrawable(null);
        checkBox.setGravity(Gravity.CENTER);
        checkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * TEXT_SCALE);
        checkBox.setTextColor(checkBox.isChecked() ? Color.WHITE : COLOR_INK);
        checkBox.setBackground(roundStroke(
                checkBox.isChecked() ? COLOR_JADE : Color.rgb(246, 249, 248),
                Color.WHITE,
                19));
        checkBox.setPadding(0, 0, 0, 0);
        checkBox.setOnCheckedChangeListener((button, checked) -> {
            button.setTextColor(checked ? Color.WHITE : COLOR_INK);
            button.setBackground(roundStroke(
                    checked ? COLOR_JADE : Color.rgb(246, 249, 248),
                    Color.WHITE,
                    19));
        });
    }

    private View sectionHeader(Icon iconValue, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(iconValue, COLOR_JADE, 24));
        row.addView(iconView, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView title = text(value, 18f, COLOR_INK, true);
        title.setPadding(dp(10), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(9));
        row.setLayoutParams(params);
        return row;
    }

    private TextView fieldLabel(String value) {
        TextView label = text(value, 12.5f, COLOR_MUTED, false);
        label.setPadding(dp(2), dp(4), 0, dp(1));
        return label;
    }

    private View recipientChips(EditText backing, String... addresses) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(5), dp(7), dp(5));
        row.setBackground(insetSurface(18));
        for (String address : addresses) {
            TextView chip = text(address, 10.5f, COLOR_MUTED, false);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(9), dp(5), dp(7), dp(5));
            chip.setCompoundDrawablePadding(dp(4));
            chip.setCompoundDrawablesWithIntrinsicBounds(
                    null, null, icon(MaterialIcons.md_close, COLOR_MUTED, 13), null);
            chip.setBackground(glassSurface(15));
            applyGlassDepth(chip, 3f, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            params.setMargins(0, 0, dp(6), 0);
            row.addView(chip, params);
        }
        ImageView expand = new ImageView(this);
        expand.setImageDrawable(icon(MaterialIcons.md_expand_more, COLOR_INK, 19));
        row.addView(expand, new LinearLayout.LayoutParams(0, dp(24), 1f));
        row.setContentDescription("收件邮箱：" + TextUtils.join("，", addresses) + "。点按编辑");
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> {
            row.setVisibility(View.GONE);
            backing.setVisibility(View.VISIBLE);
            backing.requestFocus();
            backing.setSelection(backing.getText().length());
        });
        MotionEffects.bindPress(row);
        return row;
    }

    private LinearLayout collapsibleCard(
            Icon iconValue,
            String title,
            String summary,
            LinearLayout content,
            boolean expanded) {
        LinearLayout container = card();
        container.setPadding(dp(13), dp(10), dp(13), dp(10));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(iconValue, COLOR_JADE, 25));
        header.addView(iconView, new LinearLayout.LayoutParams(dp(31), dp(31)));
        TextView titleView = text(title, 17f, COLOR_INK, false);
        titleView.setPadding(dp(10), 0, dp(8), 0);
        header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView summaryView = text(summary, 11.5f, COLOR_MUTED, false);
        summaryView.setSingleLine(true);
        summaryView.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(summaryView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ImageView arrow = new ImageView(this);
        arrow.setImageDrawable(icon(expanded ? MaterialIcons.md_expand_less : MaterialIcons.md_expand_more, COLOR_INK, 24));
        header.addView(arrow, new LinearLayout.LayoutParams(dp(27), dp(27)));
        header.setClickable(true);
        header.setFocusable(true);
        MotionEffects.bindPress(header);
        content.setPadding(0, dp(8), 0, 0);
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        header.setOnClickListener(view -> {
            boolean nowExpanded = content.getVisibility() != View.VISIBLE;
            beginSoftTransition(container);
            content.setVisibility(nowExpanded ? View.VISIBLE : View.GONE);
            arrow.setImageDrawable(icon(nowExpanded ? MaterialIcons.md_expand_less : MaterialIcons.md_expand_more, COLOR_INK, 24));
            MotionEffects.select(arrow, nowExpanded, 0f);
        });
        container.addView(header, matchWrap());
        container.addView(content, matchWrap());
        return container;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * TEXT_SCALE);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.12f);
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    private EditText input(LinearLayout parent, String hint, int inputType) {
        EditText view = createInput(hint, inputType);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(2), 0, dp(2));
        parent.addView(view, params);
        return view;
    }

    private EditText passwordInput(LinearLayout parent, String hint) {
        FrameLayout container = new FrameLayout(this);
        EditText field = createInput(
                hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setPadding(dp(13), dp(7), dp(58), dp(7));
        container.addView(field, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView visibility = new ImageView(this);
        visibility.setImageDrawable(icon(MaterialCommunityIcons.mdi_eye, COLOR_JADE_DARK, 22));
        visibility.setBackground(roundRect(Color.TRANSPARENT, 20));
        visibility.setPadding(dp(12), dp(12), dp(12), dp(12));
        visibility.setContentDescription("显示授权码");
        visibility.setClickable(true);
        visibility.setFocusable(true);
        visibility.setOnClickListener(view -> {
            boolean reveal = field.getTransformationMethod() != null;
            field.setTransformationMethod(reveal ? null : PasswordTransformationMethod.getInstance());
            visibility.setImageDrawable(icon(
                    reveal ? MaterialCommunityIcons.mdi_eye_off : MaterialCommunityIcons.mdi_eye,
                    COLOR_JADE_DARK,
                    22));
            visibility.setContentDescription(reveal ? "隐藏授权码" : "显示授权码");
            field.setSelection(field.getText().length());
            field.requestFocus();
        });
        MotionEffects.bindPress(visibility);
        FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(
                dp(MIN_TOUCH_DP),
                dp(MIN_TOUCH_DP),
                Gravity.END | Gravity.CENTER_VERTICAL);
        container.addView(visibility, toggleParams);
        visibility.setElevation(dp(5));
        visibility.bringToFront();

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(2), 0, dp(2));
        parent.addView(container, params);
        return field;
    }

    private EditText createInput(String hint, int inputType) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setTextSize(14f * TEXT_SCALE);
        view.setTextColor(COLOR_INK);
        view.setHintTextColor(Color.rgb(126, 142, 148));
        view.setInputType(inputType);
        view.setMinHeight(dp(MIN_TOUCH_DP));
        view.setPadding(dp(13), dp(7), dp(13), dp(7));
        view.setBackground(insetSurface(18));
        applyGlassDepth(view, 3f, false);
        return view;
    }

    private Spinner spinner(LinearLayout parent, String[] values) {
        Spinner spinner = createSpinner(values);
        parent.addView(spinner, matchWrap());
        return spinner;
    }

    private Spinner createSpinner(String[] values) {
        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return spinnerItemView(getItem(position), false, false);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return spinnerItemView(
                        getItem(position), true, position == spinner.getSelectedItemPosition());
            }
        };
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(MIN_TOUCH_DP));
        spinner.setPadding(dp(3), dp(2), dp(3), dp(2));
        spinner.setBackground(insetSurface(18));
        spinner.setPopupBackgroundDrawable(glassSurface(22));
        spinner.setDropDownVerticalOffset(dp(7));
        spinner.setDropDownHorizontalOffset(-dp(2));
        spinner.setPrompt("请选择");
        spinner.post(() -> spinner.setDropDownWidth(Math.max(spinner.getWidth() + dp(4), dp(196))));
        applyGlassDepth(spinner, 3f, false);
        MotionEffects.bindPress(spinner);
        return spinner;
    }

    private TextView spinnerItemView(String value, boolean dropDown, boolean selected) {
        TextView item = text(value == null ? "" : value, dropDown ? 13.5f : 13f,
                selected ? COLOR_JADE_DARK : COLOR_INK, selected);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinHeight(dp(MIN_TOUCH_DP));
        item.setPadding(dp(dropDown ? 15 : 11), dp(dropDown ? 10 : 7),
                dp(dropDown ? 15 : 9), dp(dropDown ? 10 : 7));
        item.setCompoundDrawablePadding(dp(9));
        if (dropDown) {
            item.setCompoundDrawablesWithIntrinsicBounds(
                    selected ? icon(MaterialIcons.md_check, COLOR_JADE_DARK, 19) : null,
                    null, null, null);
            item.setBackground(selected
                    ? glassSelection()
                    : roundRect(Color.TRANSPARENT, 17));
            if (selected) applyGlassDepth(item, 5f, true);
            item.setContentDescription((selected ? "已选择，" : "选择，") + value);
        } else {
            item.setCompoundDrawablesWithIntrinsicBounds(
                    null, null, icon(MaterialIcons.md_expand_more, COLOR_JADE_DARK, 21), null);
            item.setContentDescription("当前选择，" + value + "，双击展开");
        }
        return item;
    }

    private Spinner securitySpinner(LinearLayout parent) {
        return spinner(parent, new String[]{"SSL/TLS（465）", "STARTTLS（587）"});
    }

    private Button actionButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15.5f);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(MIN_TOUCH_DP));
        button.setBackground(jadeButtonSurface(color));
        applyGlassDepth(button, 9f, true);
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        MotionEffects.bindPress(button);
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(COLOR_JADE_DARK);
        button.setTextSize(14f);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(MIN_TOUCH_DP));
        button.setBackground(glassSurface(21));
        applyGlassDepth(button, 6f, false);
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        MotionEffects.bindPress(button);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(5), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void addNotice(String message, LinearLayout parent) {
        TextView note = text(message, 13f, COLOR_MUTED, false);
        note.setPadding(dp(12), dp(10), dp(12), dp(10));
        note.setBackground(roundStroke(Color.rgb(235, 243, 240), COLOR_GLASS_BORDER, 16));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(8), 0, dp(4));
        parent.addView(note, params);
    }

    private Drawable insetSurface(int radiusDp) {
        GradientDrawable rim = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(202, 224, 218), Color.rgb(255, 255, 255)});
        rim.setCornerRadius(dp(radiusDp));
        GradientDrawable field = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(235, 245, 242), Color.rgb(255, 255, 255)});
        field.setCornerRadius(dp(Math.max(1, radiusDp - 1)));
        field.setStroke(dp(1), Color.argb(250, 255, 255, 255));
        LayerDrawable inset = new LayerDrawable(new Drawable[]{rim, field});
        inset.setLayerInset(1, dp(1), dp(1), dp(1), dp(1));
        return inset;
    }

    private LinearLayout readinessCell(String title, String state, boolean complete) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(6), dp(10), dp(6), dp(10));
        cell.addView(text(title, 12f, COLOR_MUTED, false));
        TextView value = text(state, 13f, complete ? COLOR_JADE_DARK : COLOR_AMBER, true);
        value.setPadding(0, dp(2), 0, 0);
        cell.addView(value);
        return cell;
    }

    private LinearLayout readinessCell(
            Icon iconValue, String title, String state, boolean complete) {
        LinearLayout cell = readinessCell(title, state, complete);
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(iconValue, complete ? COLOR_JADE_DARK : COLOR_MUTED, 23));
        cell.addView(iconView, 0, new LinearLayout.LayoutParams(dp(27), dp(27)));
        return cell;
    }

    private View heartbeatOrb(int color) {
        FrameLayout orb = new FrameLayout(this);
        orb.setBackground(roundStroke(Color.argb(185, 231, 243, 240), Color.WHITE, 52));
        applyGlassDepth(orb, 7f, true);
        View outerRing = new View(this);
        outerRing.setBackground(roundStroke(Color.argb(78, 255, 255, 255), Color.argb(190, 255, 255, 255), 40));
        orb.addView(outerRing, new FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER));
        View innerRing = new View(this);
        innerRing.setBackground(roundStroke(Color.argb(48, 255, 255, 255), Color.argb(150, 212, 235, 229), 32));
        orb.addView(innerRing, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        ImageView pulse = new ImageView(this);
        pulse.setImageDrawable(icon(MaterialCommunityIcons.mdi_pulse, color, 39));
        orb.addView(pulse, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        return orb;
    }

    private View toolCell(Icon iconValue, String label, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(4), dp(7), dp(4), dp(7));
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(iconValue, COLOR_JADE, 28));
        cell.addView(iconView, new LinearLayout.LayoutParams(dp(31), dp(31)));
        TextView labelView = text(label, 13f, COLOR_INK, false);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, dp(5), 0, 0);
        cell.addView(labelView, matchWrap());
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setContentDescription(label);
        cell.setOnClickListener(view -> action.run());
        MotionEffects.bindPress(cell);
        return cell;
    }

    private View pathStep(Icon iconValue, String label) {
        LinearLayout step = new LinearLayout(this);
        step.setOrientation(LinearLayout.VERTICAL);
        step.setGravity(Gravity.CENTER);
        LinearLayout well = new LinearLayout(this);
        well.setGravity(Gravity.CENTER);
        well.setBackground(roundStroke(Color.argb(225, 247, 250, 249), Color.WHITE, 24));
        applyGlassDepth(well, 5f, false);
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(iconValue, COLOR_JADE_DARK, 24));
        well.addView(iconView, new LinearLayout.LayoutParams(dp(28), dp(28)));
        step.addView(well, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView caption = text(label, 10.5f, COLOR_INK, false);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(4), 0, 0);
        step.addView(caption, matchWrap());
        return step;
    }

    private View pathBirdStep(String label) {
        LinearLayout step = new LinearLayout(this);
        step.setOrientation(LinearLayout.VERTICAL);
        step.setGravity(Gravity.CENTER);
        LinearLayout well = new LinearLayout(this);
        well.setGravity(Gravity.CENTER);
        well.setBackground(glassSurface(24));
        applyGlassDepth(well, 6f, false);
        well.addView(birdMark(34, 30), new LinearLayout.LayoutParams(dp(34), dp(30)));
        step.addView(well, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView caption = text(label, 10.5f, COLOR_INK, false);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(4), 0, 0);
        step.addView(caption, matchWrap());
        return step;
    }

    private View pathArrow() {
        ImageView arrow = new ImageView(this);
        arrow.setImageDrawable(icon(MaterialIcons.md_chevron_right, COLOR_MUTED, 18));
        return arrow;
    }

    private View permissionSwitchRow(Icon iconValue, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(7), dp(4), dp(7));
        LinearLayout iconWell = new LinearLayout(this);
        iconWell.setGravity(Gravity.CENTER);
        iconWell.setBackground(glassSelection());
        applyGlassDepth(iconWell, 4f, false);
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(iconValue, COLOR_JADE, 22));
        iconWell.addView(iconView, new LinearLayout.LayoutParams(dp(26), dp(26)));
        row.addView(iconWell, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView labelView = text(label, 15f, COLOR_INK, false);
        labelView.setPadding(dp(11), 0, 0, 0);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView state = text("用户操作", 12f, COLOR_MUTED, false);
        row.addView(state);
        Switch toggle = new Switch(this);
        toggle.setChecked(visualTestMode || TravelGuard.isBackgroundConfirmed(this));
        toggle.setClickable(false);
        toggle.setFocusable(false);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(54), dp(42)));
        return row;
    }

    private TextView navigationRow(String title, String detail, Icon iconValue) {
        TextView row = text(title + "\n" + detail, 14f, COLOR_INK, true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setCompoundDrawablePadding(dp(12));
        row.setCompoundDrawablesWithIntrinsicBounds(
                icon(iconValue, COLOR_JADE_DARK, 24), null,
                icon(MaterialIcons.md_chevron_right, COLOR_MUTED, 21), null);
        row.setBackground(insetSurface(18));
        row.setClickable(true);
        row.setFocusable(true);
        MotionEffects.bindPress(row);
        return row;
    }

    private TextView singleActionRow(String title, Icon iconValue) {
        TextView row = text(title, 14f, COLOR_INK, false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setCompoundDrawablePadding(dp(12));
        row.setCompoundDrawablesWithIntrinsicBounds(
                icon(iconValue, COLOR_JADE, 24), null,
                icon(MaterialIcons.md_chevron_right, COLOR_MUTED, 21), null);
        row.setBackground(glassSurface(18));
        applyGlassDepth(row, 7f, false);
        row.setClickable(true);
        row.setFocusable(true);
        MotionEffects.bindPress(row);
        return row;
    }

    private View settingsRow(String title, String subtitle, Icon iconValue, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(9), dp(6), dp(9));
        row.setBackground(roundRect(Color.TRANSPARENT, 18));
        LinearLayout iconWell = new LinearLayout(this);
        iconWell.setGravity(Gravity.CENTER);
        iconWell.setBackground(glassSelection());
        applyGlassDepth(iconWell, 6f, false);
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(iconValue, COLOR_JADE, 27));
        iconView.setContentDescription(null);
        iconWell.addView(iconView, new LinearLayout.LayoutParams(dp(29), dp(29)));
        row.addView(iconWell, new LinearLayout.LayoutParams(dp(43), dp(43)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        copy.addView(text(title, 15f, COLOR_INK, false));
        copy.addView(text(subtitle, 11.5f, COLOR_MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView arrow = new ImageView(this);
        arrow.setImageDrawable(icon(MaterialIcons.md_chevron_right, COLOR_INK, 22));
        arrow.setContentDescription(null);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.setContentDescription(title + "，" + subtitle);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> action.run());
        MotionEffects.bindPress(row);
        return row;
    }

    private View statusRow(Icon iconValue, String title, String state, boolean complete) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        row.setBackground(roundRect(Color.TRANSPARENT, 18));
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.setMargins(0, dp(3), 0, dp(3));
        row.setLayoutParams(rowParams);
        LinearLayout iconWell = new LinearLayout(this);
        iconWell.setGravity(Gravity.CENTER);
        iconWell.setBackground(roundStroke(Color.argb(220, 235, 246, 243), Color.WHITE, 18));
        applyGlassDepth(iconWell, 4f, false);
        ImageView leading = new ImageView(this);
        leading.setImageDrawable(icon(iconValue, COLOR_JADE, 20));
        iconWell.addView(leading, new LinearLayout.LayoutParams(dp(23), dp(23)));
        row.addView(iconWell, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView titleView = text(title, 14f, COLOR_INK, false);
        titleView.setPadding(dp(12), 0, 0, 0);
        row.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView stateView = text(state, 13f, complete ? COLOR_JADE_DARK : COLOR_AMBER, true);
        stateView.setCompoundDrawablePadding(dp(7));
        stateView.setCompoundDrawablesWithIntrinsicBounds(
                icon(complete ? MaterialIcons.md_check_circle : MaterialIcons.md_error_outline,
                        complete ? COLOR_JADE_DARK : COLOR_AMBER, 19), null, null, null);
        row.addView(stateView);
        return row;
    }

    private View actionStatusRow(
            Icon iconValue, String title, String state, boolean complete, Runnable action) {
        View row = statusRow(iconValue, title, state, complete);
        // The entire activity is scaled to 92% for compact layouts. Keep tappable
        // status rows above the 48 dp accessibility target after that transform.
        row.setMinimumHeight(dp(56));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(title + "，" + state);
        row.setOnClickListener(view -> action.run());
        MotionEffects.bindPress(row);
        return row;
    }

    private void addHistorySummary(List<HistoryItem> history) {
        int filtered = 0;
        for (HistoryItem item : history) {
            if ("FILTERED".equals(item.status)) filtered++;
        }
        LocalDayWindow today = LocalDayWindow.containing(
                System.currentTimeMillis(), TimeZone.getDefault());
        long successToday = QueueDatabase.get(this).successfulDeliveryCount(
                today.startInclusive, today.endExclusive);
        LinearLayout summary = new LinearLayout(this);
        summary.setPadding(dp(7), dp(8), dp(7), dp(8));
        summary.setBackground(glassSurface(24));
        applyGlassDepth(summary, 9f, false);
        int pending = visualTestMode ? 1 : QueueDatabase.get(this).count();
        summary.addView(historyStatCell(MaterialCommunityIcons.mdi_clock, "待发", Integer.toString(pending), pending == 0 ? COLOR_JADE_DARK : COLOR_AMBER), weightedWrap());
        summary.addView(historyStatCell(MaterialCommunityIcons.mdi_send, "今日成功", Integer.toString(visualTestMode ? 18 : successToday), COLOR_JADE_DARK), weightedWrap());
        summary.addView(historyStatCell(MaterialCommunityIcons.mdi_shield_outline, "已过滤", Integer.toString(visualTestMode ? 3 : filtered), COLOR_MUTED), weightedWrap());
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(10));
        page.addView(summary, params);
    }

    private List<HistoryItem> visualHistoryItems() {
        long now = System.currentTimeMillis();
        List<HistoryItem> items = new ArrayList<>();
        items.add(new HistoryItem("visual-1", now, "10086", "您的验证码是 123456，5 分钟内有效", 0, "SUCCESS", 1, "主通道 · 1.2 秒"));
        items.add(new HistoryItem("visual-2", now - 16 * 60_000L, "95588", "尾号 4821 账户变动…", 1, "RETRY_WAIT", 1, "网络不可用 · 2 分钟后重试"));
        items.add(new HistoryItem("visual-3", now - 94 * 60_000L, "1069***", "优惠活动…", -1, "FILTERED", 0, "命中排除关键词：广告"));
        items.add(new HistoryItem("visual-4", now - 13 * 60 * 60_000L, "10690333", "【服务通知】您的服务已受理…", 0, "SUCCESS", 1, "主通道 · 1.0 秒"));
        items.add(new HistoryItem("visual-5", now - 17 * 60 * 60_000L, "12381", "尊敬的用户，您的话费余额…", 1, "SUCCESS", 1, "备用通道 · 1.4 秒"));
        return items;
    }

    private View historyStatCell(Icon iconValue, String title, String value, int color) {
        LinearLayout cell = new LinearLayout(this);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(5), dp(5), dp(5), dp(5));
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(iconValue, color, 23));
        cell.addView(iconView, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(7), 0, 0, 0);
        copy.addView(text(title, 11.5f, COLOR_MUTED, false));
        copy.addView(text(value, 17f, color, true));
        cell.addView(copy);
        return cell;
    }

    private View historyTimelineRow(HistoryItem item, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(3), dp(6), dp(3), last ? dp(6) : 0);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout orb = new LinearLayout(this);
        orb.setGravity(Gravity.CENTER);
        int color = statusColor(item.status);
        orb.setBackground(roundStroke(Color.argb(225, Color.red(color), Color.green(color), Color.blue(color)), Color.WHITE, 20));
        applyGlassDepth(orb, 5f, true);
        ImageView statusIcon = new ImageView(this);
        Icon glyph = "SUCCESS".equals(item.status) ? MaterialCommunityIcons.mdi_check
                : "FILTERED".equals(item.status) ? MaterialCommunityIcons.mdi_filter_outline
                : MaterialCommunityIcons.mdi_refresh;
        statusIcon.setImageDrawable(icon(glyph, Color.WHITE, 19));
        orb.addView(statusIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        rail.addView(orb, new LinearLayout.LayoutParams(dp(36), dp(36)));
        if (!last) {
            View connector = new View(this);
            connector.setBackgroundColor(Color.rgb(214, 226, 223));
            rail.addView(connector, new LinearLayout.LayoutParams(dp(1), dp(39)));
        }
        row.addView(rail, new LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(7), 0, dp(2), dp(6));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String displayedStatus = visualTestMode && item.id.startsWith("visual-")
                ? visualHistoryStatus(item.status) : statusLabel(item.status);
        String displayedTime = visualTestMode && item.id.startsWith("visual-")
                ? visualHistoryTime(item.id) : timeFormat.format(new Date(item.receivedAt));
        heading.addView(text(displayedStatus + " · " + displayedTime,
                13f, color, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!"FILTERED".equals(item.status)) {
            boolean visualRow = visualTestMode && item.id.startsWith("visual-");
            boolean actionableRealRow = !visualRow
                    && (ForwardScheduler.isRetryOnlyHistoryStatus(item.status)
                    || ForwardScheduler.isExplicitHistoryResendStatus(item.status));
            boolean actionableVisualRow = visualRow
                    && ("visual-2".equals(item.id) || "visual-4".equals(item.id));
            if (actionableRealRow || actionableVisualRow) {
                heading.addView(historyRetryButton(item),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
            }
            if (visualRow && "SUCCESS".equals(item.status)) {
                ImageView chevron = new ImageView(this);
                chevron.setImageDrawable(icon(MaterialIcons.md_chevron_right, COLOR_INK, 19));
                LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(20), dp(24));
                chevronParams.setMargins(dp(4), 0, 0, 0);
                heading.addView(chevron, chevronParams);
            }
        }
        content.addView(heading, matchWrap());
        content.addView(text(item.sender + (item.simSlot >= 0 ? " · SIM " + (item.simSlot + 1) : ""), 13f, COLOR_INK, false));
        TextView body = text(item.body, 12.5f, COLOR_INK, false);
        body.setMaxLines(1);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, dp(3), 0, dp(3));
        content.addView(body, matchWrap());
        content.addView(text(item.detail, 11.5f, COLOR_MUTED, false));
        if (!last) {
            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(226, 234, 232));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dividerParams.setMargins(0, dp(6), 0, 0);
            content.addView(divider, dividerParams);
        }
        row.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private Button historyRetryButton(HistoryItem item) {
        Button resend = secondaryButton(
                ForwardScheduler.isRetryOnlyHistoryStatus(item.status)
                        ? "立即重试" : "重新转发");
        resend.setTextSize(11f);
        resend.setMinHeight(dp(MIN_TOUCH_DP));
        resend.setPadding(dp(9), dp(2), dp(9), dp(2));
        resend.setOnClickListener(view -> {
            if (item.id.startsWith("visual-")) {
                showToast("视觉测试记录不会写入发送队列");
            } else if (ForwardScheduler.isRetryOnlyHistoryStatus(item.status)) {
                if (ForwardScheduler.retryNow(this, item.id)) {
                    showToast("此条已设为立即重试");
                } else {
                    showToast("发送状态已变化，本次没有重复转发");
                }
                showPage(PAGE_HISTORY);
            } else if (QueueDatabase.get(this).requeue(item)) {
                ForwardScheduler.schedule(this);
                showToast("已重新加入加密发送队列");
                showPage(PAGE_HISTORY);
            } else {
                showToast("此条已经在待发队列中");
            }
        });
        return resend;
    }

    private void styleCheckBox(CheckBox checkBox) {
        checkBox.setTextColor(COLOR_INK);
        checkBox.setTextSize(14f);
        checkBox.setPadding(dp(2), dp(6), dp(2), dp(6));
        checkBox.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{COLOR_JADE, Color.rgb(155, 171, 176)}));
    }

    private void beginSoftTransition(ViewGroup group) {
        if (!MotionEffects.enabled(this)) {
            return;
        }
        AutoTransition transition = new AutoTransition();
        transition.setDuration(MotionEffects.EMPHASIZED);
        TransitionManager.beginDelayedTransition(group, transition);
    }

    private void tintCompoundDrawables(TextView view, int color) {
        for (Drawable drawable : view.getCompoundDrawables()) {
            if (drawable != null) {
                drawable.mutate().setTint(color);
            }
        }
    }

    private IconDrawable icon(Icon iconValue, int color, int sizeDp) {
        return new IconDrawable(this, iconValue).color(color).sizeDp(sizeDp);
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundStroke(int color, int stroke, int radiusDp) {
        GradientDrawable drawable = roundRect(color, radiusDp);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private Drawable glassSurface(int radiusDp) {
        GradientDrawable shade = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(255, 255, 255),
                        Color.rgb(241, 246, 244),
                        Color.rgb(226, 235, 232)
                });
        shade.setCornerRadius(dp(radiusDp));

        GradientDrawable rim = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(255, 255, 255),
                        Color.rgb(250, 252, 251),
                        Color.rgb(239, 245, 243)
                });
        rim.setCornerRadius(dp(Math.max(1, radiusDp - 1)));

        GradientDrawable pane = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(254, 255, 255, 255),
                        Color.argb(252, 255, 255, 255),
                        Color.argb(248, 252, 253, 252),
                        Color.argb(244, 248, 250, 249)
                });
        pane.setCornerRadius(dp(Math.max(1, radiusDp - 2)));
        pane.setStroke(dp(1), Color.WHITE);

        LayerDrawable glass = new LayerDrawable(new Drawable[]{shade, rim, pane});
        glass.setLayerInset(1, dp(1), dp(1), dp(1), dp(2));
        glass.setLayerInset(2, dp(2), dp(2), dp(2), dp(3));
        return glass;
    }

    private Drawable glassSelection() {
        GradientDrawable shade = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(255, 255, 255),
                        Color.rgb(159, 211, 200),
                        Color.rgb(205, 231, 225)
                });
        shade.setCornerRadius(dp(29));

        GradientDrawable rim = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.WHITE,
                        Color.rgb(218, 241, 235),
                        Color.rgb(165, 213, 203)
                });
        rim.setCornerRadius(dp(28));

        GradientDrawable lens = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(250, 255, 255, 255),
                        Color.argb(238, 229, 247, 242),
                        Color.argb(224, 172, 222, 211),
                        Color.argb(240, 238, 249, 246)
                });
        lens.setCornerRadius(dp(27));
        lens.setStroke(dp(1), Color.WHITE);

        LayerDrawable glass = new LayerDrawable(new Drawable[]{shade, rim, lens});
        glass.setLayerInset(1, dp(1), dp(1), dp(1), dp(2));
        glass.setLayerInset(2, dp(2), dp(2), dp(2), dp(3));
        return glass;
    }

    private Drawable jadeButtonSurface(int color) {
        GradientDrawable rim = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                color == COLOR_JADE
                        ? new int[]{Color.rgb(28, 110, 94), Color.rgb(18, 91, 78)}
                        : new int[]{color, color});
        rim.setCornerRadius(dp(25));
        GradientDrawable face = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                color == COLOR_JADE
                        ? new int[]{Color.rgb(58, 129, 111), Color.rgb(39, 118, 102), Color.rgb(24, 105, 90)}
                        : new int[]{color, color});
        face.setCornerRadius(dp(24));
        face.setStroke(dp(1), Color.argb(190, 255, 255, 255));
        LayerDrawable button = new LayerDrawable(new Drawable[]{rim, face});
        button.setLayerInset(1, dp(1), dp(1), dp(1), dp(2));
        return button;
    }

    private void applyGlassDepth(View view, float elevationDp, boolean raised) {
        view.setElevation(dp(Math.round(elevationDp)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setOutlineAmbientShadowColor(raised
                    ? Color.rgb(123, 179, 167)
                    : Color.rgb(188, 199, 196));
            view.setOutlineSpotShadowColor(raised
                    ? Color.rgb(87, 156, 141)
                    : Color.rgb(153, 170, 166));
        }
    }

    private Drawable paperSurface() {
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(255, 255, 255),
                        Color.rgb(252, 253, 253),
                        Color.rgb(248, 251, 250)
                });

        GradientDrawable upperGlow = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(22, 207, 237, 230), Color.TRANSPARENT});
        upperGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        upperGlow.setGradientCenter(0.08f, 0.05f);
        upperGlow.setGradientRadius(dp(330));

        GradientDrawable lowerGlow = new GradientDrawable(
                GradientDrawable.Orientation.BR_TL,
                new int[]{Color.argb(8, 178, 225, 214), Color.TRANSPARENT});
        lowerGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        lowerGlow.setGradientCenter(0.92f, 0.94f);
        lowerGlow.setGradientRadius(dp(360));

        return new LayerDrawable(new Drawable[]{base, upperGlow, lowerGlow});
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(6), 0, dp(10));
        return params;
    }

    private LinearLayout.LayoutParams compactCardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(3), 0, dp(4));
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * UI_SCALE * getResources().getDisplayMetrics().density);
    }

    private Dialog showGlassDialog(
            String title, String message, String positiveLabel,
            Runnable positiveAction, String negativeLabel) {
        TextView body = text(message, 14f, COLOR_MUTED, false);
        body.setPadding(dp(2), dp(3), dp(2), dp(5));
        body.setLineSpacing(dp(2), 1.08f);
        body.setMaxLines(16);
        body.setEllipsize(TextUtils.TruncateAt.END);
        return showGlassDialog(title, body, positiveLabel, positiveAction, negativeLabel);
    }

    private Dialog showGlassDialog(
            String title, View content, String positiveLabel,
            Runnable positiveAction, String negativeLabel) {
        return showGlassDialog(title, content, positiveLabel, positiveAction, negativeLabel, null);
    }

    private Dialog showGlassDialog(
            String title, String message, String positiveLabel,
            Runnable positiveAction, String negativeLabel, Runnable negativeAction) {
        TextView body = text(message, 14f, COLOR_MUTED, false);
        body.setPadding(dp(2), dp(3), dp(2), dp(5));
        body.setLineSpacing(dp(2), 1.08f);
        body.setMaxLines(16);
        body.setEllipsize(TextUtils.TruncateAt.END);
        return showGlassDialog(
                title, body, positiveLabel, positiveAction, negativeLabel, negativeAction);
    }

    private Dialog showGlassDialog(
            String title, View content, String positiveLabel,
            Runnable positiveAction, String negativeLabel, Runnable negativeAction) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(18));
        panel.setBackground(glassSurface(30));
        applyGlassDepth(panel, 18f, true);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout iconWell = new LinearLayout(this);
        iconWell.setGravity(Gravity.CENTER);
        iconWell.setBackground(glassSelection());
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(MaterialCommunityIcons.mdi_shield_outline, COLOR_JADE_DARK, 24));
        iconWell.addView(iconView, new LinearLayout.LayoutParams(dp(28), dp(28)));
        heading.addView(iconWell, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView headingText = text(title, 20f, COLOR_INK, true);
        headingText.setPadding(dp(12), 0, 0, 0);
        heading.addView(headingText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(heading, matchWrap());

        ScrollView bodyScroll = new ScrollView(this);
        bodyScroll.setFillViewport(false);
        bodyScroll.setClipToPadding(false);
        bodyScroll.setPadding(0, dp(14), 0, dp(13));
        bodyScroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams bodyParams = matchWrap();
        panel.addView(bodyScroll, bodyParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (negativeLabel != null && !negativeLabel.isBlank()) {
            Button negative = secondaryButton(negativeLabel);
            negative.setOnClickListener(view -> {
                dialog.dismiss();
                if (negativeAction != null) negativeAction.run();
            });
            LinearLayout.LayoutParams negativeParams = new LinearLayout.LayoutParams(
                    0, dp(MIN_TOUCH_DP), 1f);
            negativeParams.setMargins(0, 0, dp(8), 0);
            actions.addView(negative, negativeParams);
        }
        Button positive = actionButton(positiveLabel, COLOR_JADE);
        positive.setOnClickListener(view -> {
            dialog.dismiss();
            if (positiveAction != null) positiveAction.run();
        });
        actions.addView(positive, new LinearLayout.LayoutParams(
                0, dp(MIN_TOUCH_DP), 1f));
        panel.addView(actions, matchWrap());

        dialog.setContentView(panel);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = dialog.getWindow().getAttributes();
            attributes.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            attributes.dimAmount = 0.42f;
            dialog.getWindow().setAttributes(attributes);
        }
        dialog.setOnShowListener(ignored -> {
            View decor = dialog.getWindow() == null ? panel : dialog.getWindow().getDecorView();
            if (!MotionEffects.enabled(this)) {
                decor.setAlpha(1f);
                decor.setScaleX(1f);
                decor.setScaleY(1f);
                return;
            }
            decor.setAlpha(0f);
            decor.setScaleX(0.96f);
            decor.setScaleY(0.96f);
            decor.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220L)
                    .start();
        });
        dialog.show();
        return dialog;
    }

    private void showToast(String message) {
        Toast toast = new Toast(this);
        TextView banner = text(message, 13f, COLOR_INK, true);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(17), dp(12), dp(17), dp(12));
        banner.setCompoundDrawablePadding(dp(9));
        banner.setCompoundDrawablesWithIntrinsicBounds(
                icon(MaterialCommunityIcons.mdi_email_outline, COLOR_JADE_DARK, 21), null, null, null);
        banner.setBackground(glassSurface(24));
        applyGlassDepth(banner, 12f, true);
        toast.setView(banner);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(72));
        toast.setDuration(Toast.LENGTH_LONG);
        toast.show();
    }

    private static int strategyIndex(String value) {
        if (AppConfig.STRATEGY_PRIMARY_ONLY.equals(value)) {
            return 1;
        }
        if (AppConfig.STRATEGY_ALL.equals(value)) {
            return 2;
        }
        return 0;
    }

    private static String strategyValue(int index) {
        return index == 1 ? AppConfig.STRATEGY_PRIMARY_ONLY
                : index == 2 ? AppConfig.STRATEGY_ALL : AppConfig.STRATEGY_FAILOVER;
    }

    private static int ruleModeIndex(String value) {
        if (RuleConfig.MODE_OTP_ONLY.equals(value)) return 1;
        if (RuleConfig.MODE_NON_OTP.equals(value)) return 2;
        if (RuleConfig.MODE_MATCH.equals(value)) return 3;
        return 0;
    }

    private static String ruleModeLabel(String value) {
        if (RuleConfig.MODE_OTP_ONLY.equals(value)) return "仅验证码";
        if (RuleConfig.MODE_NON_OTP.equals(value)) return "排除验证码";
        if (RuleConfig.MODE_MATCH.equals(value)) return "仅匹配条件";
        return "全部短信";
    }

    private static String ruleModeValue(int index) {
        if (index == 1) return RuleConfig.MODE_OTP_ONLY;
        if (index == 2) return RuleConfig.MODE_NON_OTP;
        if (index == 3) return RuleConfig.MODE_MATCH;
        return RuleConfig.MODE_ALL;
    }

    private static int contentModeIndex(String value) {
        if (RuleConfig.CONTENT_CODE_ONLY.equals(value)) return 1;
        if (RuleConfig.CONTENT_MASKED.equals(value)) return 2;
        if (RuleConfig.CONTENT_METADATA.equals(value)) return 3;
        return 0;
    }

    private static String contentModeValue(int index) {
        if (index == 1) return RuleConfig.CONTENT_CODE_ONLY;
        if (index == 2) return RuleConfig.CONTENT_MASKED;
        if (index == 3) return RuleConfig.CONTENT_METADATA;
        return RuleConfig.CONTENT_FULL;
    }

    private static int parseTime(String value) {
        String[] parts = value.trim().split(":");
        if (parts.length != 2) return -1;
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59
                    ? hour * 60 + minute : -1;
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    private static String formatMinute(int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    private static String visualHistoryStatus(String status) {
        if ("SUCCESS".equals(status)) return "已发送";
        return statusLabel(status);
    }

    private static String visualHistoryTime(String id) {
        if ("visual-1".equals(id)) return "11:02";
        if ("visual-2".equals(id)) return "10:46";
        if ("visual-3".equals(id)) return "09:28";
        if ("visual-4".equals(id)) return "昨天 22:17";
        if ("visual-5".equals(id)) return "昨天 18:03";
        return "--:--";
    }

    private static String statusLabel(String status) {
        if ("SUCCESS".equals(status)) return "已送达 SMTP";
        if ("FILTERED".equals(status)) return "已按规则跳过";
        if ("SENDING".equals(status)) return "发送中";
        if ("RETRY_WAIT".equals(status)) return "等待重试";
        return "待发送";
    }

    private static int statusColor(String status) {
        if ("SUCCESS".equals(status)) return COLOR_JADE;
        if ("FILTERED".equals(status)) return COLOR_MUTED;
        if ("RETRY_WAIT".equals(status)) return COLOR_CINNABAR;
        return Color.rgb(176, 118, 38);
    }
}
