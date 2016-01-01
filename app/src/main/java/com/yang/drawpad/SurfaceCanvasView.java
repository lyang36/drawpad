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
    // the maximum number of paths to undo
    private static final int MAX_NUM_PATHS = 200;
    private static final float TOUCH_TOLERANCE = 5;
    int pathPointCounter = 0;
    private Thread drawThread = null;
    // the bitmap to record the paths when exceed max_num_paths
    private Bitmap overflowBitmap = null;
    // the current screen bitmap
    private Bitmap currentScreenBitMap = null;
    private float mScaleFactor = 1.f;
    private int scalePivotX = 0;
    private int scalePivotY = 0;
    private ScaleGestureDetector scaleGestureDetector;
    // the draw bound
    private Rect drawBound;
    private Context context = null;
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
    private Drawer drawer = Drawer.PEN;
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
    private float translateX = 0F;
    private float translateY = 0F;
    // to draw the spline
    private float prevX, prevY;
    private float prevX1, prevY1;
    private BezierCurveConstructor bezierCurveConstructor;

    // move the view
    private boolean isTwoFingerDown = false;
    private float twoFingerStartX;
    private float twoFingerStartY;
    private float currentTranslateX = 0F;
    private float currentTranslateY = 0F;
    private SurfaceHolder surfaceHolder;
    private boolean okToDraw = false;
    private boolean isRedrawBackground = false;
    private boolean isAddNewestPath = false;


    private long mLastTime = 0;
    private int fps = 0, ifps = 0;

    // when drawing,
    // only the updated rectangle need to be redrawed
    private RenderRect renderRect;

    private Rect testRect;

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
    //private Rect currentRect;

    /**
     * This static method gets the designated bitmap as byte array.
     *
     * @param bitmap
     * @param format
     * @param quality
     * @return This is returned as byte array of bitmap.
     */
    public static byte[] getBitmapAsByteArray(Bitmap bitmap, CompressFormat format, int quality) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(format, quality, byteArrayOutputStream);

        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Common initialization.
     *
     * @param context
     */
    private void setup(Context context) {
        this.context = context;

        /*this.pathLists.add(new Path());
        this.paintLists.add(this.createPaint());
        this.historyPointer++;*/
        addPath(new Path(), this.createPaint());

        this.textPaint.setARGB(0, 255, 255, 255);
        this.drawBound = new Rect(0, 0, 0, 0);


        scaleGestureDetector = new ScaleGestureDetector(context,
                new ScaleListener());

        surfaceHolder = getHolder();
    }

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

    public void setTranslate(float X, float Y) {
        this.translateX = X;
        this.translateY = Y;
        //this.invalidate();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    public void setScaleFactor(float scaleFactor) {
        this.mScaleFactor = scaleFactor;
        //this.invalidate();
    }

    public void setScaleGestureDetector(ScaleGestureDetector scaleGestureDetector) {
        this.scaleGestureDetector = scaleGestureDetector;
    }

    public void setDrawBound(Rect drawBound) {
        this.drawBound = drawBound;
        //this.invalidate();
    }

    public void setDrawBound(int left, int top, int right, int bottom) {
        this.drawBound = new Rect(left, top, right, bottom);
        //this.invalidate();
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
        paint.setStrokeJoin(Paint.Join.ROUND);    // set the join to round you want
        paint.setStrokeCap(Paint.Cap.ROUND);      // set the paint cap to round too
        //paint.set
        //paint.setPathEffect(new CornerPathEffect(this.paintStrokeWidth * 2));   // set the path effect when they join.

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

            // paint.setColor(this.baseColor);
            // paint.setShadowLayer(this.blur, 0F, 0F, this.baseColor);
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

        // Save for ACTION_MOVE
        this.startX = getEventX(event);
        this.startY = getEventY(event);
        prevX = startX;
        prevY = startY;
        prevX1 = prevX;
        prevY1 = prevY;
        pathPointCounter = 0;



        path.moveTo(this.startX, this.startY);

        bezierCurveConstructor.addPoint(this.startX, this.startY);

        return path;
    }

    private float convertCoorX(float x) {
        return (x - translateX - scalePivotX) / mScaleFactor + scalePivotX;// - drawBound.left * mScaleFactor;
    }

    private float convertCoorY(float y) {
        return (y - translateY - scalePivotY) / mScaleFactor + scalePivotY;// - drawBound.left * mScaleFactor;
    }

    private float getEventX(MotionEvent event) {
        return convertCoorX(event.getX());// - drawBound.left * mScaleFactor;
    }

    private float getEventY(MotionEvent event) {
        return convertCoorY(event.getY());
    }

    /**
     * This method updates the lists for the instance of Path and Paint.
     * "Undo" and "Redo" are enabled by this method.
     *
     * @param path the instance of Path
     */
    private void updateHistory(Path path) {
        if (this.historyPointer == this.pathLists.size()) {
            /*this.pathLists.add(path);
            this.paintLists.add(this.createPaint());
            this.historyPointer++;*/
            addPath(path, this.createPaint());
        } else {
            // On the way of Undo or Redo
            /*this.pathLists.set(this.historyPointer, path);
            this.paintLists.set(this.historyPointer, this.createPaint());
            this.historyPointer++;*/
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
        if (renderRect == null) {
            renderRect = new RenderRect((int) event.getX(), (int) event.getY(), (int) paintStrokeWidth);
        }
        renderRect.update((int) event.getX(), (int) event.getY(), (int) paintStrokeWidth);
        renderRect.reset();

        switch (this.mode) {
            case DRAW:
            case ERASER:
                if ((this.drawer != Drawer.QUADRATIC_BEZIER) && (this.drawer != Drawer.QUBIC_BEZIER)) {


                    pathPointCounter = 0;
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
                        this.controlX = getEventX(event);
                        this.controlY = getEventY(event);

                        this.isDown = true;
                    }
                }

                break;
            case TEXT:
                this.startX = getEventX(event);
                this.startY = getEventY(event);

                break;
            default:
                break;
        }
    }

    private void addPointToPath(float x, float y, Path path) {

        float dx = Math.abs(x - prevX);
        float dy = Math.abs(y - prevY);

        // this is crucial to draw a reasonable path
        if (!(dx >= TOUCH_TOLERANCE / mScaleFactor || dy >= TOUCH_TOLERANCE / mScaleFactor)) {
            return;
        }
        //path.quadTo(prevX, prevY, (x + prevX)/2, (y + prevY)/2);
        bezierCurveConstructor.addPoint(x, y);


        float d0x, d0y;
        float d1x, d1y;
        if (pathPointCounter < 2) {
            // less than 3 points, not drawing
        } else {
            //d1x = ((prevX - prevX1) / 3);
            //d1y = ((prevY - prevY1) / 3);
            d0x = ((x - prevX1) / 5);
            d0y = ((y - prevY1) / 5);
            //path.cubicTo(prevX + d0x, prevY + d0y, x - d0x, y - d0y, x, y);
        }

        prevX1 = prevX;
        prevY1 = prevY;
        prevX = x;
        prevY = y;
        pathPointCounter++;
        //path.lineTo(x, y);
    }


    /**
     * This method defines processes on MotionEvent.ACTION_MOVE
     *
     * @param event This is argument of onTouchEvent method
     */
    private void onActionMove(MotionEvent event) {
        float x = getEventX(event);
        float y = getEventY(event);


        renderRect.update((int) event.getX(), (int) event.getY(), (int) paintStrokeWidth);

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
                            int historySize = event.getHistorySize();
                            /*for (int i = 0; i < historySize; i++) {
                                float historicalX = convertCoorX(event.getHistoricalX(i));
                                float historicalY = convertCoorY(event.getHistoricalY(i));

                                //path.quadTo(prevX, prevY, historicalX, historicalY);
                                addPointToPath(historicalX, historicalY, path);
                            }*/
                            addPointToPath(x, y, path);

                            this.pathLists.set(this.historyPointer - 1,
                                    bezierCurveConstructor.constructPath());
                            //path.lineTo(x, y);
                            //path.quadTo(prevX, prevY, x, y);

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
            //add the newest path to the drawpad
            //drawBitMap(this.historyPointer - 1);
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

        canvas.translate(translateX, translateY);
        canvas.scale(mScaleFactor, mScaleFactor, scalePivotX, scalePivotY);
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
        }
        //put a hole in the current clip
        canvas.clipRect(drawBound, Region.Op.DIFFERENCE);
        //fill with plain color at the plain region
        canvas.drawColor(plainColor);
        //restore full canvas clip for any subsequent operations
        canvas.clipRect(new Rect(0, 0, canvas.getWidth(), canvas.getHeight())
                , Region.Op.REPLACE);


        canvas.restore();

        //test
        if (testRect != null) {
            Paint p = new Paint(Color.BLACK);
            p.setStyle(Paint.Style.STROKE);
            canvas.drawRect(testRect, p);
        }


        long now = System.currentTimeMillis();
        ifps++;
        if (now > (mLastTime + 1000)) {
            mLastTime = now;
            fps = ifps;
            ifps = 0;
            Log.d("FPS:", "" + fps);
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
        //Log.d("PointA", "" + (event.getAction() == MotionEvent.ACTION_POINTER_DOWN));
        //float initTransX = translateX;
        //float initTransY = translateY;
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_POINTER_DOWN:
                Log.d("PointDown", "" + event.getPointerCount());
                if (event.getPointerCount() == 2) {
                    isTwoFingerDown = true;
                    twoFingerStartX = event.getX();
                    twoFingerStartY = event.getY();

                    scalePivotX = (int) event.getX();
                    scalePivotY = (int) event.getY();

                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 2) {
                    if (isTwoFingerDown) {
                        translateX =
                                (event.getX() - twoFingerStartX)
                                        + currentTranslateX;
                        translateY =
                                (event.getY() - twoFingerStartY)
                                        + currentTranslateY;
                        //invalidate();
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
                //invalid the move
                //translateX = initTransX;
                //translateY = initTransY;

                if (isDown) {
                    // undo the current drawing
                    onActionUp(event);
                    undo();
                }
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

            // redraw the screen
            //drawBitMap(-1);
            // invalidates
            requestRedrawBackground();
            //this.invalidate();

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
            //this.invalidate();

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

        // Clear
        //this.invalidate();
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

                /*if(isDown) {
                    //canvas = surfaceHolder.lockCanvas(null);
                    synchronized (renderRect) {
                        canvas = surfaceHolder.lockCanvas(renderRect.redrawRect);
                        testRect = renderRect.redrawRect;
                        //renderRect.reset();
                    }
                }else*/
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
        private float mScaleFactor = 1.f;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            //Log.d("Scale0", "" + mScaleFactor + " " + detector.getScaleFactor());

            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 5.0f));

            setScaleFactor(mScaleFactor);
            //invalidate();
            return true;
        }
    }


}
