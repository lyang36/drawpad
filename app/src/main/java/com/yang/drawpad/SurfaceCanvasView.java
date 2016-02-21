/**
 * CanvasView.java
 *
 * Copyright (c) 2014 Tomohiro IKEDA (Korilakkuma)
 * Released under the MIT license
 *
 *
 * Modified by Lin Yang, 12/26/2015
 */

package com.yang.drawpad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This class defines fields and methods for drawing.
 */
public class SurfaceCanvasView extends SurfaceView implements Runnable {
    public interface OnDrawListener{
        /**
         * what to do if the canvas is updated.
         */
        void onCanvasUpdated();
    }

    // the maximum number of paths to undo
    private static final int MAX_NUM_PATHS = 200;
    private static final float TOUCH_TOLERANCE = 15;        //pixel

    // the draw thread on the background
    private Thread drawThread = null;

    // the bitmap to record the paths when exceed max_num_paths
    private Bitmap overflowBitmap = null;

    // the current screen bitmap
    private Bitmap currentScreenBitMap = null;
    private ScaleGestureDetector scaleGestureDetector;

    // the draw bound
    private Rect drawBound;

    //private Canvas canvas   = null;
    private Bitmap bitmap;
    private List<Path> pathLists = new ArrayList<Path>();
    private List<Paint> paintLists = new ArrayList<Paint>();


    // for Eraser
    private int baseColor = Color.WHITE;
    // for Undo, Redo
    private int historyPointer = 0;
    // Flags
    private Mode mode = Mode.DRAW;
    private Drawer drawer = Drawer.SMOOTH_PEN;
    private boolean isDown = false;


    // for Paint
    private Paint.Style paintStyle = Paint.Style.STROKE;
    private int paintStrokeColor = Color.BLACK;
    private int paintFillColor = Color.BLACK;
    private int plainColor = Color.parseColor("#a2a2a2");
    private float paintStrokeWidth = 3F;
    private int opacity = 255;
    private float blur = 0F;
    private Paint.Cap lineCap = Paint.Cap.ROUND;


    // for Text
    private String text = "";
    private Typeface fontFamily = Typeface.DEFAULT;
    private float fontSize = 32F;
    private Paint.Align textAlign = Paint.Align.RIGHT;  // fixed
    private Paint textPaint = new Paint();
    private float textX = 0F;
    private float textY = 0F;


    // for Drawer
    private float startX = 0F;
    private float startY = 0F;
    private float controlX = 0F;
    private float controlY = 0F;
    private float prevX = 0F;
    private float prevY = 0F;


    // for zooming and pan
    private float currentTranslationX = 0f;
    private float currentTranslationY;
    private float mScaleFactor = 1.f;
    private int scalePivotX = 0;
    private int scalePivotY = 0;
    private Matrix currentMatrix = new Matrix();
    private Matrix currentMatrixInverse = new Matrix();
    private Matrix tempMatrix = new Matrix();


    // to draw the spline
    private BezierCurveConstructor bezierCurveConstructor;

    // move the view
    private boolean isTwoFingerDown = false;
    private float twoFingerStartX;
    private float twoFingerStartY;
    private SurfaceHolder surfaceHolder;
    private boolean okToDraw = false;
    private boolean isRedrawBackground = false;
    private boolean isAddNewestPath = false;

    // measuring fps
    private long mLastTime = 0;
    private int fps = 0, ifps = 0;

    OnDrawListener onDrawListener;


    // when drawing,
    // only the updated rectangle need to be redrawed
    // WARNING: I tried to implement this, but failed.
    // the current frame-rate is good enough. If
    // it's too slow, considering implement this.
    //private RenderRect renderRect;

