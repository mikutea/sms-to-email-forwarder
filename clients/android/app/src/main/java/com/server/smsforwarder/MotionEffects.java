package com.server.smsforwarder;

import android.annotation.SuppressLint;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

final class MotionEffects {
    static final long QUICK = 140L;
    static final long STANDARD = 220L;
    static final long EMPHASIZED = 300L;

    private static final TimeInterpolator EASE_OUT =
            new PathInterpolator(0.16f, 1f, 0.3f, 1f);
    private static final TimeInterpolator EASE_IN_OUT =
            new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private static final TimeInterpolator SOFT_SPRING = new OvershootInterpolator(0.62f);

    private MotionEffects() {
    }

    static boolean enabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return ValueAnimator.areAnimatorsEnabled();
        }
        try {
            return Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f) > 0f;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    static void enterPage(View view, float distancePx) {
        view.animate().cancel();
        if (!enabled(view.getContext())) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(distancePx);
        view.setScaleX(0.992f);
        view.setScaleY(0.992f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(EMPHASIZED)
                .setInterpolator(EASE_OUT)
                .withLayer()
                .start();
    }

    static void select(View view, boolean selected, float liftPx) {
        view.animate().cancel();
        float scale = selected ? 1.035f : 1f;
        float lift = selected ? -liftPx : 0f;
        if (!enabled(view.getContext())) {
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setTranslationY(lift);
            return;
        }
        view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationY(lift)
                .setDuration(STANDARD)
                .setInterpolator(selected ? SOFT_SPRING : EASE_IN_OUT)
                .withLayer()
                .start();
    }

    @SuppressLint("ClickableViewAccessibility")
    static void bindPress(View view) {
        view.setOnTouchListener((target, event) -> {
            if (!target.isEnabled() || !enabled(target.getContext())) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    target.animate().cancel();
                    target.animate()
                            .scaleX(0.975f)
                            .scaleY(0.975f)
                            .alpha(0.94f)
                            .setDuration(QUICK)
                            .setInterpolator(EASE_IN_OUT)
                            .withLayer()
                            .start();
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    target.animate().cancel();
                    target.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(STANDARD)
                            .setInterpolator(SOFT_SPRING)
                            .withLayer()
                            .start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    static void revealChildren(ViewGroup group, float distancePx) {
        if (!enabled(group.getContext())) {
            return;
        }
        int count = Math.min(group.getChildCount(), 10);
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            child.animate().cancel();
            child.setAlpha(0f);
            child.setTranslationY(distancePx);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(Math.min(i * 24L, 144L))
                    .setDuration(STANDARD)
                    .setInterpolator(EASE_OUT)
                    .withLayer()
                    .start();
        }
    }
}
