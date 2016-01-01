package com.yang.drawpad;

import android.graphics.Path;

import java.util.ArrayList;

/**
 * Created by lyang on 12/31/15.
 */
public class BezierCurveConstructor {
    ArrayList<EPointF> pointList;


    public BezierCurveConstructor() {
        reset();
    }


    /**
     * reset the path
     */
    public void reset() {
        if (pointList == null) {
            pointList = new ArrayList<>();
        }
        pointList.clear();
    }


    public void addPoint(float x, float y) {
        pointList.add(new EPointF(x, y));
    }

    /**
     * construct path by points
     *
     * @return
     */
    Path constructPath() {
        return PolyBezierPathUtil.computePathThroughKnots(pointList);
    }

}