    /**
     * matrix operations for pan&zooming
     */
    protected void saveMatrix(){
        tempMatrix.set(currentMatrix);
    }
    /**
     * matrix operations for pan&zooming
     */
    protected void restoreMatrix(){
        currentMatrix.set(tempMatrix);
        tempMatrix.invert(currentMatrixInverse);
    }
    /**
     * matrix operations for pan&zooming
     */
    protected void translateMatrix(float tx, float ty){
        currentMatrix.postTranslate(tx, ty);
        currentMatrix.invert(currentMatrixInverse);
    }
    /**
     * matrix operations for pan&zooming
     */
    protected void scaleMatrix(float sx, float sy, float px, float py){
        currentMatrix.postScale(sx, sy, px, py);
        currentMatrix.invert(currentMatrixInverse);
    }
    /**
     * matrix operations for pan&zooming
     */
    protected void applyCurrentTranslationScale(){
        restoreMatrix();
        translateMatrix(currentTranslationX, currentTranslationY);
        scaleMatrix(mScaleFactor, mScaleFactor, scalePivotX, scalePivotY);
        Log.d("Scale", "" + mScaleFactor);
    }



    /**
     * when the canvas is updated, it suppose to use this listener to store it to file
     * @param onDrawListener
     */
    public void setOnDrawListener(OnDrawListener onDrawListener) {
        this.onDrawListener = onDrawListener;
    }


