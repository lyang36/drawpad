package com.yang.drawpad;

import android.graphics.Path;

import java.util.Collection;
import java.util.List;

/**
 * Based on http://stkent.github.io/2015/07/03/building-smooth-paths-using-bezier-curves.html
 * <p/>
 * by Stuart Kent
 */

public class PolyBezierPathUtil {

    // if the point with cosine greater than this threshold
    // use line to
    public static final float COS_ANGLE_THRESHOLD = 0.6F;

    /**
     * Computes a Poly-Bezier curve passing through a given list of knots.
     * The curve will be twice-differentiable everywhere and satisfy natural
     * boundary conditions at both ends.
     *
     * @param knots a list of knots
     * @return a Path representing the twice-differentiable curve
     * passing through all the given knots
     */
    public static Path computePathThroughKnots(List<EPointF> knots) {
        throwExceptionIfInputIsInvalid(knots);

        final Path polyBezierPath = new Path();
        final EPointF firstKnot = knots.get(0);
        polyBezierPath.moveTo(firstKnot.getX(), firstKnot.getY());

    /*
     * variable representing the number of Bezier curves we will join
     * together
     */
        final int n = knots.size() - 1;

        if (n == 1) {
            final EPointF lastKnot = knots.get(1);
            polyBezierPath.lineTo(lastKnot.getX(), lastKnot.getY());
        } else {
            final EPointF[] controlPoints = computeControlPoints(n, knots);

            for (int i = 0; i < n; i++) {
                final EPointF targetKnot = knots.get(i + 1);
                appendCurveToPath(polyBezierPath, controlPoints[i], controlPoints[n + i], targetKnot);
            }
        }

        return polyBezierPath;
    }

    private static EPointF[] computeControlPoints(int n, List<EPointF> knots) {
        final EPointF[] result = new EPointF[2 * n];

        final EPointF[] target = constructTargetVector(n, knots);
        final Float[] lowerDiag = constructLowerDiagonalVector(n - 1);
        final Float[] mainDiag = constructMainDiagonalVector(n);
        final Float[] upperDiag = constructUpperDiagonalVector(n - 1);

        final EPointF[] newTarget = new EPointF[n];
        final Float[] newUpperDiag = new Float[n - 1];

        // forward sweep for control points c_i,0:
        newUpperDiag[0] = upperDiag[0] / mainDiag[0];
        newTarget[0] = target[0].scaleBy(1 / mainDiag[0]);

        for (int i = 1; i < n - 1; i++) {
            newUpperDiag[i] = upperDiag[i] /
                    (mainDiag[i] - lowerDiag[i - 1] * newUpperDiag[i - 1]);
        }

        for (int i = 1; i < n; i++) {
            final float targetScale = 1 /
                    (mainDiag[i] - lowerDiag[i - 1] * newUpperDiag[i - 1]);

            newTarget[i] =
                    (target[i].minus(newTarget[i - 1].scaleBy(lowerDiag[i - 1]))).scaleBy(targetScale);
        }

        // backward sweep for control points c_i,0:
        result[n - 1] = newTarget[n - 1];

        for (int i = n - 2; i >= 0; i--) {
            result[i] = newTarget[i].minus(newUpperDiag[i], result[i + 1]);
        }

        // calculate remaining control points c_i,1 directly:
        for (int i = 0; i < n - 1; i++) {
            result[n + i] = knots.get(i + 1).scaleBy(2).minus(result[i + 1]);
        }

        result[2 * n - 1] = knots.get(n).plus(result[n - 1]).scaleBy(0.5f);

        return result;
    }

    private static EPointF[] constructTargetVector(int n, List<EPointF> knots) {
        final EPointF[] result = new EPointF[n];

        result[0] = knots.get(0).plus(2, knots.get(1));

        for (int i = 1; i < n - 1; i++) {
            result[i] = (knots.get(i).scaleBy(2).plus(knots.get(i + 1))).scaleBy(2);
        }

        result[result.length - 1] = knots.get(n - 1).scaleBy(8).plus(knots.get(n));

        return result;
    }

    private static Float[] constructLowerDiagonalVector(int length) {
        final Float[] result = new Float[length];

        for (int i = 0; i < result.length - 1; i++) {
            result[i] = 1f;
        }

        result[result.length - 1] = 2f;

        return result;
    }

    private static Float[] constructMainDiagonalVector(int n) {
        final Float[] result = new Float[n];

        result[0] = 2f;

        for (int i = 1; i < result.length - 1; i++) {
            result[i] = 4f;
        }

        result[result.length - 1] = 7f;

        return result;
    }

    private static Float[] constructUpperDiagonalVector(int length) {
        final Float[] result = new Float[length];

        for (int i = 0; i < result.length; i++) {
            result[i] = 1f;
        }

        return result;
    }

    private static void appendCurveToPath(Path path, EPointF control1, EPointF control2, EPointF targetKnot) {
        path.cubicTo(
                control1.getX(),
                control1.getY(),
                control2.getX(),
                control2.getY(),
                targetKnot.getX(),
                targetKnot.getY()
        );

        /*if(BuildConfig.DEBUG){
            Log.d("PATH", String.format("[(%2.0f, %2.0f), (%2.0f, %2.0f), (%2.0f, %2.0f)]",
                    control1.getX(), control1.getY(),
                    control2.getX(), control2.getY(),
                    targetKnot.getX(), targetKnot.getY()));
        }*/
    }

    private static void throwExceptionIfInputIsInvalid(Collection<EPointF> knots) {
        if (knots.size() < 2) {
            throw new IllegalArgumentException(
                    "Collection must contain at least two knots"
            );
        }
    }

}