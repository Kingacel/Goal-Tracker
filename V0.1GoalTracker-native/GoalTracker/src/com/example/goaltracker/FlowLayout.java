package com.example.goaltracker;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Minimal wrapping row layout (Flutter's Wrap). Children are centred vertically per line. */
final class FlowLayout extends ViewGroup {
    private final int hGap, vGap;
    private int[] px = new int[0], py = new int[0], lineH = new int[0];

    FlowLayout(Context c, int hGap, int vGap) {
        super(c);
        this.hGap = hGap;
        this.vGap = vGap;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int mode = MeasureSpec.getMode(wSpec);
        int size = MeasureSpec.getSize(wSpec);
        int maxW = mode == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE / 2 : size - getPaddingLeft() - getPaddingRight();
        int n = getChildCount();
        px = new int[n]; py = new int[n]; lineH = new int[n];

        int x = 0, y = 0, curH = 0, usedW = 0, lineStart = 0;
        for (int i = 0; i < n; i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            c.measure(MeasureSpec.makeMeasureSpec(Math.max(0, maxW), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int cw = c.getMeasuredWidth(), ch = c.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) {
                for (int k = lineStart; k < i; k++) lineH[k] = curH;
                lineStart = i;
                y += curH + vGap;
                x = 0;
                curH = 0;
            }
            px[i] = x;
            py[i] = y;
            x += cw + hGap;
            usedW = Math.max(usedW, x - hGap);
            curH = Math.max(curH, ch);
        }
        for (int k = lineStart; k < n; k++) lineH[k] = curH;

        int w = mode == MeasureSpec.UNSPECIFIED ? usedW + getPaddingLeft() + getPaddingRight() : size;
        int h = y + curH + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            int left = getPaddingLeft() + px[i];
            int top = getPaddingTop() + py[i] + (lineH[i] - c.getMeasuredHeight()) / 2;
            c.layout(left, top, left + c.getMeasuredWidth(), top + c.getMeasuredHeight());
        }
    }
}
