package com.elsonadero.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Coloca las etiquetas en filas que saltan de línea. */
public class FlowLayout extends ViewGroup {

    private final int gap;

    public FlowLayout(Context context) { this(context, null); }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        gap = Ui.dp(context, 8);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int max = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, rowH = 0, widest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            measureChild(ch, widthSpec, heightSpec);
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (x > 0 && x + w > max) {
                x = 0;
                y += rowH + gap;
                rowH = 0;
            }
            x += w + gap;
            widest = Math.max(widest, x - gap);
            rowH = Math.max(rowH, h);
        }
        int height = y + rowH + getPaddingTop() + getPaddingBottom();
        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? widest + getPaddingLeft() + getPaddingRight()
                : MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int max = r - l - getPaddingRight();
        int x = getPaddingLeft(), y = getPaddingTop(), rowH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (x > getPaddingLeft() && x + w > max) {
                x = getPaddingLeft();
                y += rowH + gap;
                rowH = 0;
            }
            ch.layout(x, y, x + w, y + h);
            x += w + gap;
            rowH = Math.max(rowH, h);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }
}
