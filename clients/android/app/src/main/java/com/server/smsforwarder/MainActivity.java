package com.server.smsforwarder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
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
    private static final int COLOR_INK = Color.rgb(31, 44, 40);
    private static final int COLOR_JADE = Color.rgb(23, 111, 86);
    private static final int COLOR_JADE_DARK = Color.rgb(13, 76, 59);
    private static final int COLOR_CINNABAR = Color.rgb(178, 59, 46);
    private static final int COLOR_PAPER = Color.rgb(247, 244, 236);
    private static final int COLOR_CARD = Color.rgb(255, 253, 248);
    private static final int COLOR_MUTED = Color.rgb(101, 111, 105);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout page;
    private LinearLayout navigation;
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
        showPage(0);
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
        showPage(0);
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
                        showPage(1);
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
        header.setPadding(dp(20), dp(14), dp(20), dp(10));
        TextView seal = text("雁", 24f, Color.WHITE, true);
        seal.setGravity(Gravity.CENTER);
        seal.setBackground(roundRect(COLOR_CINNABAR, 15));
        header.addView(seal, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(12), 0, 0, 0);
        brand.addView(text("雁笺", 23f, COLOR_INK, true));
        brand.addView(text("一纸远书 · 短信直达邮箱", 12f, COLOR_MUTED, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        shell.addView(header);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        navigation = new LinearLayout(this);
        navigation.setPadding(dp(14), 0, dp(14), dp(8));
        String[] labels = {"概览", "邮箱", "规则", "记录", "设置"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button button = new Button(this);
            button.setText(labels[i]);
            button.setTextSize(14f);
            button.setAllCaps(false);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(dp(16), dp(8), dp(16), dp(8));
            button.setOnClickListener(view -> showPage(index));
            navigation.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        navScroll.addView(navigation);
        shell.addView(navScroll);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(8), dp(18), dp(36));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return shell;
    }

    private void showPage(int index) {
        currentPage = index;
        page.removeAllViews();
        for (int i = 0; i < navigation.getChildCount(); i++) {
            Button button = (Button) navigation.getChildAt(i);
            boolean selected = i == index;
            button.setTextColor(selected ? Color.WHITE : COLOR_INK);
            button.setBackground(roundRect(selected ? COLOR_JADE : Color.TRANSPARENT, 18));
        }
        switch (index) {
            case 1:
                showEmailPage();
                break;
            case 2:
                showRulesPage();
                break;
            case 3:
                showHistoryPage();
                break;
            case 4:
                showSettingsPage();
                break;
            default:
                showOverviewPage();
                break;
        }
    }

    private void showOverviewPage() {
        DeviceHealth health = DeviceHealth.inspect(this);
        boolean guardEnabled = TravelGuard.isEnabled(this);
        addPageTitle("旅行守护", "离家前确认每一项状态，故障短信会先加密保存并自动补发。", page);

        LinearLayout statusCard = card();
        int statusColor = health.readyForTravel() ? COLOR_JADE
                : guardEnabled ? COLOR_CINNABAR : Color.rgb(176, 118, 38);
        TextView status = text(
                health.readyForTravel() && guardEnabled ? "守护运行中" : "尚未满足离家条件",
                20f,
                statusColor,
                true);
        statusCard.addView(status);
        TextView summary = text(health.summary(), 14f, COLOR_INK, false);
        summary.setLineSpacing(0f, 1.25f);
        summary.setPadding(0, dp(10), 0, 0);
        statusCard.addView(summary);
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
            showPage(0);
        });
        actions.addView(pause, matchWrap());
        Button guard = actionButton(guardEnabled ? "关闭旅行守护" : "开启旅行守护", COLOR_CINNABAR);
        guard.setOnClickListener(view -> toggleTravelGuard());
        actions.addView(guard, matchWrap());
        page.addView(actions, cardParams());

        LinearLayout tools = card();
        tools.addView(sectionTitle("离家前动作"));
        Button test = secondaryButton("发送 SMTP 测试邮件");
        test.setOnClickListener(view -> testConfiguredProfile(false));
        tools.addView(test, matchWrap());
        Button heartbeat = secondaryButton("发送一次状态心跳");
        heartbeat.setOnClickListener(view -> {
            if (!AppConfig.load(this).enabled) {
                showToast("请先启用自动转发");
                return;
            }
            TravelGuard.enqueueHeartbeatNow(this, "旅行前手动自检", false);
            showToast("状态心跳已进入发送队列");
            showPage(0);
        });
        tools.addView(heartbeat, matchWrap());
        Button retry = secondaryButton("立即重试待发队列");
        retry.setOnClickListener(view -> {
            ForwardScheduler.schedule(this);
            showToast("已请求立即重试");
        });
        tools.addView(retry, matchWrap());
        page.addView(tools, cardParams());

        addNotice(
                "秒级指标统计到 SMTP 服务器接受邮件为止。手机关机、无网络、SIM 无服务或被系统“强制停止”时，普通 App 无法继续转发。旅行期间建议持续充电，并同时开启 Wi-Fi 与移动数据。",
                page);
    }

    private void showEmailPage() {
        AppConfig config = AppConfig.load(this);
        addPageTitle("邮箱通道", "邮件由手机直接连接你的 SMTP 服务发送，授权码只保存在本机 Keystore。", page);

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
        backupEnabled.setOnCheckedChangeListener((button, checked) -> backupFields.setVisibility(checked ? View.VISIBLE : View.GONE));
        strategyCard.addView(backupFields);
        page.addView(strategyCard, cardParams());

        LinearLayout privacy = card();
        privacyConsent = new CheckBox(this);
        privacyConsent.setText("我理解短信可能包含验证码、账户和个人信息，并同意发送至上述邮箱");
        privacyConsent.setChecked(config.privacyConsent);
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
        addPageTitle("转发规则", "判断顺序：时段 → SIM → 黑名单 → 白名单 → 类型 → 正文。前面的拒绝条件优先。", page);

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
        addPageTitle("转发记录", "正文和发送方加密保存在本机；诊断状态不会记录 SMTP 授权码。", page);
        Button retry = actionButton("立即重试全部待发短信", COLOR_JADE);
        retry.setOnClickListener(view -> {
            ForwardScheduler.schedule(this);
            showToast("已安排重试");
        });
        page.addView(retry, matchWrap());

        List<HistoryItem> history = QueueDatabase.get(this).recentHistory(50);
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
                        showPage(3);
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
                    showPage(3);
                }).show());
        page.addView(clear, matchWrap());
    }

    private void showSettingsPage() {
        addPageTitle("系统与守护设置", "华为设备通常需要完成后台授权，单纯安装 APK 不足以保证锁屏运行。", page);
        LinearLayout guide = card();
        guide.addView(sectionTitle("华为后台授权"));
        CheckBox confirmed = new CheckBox(this);
        confirmed.setText("我已完成：手动启动管理、自启动、关联启动、后台活动、锁定任务卡片、休眠保持网络");
        confirmed.setChecked(TravelGuard.isBackgroundConfirmed(this));
        confirmed.setOnCheckedChangeListener((button, checked) -> TravelGuard.setBackgroundConfirmed(this, checked));
        guide.addView(confirmed);
        Button appSettings = secondaryButton("打开应用详情设置");
        appSettings.setOnClickListener(view -> openAppSettings());
        guide.addView(appSettings, matchWrap());
        Button battery = secondaryButton("打开电池优化设置");
        battery.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        guide.addView(battery, matchWrap());
        Button launch = secondaryButton("尝试打开华为应用启动管理");
        launch.setOnClickListener(view -> openHuaweiLaunchSettings());
        guide.addView(launch, matchWrap());
        page.addView(guide, cardParams());

        LinearLayout heartbeatCard = card();
        heartbeatCard.addView(sectionTitle("心跳间隔"));
        Spinner hours = spinner(heartbeatCard, new String[]{"每 6 小时", "每 12 小时", "每 24 小时"});
        int current = TravelGuard.heartbeatHours(this);
        hours.setSelection(current == 6 ? 0 : current == 24 ? 2 : 1);
        Button save = secondaryButton("保存心跳设置");
        save.setOnClickListener(view -> {
            int selected = hours.getSelectedItemPosition() == 0 ? 6 : hours.getSelectedItemPosition() == 2 ? 24 : 12;
            TravelGuard.setHeartbeatHours(this, selected);
            showToast("心跳间隔已保存");
        });
        heartbeatCard.addView(save, matchWrap());
        page.addView(heartbeatCard, cardParams());

        LinearLayout privacy = card();
        privacy.addView(sectionTitle("隐私与清理"));
        privacy.addView(text("应用不申请通讯录、历史短信、通话记录、通知读取、无障碍或 Root 权限。界面禁止系统截图。", 14f, COLOR_MUTED, false));
        Button clearQueue = secondaryButton("清空本机待发送队列");
        clearQueue.setOnClickListener(view -> confirmClearQueue());
        privacy.addView(clearQueue, matchWrap());
        Button diagnostics = secondaryButton("分享脱敏诊断报告");
        diagnostics.setOnClickListener(view -> shareDiagnostics());
        privacy.addView(diagnostics, matchWrap());
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
        showPage(0);
    }

    private void toggleTravelGuard() {
        if (TravelGuard.isEnabled(this)) {
            TravelGuard.setEnabled(this, false);
            showToast("旅行守护已关闭，自动转发仍保持原状态");
            showPage(0);
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
        showPage(0);
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
                    showPage(0);
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
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundRect(COLOR_CARD, 18));
        card.setElevation(dp(1));
        return card;
    }

    private void addPageTitle(String title, String subtitle, LinearLayout parent) {
        parent.addView(text(title, 25f, COLOR_INK, true));
        TextView sub = text(subtitle, 14f, COLOR_MUTED, false);
        sub.setPadding(0, dp(4), 0, dp(12));
        parent.addView(sub);
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
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private EditText input(LinearLayout parent, String hint, int inputType) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setTextSize(15f);
        view.setTextColor(COLOR_INK);
        view.setHintTextColor(Color.rgb(137, 143, 138));
        view.setInputType(inputType);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackground(roundStroke(Color.WHITE, Color.rgb(218, 214, 203), 12));
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
        spinner.setPadding(dp(6), dp(4), dp(6), dp(4));
        parent.addView(spinner, matchWrap());
        return spinner;
    }

    private Spinner securitySpinner(LinearLayout parent) {
        return spinner(parent, new String[]{"SSL/TLS（465）", "STARTTLS（587）"});
    }

    private Button actionButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15f);
        button.setAllCaps(false);
        button.setBackground(roundRect(color, 14));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(COLOR_JADE_DARK);
        button.setTextSize(14f);
        button.setAllCaps(false);
        button.setBackground(roundStroke(Color.WHITE, Color.rgb(183, 202, 193), 14));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(5), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void addNotice(String message, LinearLayout parent) {
        TextView note = text(message, 13f, COLOR_MUTED, false);
        note.setPadding(dp(12), dp(10), dp(12), dp(10));
        note.setBackground(roundRect(Color.rgb(239, 235, 222), 12));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(8), 0, dp(4));
        parent.addView(note, params);
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