    /**
     * Copy Constructor
     *
     * @param context
     * @param attrs
     * @param defStyle
     */
    public SurfaceCanvasView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.setup(context);
        bitmap = null;
    }

    /**
     * Copy Constructor
     *
     * @param context
     * @param attrs
     */
    public SurfaceCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setup(context);
        bitmap = null;
    }

    /**
     * Copy Constructor
     *
     * @param context
     */
    public SurfaceCanvasView(Context context) {
        super(context);
        this.setup(context);
        bitmap = null;
    }


    /**
     * Common initialization.
     *
     * @param context
     */
    private void setup(Context context) {
        addPath(new Path(), this.createPaint());

        this.textPaint.setARGB(0, 255, 255, 255);
        this.drawBound = new Rect(0, 0, 0, 0);


        scaleGestureDetector = new ScaleGestureDetector(context,
                new ScaleListener());

        surfaceHolder = getHolder();
    }

    /**
     * add a path to the array
     * @param path
     * @param paint
     */
    private void addPath(Path path, Paint paint) {

        if (historyPointer < pathLists.size()) {
            this.pathLists.set(historyPointer, path);
            this.paintLists.set(historyPointer, paint);
        } else {
            this.pathLists.add(path);
            this.paintLists.add(paint);
        }
        this.historyPointer++;

        if (historyPointer >= MAX_NUM_PATHS) {
            // draw the image to bitmap
            if (overflowBitmap == null) {
                overflowBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            }
            // draw the path to the bitmap
            Canvas canvas = new Canvas(overflowBitmap);
            Path path1 = this.pathLists.get(0);
            Paint paint1 = this.paintLists.get(0);
            canvas.drawPath(path1, paint1);
            this.pathLists.remove(0);
            this.paintLists.remove(0);
            this.historyPointer--;
        }
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }


    /**
     * set the draw bound, outside which, the canvas will not draw
     * @param drawBound
     */
    public void setDrawBound(Rect drawBound) {
        this.drawBound = drawBound;
    }
    /**
     * set the draw bound, outside which, the canvas will not draw
     */
    public void setDrawBound(int left, int top, int right, int bottom) {
        this.drawBound = new Rect(left, top, right, bottom);
    }

    /**
     * This method creates the instance of Paint.
     * In addition, this method sets styles for Paint.
     *
     * @return paint This is returned as the instance of Paint
     */
    private Paint createPaint() {
        Paint paint = new Paint();

        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        paint.setStyle(this.paintStyle);
        paint.setStrokeWidth(this.paintStrokeWidth);
        paint.setStrokeJoin(Paint.Join.BEVEL);    // set the join to round you want
        paint.setStrokeCap(Paint.Cap.ROUND);      // set the paint cap to round too
        paint.setPathEffect(new CornerPathEffect(paintStrokeWidth));

        // for Text
        if (this.mode == Mode.TEXT) {
            paint.setTypeface(this.fontFamily);
            paint.setTextSize(this.fontSize);
            paint.setTextAlign(this.textAlign);
            paint.setStrokeWidth(0F);
        }

        if (this.mode == Mode.ERASER) {
            // Eraser
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            paint.setARGB(0, 0, 0, 0);

        } else {
            // Otherwise
            paint.setColor(this.paintStrokeColor);
            paint.setShadowLayer(this.blur, 0F, 0F, this.paintStrokeColor);
            paint.setAlpha(this.opacity);
        }

        return paint;
    }

    /**
     * This method initialize Path.
     * Namely, this method creates the instance of Path,
     * and moves current position.
     *
     * @param event This is argument of onTouchEvent method
     * @return path This is returned as the instance of Path
     */
    private Path createPath(MotionEvent event) {
        Path path = new Path();

        EPointF ePointF = getConvertedPoints(event.getX(), event.getY());
        this.startX = ePointF.getX();
        this.startY = ePointF.getY();

        path.moveTo(this.startX, this.startY);
        bezierCurveConstructor.addPoint(this.startX, this.startY);

        return path;
    }

    /**
     * get the canvas coordinates from the current screen points.
     * This is done by inverse the currentMatrix, use it to map
     * @param x
     * @param y
     * @return
     */
    private EPointF getConvertedPoints(float x, float y){
        float[] src = new float[]{x, y};

        Matrix inverse = new Matrix();
        currentMatrix.invert(inverse);
        inverse.mapPoints(src);
        EPointF ePointF = new EPointF(src[0], src[1]);

        //Log.d("Matrix", "" + x + " " + y + " " + ePointF.getX() + " " + ePointF.getY());
        return ePointF;
    }


    /**
     * This method updates the lists for the instance of Path and Paint.
     * "Undo" and "Redo" are enabled by this method.
     *
     * @param path the instance of Path
     */
    private void updateHistory(Path path) {
        if (this.historyPointer == this.pathLists.size()) {
            addPath(path, this.createPaint());
        } else {
            // On the way of Undo or Redo
            addPath(path, this.createPaint());

            for (int i = this.historyPointer, size = this.paintLists.size(); i < size; i++) {
                this.pathLists.remove(this.historyPointer);
                this.paintLists.remove(this.historyPointer);
            }
        }
    }

    /**
     * This method gets the instance of Path that pointer indicates.
     *
     * @return the instance of Path
     */
    private Path getCurrentPath() {
        return this.pathLists.get(this.historyPointer - 1);
    }

    /**
     * This method draws text.
     *
     * @param canvas the instance of Canvas
     */
    private void drawText(Canvas canvas) {
        if (this.text.length() <= 0) {
            return;
        }

        if (this.mode == Mode.TEXT) {
            this.textX = this.startX;
            this.textY = this.startY;

            this.textPaint = this.createPaint();
        }

        float textX = this.textX;
        float textY = this.textY;

        Paint paintForMeasureText = new Paint();

        // Line break automatically
        float textLength = paintForMeasureText.measureText(this.text);
        float lengthOfChar = textLength / (float) this.text.length();
        float restWidth = getWidth() - textX;  // text-align : right
        int numChars = (lengthOfChar <= 0) ? 1 : (int) Math.floor((double) (restWidth / lengthOfChar));  // The number of characters at 1 line
        int modNumChars = (numChars < 1) ? 1 : numChars;
        float y = textY;

        for (int i = 0, len = this.text.length(); i < len; i += modNumChars) {
            String substring = "";

            if ((i + modNumChars) < len) {
                substring = this.text.substring(i, (i + modNumChars));
            } else {
                substring = this.text.substring(i, len);
            }

            y += this.fontSize;

            canvas.drawText(substring, textX, y, this.textPaint);
        }
    }

    /**
     * This method defines processes on MotionEvent.ACTION_DOWN
     *
     * @param event This is argument of onTouchEvent method
     */
    private void onActionDown(MotionEvent event) {
        /* Failed to implement to the following
        if (renderRect == null) {
            renderRect = new RenderRect((int) event.getX(), (int) event.getY(), (int) paintStrokeWidth);
        }
        renderRect.update((int) event.getX(), (int) event.getY(), (int) paintStrokeWidth);
        renderRect.reset();
        */

        switch (this.mode) {
            case DRAW:
            case ERASER:
                if ((this.drawer != Drawer.QUADRATIC_BEZIER)
                        && (this.drawer != Drawer.QUBIC_BEZIER)) {

                    if (bezierCurveConstructor == null) {
                        bezierCurveConstructor = new BezierCurveConstructor();
                    } else {
                        bezierCurveConstructor.reset();
                    }

                    // Oherwise
                    this.updateHistory(this.createPath(event));
                    this.isDown = true;
                } else {
                    // Bezier
                    if ((this.startX == 0F) && (this.startY == 0F)) {
                        // The 1st tap
                        this.updateHistory(this.createPath(event));
                    } else {
                        // The 2nd tap
                        EPointF ePointF = getConvertedPoints(event.getX(), event.getY());
                        this.controlX = ePointF.getX();
                        this.controlY = ePointF.getY();

                        this.isDown = true;
                    }
                }

                break;
            case TEXT:
                EPointF ePointF = getConvertedPoints(event.getX(), event.getY());
                this.startX = ePointF.getX();
                this.startY = ePointF.getY();

                break;
            default:
                break;
        }
    }

    private void addPointToPath(float x, float y, Path path) {

        float dx = Math.abs(x - prevX);
        float dy = Math.abs(y - prevY);

        // this is crucial to draw a reasonable path
        float scaledTolerance = currentMatrixInverse.mapRadius(TOUCH_TOLERANCE);
        if (dx >= scaledTolerance || dy >= scaledTolerance) {
            bezierCurveConstructor.addPoint(x, y);

            if (this.drawer == Drawer.PEN) {
                path.lineTo(x, y);
            }

            prevX = x;
            prevY = y;
        }

    }


    /**
     * This method defines processes on MotionEvent.ACTION_MOVE
     *
     * @param event This is argument of onTouchEvent method
     */
    private void onActionMove(MotionEvent event) {
        float x;// = getEventX(event);
        float y;// = getEventY(event);
        EPointF ePointF = getConvertedPoints(event.getX(), event.getY());
        x = ePointF.getX();
        y = ePointF.getY();

        //renderRect.update((int) event.getX(), (int) event.getY(), (int) paintStrokeWidth);

        switch (this.mode) {
            case DRAW:
            case ERASER:

                if ((this.drawer != Drawer.QUADRATIC_BEZIER) && (this.drawer != Drawer.QUBIC_BEZIER)) {
                    if (!isDown) {
                        return;
                    }

                    Path path = this.getCurrentPath();

                    switch (this.drawer) {
                        case PEN:
                        case SMOOTH_PEN:
                            int historySize = event.getHistorySize();

                            addPointToPath(x, y, path);

                            // if use smooth, replace the path
                            // with a smoothed one
                            if (drawer == Drawer.SMOOTH_PEN) {
                                this.pathLists.set(this.historyPointer - 1,
                                        bezierCurveConstructor.constructPath());
                            }


                            break;
                        case LINE:
                            path.reset();
                            path.moveTo(this.startX, this.startY);
                            path.lineTo(x, y);
                            break;
                        case RECTANGLE:
                            path.reset();
                            path.addRect(this.startX, this.startY, x, y, Path.Direction.CCW);
                            break;
                        case CIRCLE:
                            double distanceX = Math.abs((double) (this.startX - x));
                            double distanceY = Math.abs((double) (this.startX - y));
                            double radius = Math.sqrt(Math.pow(distanceX, 2.0) + Math.pow(distanceY, 2.0));

                            path.reset();
                            path.addCircle(this.startX, this.startY, (float) radius, Path.Direction.CCW);
                            break;
                        case ELLIPSE:
                            RectF rect = new RectF(this.startX, this.startY, x, y);

                            path.reset();
                            path.addOval(rect, Path.Direction.CCW);
                            break;
                        default:
                            break;
                    }
                } else {
                    if (!isDown) {
                        return;
                    }

                    Path path = this.getCurrentPath();

                    path.reset();
                    path.moveTo(this.startX, this.startY);
                    path.quadTo(this.controlX, this.controlY, x, y);

                }

                break;
            case TEXT:
                this.startX = x;
                this.startY = y;

                break;
            default:
                break;
        }
    }

    /**
     * This method defines processes on MotionEvent.ACTION_DOWN
     *
     * @param event This is argument of onTouchEvent method
     */
    private void onActionUp(MotionEvent event) {
        if (isDown) {
            this.startX = 0F;
            this.startY = 0F;

            this.isDown = false;
            requestAddNewPathToBackground();
        }
    }

    /**
     *
     * @param pathId <p>-1: draw all paths</p>
     *                <p>i>0: draw the i-th path</p>
     */
    private void drawBitMap(int pathId) {
        if (currentScreenBitMap == null) {
            currentScreenBitMap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(currentScreenBitMap);

        // redraw the whole image
        if (pathId == -1) {
            // Before "drawPath"
            canvas.drawColor(this.baseColor);

            if (this.bitmap != null) {
                canvas.drawBitmap(this.bitmap, 0F, 0F, new Paint());
            }

            if (this.overflowBitmap != null) {
                Paint paint = new Paint();
                paint.setAntiAlias(true);
                paint.setFilterBitmap(true);
                paint.setDither(true);
                canvas.drawBitmap(this.overflowBitmap, 0F, 0F, paint);
            }

            for (int i = 0; i < this.historyPointer; i++) {
                Path path = this.pathLists.get(i);
                Paint paint = this.paintLists.get(i);
                canvas.drawPath(path, paint);
            }

            this.drawText(canvas);


        } else {
            Path path = this.pathLists.get(pathId);
            Paint paint = this.paintLists.get(pathId);

            canvas.drawPath(path, paint);
        }

    }

    void requestRedrawBackground() {
        isRedrawBackground = true;
    }

    void requestAddNewPathToBackground() {
        isAddNewestPath = true;
    }

    private void drawFullScreen(Canvas canvas) {
        boolean callOnDrawListener = false;

        if (canvas == null) {
            return;
        }
        if (drawBound.bottom - drawBound.top == 0) {
            drawBound.left = 0;
            drawBound.right = getWidth();
            drawBound.top = 0;
            drawBound.bottom = getHeight();
        }

        canvas.save();
        canvas.setMatrix(currentMatrix);

        if (currentScreenBitMap == null || isRedrawBackground) {
            drawBitMap(-1);
            isRedrawBackground = false;
        }

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        canvas.drawBitmap(this.currentScreenBitMap, 0F, 0F, paint);

        if (isDown || isAddNewestPath) {
            Path path = this.pathLists.get(this.historyPointer - 1);
            paint = this.paintLists.get(this.historyPointer - 1);
            canvas.drawPath(path, paint);
        }

        if (isAddNewestPath) {
            drawBitMap(this.historyPointer - 1);
            isAddNewestPath = false;
            callOnDrawListener = true;
        }
        //put a hole in the current clip
        canvas.clipRect(drawBound, Region.Op.DIFFERENCE);
        //fill with plain color at the plain region
        canvas.drawColor(plainColor);
        //restore full canvas clip for any subsequent operations
        canvas.clipRect(new Rect(0, 0, canvas.getWidth(), canvas.getHeight())
                , Region.Op.REPLACE);

        canvas.restore();

        if(callOnDrawListener){
            if(onDrawListener != null){
                onDrawListener.onCanvasUpdated();
            }
        }
        if (BuildConfig.DEBUG) {
            long now = System.currentTimeMillis();
            ifps++;
            if (now > (mLastTime + 1000)) {
                mLastTime = now;
                fps = ifps;
                ifps = 0;
                Log.d("FPS:", "" + fps);
            }
        }

    }

    public void setPlainColor(int plainColor) {
        this.plainColor = plainColor;
    }


    /**
     * This method set event listener for drawing.
     *
     * @param event the instance of MotionEvent
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_POINTER_DOWN:
                Log.d("PointDown", "" + event.getPointerCount());
                if (event.getPointerCount() == 2) {
                    isTwoFingerDown = true;
                    float sx = (event.getX(0) + event.getX(1)) / 2;
                    float sy = (event.getY(0) + event.getY(1)) / 2;
                    twoFingerStartX = sx;
                    twoFingerStartY = sy;
                    scalePivotY = (int) (sy);
                    scalePivotX = (int) (sx);
                    mScaleFactor = 1.0f;

                    saveMatrix();

                    if (isDown) {
                        // undo the current drawing
                        onActionUp(event);
                        //undo();
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 2) {
                    if (isTwoFingerDown) {
                        float sx = (event.getX(0) + event.getX(1)) / 2;
                        float sy = (event.getY(0) + event.getY(1)) / 2;
                        scalePivotY = (int) (sy);
                        scalePivotX = (int) (sx);
                        currentTranslationX = sx - twoFingerStartX;
                        currentTranslationY = sy - twoFingerStartY;
                        applyCurrentTranslationScale();
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() < 2) {
                    isTwoFingerDown = false;
                    return false;
                }
                break;
        }

        // zooming
        if (scaleGestureDetector != null) {
            scaleGestureDetector.onTouchEvent(event);

            if (scaleGestureDetector.isInProgress()) {
                return true;
            }
        }

        // normal drawing event
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.d("Down", "" + event.getPointerCount());
                this.onActionDown(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1) {
                    this.onActionMove(event);
                }
                break;
            case MotionEvent.ACTION_UP:
                this.onActionUp(event);
                break;

            default:
                break;
        }

        // Re draw
        //this.invalidate();
        return true;
    }

    /**
     * This method is getter for mode.
     *
     * @return
     */
    public Mode getMode() {
        return this.mode;
    }

    /**
     * This method is setter for mode.
     *
     * @param mode
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * This method is getter for drawer.
     *
     * @return
     */
    public Drawer getDrawer() {
        return this.drawer;
    }

    /**
     * This method is setter for drawer.
     *
     * @param drawer
     */
    public void setDrawer(Drawer drawer) {
        this.drawer = drawer;
    }

    /**
     * This method draws canvas again for Undo.
     *
     * @return If Undo is enabled, this is returned as true. Otherwise, this is returned as false.
     */
    public boolean undo() {
        if (this.historyPointer > 1) {
            this.historyPointer--;
            requestRedrawBackground();
            return true;
        } else {
            return false;
        }
    }

    /**
     * This method draws canvas again for Redo.
     *
     * @return If Redo is enabled, this is returned as true. Otherwise, this is returned as false.
     */
    public boolean redo() {
        if (this.historyPointer < this.pathLists.size()) {
            this.historyPointer++;
            return true;
        } else {
            return false;
        }
    }

    /**
     * This method initializes canvas.
     *
     * @return
     */
    public void clear() {
        Path path = new Path();
        path.moveTo(0F, 0F);
        path.addRect(0F, 0F, 1000F, 1000F, Path.Direction.CCW);
        path.close();

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);

        if (this.historyPointer == this.pathLists.size()) {
            this.pathLists.add(path);
            this.paintLists.add(paint);
            this.historyPointer++;
        } else {
            // On the way of Undo or Redo
            this.pathLists.set(this.historyPointer, path);
            this.paintLists.set(this.historyPointer, paint);
            this.historyPointer++;

            for (int i = this.historyPointer, size = this.paintLists.size(); i < size; i++) {
                this.pathLists.remove(this.historyPointer);
                this.paintLists.remove(this.historyPointer);
            }
        }

        this.text = "";
    }

    /**
     * This method is getter for canvas background color
     *
     * @return
     */
    public int getBaseColor() {
        return this.baseColor;
    }

    /**
     * This method is setter for canvas background color
     *
     * @param color
     */
    public void setBaseColor(int color) {
        this.baseColor = color;
    }

    /**
     * This method is getter for drawn text.
     *
     * @return
     */
    public String getText() {
        return this.text;
    }

    /**
     * This method is setter for drawn text.
     *
     * @param text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * This method is getter for stroke or fill.
     *
     * @return
     */
    public Paint.Style getPaintStyle() {
        return this.paintStyle;
    }

    /**
     * This method is setter for stroke or fill.
     *
     * @param style
     */
    public void setPaintStyle(Paint.Style style) {
        this.paintStyle = style;
    }

    /**
     * This method is getter for stroke color.
     *
     * @return
     */
    public int getPaintStrokeColor() {
        return this.paintStrokeColor;
    }

    /**
     * This method is setter for stroke color.
     *
     * @param color
     */
    public void setPaintStrokeColor(int color) {
        this.paintStrokeColor = color;
    }

    /**
     * This method is getter for fill color.
     * But, current Android API cannot set fill color (?).
     *
     * @return
     */
    public int getPaintFillColor() {
        return this.paintFillColor;
    }

    /**
     * This method is setter for fill color.
     * But, current Android API cannot set fill color (?).
     *
     * @param color
     */
    public void setPaintFillColor(int color) {
        this.paintFillColor = color;
    }

    /**
     * This method is getter for stroke width.
     *
     * @return
     */
    public float getPaintStrokeWidth() {
        return this.paintStrokeWidth;
    }

    /**
     * This method is setter for stroke width.
     *
     * @param width
     */
    public void setPaintStrokeWidth(float width) {
        if (width >= 0) {
            this.paintStrokeWidth = width;
        } else {
            this.paintStrokeWidth = 3F;
        }
    }

    /**
     * This method is getter for alpha.
     *
     * @return
     */
    public int getOpacity() {
        return this.opacity;
    }

    /**
     * This method is setter for alpha.
     * The 1st argument must be between 0 and 255.
     *
     * @param opacity
     */
    public void setOpacity(int opacity) {
        if ((opacity >= 0) && (opacity <= 255)) {
            this.opacity = opacity;
        } else {
            this.opacity = 255;
        }
    }

    /**
     * This method is getter for amount of blur.
     *
     * @return
     */
    public float getBlur() {
        return this.blur;
    }

    /**
     * This method is setter for amount of blur.
     * The 1st argument is greater than or equal to 0.0.
     *
     * @param blur
     */
    public void setBlur(float blur) {
        if (blur >= 0) {
            this.blur = blur;
        } else {
            this.blur = 0F;
        }
    }

    /**
     * This method is getter for line cap.
     *
     * @return
     */
    public Paint.Cap getLineCap() {
        return this.lineCap;
    }

    /**
     * This method is setter for line cap.
     *
     * @param cap
     */
    public void setLineCap(Paint.Cap cap) {
        this.lineCap = cap;
    }

    /**
     * This method is getter for font size,
     *
     * @return
     */
    public float getFontSize() {
        return this.fontSize;
    }

    /**
     * This method is setter for font size.
     * The 1st argument is greater than or equal to 0.0.
     *
     * @param size
     */
    public void setFontSize(float size) {
        if (size >= 0F) {
            this.fontSize = size;
        } else {
            this.fontSize = 32F;
        }
    }

    /**
     * This method is getter for font-family.
     *
     * @return
     */
    public Typeface getFontFamily() {
        return this.fontFamily;
    }

    /**
     * This method is setter for font-family.
     *
     * @param face
     */
    public void setFontFamily(Typeface face) {
        this.fontFamily = face;
    }

    /**
     * This method gets current canvas as bitmap.
     *
     * @return This is returned as bitmap.
     */
    public Bitmap getBitmap() {
        this.setDrawingCacheEnabled(false);
        this.setDrawingCacheEnabled(true);

        return Bitmap.createBitmap(this.getDrawingCache());
    }

    /**
     * This method gets current canvas as scaled bitmap.
     *
     * @return This is returned as scaled bitmap.
     */
    public Bitmap getScaleBitmap(int w, int h) {
        this.setDrawingCacheEnabled(false);
        this.setDrawingCacheEnabled(true);

        return Bitmap.createScaledBitmap(this.getDrawingCache(), w, h, true);
    }

    /**
     * This method draws the designated bitmap to canvas.
     *
     * @param bitmap
     */
    public void drawBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        //this.invalidate();
    }

    /**
     * This method draws the designated byte array of bitmap to canvas.
     *
     * @param byteArray This is returned as byte array of bitmap.
     */
    public void drawBitmap(byte[] byteArray) {
        this.drawBitmap(BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length));
    }

    /**
     * This method gets the bitmap as byte array.
     *
     * @param format
     * @param quality
     * @return This is returned as byte array of bitmap.
     */
    public byte[] getBitmapAsByteArray(CompressFormat format, int quality) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        this.getBitmap().compress(format, quality, byteArrayOutputStream);

        return byteArrayOutputStream.toByteArray();
    }

    /**
     * This method gets the bitmap as byte array.
     * Bitmap format is PNG, and quality is 100.
     *
     * @return This is returned as byte array of bitmap.
     */
    public byte[] getBitmapAsByteArray() {
        return this.getBitmapAsByteArray(CompressFormat.PNG, 100);
    }

    @Override
    public void run() {
        while (okToDraw == true) {
            //perfom convas drawing
            if (!surfaceHolder.getSurface().isValid()) {
                continue;
            }
            //drawFullScreen();
            Canvas canvas = null;
            try {
                {
                    Rect rect = new Rect(0, 0, getWidth(), getHeight());
                    canvas = surfaceHolder.lockCanvas(rect);
                }
                synchronized (surfaceHolder) {
                    if (canvas != null) {
                        drawFullScreen(canvas);
                    }
                }
            } finally {
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }

    }

    public void pause() {
        okToDraw = false;
        while (true) {
            try {
                drawThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            break;
        }
        drawThread = null;

    }

    public void resume() {
        okToDraw = true;
        drawThread = new Thread(this);
        drawThread.start();

    }

    // Enumeration for Mode
    public enum Mode {
        DRAW,
        TEXT,
        ERASER,
        NOTHING
    }


    // Enumeration for Drawer
    public enum Drawer {
        PEN,
        SMOOTH_PEN, // use cubic_bezier line
        LINE,
        RECTANGLE,
        CIRCLE,
        ELLIPSE,
        QUADRATIC_BEZIER,
        QUBIC_BEZIER
    }

    private static class RenderRect {
        public Rect redrawRect;
        private int currentRenderedX;
        private int currentRenderedY;
        private int strokeSize;

        public RenderRect(int x, int y, int size) {
            this.redrawRect = new Rect(x - size, y - size, x + size, y + size);
            currentRenderedX = x;
            currentRenderedY = y;
            strokeSize = size;
        }

        public void update(int x, int y, int size) {
            if (redrawRect.left > x - size) {
                redrawRect.left = (x - size);
            }
            if (redrawRect.right < x + size) {
                redrawRect.right = (x + size);
            }
            if (redrawRect.top > y - size) {
                redrawRect.top = (y - size);
            }
            if (redrawRect.bottom < y + size) {
                redrawRect.bottom = (y + size);
            }

            currentRenderedX = x;
            currentRenderedY = y;
            strokeSize = size;
        }

        public void reset() {
            redrawRect.left = currentRenderedX - strokeSize;
            redrawRect.right = currentRenderedX + strokeSize;
            redrawRect.top = currentRenderedY - strokeSize;
            redrawRect.bottom = currentRenderedY + strokeSize;

        }
    }

    class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            //Log.d("Scale0", "" + mScaleFactor + " " + detector.getScaleFactor());

            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 20.0f));

            Log.d("Scale 1", "" + mScaleFactor);
            applyCurrentTranslationScale();
            return true;
        }
    }


}
