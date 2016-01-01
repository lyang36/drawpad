package com.yang.drawpad;

import android.graphics.Path;

import java.util.ArrayList;

/**
 * Created by lyang on 12/31/15.
 */
public class BezierCurveConstructor {
    ArrayList<EPointF> pointList;
    Path path;

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
        path = new Path();
    }


    public void addPoint(float x, float y) {
        pointList.add(new EPointF(x, y));
        if (pointList.size() == 1) {
            path.moveTo(x, y);
            return;
        }

        EPointF p1 = pointList.get(pointList.size() - 2);
        EPointF cp = pointList.get(pointList.size() - 1);
        EPointF mid = cp.plus(p1).scaleBy(0.5f);

        if (pointList.size() < 3) {
            path.lineTo(mid.getX(), mid.getY());
        } else {
            //EPointF p2 = pointList.get(pointList.size() - 3);
            //EPointF mid1 = p1.plus(p2).scaleBy(0.5f);
            //Path subPath = new Path();
            //subPath.moveTo(mid1.getX(), mid1.getY());
            //subPath.quadTo(p1.getX(), p1.getY(), mid.getX(), mid.getY());
            //path.addPath(subPath);
            path.quadTo(p1.getX(), p1.getY(), mid.getX(), mid.getY());
            //Log.d("PATH", mid1.toString() + "-" +  p1.toString() + "-" + mid.toString());
        }
    }

    /**
     * construct path by points
     *
     * @return
     */
    Path constructPath() {
        return path;
        /*if(pointList == null || pointList.size() < 3){
            return  new Path();
        }
        return PolyBezierPathUtil.computePathThroughKnots(pointList);*/
        /*Bezier bezier = new Bezier(pointList);
        Path path = new Path();

        path.moveTo(pointList.get(0).getX(), pointList.get(0).getY());
        path.quadTo(bezier.getPoint(0).getX(),
                bezier.getPoint(0).getY(),
                pointList.get(1).getX(),
                pointList.get(1).getY());

        for(int i = 2; i < pointList.size() - 1; i++){
            //path.lineTo(bezier.getPoint(i).getX(), bezier.getPoint(i).getY());

            EPointF b0 = bezier.getPoint(2*i-3);
            EPointF b1 = bezier.getPoint(2*i-2);
            path.cubicTo(b0.getX(), b0.getY(), b1.getX(), b1.getY(),
                    pointList.get(i).getX(), pointList.get(i).getY());
        }
        path.quadTo(bezier.getPoint(bezier.getPointCount()-1).getX(),
                bezier.getPoint(bezier.getPointCount()-1).getY(),
                pointList.get(pointList.size() - 1).getX(),
                pointList.get(pointList.size() - 1).getY());

        return path;*/
    }

}
