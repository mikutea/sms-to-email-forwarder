package com.server.smsforwarder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class EmailFormatterTest {
    @Test
    public void smsUsesReadableMultipartContentAndEscapesHtml() {
        QueueItem item = new QueueItem(
                "stable-message-id-123456",
                QueueItem.KIND_SMS,
                1_700_000_000_000L,
                "10086 <service>",
                "验证码：123456\n<script>alert('x')</script>",
                1,
                0);

        EmailFormatter.Content content = EmailFormatter.format(item);

        assertTrue(content.subject.startsWith("[雁笺短信] 10086"));
        assertTrue(content.plainText.contains("发件人：10086 <service>"));
        assertTrue(content.plainText.contains("卡槽：SIM 2"));
        assertTrue(content.html.contains("10086 &lt;service&gt;"));
        assertTrue(content.html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
        assertFalse(content.html.contains("<script>"));
        assertTrue(content.html.contains("投递标识 stable-messa"));
    }

    @Test
    public void subjectRemovesLineBreaksAndLimitsSenderLength() {
        QueueItem item = new QueueItem(
                "id",
                QueueItem.KIND_SMS,
                0L,
                "sender\r\nwith-a-very-long-name-abcdefghijklmnopqrstuvwxyz",
                "body",
                -1,
                0);

        EmailFormatter.Content content = EmailFormatter.format(item);

        assertFalse(content.subject.contains("\r"));
        assertFalse(content.subject.contains("\n"));
        assertTrue(content.subject.length() <= "[雁笺短信] ".length() + 40);
    }

    @Test
    public void statusKindsHaveDistinctLabels() {
        QueueItem test = new QueueItem("test-id", QueueItem.KIND_TEST, 0L,
                "设备自检", "测试正文", -1, 0);
        QueueItem heartbeat = new QueueItem("heartbeat-id", QueueItem.KIND_HEARTBEAT, 0L,
                "设备状态", "网络正常", -1, 0);

        assertTrue(EmailFormatter.format(test).subject.contains("SMTP 测试成功"));
        assertTrue(EmailFormatter.format(test).html.contains("SMTP 自检"));
        assertTrue(EmailFormatter.format(heartbeat).subject.contains("旅行守护心跳正常"));
        assertTrue(EmailFormatter.format(heartbeat).html.contains("旅行守护心跳"));
    }
}
