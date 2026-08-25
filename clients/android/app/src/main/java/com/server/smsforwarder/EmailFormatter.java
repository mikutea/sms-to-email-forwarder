package com.server.smsforwarder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class EmailFormatter {
    private EmailFormatter() {
    }

    static Content format(QueueItem item) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
                .format(new Date(item.receivedAt));
        String label = kindLabel(item.kind);
        String sender = safeValue(item.sender, "未知发件人");
        String body = safeValue(item.body, "（无正文）");
        String sim = item.simSlot >= 0 ? "SIM " + (item.simSlot + 1) : "未知";
        String shortId = shortId(item.id);

        String subject;
        if (QueueItem.KIND_TEST.equals(item.kind)) {
            subject = "[雁笺] SMTP 测试成功";
        } else if (QueueItem.KIND_HEARTBEAT.equals(item.kind)) {
            subject = "[雁笺] 旅行守护心跳正常";
        } else if (QueueItem.KIND_ALERT.equals(item.kind)) {
            subject = "[雁笺提醒] " + safeSubject(sender);
        } else {
            subject = "[雁笺短信] " + safeSubject(sender);
        }

        StringBuilder plain = new StringBuilder();
        plain.append("雁笺 · ").append(label).append('\n')
                .append("================================\n")
                .append(QueueItem.KIND_SMS.equals(item.kind) ? "发件人：" : "来源：")
                .append(sender).append('\n')
                .append(QueueItem.KIND_SMS.equals(item.kind) ? "接收时间：" : "生成时间：")
                .append(time).append('\n');
        if (QueueItem.KIND_SMS.equals(item.kind)) {
            plain.append("卡槽：").append(sim).append('\n');
        }
        plain.append('\n').append(body).append('\n')
                .append("\n--------------------------------\n")
                .append("投递标识：").append(shortId).append('\n')
                .append("由雁笺在手机本地加密入队，并通过你配置的 SMTP 服务发送。\n");

        String metadata = metadataHtml(item, sender, time, sim);
        String html = "<!doctype html><html lang=\"zh-CN\"><head>"
                + "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "</head><body style=\"margin:0;padding:0;background:#f2f6f4;color:#0f2230;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background:#f2f6f4;\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"max-width:620px;background:#ffffff;border:1px solid #dceae6;border-radius:24px;overflow:hidden;\">"
                + "<tr><td style=\"padding:24px 26px 18px;background:#edf7f4;border-bottom:1px solid #dceae6;\">"
                + "<div style=\"font:600 13px Arial,sans-serif;color:#1c7768;letter-spacing:1px;\">雁笺 · YANJIAN</div>"
                + "<div style=\"margin-top:9px;font:700 26px Arial,sans-serif;color:#0f2230;\">" + escapeHtml(label) + "</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:22px 26px 4px;\">" + metadata + "</td></tr>"
                + "<tr><td style=\"padding:14px 26px 26px;\">"
                + "<div style=\"padding:18px 20px;background:#f7faf9;border-left:4px solid #4e8d7c;border-radius:14px;font:16px/1.75 Arial,sans-serif;color:#0f2230;white-space:pre-wrap;word-break:break-word;\">"
                + escapeHtml(body) + "</div></td></tr>"
                + "<tr><td style=\"padding:16px 26px 22px;border-top:1px solid #e7efed;font:12px/1.7 Arial,sans-serif;color:#5e6f7e;\">"
                + "投递标识 " + escapeHtml(shortId)
                + "<br>由雁笺在手机本地加密入队，并通过你配置的 SMTP 服务发送。"
                + "</td></tr></table></td></tr></table></body></html>";

        return new Content(subject, plain.toString(), html);
    }

    private static String metadataHtml(QueueItem item, String sender, String time, String sim) {
        StringBuilder html = new StringBuilder();
        html.append(metadataRow(
                QueueItem.KIND_SMS.equals(item.kind) ? "发件人" : "来源", sender));
        html.append(metadataRow(
                QueueItem.KIND_SMS.equals(item.kind) ? "接收时间" : "生成时间", time));
        if (QueueItem.KIND_SMS.equals(item.kind)) {
            html.append(metadataRow("卡槽", sim));
        }
        return html.toString();
    }

    private static String metadataRow(String label, String value) {
        return "<div style=\"margin:0 0 8px;font:14px/1.6 Arial,sans-serif;\">"
                + "<span style=\"display:inline-block;min-width:76px;color:#5e6f7e;\">"
                + escapeHtml(label) + "</span>"
                + "<strong style=\"color:#0f2230;\">" + escapeHtml(value) + "</strong></div>";
    }

    private static String kindLabel(String kind) {
        if (QueueItem.KIND_TEST.equals(kind)) return "SMTP 自检";
        if (QueueItem.KIND_HEARTBEAT.equals(kind)) return "旅行守护心跳";
        if (QueueItem.KIND_ALERT.equals(kind)) return "设备提醒";
        return "收到新短信";
    }

    private static String safeValue(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value;
    }

    private static String safeSubject(String value) {
        String sanitized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return sanitized.length() > 40 ? sanitized.substring(0, 40) : sanitized;
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "");
        return sanitized.length() > 12 ? sanitized.substring(0, 12) : sanitized;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static final class Content {
        final String subject;
        final String plainText;
        final String html;

        Content(String subject, String plainText, String html) {
            this.subject = subject;
            this.plainText = plainText;
            this.html = html;
        }
    }
}
