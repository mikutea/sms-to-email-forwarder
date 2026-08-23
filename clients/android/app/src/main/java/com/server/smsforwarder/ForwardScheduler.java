package com.server.smsforwarder;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class ForwardScheduler {
    private static final int JOB_ID = 420_051;
    private static final long MIN_BACKOFF_MS = 30_000L;

    private ForwardScheduler() {
    }

    static void schedule(Context context) {
        schedule(context, 0L);
    }

    static void schedule(Context context, long minimumLatencyMs) {
        JobInfo.Builder builder = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context, ForwardJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(MIN_BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
        if (minimumLatencyMs > 0L) {
            builder.setMinimumLatency(minimumLatencyMs);
        }
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || scheduler.schedule(builder.build()) != JobScheduler.RESULT_SUCCESS) {
            AppConfig.setStatus(context, "无法安排后台发送任务，请检查系统后台运行设置");
        }
    }
}

