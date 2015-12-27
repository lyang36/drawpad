/**
 * CanvasView.java
 *
 * Copyright (c) 2014 Tomohiro IKEDA (Korilakkuma)
 * Released under the MIT license
 *
 *
 * Modified by Lin Yang, 12/16/2015
 */

package com.yang.drawpad;

import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;

public class RangePickBar{
    private View pickingDot;
    private View pickingBar;
    private View sampleDotView;
    private ColorDotView colorDotView;
    private Context context;

    //dp
    private float containerHeight = 300;
    private float dotSize = 60;
    private float initialY = 0;
    private float maxY = 240;
    private float currentY = 0;

    //density
    private float density;

    private OnRangePickedListener listener;
    private boolean isPicking = false;

    View.OnClickListener dotOnClick;
    View.OnTouchListener dotOnTouch;

    public interface OnRangePickedListener{
        /**
         * @param range: a float number from 0 to 1
         */
        void onRangePicked(float range);
    }

    public RangePickBar(Context context,
                        View dot,
                        View bar,
                        View sampleDotView){
        this.pickingBar = bar;
        this.pickingDot = dot;
        this.context = context;
        this.sampleDotView = sampleDotView;
        this.colorDotView = (ColorDotView) sampleDotView.findViewById(R.id.colorDot);

        density = context.getResources().getDisplayMetrics().density;
        setListeners();
    }

    /**
     * the initial bottom
     * @param initialY
     */
    public void setInitialY(float initialY) {
        this.initialY = initialY;
    }

    /**
     * the maximum top of the picking dot
     * @param maxY
     */
    public void setMaxY(float maxY) {
        this.maxY = maxY;
    }

    public float getCurrentY() {
        return currentY;
    }

    public void setCurrentY(float currentY) {
        this.currentY = currentY;
    }

    public void setDotSize(float dotSize) {
        this.dotSize = dotSize;
    }

    public void setContainerHeight(float containerHeight) {
        this.containerHeight = containerHeight;
    }

    private void setListeners(){
        dotOnClick =
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(!isPicking){
                            startPicking();
                            pickingDot.setOnClickListener(null);
                        }
                    }
                };
        pickingDot.setOnClickListener(dotOnClick);


        dotOnTouch = new View.OnTouchListener() {
            float initY;
            float iniTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    initY = pickingDot.getY();
                    iniTouchY = event.getRawY();
                    return true;
                }else if(event.getAction() == MotionEvent.ACTION_MOVE){
                    float distance = event.getRawY() - iniTouchY;
                    float cY = initY + distance;
                    if(cY <= 0){
                        cY = 0;
                    }else if(cY >= (containerHeight - dotSize) * density){
                        cY = (containerHeight - dotSize) * density;
                    }
                    currentY = (containerHeight - dotSize) - cY / density;
                    if(currentY < 0){
                        currentY = 0;
                    }
                    if(currentY > maxY){
                        currentY = maxY;
                    }
                    pickingDot.setY(cY);
                    if(listener != null){
                        listener.onRangePicked(currentY / maxY);
                    }
                    return true;
                }else if(event.getAction() == MotionEvent.ACTION_UP){
                    stopPicking();
                }
                return false;
            }
        };
        pickingDot.setOnTouchListener(null);

    }

    public float getMaxY() {
        return maxY;
    }

    public float getDotSize() {
        return dotSize;
    }

    public float getInitialY() {
        return initialY;
    }

    public void startPicking(){
        isPicking = true;
        sampleDotView.setVisibility(View.VISIBLE);
        pickingBar.setVisibility(View.VISIBLE);
        pickingDot.setY(containerHeight * density - (currentY + dotSize) * density);
        pickingDot.setOnTouchListener(dotOnTouch);
        if(listener != null){
            listener.onRangePicked(currentY / maxY);
        }

        sampleDotView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                stopPicking();
                return false;
            }
        });

    }

    public void stopPicking(){
        isPicking = false;
        sampleDotView.setVisibility(View.GONE);
        pickingBar.setVisibility(View.GONE);
        pickingDot.setY(containerHeight * density - (initialY + dotSize) * density);
        pickingDot.setOnTouchListener(null);
        pickingDot.setOnClickListener(dotOnClick);

    }

    public void setOnRangePickListener(OnRangePickedListener listener) {
        this.listener = listener;
    }


}
