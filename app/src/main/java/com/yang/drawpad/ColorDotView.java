package com.yang.drawpad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by lyang_000 on 12/27/2015.
 */
public class ColorDotView extends View{
    private int faceColor = Color.parseColor("#ffd200");
    private int circleColor = Color.parseColor("#ffffff");

    private int alpha = 255;
    private float ringArcWidth = 5.0f;  // in pixel
    private float ringRadius = 20.0f;    // in pixel


    public ColorDotView(Context context) {
        super(context);
    }

    public ColorDotView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorDotView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public int getFaceColor() {
        return faceColor;
    }

    public int getCircleColor() {
        return circleColor;
    }

    public float getRingArcWidth() {
        return ringArcWidth;
    }

    public float getRingRadius() {
        return ringRadius;
    }

    public void setFaceColor(int faceColor) {
        this.faceColor = faceColor;
        this.invalidate();
    }

    public void setCircleColor(int circleColor) {
        this.circleColor = circleColor;
        this.invalidate();
    }

    public void setRingArcWidth(float ringArcWidth) {
        this.ringArcWidth = ringArcWidth;
        this.invalidate();
    }

    public void setRingRadius(float ringRadius) {
        this.ringRadius = ringRadius;
        this.invalidate();
    }

    public void setAlpha(int alpha) {
        this.alpha = alpha;
        this.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint();
        paint.setColor(faceColor);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(alpha);
        canvas.save();
        RectF rect = new RectF(0, 0, getWidth(), getHeight());
        canvas.drawOval(rect, paint);

        RectF rectCircle = new RectF(
                getWidth() / 2.0f - ringRadius,
                getHeight() / 2.0f - ringRadius,
                getWidth() / 2.0f + ringRadius,
                getHeight() / 2.0f + ringRadius);

        paint.setColor(circleColor);
        paint.setStrokeWidth(ringArcWidth);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawOval(rectCircle, paint);

        canvas.restore();
    }
}
