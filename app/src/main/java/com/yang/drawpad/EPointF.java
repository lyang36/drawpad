package com.yang.drawpad;

/**
 * API inspired by the Apache Commons Math Vector2D class.
 */
public class EPointF {

    private final float x;
    private final float y;

    public EPointF(final float x, final float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public EPointF plus(float factor, EPointF ePointF) {
        return new EPointF(x + factor * ePointF.x, y + factor * ePointF.y);
    }

    public EPointF plus(EPointF ePointF) {
        return plus(1.0f, ePointF);
    }

    public EPointF minus(float factor, EPointF ePointF) {
        return new EPointF(x - factor * ePointF.x, y - factor * ePointF.y);
    }

    public EPointF minus(EPointF ePointF) {
        return minus(1.0f, ePointF);
    }

    public EPointF scaleBy(float factor) {
        return new EPointF(factor * x, factor * y);
    }

}