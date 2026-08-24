package com.server.smsforwarder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.MessagingException;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final int REQUEST_RECEIVE_SMS = 1001;
    private static final int REQUEST_EXPORT_CONFIG = 1002;
    private static final int REQUEST_IMPORT_CONFIG = 1003;
    private static final int PAGE_GUARDIAN = UiDestination.GUARDIAN;
    private static final int PAGE_EMAIL = UiDestination.EMAIL;
    private static final int PAGE_RULES = UiDestination.RULES;
    private static final int PAGE_HISTORY = UiDestination.HISTORY;
    private static final int PAGE_SETTINGS = UiDestination.SETTINGS;
    private static final int PAGE_SYSTEM_GUARDIAN = UiDestination.SYSTEM_GUARDIAN;
    private static final int PAGE_MAINTENANCE = UiDestination.MAINTENANCE;
    private static final int PAGE_ONBOARDING = UiDestination.ONBOARDING;

    private static final int COLOR_INK = Color.rgb(20, 35, 43);
    private static final int COLOR_JADE = Color.rgb(78, 141, 124);
    private static final int COLOR_JADE_DARK = Color.rgb(39, 104, 89);
    private static final int COLOR_JADE_SOFT = Color.rgb(184, 216, 207);
    private static final int COLOR_CINNABAR = Color.rgb(201, 75, 61);
    private static final int COLOR_AMBER = Color.rgb(205, 132, 31);
    private static final int COLOR_PAPER = Color.rgb(244, 247, 246);
    private static final int COLOR_CARD = Color.rgb(249, 251, 250);
    private static final int COLOR_INSET = Color.rgb(237, 243, 241);
    private static final int COLOR_GLASS = Color.argb(224, 252, 253, 253);
    private static final int COLOR_GLASS_BORDER = Color.rgb(220, 234, 230);
    private static final int COLOR_MUTED = Color.rgb(93, 108, 116);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout page;
    private LinearLayout navigation;
    private ScrollView pageScroll;
    private int currentPage;
    private boolean enableAfterPermission;
    private boolean updateDialogShowing;

    private EditText primaryHost;
    private EditText primaryPort;
    private Spinner primarySecurity;
    private EditText primaryUsername;
    private EditText primaryPassword;
    private EditText primaryFrom;
    private EditText primaryRecipients;
    private CheckBox backupEnabled;
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
    private CheckBox scheduleEnabled;
    private EditText scheduleStart;
    private EditText scheduleEnd;
    private CheckBox[] weekdays;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(COLOR_PAPER);
        getWindow().setNavigationBarColor(COLOR_PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildShell());
        showPage(PAGE_GUARDIAN);
        checkForUpdates(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (page != null) {
            showPage(currentPage);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_RECEIVE_SMS) {
            return;
        }
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (granted && enableAfterPermission) {
            AppConfig.setEnabled(this, true);
            AppConfig.setStatus(this, "自动转发已启用，等待新短信");
        } else if (!granted) {
            showToast("未获得短信权限，自动转发没有启用");
        }
        enableAfterPermission = false;
        showPage(PAGE_GUARDIAN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
        shell.setBackgroundColor(COLOR_PAPER);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.setBackground(roundStroke(COLOR_GLASS, COLOR_GLASS_BORDER, 24));
        header.setElevation(dp(5));
        ImageView seal = new ImageView(this);
        seal.setImageResource(com.server.smsforwarder.R.drawable.yanjian_app_icon);
        seal.setScaleType(ImageView.ScaleType.CENTER_CROP);
        seal.setContentDescription("雁笺应用图标");
        header.addView(seal, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12), 0, 0, 0);
        TextView brandName = text("雁笺", 24f, COLOR_INK, true);
        brandName.setTypeface(Typeface.SERIF, Typeface.BOLD);
        brand.addView(brandName);
        brand.addView(text("一纸远书 · 短信直达邮箱", 12f, COLOR_MUTED, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams headerParams = matchWrap();
        headerParams.setMargins(dp(16), dp(10), dp(16), dp(8));
        shell.addView(header, headerParams);

        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setClipToPadding(false);
        pageScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(12), dp(18), dp(22));
        pageScroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(pageScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(6), dp(6), dp(6), dp(6));
        navigation.setBackground(roundStroke(COLOR_GLASS, COLOR_GLASS_BORDER, 30));
        navigation.setElevation(dp(14));
        addNavigationButton("记录", android.R.drawable.ic_menu_agenda, PAGE_HISTORY);
        addNavigationButton("规则", android.R.drawable.ic_menu_sort_by_size, PAGE_RULES);
        addNavigationButton("守护", android.R.drawable.ic_lock_idle_lock, PAGE_GUARDIAN);
        addNavigationButton("设置", android.R.drawable.ic_menu_manage, PAGE_SETTINGS);
        LinearLayout.LayoutParams navParams = matchWrap();
        navParams.setMargins(dp(14), dp(6), dp(14), dp(10));
        shell.addView(navigation, navParams);
        return shell;
    }

    private void addNavigationButton(String label, int iconRes, int pageIndex) {
        Button button = new Button(this);
        button.setTag(pageIndex);
        button.setText(label);
        button.setTextSize(12f);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(6), dp(7), dp(6), dp(7));
        button.setCompoundDrawablePadding(dp(3));
        button.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
        button.setContentDescription(label + "页面");
        button.setOnClickListener(view -> showPage(pageIndex));
        MotionEffects.bindPress(button);
        navigation.addView(button, new LinearLayout.LayoutParams(
                0,
                dp(62),
                1f));
    }

    private void showPage(int index) {
        currentPage = index;
        page.removeAllViews();
        pageScroll.scrollTo(0, 0);
        int selectedRoot = rootPage(index);
        for (int i = 0; i < navigation.getChildCount(); i++) {
            Button button = (Button) navigation.getChildAt(i);
            boolean selected = ((Integer) button.getTag()) == selectedRoot;
            button.setTextColor(selected ? COLOR_JADE_DARK : COLOR_MUTED);
            tintCompoundDrawables(button, selected ? COLOR_JADE_DARK : COLOR_MUTED);
            button.setBackground(selected
                    ? roundStroke(Color.argb(190, 221, 239, 234), Color.WHITE, 24)
                    : roundRect(Color.TRANSPARENT, 24));
            button.setSelected(selected);
            MotionEffects.select(button, selected, dp(2));
        }
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
            default:
                showOverviewPage();
                break;
        }
        MotionEffects.enterPage(pageScroll, dp(12));
    }

    private static int rootPage(int pageIndex) {
        return UiDestination.root(pageIndex);
    }

    private void showOverviewPage() {
        DeviceHealth health = DeviceHealth.inspect(this);
        boolean guardEnabled = TravelGuard.isEnabled(this);
        addPageTitle("旅行守护", "离家前确认状态，故障短信会加密保存并自动补发", page);

        LinearLayout statusCard = card();
        int statusColor = health.readyForTravel() ? COLOR_JADE
                : guardEnabled ? COLOR_CINNABAR : COLOR_AMBER;
        TextView status = text(
                health.readyForTravel() && guardEnabled ? "守护运行中" : "尚未满足离家条件",
                25f,
                statusColor,
                true);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        statusCard.addView(status);
        TextView recent = text(AppConfig.getLastStatus(this), 13f, COLOR_MUTED, false);
        recent.setGravity(Gravity.CENTER_HORIZONTAL);
        recent.setPadding(0, dp(6), 0, dp(14));
        statusCard.addView(recent);

        LinearLayout readiness = new LinearLayout(this);
        readiness.setOrientation(LinearLayout.VERTICAL);
        readiness.setPadding(dp(4), dp(4), dp(4), dp(2));
        readiness.setBackground(insetSurface(20));
        LinearLayout readinessTop = new LinearLayout(this);
        readinessTop.addView(readinessCell("短信权限", health.smsPermission ? "已授权" : "未授权", health.smsPermission), weightedWrap());
        readinessTop.addView(readinessCell("后台运行", health.batteryExempt && health.backgroundConfirmed ? "已允许" : "待完成", health.batteryExempt && health.backgroundConfirmed), weightedWrap());
        LinearLayout readinessBottom = new LinearLayout(this);
        readinessBottom.addView(readinessCell("SMTP", health.smtpValid ? "已连接" : "待配置", health.smtpValid), weightedWrap());
        readinessBottom.addView(readinessCell("真实短信", health.lastSmsForwardedAt > 0L ? "已验证" : "待验证", health.lastSmsForwardedAt > 0L), weightedWrap());
        readiness.addView(readinessTop, matchWrap());
        readiness.addView(readinessBottom, matchWrap());
        statusCard.addView(readiness, matchWrap());
        page.addView(statusCard, cardParams());

        LinearLayout actions = card();
        actions.addView(sectionTitle("转发控制"));
        AppConfig config = AppConfig.load(this);
        Button enable = actionButton(config.enabled ? "自动转发已启用" : "启用自动转发", COLOR_JADE);
        enable.setEnabled(!config.enabled);
        enable.setOnClickListener(view -> requestEnableForwarding());
        actions.addView(enable, matchWrap());
        Button pause = secondaryButton("暂停自动转发");
        pause.setOnClickListener(view -> {
            AppConfig.setEnabled(this, false);
            TravelGuard.setEnabled(this, false);
            AppConfig.setStatus(this, "自动转发已由用户暂停");
            showPage(PAGE_GUARDIAN);
        });
        actions.addView(pause, matchWrap());
        Button guard = secondaryButton(guardEnabled ? "旅行守护已开启 · 点击关闭" : "开启旅行守护");
        guard.setOnClickListener(view -> toggleTravelGuard());
        actions.addView(guard, matchWrap());
        page.addView(actions, cardParams());

        LinearLayout tools = card();
        tools.addView(sectionTitle("离家前动作"));
        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setGravity(Gravity.CENTER);
        Button test = secondaryButton("测试邮件");
        test.setOnClickListener(view -> testConfiguredProfile(false));
        toolRow.addView(test, weightedWrap());
        Button heartbeat = secondaryButton("发送心跳");
        heartbeat.setOnClickListener(view -> {
            if (!AppConfig.load(this).enabled) {
                showToast("请先启用自动转发");
                return;
            }
            TravelGuard.enqueueHeartbeatNow(this, "旅行前手动自检", false);
            showToast("状态心跳已进入发送队列");
            showPage(PAGE_GUARDIAN);
        });
        toolRow.addView(heartbeat, weightedWrap());
        Button retry = secondaryButton("重试队列");
        retry.setOnClickListener(view -> {
            ForwardScheduler.schedule(this);
            showToast("已请求立即重试");
        });
        toolRow.addView(retry, weightedWrap());
        tools.addView(toolRow, matchWrap());
        TextView queue = navigationRow("队列状态", "待发 " + health.pendingCount + " 条", android.R.drawable.ic_menu_recent_history);
        queue.setOnClickListener(view -> showPage(PAGE_HISTORY));
        tools.addView(queue, matchWrap());
        page.addView(tools, cardParams());

        addNotice(
                "手机关机、无网络、SIM 无服务或被系统强制停止时，普通 App 无法继续转发。旅行期间建议持续充电，并同时开启 Wi-Fi 与移动数据。",
                page);
    }

    private void showEmailPage() {
        AppConfig config = AppConfig.load(this);
        addSubPageTitle("邮箱通道", "直连你的 SMTP，授权码仅加密保存在本机", PAGE_SETTINGS);

        LinearLayout primary = card();
        primary.addView(sectionTitle("主通道"));
        addProviderPreset(primary, true);
        primaryHost = input(primary, "SMTP 主机，例如 smtp.qq.com", InputType.TYPE_CLASS_TEXT);
        primaryPort = input(primary, "端口，例如 465", InputType.TYPE_CLASS_NUMBER);
        primarySecurity = securitySpinner(primary);
        primaryUsername = input(primary, "SMTP 用户名", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        primaryPassword = input(primary, "授权码 / 应用专用密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        primaryFrom = input(primary, "发件邮箱", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        primaryRecipients = input(primary, "收件邮箱，可用逗号或换行填写多个", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        primaryRecipients.setMinLines(2);
        fillProfile(config.primaryProfile(), primaryHost, primaryPort, primarySecurity, primaryUsername, primaryPassword, primaryFrom, primaryRecipients);
        page.addView(primary, cardParams());

        LinearLayout strategyCard = card();
        backupEnabled = new CheckBox(this);
        backupEnabled.setText("启用独立备用 SMTP 通道");
        backupEnabled.setChecked(config.backupEnabled);
        styleCheckBox(backupEnabled);
        strategyCard.addView(backupEnabled);
        dispatchStrategy = spinner(strategyCard, new String[]{"主通道成功即止，失败切备用", "仅使用主通道", "主备通道都发送"});
        dispatchStrategy.setSelection(strategyIndex(config.dispatchStrategy));
        backupFields = new LinearLayout(this);
        backupFields.setOrientation(LinearLayout.VERTICAL);
        backupFields.addView(sectionTitle("备用通道"));
        addProviderPreset(backupFields, false);
        backupHost = input(backupFields, "备用 SMTP 主机", InputType.TYPE_CLASS_TEXT);
        backupPort = input(backupFields, "备用端口", InputType.TYPE_CLASS_NUMBER);
        backupSecurity = securitySpinner(backupFields);
        backupUsername = input(backupFields, "备用 SMTP 用户名", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        backupPassword = input(backupFields, "备用授权码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        backupFrom = input(backupFields, "备用发件邮箱", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        backupRecipients = input(backupFields, "备用收件邮箱，可填写多个", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        fillProfile(config.backupProfile(), backupHost, backupPort, backupSecurity, backupUsername, backupPassword, backupFrom, backupRecipients);
        backupFields.setVisibility(config.backupEnabled ? View.VISIBLE : View.GONE);
        backupEnabled.setOnCheckedChangeListener((button, checked) -> {
            beginSoftTransition(strategyCard);
            backupFields.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        strategyCard.addView(backupFields);
        page.addView(strategyCard, cardParams());

        LinearLayout privacy = card();
        privacyConsent = new CheckBox(this);
        privacyConsent.setText("我理解短信可能包含验证码、账户和个人信息，并同意发送至上述邮箱");
        privacyConsent.setChecked(config.privacyConsent);
        styleCheckBox(privacyConsent);
        privacy.addView(privacyConsent);
        addNotice("请使用邮箱服务生成的授权码，不要填写网页登录密码。仅允许 TLS 或强制 STARTTLS。", privacy);
        page.addView(privacy, cardParams());

        Button save = actionButton("保存邮箱配置", COLOR_JADE);
        save.setOnClickListener(view -> saveEmailConfiguration());
        page.addView(save, matchWrap());
        Button testPrimary = secondaryButton("测试主通道");
        testPrimary.setOnClickListener(view -> {
            if (saveEmailConfiguration()) {
                testProfile(AppConfig.load(this).primaryProfile(), "主通道");
            }
        });
        page.addView(testPrimary, matchWrap());
        Button testBackup = secondaryButton("测试备用通道");
        testBackup.setVisibility(config.backupEnabled ? View.VISIBLE : View.GONE);
        backupEnabled.setOnCheckedChangeListener((button, checked) -> {
            beginSoftTransition(strategyCard);
            backupFields.setVisibility(checked ? View.VISIBLE : View.GONE);
            testBackup.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        testBackup.setOnClickListener(view -> {
            if (saveEmailConfiguration()) {
                testProfile(AppConfig.load(this).backupProfile(), "备用通道");
            }
        });
        page.addView(testBackup, matchWrap());
    }

    private void showRulesPage() {
        RuleConfig rules = RuleConfig.load(this);
        addPageTitle("转发规则", "按时段、SIM、发送方与正文依次判断", page);
        addStatusChip("当前规则：" + ruleModeLabel(rules.mode) + " · " + (rules.simSlot < 0 ? "全部 SIM" : "SIM " + (rules.simSlot + 1)), page);

        LinearLayout modeCard = card();
        modeCard.addView(sectionTitle("范围与隐私"));
        ruleMode = spinner(modeCard, new String[]{"全部短信", "仅验证码", "排除验证码", "仅匹配下方条件"});
        ruleMode.setSelection(ruleModeIndex(rules.mode));
        contentMode = spinner(modeCard, new String[]{"完整正文", "仅提取验证码", "隐藏连续数字", "只发元数据"});
        contentMode.setSelection(contentModeIndex(rules.contentMode));
        simRule = spinner(modeCard, new String[]{"全部 SIM", "仅 SIM 1", "仅 SIM 2"});
        simRule.setSelection(rules.simSlot < 0 ? 0 : rules.simSlot + 1);
        page.addView(modeCard, cardParams());

        LinearLayout senderCard = card();
        senderCard.addView(sectionTitle("发送方"));
        senderAllow = input(senderCard, "白名单：号码、1069* 或 re:正则；留空不限制", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        senderBlock = input(senderCard, "黑名单：优先于白名单", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        senderAllow.setText(rules.senderAllow);
        senderBlock.setText(rules.senderBlock);
        page.addView(senderCard, cardParams());

        LinearLayout bodyCard = card();
        bodyCard.addView(sectionTitle("正文匹配"));
        bodyInclude = input(bodyCard, "必须包含的关键词，逗号或换行分隔", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bodyExclude = input(bodyCard, "排除关键词", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bodyRegex = input(bodyCard, "正文正则表达式（可选）", InputType.TYPE_CLASS_TEXT);
        includeAll = new CheckBox(this);
        includeAll.setText("所有包含关键词必须同时出现");
        includeAll.setChecked(rules.includeAll);
        styleCheckBox(includeAll);
        bodyCard.addView(includeAll);
        bodyInclude.setText(rules.bodyInclude);
        bodyExclude.setText(rules.bodyExclude);
        bodyRegex.setText(rules.bodyRegex);
        page.addView(bodyCard, cardParams());

        LinearLayout scheduleCard = card();
        scheduleCard.addView(sectionTitle("生效时段"));
        scheduleEnabled = new CheckBox(this);
        scheduleEnabled.setText("仅在指定时段转发");
        scheduleEnabled.setChecked(rules.scheduleEnabled);
        styleCheckBox(scheduleEnabled);
        scheduleCard.addView(scheduleEnabled);
        scheduleStart = input(scheduleCard, "开始时间 HH:mm", InputType.TYPE_CLASS_DATETIME);
        scheduleEnd = input(scheduleCard, "结束时间 HH:mm；相同表示全天", InputType.TYPE_CLASS_DATETIME);
        scheduleStart.setText(formatMinute(rules.startMinute));
        scheduleEnd.setText(formatMinute(rules.endMinute));
        LinearLayout dayRow = new LinearLayout(this);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        weekdays = new CheckBox[7];
        String[] dayNames = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < weekdays.length; i++) {
            weekdays[i] = new CheckBox(this);
            weekdays[i].setText(dayNames[i]);
            weekdays[i].setChecked((rules.weekdayMask & (1 << i)) != 0);
            styleCheckBox(weekdays[i]);
            dayRow.addView(weekdays[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        scheduleCard.addView(dayRow);
        page.addView(scheduleCard, cardParams());

        Button save = actionButton("保存规则", COLOR_JADE);
        save.setOnClickListener(view -> saveRules());
        page.addView(save, matchWrap());
        Button preview = secondaryButton("用示例短信测试当前规则");
        preview.setOnClickListener(view -> previewRules());
        page.addView(preview, matchWrap());
    }

    private void showHistoryPage() {
        addPageTitle("转发记录", "正文与发送方加密保存在本机", page);
        List<HistoryItem> history = QueueDatabase.get(this).recentHistory(50);
        addHistorySummary(history);
        Button retry = actionButton("立即重试全部待发短信", COLOR_JADE);
        retry.setOnClickListener(view -> {
            ForwardScheduler.schedule(this);
            showToast("已安排重试");
        });
        page.addView(retry, matchWrap());

        if (history.isEmpty()) {
            addNotice("还没有短信记录。完成一条真实短信测试后，这里会显示接收、过滤、重试和成功状态。", page);
        } else {
            SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
            for (HistoryItem item : history) {
                LinearLayout record = card();
                record.addView(text(statusLabel(item.status) + " · " + format.format(new Date(item.receivedAt)), 15f, statusColor(item.status), true));
                record.addView(text(item.sender + (item.simSlot >= 0 ? " · SIM " + (item.simSlot + 1) : ""), 14f, COLOR_INK, false));
                String body = item.body.length() > 180 ? item.body.substring(0, 180) + "…" : item.body;
                TextView bodyView = text(body, 14f, COLOR_MUTED, false);
                bodyView.setPadding(0, dp(6), 0, dp(4));
                record.addView(bodyView);
                record.addView(text(item.detail + (item.attempts > 0 ? " · 已尝试 " + item.attempts + " 次" : ""), 12f, COLOR_MUTED, false));
                Button resend = secondaryButton("重新转发此条");
                resend.setOnClickListener(view -> {
                    if (QueueDatabase.get(this).requeue(item)) {
                        ForwardScheduler.schedule(this);
                        showToast("已重新加入加密发送队列");
                        showPage(PAGE_HISTORY);
                    } else {
                        showToast("此条已经在待发队列中");
                    }
                });
                record.addView(resend, matchWrap());
                page.addView(record, cardParams());
            }
        }
        Button clear = secondaryButton("清空历史记录");
        clear.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("清空历史记录？")
                .setMessage("不会删除仍在待发队列中的短信。历史记录删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清空", (dialog, which) -> {
                    QueueDatabase.get(this).clearHistory();
                    showPage(PAGE_HISTORY);
                }).show());
        page.addView(clear, matchWrap());
    }

    private void showSettingsPage() {
        addPageTitle("设置", "邮箱、守护、隐私与应用维护", page);
        addStatusChip("所有配置仅加密保存在本机", page);

        AppConfig config = AppConfig.load(this);
        RuleConfig rules = RuleConfig.load(this);
        DeviceHealth health = DeviceHealth.inspect(this);

        LinearLayout forwarding = card();
        forwarding.addView(sectionTitle("转发配置"));
        forwarding.addView(settingsRow(
                "邮箱通道",
                config.primaryProfile().validate() == null ? "主通道已配置" : "等待完成配置",
                android.R.drawable.ic_dialog_email,
                () -> showPage(PAGE_EMAIL)));
        forwarding.addView(settingsRow(
                "转发规则",
                ruleModeLabel(rules.mode) + " · " + (rules.simSlot < 0 ? "全部 SIM" : "SIM " + (rules.simSlot + 1)),
                android.R.drawable.ic_menu_sort_by_size,
                () -> showPage(PAGE_RULES)));
        page.addView(forwarding, cardParams());

        LinearLayout guardian = card();
        guardian.addView(sectionTitle("系统守护"));
        guardian.addView(settingsRow(
                "后台授权",
                health.backgroundConfirmed && health.batteryExempt ? "关键授权已完成" : "仍有项目待完成",
                android.R.drawable.ic_lock_idle_lock,
                () -> showPage(PAGE_SYSTEM_GUARDIAN)));
        guardian.addView(settingsRow(
                "状态心跳",
                "每 " + TravelGuard.heartbeatHours(this) + " 小时",
                android.R.drawable.ic_popup_sync,
                () -> showPage(PAGE_SYSTEM_GUARDIAN)));
        page.addView(guardian, cardParams());

        LinearLayout data = card();
        data.addView(sectionTitle("数据与应用"));
        data.addView(settingsRow(
                "隐私与安全",
                "本机加密 · 禁止系统截图",
                android.R.drawable.ic_secure,
                () -> showPage(PAGE_MAINTENANCE)));
        data.addView(settingsRow(
                "配置迁移",
                "无密码导入与导出",
                android.R.drawable.ic_menu_save,
                () -> showPage(PAGE_MAINTENANCE)));
        data.addView(settingsRow(
                "维护与诊断",
                "当前 " + BuildConfig.VERSION_NAME,
                android.R.drawable.ic_menu_info_details,
                () -> showPage(PAGE_MAINTENANCE)));
        page.addView(data, cardParams());

        addNotice("应用不申请通讯录、历史短信、通话记录、通知读取、无障碍或 Root 权限。", page);
    }

    private void showSystemGuardianPage() {
        addSubPageTitle("系统守护", "完成后台授权，提高锁屏与长期运行可靠性", PAGE_SETTINGS);
        DeviceHealth health = DeviceHealth.inspect(this);
        int completed = (health.smsPermission ? 1 : 0)
                + (health.batteryExempt ? 1 : 0)
                + (health.backgroundConfirmed ? 3 : 0)
                + (health.connected ? 1 : 0);

        LinearLayout progress = card();
        TextView progressTitle = text("后台守护  " + completed + " / 6  已完成", 22f,
                completed == 6 ? COLOR_JADE_DARK : COLOR_AMBER, true);
        progressTitle.setGravity(Gravity.CENTER);
        progress.addView(progressTitle);
        TextView progressLine = new TextView(this);
        progressLine.setBackground(roundRect(completed == 6 ? COLOR_JADE : COLOR_AMBER, 3));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                Math.max(dp(40), dp(44) * completed), dp(6));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(12), 0, dp(2));
        progress.addView(progressLine, progressParams);
        page.addView(progress, cardParams());

        LinearLayout guide = card();
        guide.addView(sectionTitle("后台授权清单"));
        guide.addView(statusRow("接收短信权限", health.smsPermission ? "已允许" : "待允许", health.smsPermission));
        guide.addView(statusRow("关闭电池优化", health.batteryExempt ? "已完成" : "待完成", health.batteryExempt));
        guide.addView(statusRow("允许自启动", health.backgroundConfirmed ? "已完成" : "待确认", health.backgroundConfirmed));
        guide.addView(statusRow("允许关联启动", health.backgroundConfirmed ? "已完成" : "待确认", health.backgroundConfirmed));
        guide.addView(statusRow("允许后台活动", health.backgroundConfirmed ? "已完成" : "待确认", health.backgroundConfirmed));
        guide.addView(statusRow("锁屏保持网络", health.connected ? "当前可用" : "待检查", health.connected));
        page.addView(guide, cardParams());

        Button continueGuide = actionButton("继续完成授权", COLOR_JADE);
        continueGuide.setOnClickListener(view -> showPage(PAGE_ONBOARDING));
        page.addView(continueGuide, matchWrap());
        Button appSettings = secondaryButton("打开应用详情设置");
        appSettings.setOnClickListener(view -> openAppSettings());
        page.addView(appSettings, matchWrap());

        LinearLayout heartbeatCard = card();
        heartbeatCard.addView(sectionTitle("状态心跳"));
        Spinner hours = spinner(heartbeatCard, new String[]{"每 6 小时", "每 12 小时", "每 24 小时"});
        int current = TravelGuard.heartbeatHours(this);
        hours.setSelection(current == 6 ? 0 : current == 24 ? 2 : 1);
        heartbeatCard.addView(text("低电量与断电异常会随心跳提醒", 13f, COLOR_MUTED, false));
        CheckBox confirmed = new CheckBox(this);
        confirmed.setText("我已完成华为应用启动管理设置");
        confirmed.setChecked(TravelGuard.isBackgroundConfirmed(this));
        confirmed.setOnCheckedChangeListener((button, checked) -> TravelGuard.setBackgroundConfirmed(this, checked));
        styleCheckBox(confirmed);
        heartbeatCard.addView(confirmed);
        Button save = secondaryButton("保存心跳设置");
        save.setOnClickListener(view -> {
            int selected = hours.getSelectedItemPosition() == 0 ? 6 : hours.getSelectedItemPosition() == 2 ? 24 : 12;
            TravelGuard.setHeartbeatHours(this, selected);
            showToast("心跳间隔已保存");
        });
        heartbeatCard.addView(save, matchWrap());
        page.addView(heartbeatCard, cardParams());

        addNotice("普通 App 无法承诺永不被系统回收；完成授权、保持联网并通过真实短信闭环可以显著提高可靠性。", page);
    }

    private void showOnboardingPage() {
        addSubPageTitle("允许系统唤醒雁笺", "完成后台授权，锁屏后也能持续转发", PAGE_SYSTEM_GUARDIAN);
        addStatusChip("守护设置 · 第 3 步，共 4 步", page);

        LinearLayout path = card();
        path.addView(sectionTitle("设置路径"));
        path.addView(text("设置  ›  应用和服务  ›  应用启动管理  ›  雁笺", 15f, COLOR_INK, true));
        path.addView(text("部分系统名称可能略有差异，请以手机实际页面为准。", 12f, COLOR_MUTED, false));
        page.addView(path, cardParams());

        LinearLayout permissions = card();
        permissions.addView(statusRow("允许自启动", "用户操作", TravelGuard.isBackgroundConfirmed(this)));
        permissions.addView(statusRow("允许关联启动", "用户操作", TravelGuard.isBackgroundConfirmed(this)));
        permissions.addView(statusRow("允许后台活动", "用户操作", TravelGuard.isBackgroundConfirmed(this)));
        page.addView(permissions, cardParams());

        Button launch = actionButton("打开雁笺的应用设置", COLOR_JADE);
        launch.setOnClickListener(view -> openHuaweiLaunchSettings());
        page.addView(launch, matchWrap());
        Button battery = secondaryButton("打开电池优化设置");
        battery.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        page.addView(battery, matchWrap());
        Button done = secondaryButton("我已完成");
        done.setOnClickListener(view -> {
            TravelGuard.setBackgroundConfirmed(this, true);
            showToast("后台授权已由你确认，请继续完成锁屏试投");
            showPage(PAGE_SYSTEM_GUARDIAN);
        });
        page.addView(done, matchWrap());
        addNotice("仍然延迟？请检查休眠时网络连接，并完成下一步锁屏试投。", page);
    }

    private void showMaintenancePage() {
        addSubPageTitle("维护与诊断", "排查问题、迁移配置并安全更新", PAGE_SETTINGS);
        DeviceHealth health = DeviceHealth.inspect(this);
        LinearLayout status = card();
        status.addView(sectionTitle("运行状态"));
        status.addView(text(health.readyForTravel() ? "良好" : "仍有项目待完成", 24f,
                health.readyForTravel() ? COLOR_JADE_DARK : COLOR_AMBER, true));
        status.addView(text("队列 " + health.pendingCount + " · " + health.networkLabel
                + " · SMTP " + (health.smtpValid ? "已配置" : "待配置"), 13f, COLOR_MUTED, false));
        Button diagnostics = secondaryButton("分享脱敏诊断报告");
        diagnostics.setOnClickListener(view -> shareDiagnostics());
        status.addView(diagnostics, matchWrap());
        page.addView(status, cardParams());

        LinearLayout privacy = card();
        privacy.addView(sectionTitle("隐私与清理"));
        privacy.addView(text("应用不申请通讯录、历史短信、通话记录、通知读取、无障碍或 Root 权限。界面禁止系统截图。", 14f, COLOR_MUTED, false));
        Button clearQueue = secondaryButton("清空本机待发送队列");
        clearQueue.setOnClickListener(view -> confirmClearQueue());
        privacy.addView(clearQueue, matchWrap());
        page.addView(privacy, cardParams());

        LinearLayout backup = card();
        backup.addView(sectionTitle("配置迁移"));
        backup.addView(text("导出文件包含邮箱地址、规则和服务器地址，但不包含 SMTP 授权码、短信正文或历史记录。", 14f, COLOR_MUTED, false));
        Button export = secondaryButton("导出无密码配置");
        export.setOnClickListener(view -> exportConfiguration());
        backup.addView(export, matchWrap());
        Button importButton = secondaryButton("导入配置");
        importButton.setOnClickListener(view -> importConfiguration());
        backup.addView(importButton, matchWrap());
        page.addView(backup, cardParams());

        LinearLayout updates = card();
        updates.addView(sectionTitle("版本更新"));
        updates.addView(text(
                "当前版本：" + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）",
                14f,
                COLOR_MUTED,
                false));
        CheckBox automaticUpdates = new CheckBox(this);
        automaticUpdates.setText("启动时每天最多检查一次 GitHub Releases");
        automaticUpdates.setChecked(UpdateChecker.isAutomaticEnabled(this));
        styleCheckBox(automaticUpdates);
        automaticUpdates.setOnCheckedChangeListener(
                (button, checked) -> UpdateChecker.setAutomaticEnabled(this, checked));
        updates.addView(automaticUpdates);
        Button checkUpdate = secondaryButton("立即检查更新");
        checkUpdate.setOnClickListener(view -> checkForUpdates(true));
        updates.addView(checkUpdate, matchWrap());
        updates.addView(text(
                "自动检查只访问本项目公开 GitHub Release，不上传短信、邮箱配置或设备标识；安装仍由系统确认。",
                13f,
                COLOR_MUTED,
                false));
        page.addView(updates, cardParams());

        addNotice("强制停止、关机、重启后尚未首次解锁、SIM 失去服务或 SMTP 服务商延迟都超出普通 App 的控制范围。", page);
    }

    private void requestEnableForwarding() {
        AppConfig config = AppConfig.load(this);
        String error = config.validateForForwarding();
        if (error != null) {
            showToast("请先在“邮箱”页完成配置：" + error);
            return;
        }
        String ruleError = RuleConfig.load(this).validate();
        if (ruleError != null) {
            showToast("请先修正规则：" + ruleError);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            enableAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, REQUEST_RECEIVE_SMS);
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
            new AlertDialog.Builder(this)
                    .setTitle("尚未满足离家条件")
                    .setMessage(health.summary() + "\n\n请先补齐未完成项，再开启旅行守护。")
                    .setPositiveButton("知道了", null)
                    .show();
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
        showToast("邮箱配置已保存");
        return true;
    }

    private void testConfiguredProfile(boolean backup) {
        AppConfig config = AppConfig.load(this);
        String error = config.validateForForwarding();
        if (error != null) {
            showToast("邮箱配置不完整：" + error);
            return;
        }
        testProfile(backup ? config.backupProfile() : config.primaryProfile(), backup ? "备用通道" : "主通道");
    }

    private void testProfile(SmtpProfile profile, String label) {
        showToast("正在测试" + label + "…");
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
                result = label + "测试失败：" + ForwardProcessor.safeMessage(error);
                success = false;
            }
            if (success) {
                AppConfig.setSuccess(this, result);
            } else {
                AppConfig.setStatus(this, result);
            }
            String message = result;
            runOnUiThread(() -> {
                showToast(message);
                if (currentPage == 0) {
                    showPage(PAGE_GUARDIAN);
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
        new AlertDialog.Builder(this)
                .setTitle("规则测试")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("测试", (dialog, which) -> {
                    RuleConfig config = RuleConfig.load(this);
                    MessageFilter.Decision decision = MessageFilter.decide(
                            sender.getText().toString(),
                            body.getText().toString(),
                            simRule.getSelectedItemPosition() - 1,
                            System.currentTimeMillis(),
                            config);
                    new AlertDialog.Builder(this)
                            .setTitle(decision == MessageFilter.Decision.FORWARD ? "会转发" : "不会转发")
                            .setMessage(decision == MessageFilter.Decision.FORWARD
                                    ? "邮件正文预览：\n\n" + MessageFilter.transformBody(body.getText().toString(), config)
                                    : "命中结果：" + decision.name())
                            .setPositiveButton("知道了", null)
                            .show();
                }).show();
    }

    private void confirmClearQueue() {
        int count = QueueDatabase.get(this).count();
        if (count == 0) {
            showToast("当前没有待发送短信");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空待发送队列？")
                .setMessage("将删除 " + count + " 条尚未成功发送的加密消息，删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认删除", (dialog, which) -> {
                    QueueDatabase.get(this).clear();
                    AppConfig.setStatus(this, "待发送队列已由用户清空");
                    showPage(currentPage);
                }).show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void checkForUpdates(boolean manual) {
        if (!manual && !UpdateChecker.beginAutomaticCheck(this, System.currentTimeMillis())) {
            return;
        }
        if (manual) {
            showToast("正在检查 GitHub 正式版本…");
        }
        executor.execute(() -> {
            UpdateChecker.ReleaseInfo release = null;
            String errorMessage = null;
            try {
                release = UpdateChecker.fetchLatest();
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
                        showToast("项目尚未发布稳定版本");
                    }
                    return;
                }
                if (!UpdateChecker.isNewer(result.version, BuildConfig.VERSION_NAME)) {
                    if (manual) {
                        showToast("当前已经是最新稳定版本");
                    }
                    return;
                }
                showUpdateDialog(result);
            });
        });
    }

    private void showUpdateDialog(UpdateChecker.ReleaseInfo release) {
        if (updateDialogShowing) {
            return;
        }
        updateDialogShowing = true;
        String notes = release.notes.isEmpty() ? "请前往项目官方发布页查看更新内容。" : release.notes;
        new AlertDialog.Builder(this)
                .setTitle("发现雁笺 " + release.version)
                .setMessage(notes
                        + "\n\n将打开项目官方 GitHub Release。下载和安装都需要你确认，Android 会校验 APK 签名。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("查看正式发布", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl));
                    startActivity(intent);
                })
                .setOnDismissListener(dialog -> updateDialogShowing = false)
                .show();
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

    private void openHuaweiLaunchSettings() {
        try {
            startActivity(new Intent("huawei.intent.action.HSM_BOOTAPP_MANAGER"));
        } catch (RuntimeException error) {
            openAppSettings();
            showToast("系统未开放直接入口，请在应用详情或“应用启动管理”中手动设置");
        }
    }

    private void addProviderPreset(LinearLayout parent, boolean primary) {
        LinearLayout row = new LinearLayout(this);
        Spinner provider = spinner(row, new String[]{"QQ 邮箱", "163/126 邮箱", "Gmail", "Outlook", "iCloud", "自定义"});
        Button apply = secondaryButton("套用预设");
        apply.setOnClickListener(view -> applyProviderPreset(provider.getSelectedItemPosition(), primary));
        row.addView(apply, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(row, matchWrap());
    }

    private void applyProviderPreset(int index, boolean primary) {
        String host;
        int port;
        int security;
        switch (index) {
            case 1:
                host = "smtp.163.com";
                port = 465;
                security = 0;
                break;
            case 2:
                host = "smtp.gmail.com";
                port = 465;
                security = 0;
                break;
            case 3:
                host = "smtp-mail.outlook.com";
                port = 587;
                security = 1;
                break;
            case 4:
                host = "smtp.mail.me.com";
                port = 587;
                security = 1;
                break;
            case 5:
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

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(roundStroke(COLOR_CARD, Color.WHITE, 24));
        card.setElevation(dp(6));
        return card;
    }

    private void addPageTitle(String title, String subtitle, LinearLayout parent) {
        TextView heading = text(title, 30f, COLOR_INK, true);
        heading.setTypeface(Typeface.SERIF, Typeface.BOLD);
        parent.addView(heading);
        TextView sub = text(subtitle, 14f, COLOR_MUTED, false);
        sub.setPadding(0, dp(5), 0, dp(14));
        parent.addView(sub);
    }

    private void addSubPageTitle(String title, String subtitle, int backPage) {
        Button back = secondaryButton("返回");
        back.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_media_previous, 0, 0, 0);
        back.setCompoundDrawablePadding(dp(4));
        tintCompoundDrawables(back, COLOR_JADE_DARK);
        back.setOnClickListener(view -> showPage(backPage));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        backParams.setMargins(0, 0, 0, dp(10));
        page.addView(back, backParams);
        addPageTitle(title, subtitle, page);
    }

    private void addStatusChip(String value, LinearLayout parent) {
        TextView chip = text(value, 13f, COLOR_JADE_DARK, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(9), dp(14), dp(9));
        chip.setBackground(roundStroke(COLOR_GLASS, COLOR_GLASS_BORDER, 20));
        chip.setElevation(dp(3));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        parent.addView(chip, params);
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 18f, COLOR_INK, true);
        title.setPadding(0, 0, 0, dp(8));
        return title;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.12f);
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    private EditText input(LinearLayout parent, String hint, int inputType) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setTextSize(15f);
        view.setTextColor(COLOR_INK);
        view.setHintTextColor(Color.rgb(126, 142, 148));
        view.setInputType(inputType);
        view.setMinHeight(dp(52));
        view.setPadding(dp(14), dp(11), dp(14), dp(11));
        view.setBackground(insetSurface(18));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(5), 0, dp(5));
        parent.addView(view, params);
        return view;
    }

    private Spinner spinner(LinearLayout parent, String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(50));
        spinner.setPadding(dp(10), dp(6), dp(10), dp(6));
        spinner.setBackground(insetSurface(18));
        parent.addView(spinner, matchWrap());
        return spinner;
    }

    private Spinner securitySpinner(LinearLayout parent) {
        return spinner(parent, new String[]{"SSL/TLS（465）", "STARTTLS（587）"});
    }

    private Button actionButton(String label, int color) {
        Button button = new Button(this);
        int fillColor = color == COLOR_JADE ? COLOR_JADE_DARK : color;
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15f);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(52));
        button.setBackground(roundStroke(fillColor, Color.argb(130, 255, 255, 255), 22));
        button.setElevation(dp(6));
        button.setPadding(dp(16), dp(11), dp(16), dp(11));
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
        button.setMinHeight(dp(48));
        button.setBackground(roundStroke(COLOR_GLASS, COLOR_GLASS_BORDER, 20));
        button.setElevation(dp(3));
        button.setPadding(dp(12), dp(9), dp(12), dp(9));
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

    private GradientDrawable insetSurface(int radiusDp) {
        return roundStroke(COLOR_INSET, Color.rgb(214, 228, 223), radiusDp);
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

    private TextView navigationRow(String title, String detail, int iconRes) {
        TextView row = text(title + "\n" + detail, 14f, COLOR_INK, true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setCompoundDrawablePadding(dp(12));
        row.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, android.R.drawable.ic_media_next, 0);
        tintCompoundDrawables(row, COLOR_JADE_DARK);
        row.setBackground(insetSurface(18));
        row.setClickable(true);
        row.setFocusable(true);
        MotionEffects.bindPress(row);
        return row;
    }

    private View settingsRow(String title, String subtitle, int iconRes, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(11), dp(8), dp(11));
        row.setBackground(roundRect(Color.TRANSPARENT, 18));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(COLOR_JADE_DARK);
        icon.setContentDescription(null);
        row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        copy.addView(text(title, 16f, COLOR_INK, true));
        copy.addView(text(subtitle, 12f, COLOR_MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(android.R.drawable.ic_media_next);
        arrow.setColorFilter(COLOR_MUTED);
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

    private View statusRow(String title, String state, boolean complete) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        row.setBackground(insetSurface(18));
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.setMargins(0, dp(3), 0, dp(3));
        row.setLayoutParams(rowParams);
        TextView titleView = text(title, 15f, COLOR_INK, true);
        row.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView stateView = text(state, 13f, complete ? COLOR_JADE_DARK : COLOR_AMBER, true);
        stateView.setCompoundDrawablePadding(dp(7));
        stateView.setCompoundDrawablesWithIntrinsicBounds(
                complete ? android.R.drawable.checkbox_on_background : android.R.drawable.ic_dialog_alert,
                0, 0, 0);
        tintCompoundDrawables(stateView, complete ? COLOR_JADE_DARK : COLOR_AMBER);
        row.addView(stateView);
        return row;
    }

    private void addHistorySummary(List<HistoryItem> history) {
        int success = 0;
        int filtered = 0;
        for (HistoryItem item : history) {
            if ("SUCCESS".equals(item.status)) success++;
            if ("FILTERED".equals(item.status)) filtered++;
        }
        LinearLayout summary = new LinearLayout(this);
        summary.setPadding(dp(6), dp(6), dp(6), dp(6));
        summary.setBackground(insetSurface(20));
        summary.addView(readinessCell("待发", Integer.toString(QueueDatabase.get(this).count()), QueueDatabase.get(this).count() == 0), weightedWrap());
        summary.addView(readinessCell("近期成功", Integer.toString(success), true), weightedWrap());
        summary.addView(readinessCell("已过滤", Integer.toString(filtered), true), weightedWrap());
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(10));
        page.addView(summary, params);
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

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(6), 0, dp(8));
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
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
