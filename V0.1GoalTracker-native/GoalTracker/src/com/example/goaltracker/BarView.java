package com.example.goaltracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Rounded progress bar (value 0..1). */
final class BarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int track, fill;
    private final float value;

    BarView(Context c, int track, int fill, float value) {
        super(c);
        this.track = track;
        this.fill = fill;
        this.value = Math.max(0f, Math.min(1f, value));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        paint.setColor(track);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, paint);
        if (value > 0f) {
            paint.setColor(fill);
            rect.set(0, 0, Math.max(h, w * value), h);
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }
}
