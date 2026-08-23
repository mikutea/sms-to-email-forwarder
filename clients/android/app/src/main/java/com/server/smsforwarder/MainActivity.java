package com.server.smsforwarder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.MessagingException;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final int REQUEST_RECEIVE_SMS = 1001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText smtpHost;
    private EditText smtpPort;
    private Spinner smtpSecurity;
    private EditText smtpUsername;
    private EditText smtpPassword;
    private EditText fromAddress;
    private EditText recipient;
    private EditText senderAllowlist;
    private CheckBox skipOtp;
    private CheckBox privacyConsent;
    private TextView stateView;
    private boolean currentlyEnabled;
    private boolean enableAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(buildContent());
        loadForm();
        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECEIVE_SMS) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && enableAfterPermission) {
                enableAfterPermission = false;
                saveAndEnable();
            } else if (!granted) {
                enableAfterPermission = false;
                showToast("未获得短信权限，自动转发没有启用");
            }
            refreshState();
        }
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        root.setPadding(padding, dp(18), padding, dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("短信转邮箱");
        title.setTextSize(26f);
        title.setTextColor(Color.rgb(20, 25, 35));
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView description = new TextView(this);
        description.setText("Android / 鸿蒙 2–4 兼容版 · 邮件由手机直连你自己的 SMTP 服务发送");
        description.setTextSize(14f);
        description.setTextColor(Color.DKGRAY);
        description.setPadding(0, 0, 0, dp(18));
        root.addView(description);

        stateView = new TextView(this);
        stateView.setTextSize(14f);
        stateView.setTextColor(Color.rgb(20, 70, 130));
        stateView.setBackgroundColor(Color.rgb(238, 245, 255));
        stateView.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(stateView, matchWrap());

        addSection(root, "SMTP 配置");
        smtpHost = addInput(root, "SMTP 主机，例如 smtp.qq.com", InputType.TYPE_CLASS_TEXT);
        smtpPort = addInput(root, "端口，例如 465 或 587", InputType.TYPE_CLASS_NUMBER);

        smtpSecurity = new Spinner(this);
        ArrayAdapter<String> securityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"SSL/TLS（常用端口 465）", "STARTTLS（常用端口 587）"});
        securityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        smtpSecurity.setAdapter(securityAdapter);
        root.addView(smtpSecurity, matchWrap());

        smtpUsername = addInput(
                root,
                "SMTP 用户名，通常是完整邮箱地址",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        smtpPassword = addInput(
                root,
                "SMTP 授权码 / 应用专用密码",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fromAddress = addInput(
                root,
                "发件邮箱，通常与 SMTP 用户名相同",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        recipient = addInput(
                root,
                "收件邮箱",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        TextView credentialNote = new TextView(this);
        credentialNote.setText("请使用邮箱服务生成的授权码，不要填写网页登录密码。仅支持加密 SMTP 连接。");
        credentialNote.setTextColor(Color.rgb(150, 75, 0));
        credentialNote.setPadding(0, dp(6), 0, dp(6));
        root.addView(credentialNote);

        addSection(root, "转发规则");
        senderAllowlist = addInput(
                root,
                "发件人白名单（可选，每行或逗号分隔；留空表示全部）",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        senderAllowlist.setMinLines(3);
        senderAllowlist.setGravity(android.view.Gravity.TOP);

        skipOtp = new CheckBox(this);
        skipOtp.setText("跳过疑似验证码短信（推荐）");
        root.addView(skipOtp, matchWrap());

        privacyConsent = new CheckBox(this);
        privacyConsent.setText("我理解短信可能包含验证码、账户和个人信息，并同意将其发送至上述邮箱");
        root.addView(privacyConsent, matchWrap());

        Button saveButton = addButton(root, "保存配置");
        saveButton.setOnClickListener(v -> saveConfiguration(currentlyEnabled));

        Button testButton = addButton(root, "发送 SMTP 测试邮件");
        testButton.setOnClickListener(v -> testSmtp());

        Button enableButton = addButton(root, "启用自动转发");
        enableButton.setOnClickListener(v -> requestEnable());

        Button pauseButton = addButton(root, "暂停自动转发");
        pauseButton.setOnClickListener(v -> {
            AppConfig.setEnabled(this, false);
            currentlyEnabled = false;
            AppConfig.setStatus(this, "自动转发已由用户暂停");
            refreshState();
        });

        Button clearButton = addButton(root, "清空本机待发送队列");
        clearButton.setOnClickListener(v -> confirmClearQueue());

        TextView systemNote = new TextView(this);
        systemNote.setText("真机安装后，还需要在鸿蒙“应用启动管理”中允许自动启动和后台活动，并完成一次真实短信测试。");
        systemNote.setTextColor(Color.DKGRAY);
        systemNote.setPadding(0, dp(18), 0, 0);
        root.addView(systemNote);

        return scroll;
    }

    private void loadForm() {
        AppConfig config = AppConfig.load(this);
        currentlyEnabled = config.enabled;
        smtpHost.setText(config.smtpHost);
        smtpPort.setText(Integer.toString(config.smtpPort));
        smtpSecurity.setSelection(
                AppConfig.SECURITY_STARTTLS.equals(config.smtpSecurity) ? 1 : 0);
        smtpUsername.setText(config.smtpUsername);
        smtpPassword.setText(config.smtpPassword);
        fromAddress.setText(config.fromAddress);
        recipient.setText(config.recipient);
        senderAllowlist.setText(config.senderAllowlist);
        skipOtp.setChecked(config.skipOtp);
        privacyConsent.setChecked(config.privacyConsent);
    }

    private void requestEnable() {
        String error = validateForm();
        if (error != null) {
            showToast(error);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            enableAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, REQUEST_RECEIVE_SMS);
            return;
        }
        saveAndEnable();
    }

    private void saveAndEnable() {
        if (saveConfiguration(true)) {
            currentlyEnabled = true;
            AppConfig.setStatus(this, "自动转发已启用，等待新短信");
            if (QueueDatabase.get(this).count() > 0) {
                ForwardScheduler.schedule(this);
            }
            refreshState();
        }
    }

    private boolean saveConfiguration(boolean enabled) {
        String error = validateForm();
        if (error != null) {
            showToast(error);
            return false;
        }
        String from = fromAddress.getText().toString().trim();
        AppConfig.save(
                this,
                smtpHost.getText().toString(),
                Integer.parseInt(smtpPort.getText().toString().trim()),
                selectedSecurity(),
                smtpUsername.getText().toString(),
                smtpPassword.getText().toString(),
                from,
                recipient.getText().toString(),
                senderAllowlist.getText().toString(),
                skipOtp.isChecked(),
                privacyConsent.isChecked(),
                enabled);
        currentlyEnabled = enabled;
        showToast(enabled ? "配置已保存并启用" : "配置已保存");
        refreshState();
        return true;
    }

    private String validateForm() {
        String from = fromAddress.getText().toString().trim();
        if (from.isEmpty()) {
            from = smtpUsername.getText().toString().trim();
            fromAddress.setText(from);
        }
        return AppConfig.validate(
                smtpHost.getText().toString(),
                smtpPort.getText().toString(),
                selectedSecurity(),
                smtpUsername.getText().toString(),
                smtpPassword.getText().toString(),
                from,
                recipient.getText().toString(),
                privacyConsent.isChecked());
    }

    private void testSmtp() {
        if (!saveConfiguration(currentlyEnabled)) {
            return;
        }
        AppConfig config = AppConfig.load(this);
        stateView.setText("正在连接 SMTP 并发送测试邮件……");
        executor.execute(() -> {
            String result;
            boolean success;
            try {
                QueueItem testItem = new QueueItem(
                        UUID.randomUUID().toString(),
                        QueueItem.KIND_TEST,
                        System.currentTimeMillis(),
                        "设备自检",
                        "如果你收到这封邮件，说明短信转邮箱 App 的 SMTP 配置可以正常发送邮件。",
                        -1,
                        0);
                SmtpMailer.send(config, testItem);
                result = "SMTP 测试邮件已发送，请检查收件箱和垃圾邮件目录";
                success = true;
            } catch (MessagingException | RuntimeException e) {
                result = "SMTP 测试失败：" + safeMessage(e);
                success = false;
            }
            if (success) {
                AppConfig.setSuccess(this, result);
            } else {
                AppConfig.setStatus(this, result);
            }
            String displayResult = result;
            runOnUiThread(() -> {
                refreshState();
                showToast(displayResult);
            });
        });
    }

    private void confirmClearQueue() {
        int count = QueueDatabase.get(this).count();
        if (count == 0) {
            showToast("当前没有待发送短信");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空待发送队列？")
                .setMessage("将删除 " + count + " 条尚未成功发送的本机加密短信，删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认删除", (dialog, which) -> {
                    QueueDatabase.get(this).clear();
                    AppConfig.setStatus(this, "待发送队列已由用户清空");
                    refreshState();
                })
                .show();
    }

    private void refreshState() {
        if (stateView == null) {
            return;
        }
        AppConfig config = AppConfig.load(this);
        currentlyEnabled = config.enabled;
        boolean hasPermission = checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
        int pending = QueueDatabase.get(this).count();
        stateView.setText(
                "状态：" + (config.enabled ? "已启用" : "已暂停")
                        + "\n短信权限：" + (hasPermission ? "已授权" : "未授权")
                        + "\n待发送：" + pending + " 条"
                        + "\n最近状态：" + AppConfig.getLastStatus(this));
    }

    private String selectedSecurity() {
        return smtpSecurity.getSelectedItemPosition() == 1
                ? AppConfig.SECURITY_STARTTLS
                : AppConfig.SECURITY_SSL_TLS;
    }

    private void addSection(LinearLayout root, String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextSize(19f);
        heading.setTextColor(Color.rgb(25, 30, 40));
        heading.setPadding(0, dp(22), 0, dp(8));
        root.addView(heading);
    }

    private EditText addInput(LinearLayout root, String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        root.addView(input, matchWrap());
        return input;
    }

    private Button addButton(LinearLayout root, String text) {
        Button button = new Button(this);
        button.setText(text);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        root.addView(button, params);
        return button;
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

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        message = message.replace('\r', ' ').replace('\n', ' ');
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
